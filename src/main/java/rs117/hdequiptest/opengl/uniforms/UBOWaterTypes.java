package rs117.hdequiptest.opengl.uniforms;

import rs117.hdequiptest.HdEquipTestPlugin;
import rs117.hdequiptest.scene.water_types.WaterType;
import rs117.hdequiptest.utils.buffer.GLBuffer;

import static org.lwjgl.opengl.GL33C.*;

public class UBOWaterTypes extends UniformBuffer<GLBuffer> {
	public static class WaterTypeStruct extends StructProperty {
		public final Property isFlat = addProperty(PropertyType.Int, "isFlat");
		public final Property specularStrength = addProperty(PropertyType.Float, "specularStrength");
		public final Property specularGloss = addProperty(PropertyType.Float, "specularGloss");
		public final Property normalStrength = addProperty(PropertyType.Float, "normalStrength");
		public final Property baseOpacity = addProperty(PropertyType.Float, "baseOpacity");
		public final Property hasFoam = addProperty(PropertyType.Int, "hasFoam");
		public final Property duration = addProperty(PropertyType.Float, "duration");
		public final Property fresnelAmount = addProperty(PropertyType.Float, "fresnelAmount");
		public final Property surfaceColor = addProperty(PropertyType.FVec3, "surfaceColor");
		public final Property foamColor = addProperty(PropertyType.FVec3, "foamColor");
		public final Property depthColor = addProperty(PropertyType.FVec3, "depthColor");
		public final Property normalMap = addProperty(PropertyType.Int, "normalMap");
	}

	private final WaterTypeStruct[] uboStructs;
	private final WaterType[] waterTypes;

	public UBOWaterTypes(WaterType[] waterTypes) {
		super(GL_STATIC_DRAW);
		this.waterTypes = waterTypes;
		uboStructs = addStructs(new WaterTypeStruct[waterTypes.length], WaterTypeStruct::new);
		initialize(HdEquipTestPlugin.UNIFORM_BLOCK_WATER_TYPES);
		update();
	}

	public int getCount() {
		return uboStructs.length;
	}

	public void update() {
		for (int i = 0; i < waterTypes.length; i++)
			waterTypes[i].fillStruct(uboStructs[i]);
		upload();
	}
}
