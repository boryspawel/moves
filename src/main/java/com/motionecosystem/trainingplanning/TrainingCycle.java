package com.motionecosystem.trainingplanning;

import java.util.UUID;

public record TrainingCycle(UUID id, UUID planId, int sequenceNumber, String name) {
}
