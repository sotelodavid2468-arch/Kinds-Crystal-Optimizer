package com.kinds.kindscrystaloptimizer.util;

public final class PerformanceGuard {

    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final long BREAK_ENTITY_COOLDOWN_NANOS = 60_000_000L;
    private static final long DTAP_THRESHOLD_NANOS = 220_000_000L;
    private static final long DTAP_BOOST_DURATION_NANOS = 320_000_000L;
    private static final int DTAP_EXTRA_PLACE_BOOSTS = 24;
    private static final long PHASE_FAST_NANOS = 180_000_000L;
    private static final long PHASE_SLOW_NANOS = 160_000_000L;
    private static final long PLACE_FAST_INTERVAL_NANOS = 5_000_000L;
    private static final long PLACE_SLOW_INTERVAL_NANOS = 16_000_000L;
    private static final long BREAK_FAST_INTERVAL_NANOS = 9_000_000L;
    private static final long BREAK_SLOW_INTERVAL_NANOS = 24_000_000L;

    private final int maxPlaceBoostsPerSecond;
    private final int maxBreakPredictionsPerSecond;

    private long placeWindowStartNanos;
    private int placeBoostsInWindow;

    private long breakWindowStartNanos;
    private int breakPredictionsInWindow;

    private int lastBrokenEntityId;
    private long lastBrokenEntityUntilNanos;

    private boolean placeFastPhase;
    private long placePhaseStartNanos;
    private long placeLastGrantNanos;
    private int placeJitterState;

    private boolean breakFastPhase;
    private long breakPhaseStartNanos;
    private long breakLastGrantNanos;

    private boolean useKeyDown;
    private long lastUsePressNanos;
    private long dtapBoostUntilNanos;

    public PerformanceGuard() {
        this(80, 100);
    }

    public PerformanceGuard(int maxPlaceBoostsPerSecond, int maxBreakPredictionsPerSecond) {
        this.maxPlaceBoostsPerSecond = Math.max(1, maxPlaceBoostsPerSecond);
        this.maxBreakPredictionsPerSecond = Math.max(1, maxBreakPredictionsPerSecond);

        long now = System.nanoTime();
        placeWindowStartNanos = now;
        breakWindowStartNanos = now;
        placeFastPhase = true;
        breakFastPhase = true;
        placePhaseStartNanos = now;
        breakPhaseStartNanos = now;
        placeJitterState = ((int) now) ^ 0x9E3779B9;
        lastBrokenEntityId = Integer.MIN_VALUE;
    }

    public synchronized void observeUseKeyState(boolean isDown) {
        long now = System.nanoTime();

        if (isDown && !useKeyDown) {
            if (lastUsePressNanos != 0L && now - lastUsePressNanos <= DTAP_THRESHOLD_NANOS) {
                dtapBoostUntilNanos = Math.max(dtapBoostUntilNanos, now + DTAP_BOOST_DURATION_NANOS);
            }
            lastUsePressNanos = now;
        }

        useKeyDown = isDown;
    }

    public synchronized boolean allowPlaceBoost() {
        long now = System.nanoTime();
        if (now - placeWindowStartNanos >= WINDOW_NANOS) {
            placeWindowStartNanos = now;
            placeBoostsInWindow = 0;
        }

        int activeBudget = maxPlaceBoostsPerSecond;
        if (now < dtapBoostUntilNanos) {
            activeBudget += DTAP_EXTRA_PLACE_BOOSTS;
        }

        int loadPercent = (placeBoostsInWindow * 100) / Math.max(1, activeBudget);
        updatePlacePhase(now, loadPercent);

        long minInterval = resolvePlaceMinInterval(now, loadPercent, now < dtapBoostUntilNanos);
        if (placeLastGrantNanos != 0L && now - placeLastGrantNanos < minInterval) {
            return false;
        }

        if (placeBoostsInWindow >= activeBudget) {
            return false;
        }

        placeBoostsInWindow++;
        placeLastGrantNanos = now;
        return true;
    }

    public synchronized boolean allowBreakPrediction(int entityId) {
        long now = System.nanoTime();

        if (entityId == lastBrokenEntityId && now < lastBrokenEntityUntilNanos) {
            return false;
        }

        if (now - breakWindowStartNanos >= WINDOW_NANOS) {
            breakWindowStartNanos = now;
            breakPredictionsInWindow = 0;
        }

        int loadPercent = (breakPredictionsInWindow * 100) / Math.max(1, maxBreakPredictionsPerSecond);
        updateBreakPhase(now, loadPercent);

        long minInterval = resolveBreakMinInterval(loadPercent);
        if (breakLastGrantNanos != 0L && now - breakLastGrantNanos < minInterval) {
            return false;
        }

        if (breakPredictionsInWindow >= maxBreakPredictionsPerSecond) {
            return false;
        }

        breakPredictionsInWindow++;
        breakLastGrantNanos = now;
        lastBrokenEntityId = entityId;
        lastBrokenEntityUntilNanos = now + BREAK_ENTITY_COOLDOWN_NANOS;
        return true;
    }

