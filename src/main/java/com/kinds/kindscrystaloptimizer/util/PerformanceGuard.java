package com.kinds.kindscrystaloptimizer.util;

public final class PerformanceGuard {

    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final long BURST_WINDOW_NANOS = 120_000_000L;
    private static final int PLACE_BURST_LIMIT = 8;
    private static final int BREAK_BURST_LIMIT = 10;
    private static final long BREAK_ENTITY_COOLDOWN_NANOS = 28_000_000L;
    private static final long DTAP_THRESHOLD_NANOS = 220_000_000L;
    private static final long DTAP_BOOST_DURATION_NANOS = 320_000_000L;
    private static final int DTAP_EXTRA_PLACE_BOOSTS = 44;
    private static final long PHASE_FAST_NANOS = 220_000_000L;
    private static final long PHASE_STEADY_NANOS = 180_000_000L;
    private static final long PHASE_BREAK_SLOW_NANOS = 145_000_000L;
    private static final long PLACE_FAST_INTERVAL_NANOS = 1_800_000L;
    private static final long PLACE_STEADY_INTERVAL_NANOS = 4_200_000L;
    private static final long BREAK_FAST_INTERVAL_NANOS = 4_800_000L;
    private static final long BREAK_SLOW_INTERVAL_NANOS = 10_500_000L;

    private final int maxPlaceBoostsPerSecond;
    private final int maxBreakPredictionsPerSecond;

    private long placeWindowStartNanos;
    private int placeBoostsInWindow;
    private long placeBurstWindowStartNanos;
    private int placeBoostsInBurstWindow;

    private long breakWindowStartNanos;
    private int breakPredictionsInWindow;
    private long breakBurstWindowStartNanos;
    private int breakPredictionsInBurstWindow;

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
        this(140, 180);
    }

    public PerformanceGuard(int maxPlaceBoostsPerSecond, int maxBreakPredictionsPerSecond) {
        this.maxPlaceBoostsPerSecond = Math.max(1, maxPlaceBoostsPerSecond);
        this.maxBreakPredictionsPerSecond = Math.max(1, maxBreakPredictionsPerSecond);

        long now = System.nanoTime();
        placeWindowStartNanos = now;
        placeBurstWindowStartNanos = now;
        breakWindowStartNanos = now;
        breakBurstWindowStartNanos = now;
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
        if (now - placeBurstWindowStartNanos >= BURST_WINDOW_NANOS) {
            placeBurstWindowStartNanos = now;
            placeBoostsInBurstWindow = 0;
        }

        boolean dtapBoostActive = now < dtapBoostUntilNanos;
        int activeBudget = maxPlaceBoostsPerSecond;
        if (dtapBoostActive) {
            activeBudget += DTAP_EXTRA_PLACE_BOOSTS;
        }

        int loadPercent = (placeBoostsInWindow * 100) / Math.max(1, activeBudget);
        updatePlacePhase(now, loadPercent, dtapBoostActive);

        long minInterval = resolvePlaceMinInterval(now, loadPercent, dtapBoostActive);
        if (placeLastGrantNanos != 0L && now - placeLastGrantNanos < minInterval) {
            return false;
        }

        if (placeBoostsInWindow >= activeBudget) {
            return false;
        }
        if (placeBoostsInBurstWindow >= PLACE_BURST_LIMIT) {
            return false;
        }

        placeBoostsInWindow++;
        placeBoostsInBurstWindow++;
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
        if (now - breakBurstWindowStartNanos >= BURST_WINDOW_NANOS) {
            breakBurstWindowStartNanos = now;
            breakPredictionsInBurstWindow = 0;
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
        if (breakPredictionsInBurstWindow >= BREAK_BURST_LIMIT) {
            return false;
        }

        breakPredictionsInWindow++;
        breakPredictionsInBurstWindow++;
        breakLastGrantNanos = now;
        lastBrokenEntityId = entityId;
        lastBrokenEntityUntilNanos = now + BREAK_ENTITY_COOLDOWN_NANOS;
        return true;
    }

    private void updatePlacePhase(long now, int loadPercent, boolean dtapBoostActive) {
        long fastDuration = PHASE_FAST_NANOS;
        long steadyDuration = PHASE_STEADY_NANOS;
        if (loadPercent >= 90) {
            fastDuration = Math.max(95_000_000L, fastDuration - 35_000_000L);
            steadyDuration += 60_000_000L;
        } else if (loadPercent <= 35) {
            fastDuration += 25_000_000L;
            steadyDuration = Math.max(170_000_000L, steadyDuration - 35_000_000L);
        }

        if (dtapBoostActive) {
            fastDuration += 28_000_000L;
            steadyDuration = Math.max(150_000_000L, steadyDuration - 20_000_000L);
        }

        long phaseDuration = placeFastPhase ? fastDuration : steadyDuration;
        if (now - placePhaseStartNanos >= phaseDuration) {
            placeFastPhase = !placeFastPhase;
            placePhaseStartNanos = now;
        }
    }

    private long resolvePlaceMinInterval(long now, int loadPercent, boolean dtapBoostActive) {
        long fastInterval = PLACE_FAST_INTERVAL_NANOS;
        long steadyInterval = PLACE_STEADY_INTERVAL_NANOS;

        if (loadPercent >= 90) {
            fastInterval += 450_000L;
            steadyInterval += 1_300_000L;
        } else if (loadPercent <= 35) {
            fastInterval = Math.max(1_500_000L, fastInterval - 400_000L);
            steadyInterval = Math.max(3_900_000L, steadyInterval - 500_000L);
        }

        if (dtapBoostActive) {
            fastInterval = Math.max(1_300_000L, fastInterval - 700_000L);
            steadyInterval = Math.max(3_800_000L, steadyInterval - 400_000L);
        }

        long phaseInterval = placeFastPhase ? fastInterval : steadyInterval;
        long phaseElapsed = now - placePhaseStartNanos;
        if (placeFastPhase) {
            if (phaseElapsed < 36_000_000L) {
                phaseInterval = Math.max(1_300_000L, phaseInterval - 700_000L);
            } else if (phaseElapsed > 125_000_000L) {
                phaseInterval += 400_000L;
            }
        }

        long jitterRange = placeFastPhase ? 900_000L : 130_000L;
        if (loadPercent >= 90) {
            jitterRange = placeFastPhase ? 550_000L : 80_000L;
        } else if (loadPercent <= 35 && placeFastPhase) {
            jitterRange = 1_150_000L;
        }
        if (dtapBoostActive && placeFastPhase) {
            jitterRange += 450_000L;
        }

        long jittered = phaseInterval + nextSignedPlaceJitter(jitterRange);
        return Math.max(placeFastPhase ? 1_300_000L : 3_800_000L, jittered);
    }

    private void updateBreakPhase(long now, int loadPercent) {
        long fastDuration = PHASE_FAST_NANOS;
        long slowDuration = PHASE_BREAK_SLOW_NANOS;
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
            fastInterval += 900_000L;
            slowInterval += 2_000_000L;
        } else if (loadPercent <= 35) {
            fastInterval = Math.max(3_400_000L, fastInterval - 700_000L);
            slowInterval = Math.max(8_400_000L, slowInterval - 900_000L);
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
