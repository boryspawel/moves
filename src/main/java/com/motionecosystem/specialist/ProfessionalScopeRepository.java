package com.motionecosystem.specialist;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProfessionalScopeRepository extends JpaRepository<ProfessionalScope, ProfessionalScope.Id> {
    boolean existsByIdAccountIdAndIdKindAndStatus(UUID accountId, SpecialistKind kind,
                                                   ProfessionalScope.VerificationStatus status);
}
