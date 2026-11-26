package rs117.hdequiptest.renderer.zone;

import javax.annotation.Nullable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Works out which frame of a looping animation to show, independently of what the player is doing in the world.
 */
final class EquipmentPreviewAnimation {
	/** Animation frame lengths are counted in client ticks */
	static final long CLIENT_TICK_NANOS = MILLISECONDS.toNanos(20);

	private EquipmentPreviewAnimation() {}

	/**
	 * @param frameLengths each frame's length in client ticks, or null for skeletal (Maya) animations,
	 *                     which advance one frame per client tick
	 * @param numFrames    the number of frames, used when frameLengths is null
	 * @param elapsedNanos time since the animation started
	 * @return the frame to show, looping back to the first frame after the last
	 */
	static int frameAt(@Nullable int[] frameLengths, int numFrames, long elapsedNanos) {
		long ticks = Math.max(0, elapsedNanos) / CLIENT_TICK_NANOS;
		if (frameLengths == null || frameLengths.length == 0)
			return numFrames <= 0 ? 0 : (int) (ticks % numFrames);

		long total = 0;
		for (int length : frameLengths)
			total += tickLength(length);
		long t = ticks % total;
		for (int frame = 0; frame < frameLengths.length; frame++) {
			t -= tickLength(frameLengths[frame]);
			if (t < 0)
				return frame;
		}
		return frameLengths.length - 1;
	}

	/** The client shows zero-length frames for one tick */
	private static int tickLength(int frameLength) {
		return Math.max(1, frameLength);
	}
}
