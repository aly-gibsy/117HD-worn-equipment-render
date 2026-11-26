package rs117.hdequiptest.renderer.zone;

import org.junit.Test;
import rs117.hdequiptest.utils.Camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EquipmentPreviewCameraTest {
	private static final int WIDTH = 400;
	private static final int HEIGHT = 600;
	private static final float X = 6400, GROUND_Y = -120, Z = 7000;
	private static final float CHARACTER_HEIGHT = 200;

	/** Projects a world point to pixel offsets from the viewport center, with y pointing down, and depth. */
	private static float[] toPixels(Camera camera, float x, float y, float z) {
		float[] p = camera.project(x, y, z);
		return new float[] { camera.getZoom() * p[0] / p[2], camera.getZoom() * p[1] / p[2], p[2] };
	}

	private static Camera camera(float zoomScale) {
		Camera camera = new Camera().setReverseZ(true);
		EquipmentPreviewRenderer.configureCamera(camera, X, GROUND_Y, Z, WIDTH, HEIGHT, zoomScale);
		return camera;
	}

	@Test
	public void characterFitsInsideViewportAndIsUpright() {
		Camera camera = camera(1);
		float[] feet = toPixels(camera, X, GROUND_Y, Z);
		// RuneLite's Y axis points down, so the head has a smaller Y than the feet
		float[] head = toPixels(camera, X, GROUND_Y - CHARACTER_HEIGHT, Z);

		assertTrue("feet should be in front of the camera", feet[2] > 0);
		assertTrue("head should be in front of the camera", head[2] > 0);
		assertEquals("character should be horizontally centered", 0, feet[0], 1e-3);
		assertTrue("head should be drawn above the feet", head[1] < feet[1]);
		assertTrue("feet should be inside the bottom edge", feet[1] < HEIGHT / 2f);
		assertTrue("head should be inside the top edge", head[1] > -HEIGHT / 2f);
		assertTrue("character should fill most of the height", feet[1] - head[1] > HEIGHT * .6f);
	}

	@Test
	public void cameraLooksNorthFromTheSouth() {
		Camera camera = camera(1);
		float[] south = toPixels(camera, X, GROUND_Y, Z - 64);
		float[] north = toPixels(camera, X, GROUND_Y, Z + 64);
		assertTrue("the south side of the character should be nearer the camera", south[2] < north[2]);
		assertTrue("the camera should be south of the character", camera.getPositionZ() < Z);

		// Facing north, east (+X) should appear on the right
		float[] east = toPixels(camera, X + 64, GROUND_Y, Z);
		assertTrue(east[0] > 0);
	}

	@Test
	public void zoomScalesTheCharacter() {
		float[] normal = toPixels(camera(1), X, GROUND_Y - CHARACTER_HEIGHT, Z);
		float[] zoomed = toPixels(camera(2), X, GROUND_Y - CHARACTER_HEIGHT, Z);
		assertTrue(Math.abs(zoomed[1]) > Math.abs(normal[1]));
	}
}
