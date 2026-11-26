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
	 * A point in a looping animation: the frame being shown, the one after it, and how far the animation has moved
	 * from one to the other, for smoothing.
	 */
	static final class Position {
		final int frame;
		final int nextFrame;
		/** From 0 at the start of {@link #frame} towards 1 at the start of {@link #nextFrame} */
		final float progress;

		Position(int frame, int nextFrame, float progress) {
			this.frame = frame;
			this.nextFrame = nextFrame;
			this.progress = progress;
		}
	}

	/**
	 * @param frameLengths each frame's length in client ticks, or null for skeletal (Maya) animations,
	 *                     which advance one frame per client tick
	 * @param numFrames    the number of frames, used when frameLengths is null
	 * @param elapsedNanos time since the animation started
	 * @return where the animation is, looping back to the first frame after the last
	 */
	static Position positionAt(@Nullable int[] frameLengths, int numFrames, long elapsedNanos) {
		double ticks = Math.max(0, elapsedNanos) / (double) CLIENT_TICK_NANOS;
		if (frameLengths == null || frameLengths.length == 0) {
			if (numFrames <= 0)
				return new Position(0, 0, 0);
			int frame = (int) ((long) ticks % numFrames);
			return new Position(frame, (frame + 1) % numFrames, (float) (ticks - Math.floor(ticks)));
		}

		long total = 0;
		for (int length : frameLengths)
			total += tickLength(length);
		double t = ticks % total;
		for (int frame = 0; frame < frameLengths.length; frame++) {
			int length = tickLength(frameLengths[frame]);
			if (t < length || frame == frameLengths.length - 1)
				return new Position(frame, (frame + 1) % frameLengths.length, (float) Math.min(1, t / length));
			t -= length;
		}
		throw new AssertionError("unreachable");
	}

	/** The client shows zero-length frames for one tick */
	private static int tickLength(int frameLength) {
		return Math.max(1, frameLength);
	}
}
