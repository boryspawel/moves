package com.motionecosystem.specialist;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SpecialistRelationshipService {

    private final JdbcTemplate jdbc;

    public SpecialistRelationshipService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireActive(UUID specialistAccountId, UUID participantAccountId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM specialist.participant_specialist_relationship
                WHERE specialist_account_id = ?
                  AND participant_account_id = ?
                  AND status = 'ACTIVE'
                """, Integer.class, specialistAccountId, participantAccountId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "active participant-specialist relationship is required");
        }
    }
}
