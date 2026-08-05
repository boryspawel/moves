package com.motionecosystem.participantgoals;

import java.math.BigDecimal;

public enum TargetComparator {
    AT_LEAST, AT_MOST;

    public ProgressState progress(BigDecimal target, BigDecimal value) {
        if (target == null || value == null) return ProgressState.NO_DATA;
        return switch (this) {
            case AT_LEAST -> value.compareTo(target) >= 0 ? ProgressState.TARGET_REACHED : ProgressState.IN_PROGRESS;
            case AT_MOST -> value.compareTo(target) <= 0 ? ProgressState.TARGET_REACHED : ProgressState.IN_PROGRESS;
        };
    }

    public enum ProgressState { NO_DATA, IN_PROGRESS, TARGET_REACHED, NOT_COMPARABLE }
}
