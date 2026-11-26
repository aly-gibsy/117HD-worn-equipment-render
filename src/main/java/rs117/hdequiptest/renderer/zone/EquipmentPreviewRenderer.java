package rs117.hdequiptest.renderer.zone;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.function.IntPredicate;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.lwjgl.system.MemoryStack;
import rs117.hdequiptest.HdEquipTestPlugin;
import rs117.hdequiptest.HdPluginConfig;
import rs117.hdequiptest.config.EquipmentPreviewPose;
import rs117.hdequiptest.opengl.shader.EquipmentPreviewProgram;
import rs117.hdequiptest.opengl.shader.SceneShaderProgram;
import rs117.hdequiptest.opengl.shader.ShaderException;
import rs117.hdequiptest.opengl.shader.ShaderIncludes;
import rs117.hdequiptest.renderer.SkyRenderer;
import rs117.hdequiptest.scene.EnvironmentManager;
import rs117.hdequiptest.scene.model_overrides.ModelOverride;
import rs117.hdequiptest.utils.Camera;
import rs117.hdequiptest.utils.buffer.GLBuffer;
import rs117.hdequiptest.utils.collections.PooledArrayType;
import rs117.hdequiptest.utils.collections.PrimitiveCharArray;

import static org.lwjgl.opengl.GL33C.*;
import static rs117.hdequiptest.renderer.zone.DynamicModelVAO.METADATA_SIZE;
import static rs117.hdequiptest.renderer.zone.DynamicModelVAO.VERT_SIZE_INTS;
import static rs117.hdequiptest.renderer.zone.ZoneRenderer.FRAMES_IN_FLIGHT;
import static rs117.hdequiptest.renderer.zone.ZoneRenderer.TEXTURE_UNIT_EQUIPMENT_PREVIEW;
import static rs117.hdequiptest.renderer.zone.ZoneRenderer.TEXTURE_UNIT_TEXTURED_FACES;
import static rs117.hdequiptest.utils.MathUtils.*;

/**
 * Replaces the character preview in the Equip Your Character interface with the local player's model,
 * rendered through 117 HD's scene shader and lit by the environment where the player is standing.
 * <p>
 * Interface models are rasterized by the game's software renderer into the 2D UI, which GPU plugins never
 * see. So this hides the vanilla model widget, renders the player offscreen at their real world position
 * (so sun, ambient, fog and nearby point lights all apply), and composites the result over the UI.
 */
