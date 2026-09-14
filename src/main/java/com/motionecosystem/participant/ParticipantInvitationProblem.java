package com.motionecosystem.participant;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ParticipantInvitationProblem extends ResponseStatusException {
    private final String code;

    ParticipantInvitationProblem(HttpStatus status, String code, String detail) {
        super(status, detail);
        this.code = code;
    }

    String code() {
        return code;
    }
}
