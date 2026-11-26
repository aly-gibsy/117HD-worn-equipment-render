package rs117.hdequiptest.renderer.zone;

import org.junit.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class EquipmentPreviewSwayTest {
	private static final long FRAME = MILLISECONDS.toNanos(20);

	@Test
	public void swaysBothWaysWithinTheAmplitude() {
		EquipmentPreviewSway sway = new EquipmentPreviewSway();
		int min = 0, max = 0;
		long periodNanos = (long) (EquipmentPreviewSway.PERIOD_SECONDS * 1e9);
		for (long t = 0; t <= periodNanos; t += FRAME) {
			int offset = sway.update(t, true);
			min = Math.min(min, offset);
			max = Math.max(max, offset);
		}
		assertEquals(EquipmentPreviewSway.AMPLITUDE_JAU, max, 2);
		assertEquals(-EquipmentPreviewSway.AMPLITUDE_JAU, min, 2);
	}

	@Test
	public void disabledHasNoOffset() {
		EquipmentPreviewSway sway = new EquipmentPreviewSway();
		for (long t = 0; t < SECONDS.toNanos(3); t += FRAME)
			assertEquals(0, sway.update(t, false));
	}

	@Test
	public void pauseHoldsTheOffsetThenResumesWithoutJumping() {
		EquipmentPreviewSway sway = new EquipmentPreviewSway();
		long t = 0;
		for (; t < SECONDS.toNanos(1); t += FRAME)
			sway.update(t, true);
		int beforePause = sway.update(t, true);
		assertNotEquals(0, beforePause);

		sway.pause(t);
		long resumeAt = t + EquipmentPreviewSway.RESUME_DELAY_NANOS;
		for (t += FRAME; t < resumeAt; t += FRAME)
			assertEquals("held while paused", beforePause, sway.update(t, true));

		// Two frames pass here, and the sway moves at most about 144 JAU per second, so under 6 JAU
		int afterResume = sway.update(t + FRAME, true);
		assertTrue("resumes smoothly", Math.abs(afterResume - beforePause) <= 6);
		int later = sway.update(t + SECONDS.toNanos(1), true);
		assertNotEquals("moving again", beforePause, later);
	}
}
