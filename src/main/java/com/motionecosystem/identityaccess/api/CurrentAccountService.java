package com.motionecosystem.identityaccess.api;

import java.time.Clock;
import java.util.Objects;

import com.motionecosystem.identityaccess.domain.PrincipalAccount;
import com.motionecosystem.identityaccess.domain.PrincipalAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentAccountService {

    private final PrincipalAccountRepository accounts;
    private final Clock clock;

    public CurrentAccountService(PrincipalAccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
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
        account.seenAt(clock.instant());
        return view(account);
    }

    @Transactional
    public CurrentAccount selectProfileType(String externalSubject, ProfileType profileType) {
        CurrentAccount current = requireActive(externalSubject);
        PrincipalAccount account = accounts.findById(current.id()).orElseThrow();
        try {
            account.selectProfileType(Objects.requireNonNull(profileType));
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage(), conflict);
        }
        return view(account);
    }

    private static CurrentAccount view(PrincipalAccount account) {
        return new CurrentAccount(account.id(), account.externalSubject(), account.profileType());
    }
}
