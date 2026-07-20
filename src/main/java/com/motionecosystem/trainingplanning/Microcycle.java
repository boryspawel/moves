package com.motionecosystem.trainingplanning;

import java.util.UUID;

public record Microcycle(UUID id, UUID cycleId, int sequenceNumber, String name) {
}
