package rs117.hdequiptest.opengl.shader;

import org.lwjgl.opengl.*;

public class ModelPassthroughComputeProgram extends ShaderProgram {
	public ModelPassthroughComputeProgram() {
		super(t -> t.add(GL43C.GL_COMPUTE_SHADER, "comp_unordered.glsl"));
	}
}
