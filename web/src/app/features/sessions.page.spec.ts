import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { SessionsPage } from './sessions.page';
import { ApiFacade } from '../core/api.facade';

async function settle(fixture: ReturnType<typeof TestBed.createComponent>): Promise<void> {
  await fixture.whenStable();
  for (let turn = 0; turn < 5; turn++) await Promise.resolve();
  fixture.detectChanges();
}

const api = {
  today: { today: vi.fn().mockResolvedValue({ activePlan: { revisionId: 'revision' }, sessions: [{ sessionId: 'session', title: 'Sesja', expectedDurationMinutes: 20 }] }) },
  planning: { sessions: vi.fn().mockResolvedValue([{ id: 'session', prescriptions: [{ id: 'prescription-1', position: 1, targetSets: 2, targetRepetitions: 8 }] }]) },
  safety: { checkIn: vi.fn().mockResolvedValue({}) },
  attempts: { start: vi.fn(), get: vi.fn(), resume: vi.fn(), pause: vi.fn(), progress: vi.fn(), complete: vi.fn() },
  barriers: { report: vi.fn() }
};

describe('SessionsPage', () => {
  beforeEach(async () => {
    sessionStorage.clear(); vi.clearAllMocks();
    api.today.today.mockResolvedValue({ activePlan: { revisionId: 'revision' }, sessions: [{ sessionId: 'session', title: 'Sesja', expectedDurationMinutes: 20 }] });
    api.planning.sessions.mockResolvedValue([{ id: 'session', prescriptions: [{ id: 'prescription-1', position: 1, targetSets: 2, targetRepetitions: 8 }] }]);
    await TestBed.configureTestingModule({ imports: [SessionsPage], providers: [{ provide: ApiFacade, useValue: api }] }).compileComponents();
  });

  it('renders one primary action for today\'s session', async () => {
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toContain('Twoja sesja na dziś');
    expect(page.querySelector('button[mat-flat-button]')?.textContent).toContain('Przejdź do wyboru');
    expect(page.querySelector('button.problem-link')?.textContent).toContain('Mam problem');
  });

  it('restores the active attempt after refresh', async () => {
    sessionStorage.setItem('moves.participant.active-attempt', 'attempt-1');
    api.attempts.get.mockResolvedValue({ attemptId: 'attempt-1', plannedSessionId: 'session', state: 'STARTED', progress: [] });
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    expect(api.attempts.get).toHaveBeenCalledWith({ attemptId: 'attempt-1' });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('W trakcie sesji');
  });

  it('uses prescriptions only from the exactly matching planned session for a fresh attempt', async () => {
    api.attempts.start.mockResolvedValue({ attemptId: 'attempt-1' });
    api.attempts.get.mockResolvedValue({ attemptId: 'attempt-1', plannedSessionId: 'session', state: 'STARTED', selectedVariantType: 'STANDARD', progress: [] });
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    const instance = fixture.componentInstance as any;
    instance.chooseSession(instance.todaySession()); instance.showCheckIn(); await instance.start(); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Krok 1 · 2 × 8');
    await instance.updateProgress('prescription-1', true);
    expect(api.attempts.progress).toHaveBeenCalledWith({ attemptId: 'attempt-1', progressCommand: { exercisePrescriptionId: 'prescription-1', completed: true } });
  });

  it('does not fall back to another session when its prescriptions are unavailable', async () => {
    api.planning.sessions.mockResolvedValue([{ id: 'other-session', prescriptions: [{ id: 'other-prescription' }] }]);
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    const instance = fixture.componentInstance as any;
    instance.chooseSession(instance.todaySession()); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nie możemy bezpiecznie wyświetlić pozycji tej sesji');
    expect(api.attempts.start).not.toHaveBeenCalled();
  });

  it('reports a barrier in two actions: opening the choices, then choosing a category', async () => {
    api.barriers.report.mockResolvedValue({ category: 'PAIN_OR_SYMPTOMS', proposedOptions: ['CONTACT_SPECIALIST'] });
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;
    (page.querySelector('button.problem-link') as HTMLButtonElement).click(); fixture.detectChanges();
    const category = [...page.querySelectorAll('.problem-categories button')].find(button => button.textContent?.includes('Ból lub dyskomfort')) as HTMLButtonElement;
    category.click(); await settle(fixture);
    expect(api.barriers.report).toHaveBeenCalledWith(expect.objectContaining({ barrierReportCommand: expect.objectContaining({ plannedSessionId: 'session', category: 'PAIN_OR_SYMPTOMS' }) }));
    expect(page.textContent).toContain('Zgłoszenie zapisane');
    expect(page.querySelector('button.contact-specialist')?.textContent).toContain('Skontaktuj się ze specjalistą');
  });

  it('keeps problem controls keyboard-focusable and exposes the accessibility shell rules', async () => {
    const fixture = TestBed.createComponent(SessionsPage); fixture.detectChanges(); await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;
    const problem = page.querySelector('button.problem-link') as HTMLButtonElement;
    problem.focus();
    expect(document.activeElement).toBe(problem);
    const styles = (SessionsPage as any).ɵcmp.styles.join(' ');
    expect(styles).toContain('font-size: clamp(18px');
    expect(styles).toContain('min-height: 44px');
    expect(styles).toContain(':focus-visible');
    expect(styles).toContain('prefers-reduced-motion: reduce');
    expect(styles).toContain('max-width: 480px');
  });
});