@Slf4j
@Singleton
public class EquipmentPreviewRenderer {
	// Supersampling factor, resolved by bilinear filtering when compositing
	private static final int SUPERSAMPLING = 2;
	private static final int MAX_TEXTURE_SIZE = 2048;
	// Vertical extent framed by the preview, in local units, at 100% zoom. Fixed so idle animations don't pulse the zoom.
	private static final float FRAMED_HEIGHT = 270;
	private static final float CAMERA_DISTANCE = 1500;
	private static final float CAMERA_PITCH = 0.08f; // Radians, slightly looking down like the vanilla preview
	private static final float NEAR_PLANE = 50;
	private static final int DRAG_JAU_PER_PIXEL = 4;
	private static final int MIN_PREVIEW_SIZE = 32;
	private static final int[] EQUIPMENT_SLOTS = {
		InterfaceID.Equipment.SLOT0, InterfaceID.Equipment.SLOT1, InterfaceID.Equipment.SLOT2,
		InterfaceID.Equipment.SLOT3, InterfaceID.Equipment.SLOT4, InterfaceID.Equipment.SLOT5,
		InterfaceID.Equipment.SLOT7, InterfaceID.Equipment.SLOT9, InterfaceID.Equipment.SLOT10,
		InterfaceID.Equipment.SLOT12, InterfaceID.Equipment.SLOT13
	};
	// The topmost and bottommost contents of the panel: the first bonus heading and the weapon speed line
	private static final int[] VERTICAL_EXTENT_WIDGETS = {
		InterfaceID.Equipment.STAT_GROUP_TEXT0, InterfaceID.Equipment.SLOT0,
		InterfaceID.Equipment.ATTACKSPEEDBASE, InterfaceID.Equipment.ATTACKSPEEDACTUAL
	};
	private static final int PANEL_PADDING = 6;
	private static final int[] STAT_TEXTS = {
		InterfaceID.Equipment.STAT_GROUP_TEXT0, InterfaceID.Equipment.STABATT, InterfaceID.Equipment.SLASHATT,
		InterfaceID.Equipment.CRUSHATT, InterfaceID.Equipment.STAT_GROUP_TEXT6, InterfaceID.Equipment.STABDEF,
		InterfaceID.Equipment.MELEESTRENGTH, InterfaceID.Equipment.PRAYER
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private EquipmentPreviewPoseMenu poseMenu;

	@Inject
	private HdEquipTestPlugin plugin;

	@Inject
	private HdPluginConfig config;

	@Inject
	private SkyRenderer skyRenderer;

	@Inject
	private EnvironmentManager environmentManager;

	@Inject
	private EquipmentPreviewProgram compositeProgram;

	/**
	 * The scene shader, compiled without tiled lighting, since the tiles are computed for the main camera.
	 */
	@Inject
	private SceneShaderProgram previewSceneProgram;

	private final Camera camera = new Camera().setReverseZ(true);
	private final DynamicModelVAO[] vaos = new DynamicModelVAO[FRAMES_IN_FLIGHT];
	private final PrimitiveCharArray visibleFaces = new PrimitiveCharArray();
	private final PrimitiveCharArray culledFaces = new PrimitiveCharArray();
	private final int[] worldPos = new int[3];

	private GLBuffer vboMetadata;
	private int fbo;
	private int texColor;
	private int rboDepth;
	private int fboWidth;
	private int fboHeight;

	/** The vanilla model widget currently hidden by us, only touched on the client thread. */
	@Nullable
	private Widget hiddenWidget;
	/** Canvas-space bounds of the preview, or null when the interface isn't open. */
	@Nullable
	private volatile Rectangle previewBounds;
	private volatile int userYaw;
	private final EquipmentPreviewSway sway = new EquipmentPreviewSway();
	@Nullable
	private EquipmentPreviewPose currentPose;
	private long poseStartNanos;
	@Nullable
	private Animation cachedAnimation;
	private int cachedAnimationId = -1;
	private float[] blendX = new float[0];
	private float[] blendY = new float[0];
	private float[] blendZ = new float[0];
	private int dragStartX;
	private int dragStartYaw;
	private boolean dragging;

	private final MouseAdapter dragRotation = new MouseAdapter() {
		@Override
		public MouseEvent mousePressed(MouseEvent e) {
			if (poseMenu.mousePressed(e))
				return e;
			Rectangle bounds = poseMenu.modelBounds();
			if (bounds == null || !bounds.contains(e.getPoint()))
				return e;
			dragging = true;
			dragStartX = e.getX();
			dragStartYaw = userYaw;
			sway.pause(System.nanoTime());
			e.consume();
			return e;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent e) {
			if (!dragging)
				return e;
			userYaw = mod(dragStartYaw + (e.getX() - dragStartX) * DRAG_JAU_PER_PIXEL, 2048);
			sway.pause(System.nanoTime());
			e.consume();
			return e;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent e) {
			if (poseMenu.mouseReleased(e) || !dragging)
				return e;
			dragging = false;
			sway.pause(System.nanoTime());
			e.consume();
			return e;
		}

		@Override
		public MouseEvent mouseMoved(MouseEvent e) {
			poseMenu.mouseMoved(e);
			return e;
		}
	};

	public void initialize() {
		vboMetadata = new GLBuffer("EquipmentPreviewMetadata", GL_ARRAY_BUFFER, GL_STATIC_DRAW, 0);
		vboMetadata.initialize(METADATA_SIZE);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			// World view index 0 is the top-level scene, so no extra transform is applied
			IntBuffer buf = stack.mallocInt(3);
			buf.put(0).put(0).put(0).flip();
			vboMetadata.upload(buf);
		}

		for (int i = 0; i < vaos.length; i++) {
			vaos[i] = new DynamicModelVAO("EquipmentPreview", false);
			vaos[i].initialize();
			vaos[i].bindMetadataVAO(vboMetadata);
		}

		eventBus.register(this);
		mouseManager.registerMouseListener(dragRotation);
		mouseManager.registerMouseWheelListener(poseMenu);
		overlayManager.add(poseMenu);
	}