    private void updatePlacePhase(long now, int loadPercent) {
        long fastDuration = PHASE_FAST_NANOS;
        long slowDuration = PHASE_SLOW_NANOS;
        if (loadPercent >= 80) {
            fastDuration = Math.max(90_000_000L, fastDuration - 40_000_000L);
            slowDuration += 70_000_000L;
        } else if (loadPercent <= 35) {
            fastDuration += 30_000_000L;
            slowDuration = Math.max(110_000_000L, slowDuration - 20_000_000L);
        }

        long phaseDuration = placeFastPhase ? fastDuration : slowDuration;
        if (now - placePhaseStartNanos >= phaseDuration) {
            placeFastPhase = !placeFastPhase;
            placePhaseStartNanos = now;
        }
    }

    private long resolvePlaceMinInterval(long now, int loadPercent, boolean dtapBoostActive) {
        long fastInterval = PLACE_FAST_INTERVAL_NANOS;
        long slowInterval = PLACE_SLOW_INTERVAL_NANOS;

        if (loadPercent >= 80) {
            fastInterval += 2_000_000L;
            slowInterval += 6_000_000L;
        } else if (loadPercent <= 35) {
            fastInterval = Math.max(3_600_000L, fastInterval - 900_000L);
            slowInterval = Math.max(12_500_000L, slowInterval - 1_500_000L);
        }

        if (dtapBoostActive) {
            fastInterval = Math.max(2_700_000L, fastInterval - 900_000L);
        }

        long phaseInterval = placeFastPhase ? fastInterval : slowInterval;
        long phaseElapsed = now - placePhaseStartNanos;
        if (placeFastPhase) {
            if (phaseElapsed < 42_000_000L) {
                phaseInterval = Math.max(2_600_000L, phaseInterval - 700_000L);
            } else if (phaseElapsed > 130_000_000L) {
                phaseInterval += 1_200_000L;
            }
        } else if (phaseElapsed < 35_000_000L) {
            phaseInterval = Math.max(7_000_000L, phaseInterval - 1_000_000L);
        }

        long jitterRange = 2_400_000L;
        if (loadPercent >= 80) {
            jitterRange = 1_500_000L;
        } else if (loadPercent <= 35) {
            jitterRange = 3_100_000L;
        }
        if (dtapBoostActive) {
            jitterRange += 600_000L;
        }

        long jittered = phaseInterval + nextSignedPlaceJitter(jitterRange);
        return Math.max(2_600_000L, jittered);
    }

    private void updateBreakPhase(long now, int loadPercent) {
        long fastDuration = PHASE_FAST_NANOS;
        long slowDuration = PHASE_SLOW_NANOS;
        if (loadPercent >= 80) {
            fastDuration = Math.max(95_000_000L, fastDuration - 35_000_000L);
            slowDuration += 65_000_000L;
        } else if (loadPercent <= 35) {
            fastDuration += 20_000_000L;
        }

        long phaseDuration = breakFastPhase ? fastDuration : slowDuration;
        if (now - breakPhaseStartNanos >= phaseDuration) {
            breakFastPhase = !breakFastPhase;
            breakPhaseStartNanos = now;
        }
    }

    private long resolveBreakMinInterval(int loadPercent) {
        long fastInterval = BREAK_FAST_INTERVAL_NANOS;
        long slowInterval = BREAK_SLOW_INTERVAL_NANOS;
        if (loadPercent >= 80) {
            fastInterval += 2_000_000L;
            slowInterval += 6_000_000L;
        } else if (loadPercent <= 35) {
            fastInterval = Math.max(6_000_000L, fastInterval - 1_000_000L);
            slowInterval = Math.max(18_000_000L, slowInterval - 2_000_000L);
        }

        return breakFastPhase ? fastInterval : slowInterval;
    }

    private long nextSignedPlaceJitter(long range) {
        if (range <= 0L) {
            return 0L;
        }

        placeJitterState ^= (placeJitterState << 13);
        placeJitterState ^= (placeJitterState >>> 17);
        placeJitterState ^= (placeJitterState << 5);

        long span = (range * 2L) + 1L;
        long value = (placeJitterState & 0x7fffffffL) % span;
        return value - range;
    }
}
