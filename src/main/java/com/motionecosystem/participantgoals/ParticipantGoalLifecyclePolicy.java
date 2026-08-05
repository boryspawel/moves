package com.motionecosystem.participantgoals;

import java.util.List;

final class ParticipantGoalLifecyclePolicy {
    enum Action { UPDATE, RECORD_OBSERVATION, ACHIEVE, CANCEL }
    boolean allows(Action action, ParticipantGoal goal) { return goal.status == ParticipantGoal.Status.ACTIVE; }
    List<String> availableActions(ParticipantGoal goal) { return goal.status == ParticipantGoal.Status.ACTIVE ? List.of("UPDATE", "RECORD_OBSERVATION", "ACHIEVE", "CANCEL") : List.of(); }
}