	public void destroy() {
		overlayManager.remove(poseMenu);
		mouseManager.unregisterMouseWheelListener(poseMenu);
		mouseManager.unregisterMouseListener(dragRotation);
		eventBus.unregister(this);
		previewBounds = null;
		poseMenu.setArea(null);
		clientThread.invoke(this::restoreVanillaWidget);

		destroyFbo();
		for (int i = 0; i < vaos.length; i++) {
			if (vaos[i] != null)
				vaos[i].destroy();
			vaos[i] = null;
		}
		if (vboMetadata != null)
			vboMetadata.destroy();
		vboMetadata = null;
	}

	public void initializeShaders(ShaderIncludes includes) throws ShaderException, IOException {
		previewSceneProgram.compile(includes.copy().define("TILED_LIGHTING", false));
		compositeProgram.compile(includes);
	}

	public void destroyShaders() {
		previewSceneProgram.destroy();
		compositeProgram.destroy();
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event) {
		// Hide the vanilla model before the client draws the interface, so it never shows under ours
		Widget root = config.hdEquipmentPreview() ? client.getWidget(InterfaceID.Equipment.UNIVERSE) : null;
		Widget widget = root == null || root.isHidden() ? null : findPlayerModel(root);
		if (widget != hiddenWidget)
			restoreVanillaWidget();

		if (widget == null) {
			previewBounds = null;
			poseMenu.setArea(null);
			return;
		}

		widget.setHidden(true);
		hiddenWidget = widget;
		Rectangle area = findPreviewArea();
		Rectangle bounds = area != null ? area : widget.getBounds();
		if (!bounds.equals(previewBounds))
			log.info("Equipment preview area: {} (from {}), canvas {}x{}",
				bounds, area != null ? "interface layout" : "model widget", client.getCanvasWidth(), client.getCanvasHeight());
		previewBounds = bounds;
		poseMenu.setArea(bounds);
	}

	private void restoreVanillaWidget() {
		if (hiddenWidget != null)
			hiddenWidget.setHidden(false);
		hiddenWidget = null;
	}

	/**
	 * The empty column between the equipment slots and the bonuses, where the vanilla model appears.
	 * The model widget's own bounds don't match where the game draws the model, so they can't be used.
	 * Each edge falls back to a share of the interface's width, so a changed layout still lands near the middle.
	 *
	 * @return the area in canvas coordinates, or null if the interface's bounds aren't known yet
	 */
	@Nullable
	private Rectangle findPreviewArea() {
		Rectangle frame = boundsOf(InterfaceID.Equipment.CONTENTS);
		if (frame == null)
			frame = boundsOf(InterfaceID.Equipment.UNIVERSE);
		if (frame == null)
			return null;

		int left = Integer.MIN_VALUE;
		for (int slotId : EQUIPMENT_SLOTS) {
			Rectangle slot = boundsOf(slotId);
			if (slot != null && frame.contains(slot))
				left = Math.max(left, slot.x + slot.width);
		}
		if (left == Integer.MIN_VALUE)
			left = frame.x + frame.width / 3;

		int right = Integer.MAX_VALUE;
		for (int statId : STAT_TEXTS) {
			Rectangle stat = boundsOf(statId);
			if (stat != null && frame.contains(stat) && stat.x > left)
				right = Math.min(right, stat.x);
		}
		if (right == Integer.MAX_VALUE)
			right = frame.x + frame.width * 2 / 3;

		// The frame includes the title bar and border, so take the height from the panel's contents instead
		int top = Integer.MAX_VALUE;
		int bottom = Integer.MIN_VALUE;
		for (int id : VERTICAL_EXTENT_WIDGETS) {
			Rectangle b = boundsOf(id);
			if (b != null && frame.contains(b)) {
				top = Math.min(top, b.y);
				bottom = Math.max(bottom, b.y + b.height);
			}
		}
		if (top == Integer.MAX_VALUE) {
			top = frame.y;
			bottom = frame.y + frame.height;
		} else {
			top = Math.max(frame.y, top - PANEL_PADDING);
			bottom = Math.min(frame.y + frame.height, bottom + PANEL_PADDING);
		}

		if (right - left < MIN_PREVIEW_SIZE || bottom - top < MIN_PREVIEW_SIZE)
			return null;
		return new Rectangle(left, top, right - left, bottom - top);
	}

