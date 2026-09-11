package com.motionecosystem.participantdocumentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ParticipantNoteServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-10T12:00:00Z");

    @Test
    void supportsDraftFinalArchiveLifecycleAndMakesFinalNoteImmutable() {
        Fixture fixture = fixture();
        ParticipantNote note = ownedNote(fixture);

        var updated = fixture.service.update("specialist", fixture.participantId, note.id, trainer(), 0,
                command("updated content"));
        var finalised = fixture.service.finalise("specialist", fixture.participantId, note.id, trainer(), "final-key", 0);
        var archived = fixture.service.archive("specialist", fixture.participantId, note.id, trainer(), "archive-key", 0);

        assertThat(updated.content()).isEqualTo("updated content");
        assertThat(finalised).extracting(ParticipantDocumentationService.NoteView::status,
                ParticipantDocumentationService.NoteView::availableActions).containsExactly(ParticipantNote.Status.FINAL, List.of("ARCHIVE"));
        assertThat(archived.status()).isEqualTo(ParticipantNote.Status.ARCHIVED);
        assertStatus(HttpStatus.CONFLICT, () -> fixture.service.update("specialist", fixture.participantId, note.id, trainer(), 0, command("no")));
    }

    @Test
    void rejectsForeignAndStaleNotesAndReplaysIdempotentCreate() {
        Fixture fixture = fixture();
        UUID foreign = UUID.randomUUID();
        when(fixture.notes.findByIdAndParticipantIdAndSpecialistId(foreign, fixture.participantId, fixture.specialistId)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> fixture.service.note("specialist", fixture.participantId, foreign, trainer()));
        verify(fixture.authorization).requireCapabilities(any(), any(), any(), any(), any());

        ParticipantNote stale = ownedNote(fixture);
        stale.version = 1;
        assertStatus(HttpStatus.CONFLICT, () -> fixture.service.update("specialist", fixture.participantId, stale.id, trainer(), 0, command("new")));

        ParticipantNote existing = new ParticipantNote(fixture.participantId, fixture.specialistId, "session", "Title", "content", null, NOW);
        RecordIdempotency replay = new RecordIdempotency(fixture.specialistId, "NOTE_CREATE:" + fixture.participantId, "same-key", existing.id, NOW);
        when(fixture.idempotency.findBySpecialistIdAndOperationAndKey(fixture.specialistId, "NOTE_CREATE:" + fixture.participantId, "same-key"))
                .thenReturn(Optional.of(replay));
        when(fixture.notes.findById(existing.id)).thenReturn(Optional.of(existing));

        assertThat(fixture.service.create("specialist", fixture.participantId, trainer(), "same-key", command("changed")).id()).isEqualTo(existing.id);
    }

    private static ParticipantNote ownedNote(Fixture fixture) {
        ParticipantNote note = new ParticipantNote(fixture.participantId, fixture.specialistId, "session", "Title", "content", null, NOW);
        when(fixture.notes.findByIdAndParticipantIdAndSpecialistId(note.id, fixture.participantId, fixture.specialistId)).thenReturn(Optional.of(note));
        return note;
    }
    private static ParticipantDocumentationService.NoteCommand command(String content) {
        return new ParticipantDocumentationService.NoteCommand("session", "Title", content, null);
    }
    private static ActingContext trainer() { return new ActingContext(ProfessionalRole.TRAINER); }
    private static void assertStatus(HttpStatus status, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode()).isEqualTo(status);
    }
    private static Fixture fixture() {
        UUID specialistId = UUID.randomUUID();
        CurrentAccountService accounts = org.mockito.Mockito.mock(CurrentAccountService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        ParticipantNoteRepository notes = org.mockito.Mockito.mock(ParticipantNoteRepository.class);
        RecordIdempotencyRepository idempotency = org.mockito.Mockito.mock(RecordIdempotencyRepository.class);
        SpecialistAuthorizationPort authorization = org.mockito.Mockito.mock(SpecialistAuthorizationPort.class);
        return new Fixture(specialistId, UUID.randomUUID(), notes, idempotency, authorization,
                new ParticipantDocumentationService(org.mockito.Mockito.mock(ParticipantInterviewRepository.class), org.mockito.Mockito.mock(InterviewResponseRepository.class),
                        notes, org.mockito.Mockito.mock(ParticipantDocumentationEventRepository.class), idempotency, accounts,
                        authorization, org.mockito.Mockito.mock(AuditRecorder.class), Clock.fixed(NOW, ZoneOffset.UTC)));
    }
    private record Fixture(UUID specialistId, UUID participantId, ParticipantNoteRepository notes,
                           RecordIdempotencyRepository idempotency, SpecialistAuthorizationPort authorization,
                           ParticipantDocumentationService service) { }
}
