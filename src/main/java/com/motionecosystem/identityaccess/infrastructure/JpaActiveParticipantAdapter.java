package com.motionecosystem.identityaccess.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.motionecosystem.identityaccess.api.ActiveParticipantPort;
import com.motionecosystem.identityaccess.domain.PrincipalAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class JpaActiveParticipantAdapter implements ActiveParticipantPort {

    private final PrincipalAccountRepository accounts;

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveParticipantSnapshot> findActiveParticipant(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return accounts.findActiveParticipantById(accountId)
                .map(account -> new ActiveParticipantSnapshot(account.id()));
    }
}
