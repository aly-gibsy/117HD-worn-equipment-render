package rs117.hdequiptest.renderer.zone;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Turns the preview from side to side like the vanilla preview, pausing while the player rotates it by hand.
 * The phase only advances while swaying, so pausing and resuming never makes the character jump.
 */
final class EquipmentPreviewSway {
	static final int AMPLITUDE_JAU = 160; // About 28 degrees either side
	static final double PERIOD_SECONDS = 7;
	static final long RESUME_DELAY_NANOS = SECONDS.toNanos(5);

	private double phase;
	private long lastUpdateNanos = Long.MIN_VALUE;
	private long pausedUntilNanos = Long.MIN_VALUE;

	/**
	 * Pauses swaying from now until {@link #RESUME_DELAY_NANOS} after the last call.
	 */
	synchronized void pause(long nowNanos) {
		pausedUntilNanos = nowNanos + RESUME_DELAY_NANOS;
	}

	/**
	 * @return the yaw offset to add to the player's own rotation, in JAU
	 */
	synchronized int update(long nowNanos, boolean enabled) {
		long elapsed = lastUpdateNanos == Long.MIN_VALUE ? 0 : nowNanos - lastUpdateNanos;
		lastUpdateNanos = nowNanos;
		if (!enabled)
			return 0;

		if (nowNanos >= pausedUntilNanos)
			phase = (phase + elapsed / 1e9 / PERIOD_SECONDS * 2 * Math.PI) % (2 * Math.PI);
		return (int) Math.round(AMPLITUDE_JAU * Math.sin(phase));
	}
}
