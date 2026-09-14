package com.motionecosystem.participant;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="participant_access_invitation", schema="participant") class ParticipantAccessInvitation {
 enum Status { PENDING, CLAIMED, REVOKED }
 @Id UUID id; @Column(name="participant_id") UUID participantId; @Column(name="inviter_account_id") UUID inviterAccountId; @Column(name="inviter_subject") String inviterSubject; @Column(name="intended_email") String intendedEmail; @Column(name="token_hash") String tokenHash; @Enumerated(EnumType.STRING) Status status; @Column(name="created_at") Instant createdAt; @Column(name="expires_at") Instant expiresAt; @Column(name="claimed_account_id") UUID claimedAccountId; @Column(name="claimed_at") Instant claimedAt; @Version long version;
 protected ParticipantAccessInvitation(){} ParticipantAccessInvitation(UUID participant, UUID inviter, String subject,String email,String hash,Instant now,Instant expiry){id=UUID.randomUUID();participantId=participant;inviterAccountId=inviter;inviterSubject=subject;intendedEmail=email;tokenHash=hash;status=Status.PENDING;createdAt=now;expiresAt=expiry;}
 void revoke(){status=Status.REVOKED;} void claim(UUID account,Instant now){status=Status.CLAIMED;claimedAccountId=account;claimedAt=now;}
 UUID id(){return id;}
}
