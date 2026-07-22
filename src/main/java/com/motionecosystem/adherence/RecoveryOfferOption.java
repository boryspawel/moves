package com.motionecosystem.adherence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "recovery_offer_option", schema = "adherence")
class RecoveryOfferOption {
    @Id UUID id;
    UUID recoveryOfferId;
    int ordinal;
    String path;
    boolean primaryOption;
    protected RecoveryOfferOption() { }
    RecoveryOfferOption(UUID offerId, int ordinal, String path, boolean primaryOption) {
        id = UUID.randomUUID(); recoveryOfferId = offerId; this.ordinal = ordinal; this.path = path; this.primaryOption = primaryOption;
    }
}
