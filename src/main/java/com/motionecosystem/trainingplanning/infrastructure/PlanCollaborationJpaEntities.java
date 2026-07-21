package com.motionecosystem.trainingplanning.infrastructure;

import com.motionecosystem.trainingplanning.PlanCollaborationPersistence.CollaboratorData;
import com.motionecosystem.trainingplanning.PlanCollaborationPersistence.ReviewData;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity(name = "PlanCollaboratorJpaEntity")
@Table(name = "plan_collaborator", schema = "training_planning")
class PlanCollaboratorJpaEntity {
    @Id UUID id;
    @Column(name = "plan_id") UUID planId;
    @Column(name = "specialist_account_id") UUID specialistId;
    @Column(name = "professional_role") String professionalRole;
    String status;
    @Column(name = "added_by_account_id") UUID addedBy;
    @Column(name = "added_at") Instant addedAt;
    @Column(name = "ended_at") Instant endedAt;
    @ElementCollection
    @CollectionTable(name = "plan_collaborator_scope", schema = "training_planning",
            joinColumns = @JoinColumn(name = "collaborator_id"))
    @Column(name = "scope")
    Set<String> scopes = new HashSet<>();

    protected PlanCollaboratorJpaEntity() { }

    PlanCollaboratorJpaEntity(CollaboratorData source) {
        id = source.id(); planId = source.planId(); specialistId = source.specialistId();
        professionalRole = source.professionalRole(); status = source.status();
        addedBy = source.addedBy(); addedAt = source.addedAt(); scopes.addAll(source.scopes());
    }

    CollaboratorData data() {
        return new CollaboratorData(id, planId, specialistId, professionalRole,
                scopes, status, addedBy, addedAt);
    }
}

@Entity(name = "PlanReviewRequestJpaEntity")
@Table(name = "plan_review_request", schema = "training_planning")
class PlanReviewRequestJpaEntity {
    @Id UUID id;
    @Column(name = "revision_id") UUID revisionId;
    @Column(name = "requested_by_account_id") UUID requestedBy;
    @Column(name = "reviewer_account_id") UUID reviewerId;
    String status;
    @Column(name = "request_reference") String requestReference;
    @Column(name = "decision_reference") String decisionReference;
    @Column(name = "requested_at") Instant requestedAt;
    @Column(name = "decided_at") Instant decidedAt;

    protected PlanReviewRequestJpaEntity() { }

    PlanReviewRequestJpaEntity(ReviewData source) {
        id = source.id(); revisionId = source.revisionId(); requestedBy = source.requestedBy();
        reviewerId = source.reviewerId(); status = source.status();
        requestReference = source.requestReference(); decisionReference = source.decisionReference();
        requestedAt = source.requestedAt(); decidedAt = source.decidedAt();
    }

    ReviewData data() {
        return new ReviewData(id, revisionId, requestedBy, reviewerId, status,
                requestReference, decisionReference, requestedAt, decidedAt);
    }
}
