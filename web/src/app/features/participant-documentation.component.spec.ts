import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { ParticipantDocumentationComponent } from './participant-documentation.component';

describe('ParticipantDocumentationComponent', () => {
  it('shows only server-advertised draft actions and keeps completed interviews read-only', async () => {
    const api = recordsApi({
      getParticipantDocumentationInterview: vi.fn().mockResolvedValue({ id: 'i-1', status: 'COMPLETED', questions: [{ code: 'presenting_concern', title: 'What brings the participant today?', required: true }, { code: 'goals', title: 'What does the participant want to achieve?', required: false }], answers: [{ code: 'presenting_concern', textValue: 'nie' }], availableActions: [] }),
    });
    const fixture = await fixtureFor(api, { panelType: 'interview', panelId: 'i-1' });
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Wywiad');
    expect(text).not.toContain('Zakończony');
    expect(text).not.toContain('Zapisz wersję roboczą');
    expect(text).toContain('nie');
    expect(text).not.toContain('Co uczestnik chce osiągnąć?');
  });

  it('uses the current version and a new idempotency key when finalising a note', async () => {
    const finaliseParticipantDocumentationNote = vi.fn().mockResolvedValue({});
    const api = recordsApi({ finaliseParticipantDocumentationNote, getParticipantDocumentationNote: vi.fn().mockResolvedValue({ id: 'n-1', title: 'T', category: 'GENERAL', content: 'C', status: 'DRAFT', version: 4, availableActions: ['FINALISE'] }) });
    const fixture = await fixtureFor(api, { panelType: 'note', panelId: 'n-1', panelMode: 'edit' });
    await (fixture.componentInstance as any).finaliseNote((fixture.componentInstance as any).note());
    expect(finaliseParticipantDocumentationNote).toHaveBeenCalledWith(expect.objectContaining({ participantDocumentationVersionRequest: { expectedVersion: 4 }, idempotencyKey: expect.any(String) }));
  });

  it('uses Polish presentation labels and keeps answers while navigating sections', async () => {
    const questions = [
      { code: 'presenting_concern', section: 'Presenting concern', title: 'What brings the participant today?', type: 'TEXT', required: true },
      { code: 'pain_level', section: 'Pain', title: 'Current pain level', type: 'NUMBER', required: true },
    ];
    const fixture = await fixtureFor(recordsApi({ getParticipantDocumentationInterview: vi.fn().mockResolvedValue({ id: 'i-1', status: 'DRAFT', questions, answers: [], availableActions: ['SAVE_DRAFT'] }) }), { panelType: 'interview', panelId: 'i-1', panelMode: 'edit' });
    const component = fixture.componentInstance as any;
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Z czym uczestnik zgłasza się dziś?');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('What brings the participant today?');
    component.interviewForm.controls.presenting_concern.setValue('Powrót do ruchu');
    component.nextSection(questions);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Ból i urazy');
    component.previousSection();
    fixture.detectChanges();
    expect(component.interviewForm.controls.presenting_concern.value).toBe('Powrót do ruchu');
  });

  it('guides to the first incomplete required section without invoking completion', async () => {
    const completeParticipantDocumentationInterview = vi.fn();
    const questions = [
      { code: 'presenting_concern', title: 'English title', type: 'TEXT', required: true },
      { code: 'pain_level', title: 'English pain', type: 'NUMBER', required: true },
    ];
    const fixture = await fixtureFor(recordsApi({ completeParticipantDocumentationInterview, getParticipantDocumentationInterview: vi.fn().mockResolvedValue({ id: 'i-1', status: 'DRAFT', questions, answers: [], availableActions: ['SAVE_DRAFT', 'COMPLETE'] }) }), { panelType: 'interview', panelId: 'i-1', panelMode: 'edit' });
    (fixture.componentInstance as any).selectSection(1);
    (fixture.componentInstance as any).requestCompletion((fixture.componentInstance as any).interview());
    fixture.detectChanges();
    expect((fixture.componentInstance as any).selectedSection()).toBe(0);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Uzupełnij wymagane pola przed zakończeniem wywiadu.');
    expect(completeParticipantDocumentationInterview).not.toHaveBeenCalled();
  });

  it('renders a Polish Material date control and keeps action footer server-gated', async () => {
    const questions = [
      { code: 'injury_date', title: 'Relevant injury date', type: 'DATE', required: false },
      { code: 'presenting_concern', title: 'What brings the participant today?', type: 'TEXT', required: true },
    ];
    const edit = await fixtureFor(recordsApi({ getParticipantDocumentationInterview: vi.fn().mockResolvedValue({ id: 'i-1', status: 'DRAFT', questions, answers: [], availableActions: ['SAVE_DRAFT'] }) }), { panelType: 'interview', panelId: 'i-1', panelMode: 'edit' });
    (edit.componentInstance as any).selectSection(1);
    edit.detectChanges();
    expect(edit.nativeElement.querySelector('mat-datepicker')).toBeTruthy();
    expect((edit.nativeElement as HTMLElement).textContent).not.toContain('Zakończ wywiad');
  });

  it('uses compact draft navigation and a completed document without navigation or empty answers', async () => {
    const draft = await fixtureFor(recordsApi({ getParticipantDocumentationInterview: vi.fn().mockResolvedValue({ id: 'i-1', status: 'DRAFT', questions: [{ code: 'presenting_concern', type: 'TEXT', required: true }], answers: [], availableActions: ['SAVE_DRAFT'] }) }), { panelType: 'interview', panelId: 'i-1', panelMode: 'edit' });
    const draftText = (draft.nativeElement as HTMLElement).textContent ?? '';
    expect(draftText).toContain('Cel wizyty');
    expect(draftText).toContain('Wstecz');
    expect(draftText).toContain('Dalej');
    expect(draftText).toContain('Zapisz i zamknij');
    expect(draftText).not.toContain('uzupełniona');
    draft.destroy();
    TestBed.resetTestingModule();
    const completed = await fixtureFor(recordsApi({ getParticipantDocumentationInterview: vi.fn().mockResolvedValue({ id: 'i-2', status: 'COMPLETED', completedAt: new Date('2026-08-03T10:00:00'), questions: [{ code: 'presenting_concern', type: 'TEXT', required: true }], answers: [{ code: 'presenting_concern', textValue: 'Powrót do ruchu' }], availableActions: [] }) }), { panelType: 'interview', panelId: 'i-2' });
    const completedText = (completed.nativeElement as HTMLElement).textContent ?? '';
    expect(completedText).toContain('Wywiad');
    expect(completedText).not.toContain('Zakończony');
    expect(completedText).not.toContain('3.08.2026');
    expect(completedText).not.toContain('Brak odpowiedzi');
    expect(completedText).toContain('Cel wizyty');
    expect(completed.nativeElement.querySelector('.record-panel nav')).toBeNull();
    expect(completedText).not.toContain('Zakończ wywiad');
  });

  it('uses only the requested interview summary card text', async () => {
    const fixture = await fixtureFor(recordsApi({
      listParticipantDocumentationInterviews: vi.fn().mockResolvedValue([{ id: 'i-1', status: 'COMPLETED', completedAt: new Date('2026-08-03T10:00:00'), version: 8, availableActions: [] }]),
    }), {});
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Wywiad');
    expect(text).toContain('Uzupełniony');
    expect(text).toContain('Otwórz');
    expect(text).not.toContain('COMPLETED');
    expect(text).not.toContain('3.08.2026');
    expect(text).not.toContain('8');
  });

  it('uses the server-advertised draft continuation and completed update actions', async () => {
    const startParticipantDocumentationInterview = vi.fn().mockResolvedValue({ id: 'i-next' });
    const fixture = await fixtureFor(recordsApi({
      listParticipantDocumentationInterviews: vi.fn().mockResolvedValue([{ id: 'i-1', status: 'COMPLETED', availableActions: ['START_NEXT_INTERVIEW'] }]),
      startParticipantDocumentationInterview,
    }), {});
    const opened = vi.fn();
    fixture.componentInstance.opened.subscribe(opened);
    const update = Array.from(fixture.nativeElement.querySelectorAll('button')).find((button: any) => button.textContent.includes('Aktualizuj wywiad')) as HTMLButtonElement;
    update.click();
    await fixture.whenStable();
    expect(startParticipantDocumentationInterview).toHaveBeenCalledWith(expect.objectContaining({ idempotencyKey: expect.any(String) }));
    expect(opened).toHaveBeenCalledWith({ type: 'interview', id: 'i-next', mode: 'edit' });

    fixture.componentInstance.interviews.set([{ id: 'i-draft', status: 'DRAFT', availableActions: ['SAVE_DRAFT'] }]);
    fixture.detectChanges();
    const continueButton = Array.from(fixture.nativeElement.querySelectorAll('button')).find((button: any) => button.textContent.includes('Kontynuuj wywiad')) as HTMLButtonElement;
    continueButton.click();
    expect(opened).toHaveBeenCalledWith({ type: 'interview', id: 'i-draft', mode: 'edit' });
  });

  it('creates a note only after local nonblank input and opens the returned draft', async () => {
    const createParticipantDocumentationNote = vi.fn().mockResolvedValue({ id: 'n-1', availableActions: ['UPDATE', 'FINALISE'] });
    const fixture = await fixtureFor(recordsApi({ createParticipantDocumentationNote }), {});
    const component = fixture.componentInstance as any;
    const opened = vi.fn();
    component.opened.subscribe(opened);
    component.openNewNote();
    await component.createNote();
    expect(createParticipantDocumentationNote).not.toHaveBeenCalled();
    component.newNoteForm.setValue({ title: 'Plan', category: 'GENERAL', content: 'Ustalono kolejne kroki.' });
    await component.createNote();
    expect(createParticipantDocumentationNote).toHaveBeenCalledWith(expect.objectContaining({
      noteRequest: { title: 'Plan', category: 'GENERAL', content: 'Ustalono kolejne kroki.' },
      idempotencyKey: expect.any(String),
    }));
    expect(opened).toHaveBeenCalledWith({ type: 'note', id: 'n-1', mode: 'edit' });
  });

  it('renders the new-note Material fields, shows validation feedback, and cancels without creating', async () => {
    const createParticipantDocumentationNote = vi.fn();
    const fixture = await fixtureFor(recordsApi({ createParticipantDocumentationNote }), {});
    const component = fixture.componentInstance as any;

    component.openNewNote();
    fixture.detectChanges();
    const form = (fixture.nativeElement as HTMLElement).querySelector('.new-note-form')!;
    expect(form.querySelectorAll('mat-form-field')).toHaveLength(3);
    expect(form.querySelector('input[matInput][formcontrolname="title"]')).not.toBeNull();
    expect(form.querySelector('mat-select[formcontrolname="category"]')).not.toBeNull();
    expect(form.querySelector('textarea[matInput][formcontrolname="content"]')?.getAttribute('rows')).toBe('7');
    expect(form.textContent).toContain('Zapisz notatkę');

    await component.createNote();
    fixture.detectChanges();
    expect(form.textContent).toContain('Tytuł jest wymagany.');
    expect(form.textContent).toContain('Treść jest wymagana.');
    expect(createParticipantDocumentationNote).not.toHaveBeenCalled();

    (Array.from(form.querySelectorAll('button')).find((button) => button.textContent?.includes('Anuluj')) as HTMLButtonElement).click();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.new-note-form')).toBeNull();
    expect(createParticipantDocumentationNote).not.toHaveBeenCalled();
  });
});

