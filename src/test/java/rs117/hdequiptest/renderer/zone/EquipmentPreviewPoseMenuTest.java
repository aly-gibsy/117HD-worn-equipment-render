package rs117.hdequiptest.renderer.zone;

import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static rs117.hdequiptest.renderer.zone.EquipmentPreviewPoseMenu.*;

public class EquipmentPreviewPoseMenuTest {
	private static final Rectangle AREA = new Rectangle(100, 50, 80, 200);

	@Test
	public void modelSitsBelowTheButton() {
		Rectangle model = EquipmentPreviewPoseMenu.modelBounds(AREA);
		Rectangle button = buttonBounds(AREA);
		assertEquals(AREA.y + AREA.height, model.y + model.height);
		assertTrue(model.y >= button.y + button.height);
		assertTrue(AREA.contains(button));
		assertNull(EquipmentPreviewPoseMenu.modelBounds(new Rectangle(0, 0, 50, STRIP_HEIGHT)));
	}

	@Test
	public void listFitsInTheAreaAndScrollsTheRest() {
		int rows = visibleRows(AREA, 100);
		Rectangle list = EquipmentPreviewPoseMenu.listBounds(AREA, 100);
		assertTrue(rows < 100);
		assertTrue(AREA.contains(list));
		assertEquals(rows * ROW_HEIGHT, list.height);
		assertEquals(3, visibleRows(AREA, 3));

		assertEquals(0, clampScroll(-4, 100, rows));
		assertEquals(100 - rows, clampScroll(1000, 100, rows));
	}

	@Test
	public void findsTheRowUnderThePointerWithScrolling() {
		Rectangle list = EquipmentPreviewPoseMenu.listBounds(AREA, 30);
		assertEquals(0, rowAt(AREA, 30, 0, list.x + 5, list.y));
		assertEquals(2, rowAt(AREA, 30, 0, list.x + 5, list.y + 2 * ROW_HEIGHT + 1));
		assertEquals(7, rowAt(AREA, 30, 5, list.x + 5, list.y + 2 * ROW_HEIGHT + 1));
		assertEquals(-1, rowAt(AREA, 30, 0, list.x - 1, list.y));
		assertEquals(-1, rowAt(AREA, 30, 0, list.x + 5, list.y + list.height));
	}

	@Test
	public void masksTheListInTextureCoordinates() {
		Rectangle bounds = new Rectangle(0, 0, 100, 200);
		// Top half of the preview, which is the upper half of the texture since V points up
		assertArrayEquals(new float[] { .25f, .5f, .75f, 1 },
			EquipmentPreviewRenderer.maskUv(bounds, new Rectangle(25, -10, 50, 110)), 1e-6f);
		assertArrayEquals(new float[4], EquipmentPreviewRenderer.maskUv(bounds, null), 0);
		assertArrayEquals(new float[4], EquipmentPreviewRenderer.maskUv(bounds, new Rectangle(200, 0, 10, 10)), 0);
	}
}
