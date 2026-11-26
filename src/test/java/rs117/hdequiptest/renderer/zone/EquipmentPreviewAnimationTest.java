package rs117.hdequiptest.renderer.zone;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static rs117.hdequiptest.renderer.zone.EquipmentPreviewAnimation.CLIENT_TICK_NANOS;
import static rs117.hdequiptest.renderer.zone.EquipmentPreviewAnimation.frameAt;

public class EquipmentPreviewAnimationTest {
	private static long ticks(long n) {
		return n * CLIENT_TICK_NANOS;
	}

	@Test
	public void holdsEachFrameForItsLengthAndLoops() {
		int[] lengths = { 2, 3, 1 };
		int[] expected = { 0, 0, 1, 1, 1, 2, 0, 0, 1 };
		for (int tick = 0; tick < expected.length; tick++)
			assertEquals("tick " + tick, expected[tick], frameAt(lengths, lengths.length, ticks(tick)));
	}

	@Test
	public void zeroLengthFramesLastOneTick() {
		int[] lengths = { 0, 0 };
		assertEquals(0, frameAt(lengths, 2, ticks(0)));
		assertEquals(1, frameAt(lengths, 2, ticks(1)));
		assertEquals(0, frameAt(lengths, 2, ticks(2)));
	}

	@Test
	public void skeletalAnimationsAdvanceOneFramePerTick() {
		assertEquals(0, frameAt(null, 4, ticks(0)));
		assertEquals(3, frameAt(null, 4, ticks(3)));
		assertEquals(1, frameAt(null, 4, ticks(5)));
		assertEquals(0, frameAt(null, 0, ticks(5)));
	}

	@Test
	public void partialTicksStayOnTheCurrentFrame() {
		int[] lengths = { 1, 1 };
		assertEquals(0, frameAt(lengths, 2, CLIENT_TICK_NANOS - 1));
		assertEquals(0, frameAt(lengths, 2, -5));
	}
}
