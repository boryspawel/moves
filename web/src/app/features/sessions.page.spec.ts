import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CurrentExerciseEditorComponent, SessionsPage } from './sessions.page';
import { ApiFacade } from '../core/api.facade';

const api = {
  today: { today: vi.fn() }, safety: { checkIn: vi.fn() },
  attempts: { active: vi.fn(), start: vi.fn(), get3: vi.fn(), resume: vi.fn(), pause: vi.fn(), fact: vi.fn(), finish: vi.fn() },
  barriers: { report: vi.fn() },
};

async function settle(fixture: ReturnType<typeof TestBed.createComponent<SessionsPage>>): Promise<void> {
  await fixture.whenStable(); fixture.detectChanges();
}

function input(fixture: { nativeElement: HTMLElement; detectChanges(): void }, name: string, value: string): void {
  const element = fixture.nativeElement.querySelector<HTMLInputElement>(`input[formcontrolname="${name}"]`)!;
  element.value = value; element.dispatchEvent(new Event('input')); fixture.detectChanges();
}

describe('SessionsPage safety and barriers', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    api.today.today.mockResolvedValue({ activePlan: { activeRevisionId: 'revision' }, sessions: [{ sessionId: 'session', title: 'Sesja' }] });
    api.attempts.active.mockRejectedValue(new Error('no active attempt'));
    api.safety.checkIn.mockResolvedValue({});
    await TestBed.configureTestingModule({
      imports: [SessionsPage],
      providers: [
        { provide: ApiFacade, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    }).compileComponents();
  });

  it('keeps pain-area input in the safety check-in request', async () => {
    api.attempts.start.mockResolvedValue({ attemptId: 'attempt' });
    api.attempts.get3.mockResolvedValue({ attemptId: 'attempt', state: 'STARTED', session: { prescriptions: [] } });
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    const page = fixture.componentInstance;
    page.choose(page.today()!); page.stage.set('checkin');
    page.checkIn.controls.painArea.setValue('kolano');
    await page.start();
    expect(api.safety.checkIn).toHaveBeenCalledWith({ checkInRequest: { painLevel: 0, readinessLevel: 5, painArea: 'kolano' } });
  });

  it('reports a selected barrier and offers protected specialist contact', async () => {
    api.barriers.report.mockResolvedValue({ category: 'PAIN_OR_SYMPTOMS', proposedOptions: ['CONTACT_SPECIALIST'] });
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    const page = fixture.componentInstance;
    page.openProblem();
    await page.reportProblem('PAIN_OR_SYMPTOMS');
    expect(api.barriers.report).toHaveBeenCalledWith(expect.objectContaining({ barrierReportCommand: expect.objectContaining({ plannedSessionId: 'session', category: 'PAIN_OR_SYMPTOMS' }) }));
    expect(page.canContactSpecialist()).toBe(true);
    await page.contactSpecialist();
    expect(api.barriers.report).toHaveBeenLastCalledWith(expect.objectContaining({ barrierReportCommand: expect.objectContaining({ selectedAction: 'CONTACT_SPECIALIST' }) }));
  });

  it('uses the server active lookup on a new component instance and leaves a paused attempt paused', async () => {
    api.attempts.active.mockResolvedValue({ attemptId: 'paused-attempt' });
    api.attempts.get3.mockResolvedValue({ attemptId: 'paused-attempt', plannedSessionId: 'session', state: 'PAUSED', session: { prescriptions: [] } });
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    await (fixture.componentInstance as any).load();
    expect(api.attempts.active).toHaveBeenCalledWith({ plannedSessionId: 'session' });
    expect(api.attempts.resume).not.toHaveBeenCalled();
    expect(fixture.componentInstance.stage()).toBe('paused');
  });

  it('honours the route session selection before looking up its server attempt', async () => {
    TestBed.resetTestingModule();
    api.today.today.mockResolvedValue({ activePlan: { activeRevisionId: 'revision' }, sessions: [{ sessionId: 'first' }, { sessionId: 'selected' }] });
    api.attempts.active.mockRejectedValue(new Error('none'));
    await TestBed.configureTestingModule({
      imports: [SessionsPage],
      providers: [{ provide: ApiFacade, useValue: api }, { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => 'selected' } } } }, { provide: Router, useValue: { navigate: vi.fn() } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    expect(api.attempts.active).toHaveBeenCalledWith({ plannedSessionId: 'selected' });
  });

  it('sends ordered actual sets and records partial and skipped facts without skipped dose', async () => {
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    const page = fixture.componentInstance;
    page.attempt.set({ attemptId: 'attempt', state: 'STARTED', session: { prescriptions: [{ id: 'rx', sets: 3, repetitions: 10 }] } });
    api.attempts.fact.mockResolvedValue(page.attempt());
    await page.saveFact(page.prescriptions()[0], { outcome: 'PARTIAL', reason: 'Za trudno', modified: true, result: { actualSets: 3, actualSetDetails: [{ repetitions: 10 }, { repetitions: 10 }, { repetitions: 7 }] } });
    expect(api.attempts.fact).toHaveBeenCalledWith(expect.objectContaining({ factCommand: expect.objectContaining({ outcome: 'PARTIAL', reason: 'Za trudno', result: expect.objectContaining({ actualSets: 3, actualSetDetails: [{ repetitions: 10 }, { repetitions: 10 }, { repetitions: 7 }] }) }) }));
    await page.saveFact(page.prescriptions()[0], { outcome: 'SKIPPED', reason: 'Ból' });
    expect(api.attempts.fact).toHaveBeenLastCalledWith(expect.objectContaining({ factCommand: expect.objectContaining({ outcome: 'SKIPPED', reason: 'Ból', result: expect.objectContaining({ skipped: true }) }) }));
  });

  it('requires explicit reason for partial and skipped editor submissions', () => {
    const fixture = TestBed.createComponent(CurrentExerciseEditorComponent);
    fixture.componentInstance.prescription = { id: 'rx', repetitions: 1 }; fixture.detectChanges();
    const editor = fixture.componentInstance; const submitted = vi.fn(); editor.submitted.subscribe(submitted);
    editor.choose('PARTIAL'); editor.submit('PARTIAL'); expect(submitted).not.toHaveBeenCalled();
    editor.form.controls.reason.setValue('Za trudno'); editor.form.controls.repetitions.setValue('1'); editor.submit('PARTIAL');
    expect(submitted).toHaveBeenCalledWith(expect.objectContaining({ outcome: 'PARTIAL', reason: 'Za trudno' }));
    editor.choose('SKIPPED'); editor.form.controls.reason.setValue('Ból'); editor.submit('SKIPPED');
    expect(submitted).toHaveBeenLastCalledWith(expect.objectContaining({ outcome: 'SKIPPED', reason: 'Ból' }));
  });

  it('edits ordered actual sets in the DOM and saves changed performed work without averaging', async () => {
    const fixture = TestBed.createComponent(CurrentExerciseEditorComponent);
    fixture.componentInstance.prescription = { id: 'rx', sets: 3, repetitions: 10, durationSeconds: 30, contacts: 12, externalLoadValue: 8, externalLoadUnit: 'kg', intensityValue: 7, intensityZone: 'Z2', side: 'LEFT', tempo: '3010', restSeconds: 60, notes: 'Kontroluj tor ruchu' };
    const submitted = vi.fn(); fixture.componentInstance.submitted.subscribe(submitted); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('tempo: 3010');
    expect(fixture.nativeElement.textContent).toContain('odpoczynek: 60 s');
    (fixture.nativeElement.querySelectorAll('button')[1] as HTMLButtonElement).click(); fixture.detectChanges();
    input(fixture, 'repetitions', '10, 10, 7'); input(fixture, 'setDurations', '30, 30, 25'); input(fixture, 'setContacts', '12, 12, 10');
    input(fixture, 'externalLoadValue', '9'); input(fixture, 'intensityValue', '8'); input(fixture, 'intensityZone', 'Z3'); input(fixture, 'side', 'RIGHT');
    (fixture.nativeElement.querySelector('form button[type="submit"]') as HTMLButtonElement).click();
    expect(submitted).toHaveBeenCalledWith(expect.objectContaining({ outcome: 'PERFORMED', modified: true, result: expect.objectContaining({ actualSetDetails: [{ repetitions: 10, durationSeconds: 30, contacts: 12 }, { repetitions: 10, durationSeconds: 30, contacts: 12 }, { repetitions: 7, durationSeconds: 25, contacts: 10 }], actualExternalLoadValue: 9, actualIntensityValue: 8, actualIntensityZone: 'Z3', side: 'RIGHT' }) }));
  });

  it('uses the DOM one-tap planned action with the immutable planned snapshot', () => {
    const fixture = TestBed.createComponent(CurrentExerciseEditorComponent);
    fixture.componentInstance.prescription = { id: 'rx', sets: 3, repetitions: 10, durationSeconds: 30, contacts: 12, externalLoadValue: 8, externalLoadUnit: 'kg', side: 'LEFT' };
    const submitted = vi.fn(); fixture.componentInstance.submitted.subscribe(submitted); fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    expect(submitted).toHaveBeenCalledWith(expect.objectContaining({ outcome: 'PERFORMED', modified: false, result: expect.objectContaining({ actualSetDetails: [{ repetitions: 10, durationSeconds: 30, contacts: 12, externalLoadValue: 8, externalLoadUnit: 'kg' }, { repetitions: 10, durationSeconds: 30, contacts: 12, externalLoadValue: 8, externalLoadUnit: 'kg' }, { repetitions: 10, durationSeconds: 30, contacts: 12, externalLoadValue: 8, externalLoadUnit: 'kg' }], side: 'LEFT' }) }));
  });

  it('resumes a paused session through its visible conscious-resume button', async () => {
    api.attempts.active.mockResolvedValue({ attemptId: 'paused-attempt' });
    api.attempts.get3.mockResolvedValue({ attemptId: 'paused-attempt', state: 'PAUSED', session: { prescriptions: [] } });
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    await vi.waitFor(() => expect(fixture.componentInstance.stage()).toBe('paused'));
    [...fixture.nativeElement.querySelectorAll('button')].find(button => button.textContent?.includes('Wznów świadomie'))!.click(); await vi.waitFor(() => expect(api.attempts.resume).toHaveBeenCalledWith({ attemptId: 'paused-attempt' }));
    expect(api.attempts.resume).toHaveBeenCalledWith({ attemptId: 'paused-attempt' });
  });

  it('finishes and stops through visible controls with optional RPE and note', async () => {
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    const page = fixture.componentInstance; page.attempt.set({ attemptId: 'attempt', state: 'STARTED' }); page.stage.set('finish'); api.attempts.finish.mockResolvedValue({ outcome: 'COMPLETED' }); fixture.detectChanges();
    await vi.waitFor(() => expect(fixture.nativeElement.querySelector('input[formcontrolname="sessionRpe"]')).not.toBeNull());
    input(fixture, 'sessionRpe', '8'); input(fixture, 'note', 'Dobrze wykonane');
    (fixture.nativeElement.querySelector('button[mat-flat-button]') as HTMLButtonElement).click(); await settle(fixture);
    expect(api.attempts.finish).toHaveBeenCalledWith(expect.objectContaining({ finishCommand: expect.objectContaining({ intent: 'COMPLETE', sessionRpe: 8, note: 'Dobrze wykonane' }) }));
    page.stage.set('finish'); fixture.detectChanges();
    const stop = [...fixture.nativeElement.querySelectorAll('button')].find(button => button.textContent?.includes('Zatrzymaj sesję')) as HTMLButtonElement;
    expect(stop).toBeTruthy(); stop.click(); expect(page.stopping()).toBe(true); await fixture.whenStable(); fixture.detectChanges(); const stopReason = [...fixture.nativeElement.querySelectorAll<HTMLInputElement>('input')].at(-1)!; stopReason.value = 'Ból'; stopReason.dispatchEvent(new Event('input')); fixture.detectChanges();
    [...fixture.nativeElement.querySelectorAll('button')].find(button => button.textContent?.includes('Potwierdź zatrzymanie'))!.click(); await settle(fixture);
    expect(api.attempts.finish).toHaveBeenLastCalledWith(expect.objectContaining({ finishCommand: expect.objectContaining({ intent: 'STOP', stopReason: 'Ból' }) }));
  });

  it('shows a controlled not-found message for an invalid supplied session id', async () => {
    TestBed.resetTestingModule();
    api.today.today.mockResolvedValue({ activePlan: { activeRevisionId: 'revision' }, sessions: [{ sessionId: 'other' }] });
    await TestBed.configureTestingModule({ imports: [SessionsPage], providers: [{ provide: ApiFacade, useValue: api }, { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => 'missing' } } } }, { provide: Router, useValue: { navigate: vi.fn() } }] }).compileComponents();
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    expect(fixture.componentInstance.today()).toBeNull(); expect(fixture.nativeElement.textContent).toContain('Nie znaleziono wybranej sesji.'); expect(api.attempts.active).not.toHaveBeenCalled();
  });

  it('finishes through the generated finish contract and stops without replacing saved facts', async () => {
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    const page = fixture.componentInstance;
    page.attempt.set({ attemptId: 'attempt', state: 'STARTED', facts: [{ exercisePrescriptionId: 'rx', revisionNumber: 1, outcome: 'PERFORMED' }] });
    api.attempts.finish.mockResolvedValue({ outcome: 'COMPLETED' });
    await page.finish('COMPLETE');
    expect(api.attempts.finish).toHaveBeenCalledWith(expect.objectContaining({ attemptId: 'attempt', finishCommand: expect.objectContaining({ intent: 'COMPLETE', techniqueConfidenceLevel: 5 }) }));
    expect(page.stage()).toBe('terminal');
    expect(api.attempts.start).not.toHaveBeenCalled();
    page.stage.set('finish'); page.stopReason.setValue('Ból'); api.attempts.finish.mockResolvedValue({ outcome: 'STOPPED' });
    await page.finish('STOP');
    expect(api.attempts.finish).toHaveBeenLastCalledWith(expect.objectContaining({ finishCommand: expect.objectContaining({ intent: 'STOP', stopReason: 'Ból' }) }));
    expect(page.attempt()?.facts).toHaveLength(1);
    expect(page.terminalOutcome()).toBe('STOPPED');
  });
});