	/**
	 * @return the widget's canvas bounds, or null if it doesn't exist or hasn't been laid out
	 */
	@Nullable
	private Rectangle boundsOf(int widgetId) {
		Widget widget = client.getWidget(widgetId);
		if (widget == null)
			return null;
		Rectangle bounds = widget.getBounds();
		return bounds.width > 0 && bounds.height > 0 ? bounds : null;
	}

	@Nullable
	private Widget findPlayerModel(Widget widget) {
		if (widget.getType() == WidgetType.MODEL && isPlayerModelType(widget.getModelType())) {
			// Skip models hidden by the game, but not the one we hid ourselves
			return widget == hiddenWidget || !widget.isHidden() ? widget : null;
		}

		for (Widget[] children : new Widget[][] {
			widget.getStaticChildren(),
			widget.getDynamicChildren(),
			widget.getNestedChildren()
		}) {
			if (children == null)
				continue;
			for (Widget child : children) {
				if (child == null)
					continue;
				Widget found = findPlayerModel(child);
				if (found != null)
					return found;
			}
		}
		return null;
	}

	private static boolean isPlayerModelType(int modelType) {
		return modelType == WidgetModelType.LOCAL_PLAYER_CHATHEAD || modelType == WidgetModelType.PLAYER;
	}

	/**
	 * Must be called after the scene and UI have been drawn for the current frame, with the default framebuffer bound.
	 */
	public void render() {
		Rectangle bounds = previewBounds == null ? null : poseMenu.modelBounds();
		int[] uiResolution = plugin.getUiResolution();
		Player player = client.getLocalPlayer();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0 || uiResolution == null || player == null ||
			!previewSceneProgram.isValid() || !compositeProgram.isValid())
			return;

		Model model = getPosedModel(player);
		LocalPoint location = player.getLocalLocation();
		if (model == null || location == null)
			return;

		int[] actualUiResolution = plugin.getActualUiResolution();
		int width = clamp(round(bounds.width * SUPERSAMPLING * (float) actualUiResolution[0] / uiResolution[0]), 1, MAX_TEXTURE_SIZE);
		int height = clamp(round(bounds.height * SUPERSAMPLING * (float) actualUiResolution[1] / uiResolution[1]), 1, MAX_TEXTURE_SIZE);
		ensureFbo(width, height);

		float x = location.getX();
		float z = location.getY();
		float y = Perspective.getTileHeight(client, location, player.getWorldView().getPlane());
		configureCamera(camera, x, y, z, width, height, config.equipmentPreviewZoom() / 100f);

		DynamicModelVAO vao = vaos[plugin.frame % FRAMES_IN_FLIGHT];
		int[] drawRange = uploadModel(vao, model, x, y, z);
		if (drawRange == null)
			return;

