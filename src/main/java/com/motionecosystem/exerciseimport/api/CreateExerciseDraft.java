package com.motionecosystem.exerciseimport.api;

import java.util.UUID;

public interface CreateExerciseDraft {
    UUID createDraft(UUID recordId, String actorSubject);
}
