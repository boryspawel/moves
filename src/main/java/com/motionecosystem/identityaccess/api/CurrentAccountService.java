package com.motionecosystem.identityaccess.api;

import java.time.Clock;
import java.util.Objects;

import com.motionecosystem.identityaccess.domain.PrincipalAccount;
import com.motionecosystem.identityaccess.domain.PrincipalAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CurrentAccountService {

    private final PrincipalAccountRepository accounts;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CurrentAccount requireActive(String externalSubject) {
        String subject = Objects.requireNonNull(externalSubject, "externalSubject").trim();
        if (subject.isEmpty() || subject.length() > 255) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid subject");
        }
        PrincipalAccount account = accounts.findByExternalSubject(subject)
                .orElseGet(() -> accounts.save(PrincipalAccount.create(subject, clock.instant())));
        if (!account.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account is not active");
        }
        CurrentAccount current = view(account);
        accounts.updateLastSeenAt(account.id(), clock.instant());
        return current;
    }

    @Transactional
    public CurrentAccount selectProfileType(String externalSubject, ProfileType profileType) {
        CurrentAccount current = requireActive(externalSubject);
        PrincipalAccount account = accounts.findById(current.id()).orElseThrow();
        ProfileType selected = Objects.requireNonNull(profileType);
        account.selectProfileType(selected);
        accounts.activateProfile(account.id(), selected, clock.instant());
        return view(account);
    }

    private CurrentAccount view(PrincipalAccount account) {
        return new CurrentAccount(account.id(), account.externalSubject(), account.profileType(),
                accounts.findActiveProfileTypes(account.id()));
    }
}
