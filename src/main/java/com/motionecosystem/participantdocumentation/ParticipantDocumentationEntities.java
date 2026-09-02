package com.motionecosystem.participantdocumentation;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="participant_interview", schema="participant_documentation", indexes=@Index(name="ix_participant_documentation_interview_owner", columnList="participant_id,specialist_account_id,created_at"))
class ParticipantInterview {
  enum Status { DRAFT, COMPLETED, SUPERSEDED }
  @Id UUID id; @Column(name="participant_id",nullable=false) UUID participantId; @Column(name="specialist_account_id",nullable=false) UUID specialistId;
  @Enumerated(EnumType.STRING) @Column(nullable=false) Status status; @Column(name="template_code",nullable=false,length=80) String templateCode;
  @Column(name="template_version",nullable=false) int templateVersion; @Column(name="created_at",nullable=false) Instant createdAt; @Column(name="updated_at",nullable=false) Instant updatedAt; @Column(name="completed_at") Instant completedAt; @Version long version;
  protected ParticipantInterview() {} ParticipantInterview(UUID p, UUID s, Instant now) { id=UUID.randomUUID(); participantId=p; specialistId=s; status=Status.DRAFT; templateCode="INITIAL_SPECIALIST_INTERVIEW"; templateVersion=1; createdAt=updatedAt=now; }
}
@Entity @Table(name="participant_interview_answer",schema="participant_documentation", uniqueConstraints=@UniqueConstraint(columnNames={"interview_id","question_code"}))
class InterviewResponse {
 @Id UUID id; @Column(name="interview_id",nullable=false) UUID interviewId; @Column(name="question_code",nullable=false,length=80) String code; @Column(name="section_title",nullable=false,length=120) String section; @Column(name="question_title",nullable=false,length=240) String title; @Column(name="question_type",nullable=false,length=30) String type; @Column(name="required_question",nullable=false) boolean requiredQuestion; @Column(name="text_value",length=4000) String textValue; @Column(name="number_value") java.math.BigDecimal numberValue; @Column(name="date_value") LocalDate dateValue; @Column(name="selection_values",columnDefinition="text[]") String[] selections;
 protected InterviewResponse() {} InterviewResponse(UUID interviewId, ParticipantDocumentationService.Question q, ParticipantDocumentationService.Answer a) { id=UUID.randomUUID(); this.interviewId=interviewId; code=q.code(); section=q.section(); title=q.title(); type=q.type().name(); requiredQuestion=q.required(); textValue=a.textValue(); numberValue=a.numberValue(); dateValue=a.dateValue(); selections=a.selections()==null?null:a.selections().toArray(String[]::new); }
}
@Entity @Table(name="participant_note",schema="participant_documentation",indexes=@Index(name="ix_participant_documentation_note_owner", columnList="participant_id,specialist_account_id,updated_at"))
class ParticipantNote {
 enum Status { DRAFT, FINAL, ARCHIVED }
 @Id UUID id; @Column(name="participant_id",nullable=false) UUID participantId; @Column(name="specialist_account_id",nullable=false) UUID specialistId; @Enumerated(EnumType.STRING) @Column(nullable=false) Status status; @Column(nullable=false,length=50) String category; @Column(nullable=false,length=160) String title; @Column(nullable=false,length=8000) String content; @Column(name="appointment_id") UUID appointmentId; @Column(name="created_at",nullable=false) Instant createdAt; @Column(name="updated_at",nullable=false) Instant updatedAt; @Column(name="finalised_at") Instant finalisedAt; @Column(name="archived_at") Instant archivedAt; @Version long version;
 protected ParticipantNote() {} ParticipantNote(UUID p,UUID s,String c,String t,String content,UUID ap,Instant now) {id=UUID.randomUUID();participantId=p;specialistId=s;category=c;title=t;this.content=content;appointmentId=ap;status=Status.DRAFT;createdAt=updatedAt=now;}
}
@Entity @Table(name="participant_documentation_idempotency",schema="participant_documentation",uniqueConstraints=@UniqueConstraint(columnNames={"specialist_account_id","operation","idempotency_key"}))
class RecordIdempotency { @Id UUID id; @Column(name="specialist_account_id",nullable=false) UUID specialistId; @Column(nullable=false,length=100) String operation; @Column(name="idempotency_key",nullable=false,length=120) String key; @Column(name="documentation_id",nullable=false) UUID recordId; @Column(name="created_at",nullable=false) Instant createdAt; protected RecordIdempotency(){} RecordIdempotency(UUID s,String o,String k,UUID r,Instant n){id=UUID.randomUUID();specialistId=s;operation=o;key=k;recordId=r;createdAt=n;} }
@Entity @Table(name="participant_documentation_event",schema="participant_documentation",indexes=@Index(name="ix_participant_documentation_event_timeline", columnList="participant_id,effective_at"))
class ParticipantDocumentationEvent { @Id UUID id; @Column(name="documentation_id",nullable=false) UUID recordId; @Column(name="participant_id",nullable=false) UUID participantId; @Column(name="record_type",nullable=false,length=20) String recordType; @Column(nullable=false,length=30) String action; @Column(name="effective_at",nullable=false) Instant effectiveAt; @Column(name="recorded_at",nullable=false) Instant recordedAt; @Column(name="neutral_title",nullable=false,length=180) String neutralTitle; protected ParticipantDocumentationEvent(){} ParticipantDocumentationEvent(UUID r,UUID p,String type,String action,Instant now,String title){id=UUID.randomUUID();recordId=r;participantId=p;recordType=type;this.action=action;effectiveAt=recordedAt=now;neutralTitle=title;} }
