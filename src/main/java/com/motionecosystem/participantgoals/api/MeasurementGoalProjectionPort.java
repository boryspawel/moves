package com.motionecosystem.participantgoals.api;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public interface MeasurementGoalProjectionPort { void project(UUID measurementId, UUID specialistId, UUID participantId, String metricCode, BigDecimal value, String unit, String method, Instant measuredAt, Instant recordedAt); }