async function fixtureFor(api: any, inputs: any) {
  await TestBed.configureTestingModule({ imports: [ParticipantDocumentationComponent], providers: [{ provide: ApiFacade, useValue: api }] }).compileComponents();
  const fixture = TestBed.createComponent(ParticipantDocumentationComponent);
  fixture.componentRef.setInput('participantId', 'participant-free');
  fixture.componentRef.setInput('role', 'TRAINER');
  for (const [key, value] of Object.entries(inputs)) fixture.componentRef.setInput(key, value);
  fixture.detectChanges();
  await (fixture.componentInstance as any).ngOnChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

function recordsApi(overrides: Record<string, unknown> = {}) {
  return { participantDocumentation: {
    listParticipantDocumentationInterviews: vi.fn().mockResolvedValue([]), listParticipantDocumentationNotes: vi.fn().mockResolvedValue([]),
    getParticipantDocumentationInterview: vi.fn(), getParticipantDocumentationNote: vi.fn(), startParticipantDocumentationInterview: vi.fn(), createParticipantDocumentationNote: vi.fn(),
    saveParticipantDocumentationInterviewDraft: vi.fn(), completeParticipantDocumentationInterview: vi.fn(), updateParticipantDocumentationNote: vi.fn(), finaliseParticipantDocumentationNote: vi.fn(), archiveParticipantDocumentationNote: vi.fn(), ...overrides,
  } };
}
