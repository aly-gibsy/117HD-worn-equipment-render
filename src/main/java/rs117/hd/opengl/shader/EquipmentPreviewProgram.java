package rs117.hd.opengl.shader;

import static org.lwjgl.opengl.GL33C.*;
import static rs117.hd.renderer.zone.ZoneRenderer.TEXTURE_UNIT_EQUIPMENT_PREVIEW;

/**
 * Composites the offscreen equipment preview onto the interface.
 */
public class EquipmentPreviewProgram extends ShaderProgram {
	private final UniformTexture uniPreviewTexture = addUniformTexture("previewTexture");
	public final Uniform4f uniTargetRect = addUniform4f("targetRect");

	public EquipmentPreviewProgram() {
		super(t -> t
			.add(GL_VERTEX_SHADER, "equipment_preview_vert.glsl")
			.add(GL_FRAGMENT_SHADER, "equipment_preview_frag.glsl"));
	}

	@Override
	protected void initialize() {
		uniPreviewTexture.set(TEXTURE_UNIT_EQUIPMENT_PREVIEW);
	}
}