		try {
			overrideGlobalUniforms(width, height);
			drawPreview(vao, drawRange, width, height);
			composite(bounds, poseMenu.listBounds(), uiResolution, actualUiResolution);
		} finally {
			restoreGlobalUniforms();
			restoreGlState(actualUiResolution);
		}
	}

	/**
	 * Builds the player's model in the pose picked from the dropdown.
	 * <p>
	 * There's no API for a player model without its animation, so this briefly swaps the player's pose animation
	 * and frame, asks the client for the model, and swaps them back. The pose animation is used rather than the
	 * action animation because setting the action animation fires {@link AnimationChanged} for other plugins.
	 * A playing action animation, such as skilling, still takes priority over the pose, like it does in game.
	 * <p>
	 * The client smooths animations by how far the player is through their real frame, which means nothing for the
	 * preview's frame. So the client's smoothing is turned off while the model is built, and when the Animation
	 * Smoothing plugin would smooth this animation, the preview blends between frames itself.
	 */
	@Nullable
	private Model getPosedModel(Player player) {
		EquipmentPreviewPose pose = config.equipmentPreviewPose();
		long now = System.nanoTime();
		if (pose != currentPose) {
			currentPose = pose;
			poseStartNanos = now;
		}

		int animationId = poseAnimationId(player, pose);
		Animation animation = animationId == -1 ? null : loadAnimation(animationId);
		if (animation == null)
			return player.getModel();

		EquipmentPreviewAnimation.Position position = EquipmentPreviewAnimation.positionAt(
			animation.isMayaAnim() ? null : animation.getFrameLengths(),
			animation.getNumFrames(),
			now - poseStartNanos
		);
		IntPredicate smoothingFilter = client.getAnimationInterpolationFilter();
		boolean smooth = smoothingFilter != null && smoothingFilter.test(animationId) &&
			position.nextFrame != position.frame && position.progress > 0;

		int realAnimation = player.getPoseAnimation();
		int realFrame = player.getPoseAnimationFrame();
		client.setAnimationInterpolationFilter(null);
		try {
			player.setPoseAnimation(animationId);
			if (!smooth)
				return modelAtFrame(player, position.frame);

			Model current = modelAtFrame(player, position.frame);
			if (current == null)
				return null;
			int vertexCount = current.getVerticesCount();
			copyVertices(current, vertexCount);
			// The client reuses one model for animated players, so this can overwrite the first frame's model
			Model next = modelAtFrame(player, position.nextFrame);
			if (next == null || next.getVerticesCount() != vertexCount)
				return modelAtFrame(player, position.frame);
			blend(blendX, next.getVerticesX(), vertexCount, position.progress);
			blend(blendY, next.getVerticesY(), vertexCount, position.progress);
			blend(blendZ, next.getVerticesZ(), vertexCount, position.progress);
			return next;
		} finally {
			player.setPoseAnimation(realAnimation);
			player.setPoseAnimationFrame(realFrame);
			client.setAnimationInterpolationFilter(smoothingFilter);
		}
	}

	@Nullable
	private static Model modelAtFrame(Player player, int frame) {
		player.setPoseAnimationFrame(frame);
		return player.getModel();
	}

	private void copyVertices(Model model, int vertexCount) {
		if (blendX.length < vertexCount) {
			blendX = new float[vertexCount];
			blendY = new float[vertexCount];
			blendZ = new float[vertexCount];
		}
		System.arraycopy(model.getVerticesX(), 0, blendX, 0, vertexCount);
		System.arraycopy(model.getVerticesY(), 0, blendY, 0, vertexCount);
		System.arraycopy(model.getVerticesZ(), 0, blendZ, 0, vertexCount);
	}

	/**
	 * Moves each of the first count values in to from where it was in from, by the given fraction.
	 */
	static void blend(float[] from, float[] to, int count, float progress) {
		for (int i = 0; i < count; i++)
			to[i] = from[i] + (to[i] - from[i]) * progress;
	}

	/**
	 * @return the animation to show for the pose, or -1 to show the player as they are in game
	 */
	private static int poseAnimationId(Player player, EquipmentPreviewPose pose) {
		switch (pose.getKind()) {
			case STAND:
				return player.getIdlePoseAnimation();
			case WALK:
				return player.getWalkAnimation();
			case RUN:
				return player.getRunAnimation();
			case EMOTE:
				return pose.getEmoteAnimation();
			default:
				return -1;
		}
	}

	@Nullable
	private Animation loadAnimation(int animationId) {
		if (animationId != cachedAnimationId) {
			cachedAnimationId = animationId;
			cachedAnimation = client.loadAnimation(animationId);
			if (cachedAnimation == null)
				log.warn("Equipment preview couldn't load animation {}", animationId);
		}
		return cachedAnimation;
	}

	/**
	 * Frames a character standing at the given position, viewed from the south like the vanilla preview.
	 */
	static void configureCamera(Camera camera, float x, float groundY, float z, int width, int height, float zoomScale) {
		// Y points down in RuneLite's coordinate system, so the frame's center is above the ground
		float targetY = groundY - FRAMED_HEIGHT * .45f;
		// Look north from the south side, which faces a model with orientation 0
		float sinPitch = sin(CAMERA_PITCH);
		float cosPitch = cos(CAMERA_PITCH);
		camera.setPosition(x, targetY - CAMERA_DISTANCE * sinPitch, z - CAMERA_DISTANCE * cosPitch);
		camera.setYaw(0);
		camera.setPitch(CAMERA_PITCH);
		camera.setNearPlane(NEAR_PLANE);
		camera.setViewportWidth(width);
		camera.setViewportHeight(height);
		// Zoom is in pixels per local unit at a depth of one unit
		camera.setZoom(height * CAMERA_DISTANCE / FRAMED_HEIGHT * zoomScale);
	}

	/**
	 * Writes the model's faces into the VAO with 117 HD's materials, model overrides and face sorting.
	 *
	 * @return the first vertex and the vertex count to draw, or null if nothing is visible
	 */
	@Nullable
	private int[] uploadModel(DynamicModelVAO vao, Model model, float x, float y, float z) {
		model.calculateBoundsCylinder();
		ModelOverride modelOverride = ModelOverride.NONE;
		int orientation = mod(userYaw + sway.update(System.nanoTime(), config.equipmentPreviewSway()), 2048);

		vao.map();
		int[] drawRange = null;
		try (
			SceneUploader sceneUploader = SceneUploader.POOL.acquire();
			FacePrioritySorter sorter = FacePrioritySorter.POOL.acquire()
		) {
			int[] faceDistances = PooledArrayType.INT.borrow(model.getFaceCount());
			try {
				boolean shouldSort = sceneUploader.preprocessTempModel(
					camera,
					camera.getFrustumPlanes(),
					faceDistances,
					visibleFaces,
					culledFaces,
					false,
					modelOverride,
					model,
					true,
					orientation,
					x, y, z
				);
				if (shouldSort)
					sorter.sortModelFaces(visibleFaces, model, faceDistances, false);
			} finally {
				PooledArrayType.INT.release(faceDistances);
			}

			if (visibleFaces.length > 0) {
				// Painter's order already handles transparency, so opaque and alpha faces share one view
				DynamicModelVAO.View view = vao.beginDraw(visibleFaces.length);
				sceneUploader.uploadTempModel(visibleFaces, model, modelOverride, 0, orientation, false, view, view);
				drawRange = new int[] { view.getStartOffset() / VERT_SIZE_INTS, view.getVertexCount() };
				view.end();
			}
		} finally {
			vao.unmap(false);
		}

		return drawRange == null || drawRange[1] == 0 ? null : drawRange;
	}

	private void overrideGlobalUniforms(int width, int height) {
		var ubo = plugin.uboGlobal;
		ubo.cameraPos.set(camera.getPosition());
		ubo.viewMatrix.set(camera.getViewMatrix());
		ubo.projectionMatrix.set(camera.getViewProjMatrix());
		ubo.invProjectionMatrix.set(camera.getInvViewProjMatrix());
		ubo.viewportSize.set(width, height);
		if (!config.equipmentPreviewShadows())
			ubo.castsShadows.set(0);
		// Ground fog thickens with distance from the camera, which would wash the far-away preview camera's view in fog color
		ubo.groundFogOpacity.set(0f);
		ubo.upload();
	}

	private void restoreGlobalUniforms() {
		var ubo = plugin.uboGlobal;
		ubo.cameraPos.set(plugin.cameraPosition);
		ubo.viewMatrix.set(plugin.viewMatrix);
		ubo.projectionMatrix.set(plugin.viewProjMatrix);
		ubo.invProjectionMatrix.set(plugin.invViewProjMatrix);
		if (plugin.sceneViewport != null)
			ubo.viewportSize.set(slice(plugin.sceneViewport, 2));
		ubo.castsShadows.set(skyRenderer.castsShadows ? 1 : 0);
		ubo.groundFogOpacity.set(config.groundFog() ? environmentManager.getCurrentEnvironment().groundFogOpacity : 0);
		ubo.upload();
	}

	private void drawPreview(DynamicModelVAO vao, int[] drawRange, int width, int height) {
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		glViewport(0, 0, width, height);
		glColorMask(true, true, true, true);
		if (config.equipmentPreviewDebugArea()) {
			glClearColor(.25f, 0, .25f, .25f); // Premultiplied translucent magenta
		} else {
			glClearColor(0, 0, 0, 0);
		}
		glClearDepth(0); // Reverse-Z
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		// Back faces were already culled on the CPU, using the preview camera
		glDisable(GL_CULL_FACE);
		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_GEQUAL);
		glDepthMask(true);
		glEnable(GL_BLEND);
		// Accumulate premultiplied color and coverage, so the result can be blended over the interface
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

		previewSceneProgram.use();
		glActiveTexture(TEXTURE_UNIT_TEXTURED_FACES);
		// Read after unmapping, since flushing can grow the buffer and replace its texture
		glBindTexture(GL_TEXTURE_BUFFER, vao.getTboTexId());
		glBindVertexArray(vao.getVao());
		glDrawArrays(GL_TRIANGLES, drawRange[0], drawRange[1]);

		glDisable(GL_DEPTH_TEST);
	}

	private void composite(Rectangle bounds, @Nullable Rectangle mask, int[] uiResolution, int[] actualUiResolution) {
		glBindFramebuffer(GL_FRAMEBUFFER, plugin.awtContext.getFramebuffer(false));
		glViewport(0, 0, actualUiResolution[0], actualUiResolution[1]);
		// Leave the default framebuffer's alpha untouched, like the UI pass does
		glColorMask(true, true, true, false);
		glEnable(GL_BLEND);
		glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);

		glActiveTexture(TEXTURE_UNIT_EQUIPMENT_PREVIEW);
		glBindTexture(GL_TEXTURE_2D, texColor);

		compositeProgram.use();
		// Canvas coordinates have their origin at the top left, NDC at the bottom left
		compositeProgram.uniTargetRect.set(
			(float) bounds.x / uiResolution[0] * 2 - 1,
			1 - (float) (bounds.y + bounds.height) / uiResolution[1] * 2,
			(float) (bounds.x + bounds.width) / uiResolution[0] * 2 - 1,
			1 - (float) bounds.y / uiResolution[1] * 2
		);
		compositeProgram.uniMaskRect.set(maskUv(bounds, mask));
		glBindVertexArray(plugin.vaoTri);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
	}

	/**
	 * Converts a canvas-space rectangle to the preview's texture coordinates, which have their origin at the bottom left.
	 *
	 * @return (minU, minV, maxU, maxV), empty if there's no mask or it doesn't overlap the preview
	 */
	static float[] maskUv(Rectangle bounds, @Nullable Rectangle mask) {
		Rectangle overlap = mask == null ? null : bounds.intersection(mask);
		if (overlap == null || overlap.isEmpty())
			return new float[4];
		return new float[] {
			(float) (overlap.x - bounds.x) / bounds.width,
			1 - (float) (overlap.y + overlap.height - bounds.y) / bounds.height,
			(float) (overlap.x + overlap.width - bounds.x) / bounds.width,
			1 - (float) (overlap.y - bounds.y) / bounds.height
		};
	}

	/**
	 * Leaves the state as {@link HdEquipTestPlugin#drawUi} does, so swapping buffers and the next frame are unaffected.
	 */
	private void restoreGlState(int[] actualUiResolution) {
		glBindVertexArray(0);
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
		glDisable(GL_BLEND);
		glDisable(GL_DEPTH_TEST);
		glColorMask(true, true, true, true);
		glBindFramebuffer(GL_FRAMEBUFFER, plugin.awtContext.getFramebuffer(false));
		glViewport(0, 0, actualUiResolution[0], actualUiResolution[1]);
	}

	private void ensureFbo(int width, int height) {
		if (fbo != 0 && width == fboWidth && height == fboHeight)
			return;

		destroyFbo();
		fboWidth = width;
		fboHeight = height;

		texColor = glGenTextures();
		glActiveTexture(TEXTURE_UNIT_EQUIPMENT_PREVIEW);
		glBindTexture(GL_TEXTURE_2D, texColor);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

		rboDepth = glGenRenderbuffers();
		glBindRenderbuffer(GL_RENDERBUFFER, rboDepth);
		glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT32F, width, height);
		glBindRenderbuffer(GL_RENDERBUFFER, 0);

		fbo = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texColor, 0);
		glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, rboDepth);
		int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
		glBindFramebuffer(GL_FRAMEBUFFER, plugin.awtContext.getFramebuffer(false));
		if (status != GL_FRAMEBUFFER_COMPLETE) {
			destroyFbo();
			throw new IllegalStateException(String.format("Equipment preview framebuffer is incomplete: 0x%X", status));
		}
	}

	private void destroyFbo() {
		if (fbo != 0)
			glDeleteFramebuffers(fbo);
		if (texColor != 0)
			glDeleteTextures(texColor);
		if (rboDepth != 0)
			glDeleteRenderbuffers(rboDepth);
		fbo = texColor = rboDepth = 0;
		fboWidth = fboHeight = 0;
	}
}
