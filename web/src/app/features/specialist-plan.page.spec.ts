import { ActivatedRoute } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { convertToParamMap, provideRouter, Router } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localePl from '@angular/common/locales/pl';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { SpecialistPlanPage } from './specialist-plan.page';

registerLocaleData(localePl);

function facade() {
  const specialistPlans = {
    readSpecialistPlanRevision: vi.fn().mockResolvedValue({ planId: 'plan-1', name: 'Plan powrotu', revision: {
      revisionId: 'revision-1', revisionVersion: 4, status: 'DRAFT', validFrom: new Date('2026-10-01'), validTo: new Date('2026-10-31'),
      goals: [{ sourceParticipantGoalId: 'goal-1', title: 'Pewny chód', category: 'MOBILITY' }],
      cycles: [{ microcycles: [{ sessions: [{ id: 'session-1', title: 'Mobilizacja', scheduledDate: new Date('2026-10-03'), expectedDurationMinutes: 30, sourceExerciseSetVersionId: 'set-v1', prescriptions: [{ id: 'prescription-1', materializedSnapshot: JSON.stringify({ dose: { repetitions: 8, side: 'LEFT', tempo: '3-1-1', intensity: 'LOW' } }) }] }] }] }]
    } }),
    listSpecialistPlans: vi.fn().mockResolvedValue([{ planId: 'plan-draft', currentRevisionId: 'revision-draft', name: 'Zapisany szkic', status: 'DRAFT' }]), listSpecialistPlanRevisions: vi.fn().mockResolvedValue([]), createSpecialistPlan: vi.fn().mockResolvedValue({ planId: 'plan-2', revision: { revisionId: 'revision-2' } }), createSpecialistPlanRevision: vi.fn(),
    addSpecialistPlanSession: vi.fn().mockResolvedValue({ planId: 'plan-1', revision: { revisionId: 'revision-1', revisionVersion: 5, status: 'DRAFT', cycles: [] } }), updateSpecialistPlanPeriod: vi.fn(), updateSpecialistPlanSession: vi.fn(), deleteSpecialistPlanSession: vi.fn()
  };
  const planWorkflow = {
    status: vi.fn().mockResolvedValue({ state: { status: 'NEEDS_REVIEW' }, assessment: { factors: [{ id: 'warning-1', result: 'WARNING', explanationCode: 'LOAD_REVIEW' }] }, acknowledgedWarningFactorIds: new Set<string>() }),
    validate: vi.fn().mockResolvedValue({}), acknowledge: vi.fn().mockResolvedValue({}), activate: vi.fn().mockResolvedValue({})
  };
  return {
    onboarding: { state: vi.fn().mockResolvedValue({ profile: { specialistKind: 'TRAINER' } }) },
    participantGoals: { listParticipantGoals: vi.fn().mockResolvedValue([{ id: 'goal-1', title: 'Pewny chód', status: 'ACTIVE' }]) },
    exerciseSets: { list: vi.fn().mockResolvedValue([{ title: 'Mobilność', versions: [{ id: 'set-v1', exerciseSetId: 'set-1', versionNumber: 1, status: 'PUBLISHED', itemCount: 1 }] }]), version: vi.fn().mockResolvedValue({ title: 'Mobilność', versionNumber: 1, items: [{ id: 'item-1', exerciseVersionId: 'exercise-v1', snapshot: { canonicalName: 'Wykrok' }, dose: { repetitions: 8, side: 'LEFT', loadUnit: 'KG', tempo: '3-1-1', restSeconds: 60, intensity: 'LOW' } }] }) },
    specialistPlans, planWorkflow, planningV2: { validateStructurally: vi.fn().mockResolvedValue({ result: 'PASS', violations: [] }) }
  };
}

describe('SpecialistPlanPage', () => {
  async function create(params: Record<string, string> = { participantId: 'participant-1', planId: 'plan-1', revisionId: 'revision-1' }, data: Record<string, unknown> = {}) {
    const api = facade();
    const route = { snapshot: { paramMap: convertToParamMap(params), data } };
    await TestBed.configureTestingModule({ imports: [SpecialistPlanPage], providers: [provideRouter([]), { provide: ApiFacade, useValue: api }, { provide: ActivatedRoute, useValue: route }] }).compileComponents();
    const fixture = TestBed.createComponent(SpecialistPlanPage); fixture.detectChanges();
    const page = fixture.componentInstance as any;
    await page.load(); fixture.detectChanges();
    return { api, fixture, page };
  }

  it('restores a revision deep link and renders its immutable typed-dose snapshot', async () => {
    const { api, fixture } = await create();
    const root = fixture.nativeElement as HTMLElement;

    expect(api.specialistPlans.readSpecialistPlanRevision).toHaveBeenCalledWith({ participantId: 'participant-1', planId: 'plan-1', revisionId: 'revision-1' });
    expect(root.textContent).toContain('Plan powrotu');
    expect(root.textContent).toContain('Mobilizacja');
    expect(root.textContent).toContain('repetitions: 8');
    expect(root.textContent).toContain('tempo: 3-1-1');
  });

  it('previews an exact published source version and sends validation, acknowledgement and activation to the workflow', async () => {
    const { api, fixture, page } = await create();
    page.sessionForm.patchValue({ setVersionId: 'set-v1' }); await page.previewSelectedSet(); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('loadUnit: KG');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('restSeconds: 60');

    page.sessionForm.patchValue({ title: 'Sesja źródłowa', scheduledDate: '2026-10-04', duration: 45, setVersionId: 'set-v1' });
    await page.saveSession();
    expect(api.specialistPlans.addSpecialistPlanSession).toHaveBeenCalledWith(expect.objectContaining({ sessionCommand: expect.objectContaining({ exerciseSetVersionId: 'set-v1', scheduledDate: new Date('2026-10-04'), expectedDurationMinutes: 45 }) }));

    api.specialistPlans.readSpecialistPlanRevision.mockResolvedValue({ planId: 'plan-1', name: 'Plan powrotu', revision: { revisionId: 'revision-1', revisionVersion: 5, status: 'NEEDS_REVIEW', cycles: [] } });
    await page.validateWorkflow(); fixture.detectChanges();
    expect(api.planWorkflow.validate).toHaveBeenCalledWith(expect.objectContaining({ revisionId: 'revision-1' }));
    expect(page.editable()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Utwórz nową rewizję');
    page.editor.set({ planId: 'plan-1', revision: { revisionId: 'revision-1', revisionVersion: 5, status: 'DRAFT', cycles: [] } });
    page.rationale.set('Zaakceptowano po rozmowie z uczestnikiem'); await page.acknowledge();
    expect(api.planWorkflow.acknowledge).toHaveBeenCalledWith(expect.objectContaining({ acknowledgeWarningCommand: expect.objectContaining({ factorIds: new Set(['warning-1']) }) }));

    page.workflow.set({ state: { status: 'NEEDS_REVIEW' }, assessment: { factors: [{ id: 'warning-1', result: 'WARNING' }] }, acknowledgedWarningFactorIds: new Set(['warning-1']) });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aktywuj rewizję');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await page.activate();
    expect(api.planWorkflow.activate).toHaveBeenCalledWith(expect.objectContaining({ revisionId: 'revision-1' }));
    expect(navigate).toHaveBeenCalledWith(['/specialist/clients', 'participant-1']);
  });

  it('uses the rendered participant goal and period form to create a participant-scoped plan', async () => {
    const { api, fixture, page } = await create({ participantId: 'participant-1' });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Pewny chód');
    page.createForm.patchValue({ name: 'Plan mobilności', goalId: 'goal-1', validFrom: '2026-10-01', validTo: '2026-10-31', phaseIntent: 'Powrót do sprawności' });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await page.create();
    expect(api.specialistPlans.createSpecialistPlan).toHaveBeenCalledWith(expect.objectContaining({ participantId: 'participant-1', createPlanCommand: expect.objectContaining({ participantGoalId: 'goal-1', validFrom: new Date('2026-10-01'), validTo: new Date('2026-10-31') }) }));
    expect(navigate).toHaveBeenCalledWith(['/specialist/clients', 'participant-1', 'plans', 'plan-2', 'revisions', 'revision-2']);
  });

  it('lists a saved draft through the participant-scoped landing route', async () => {
    const { api, fixture } = await create({ participantId: 'participant-1' }, { plansIndex: true });
    const root = fixture.nativeElement as HTMLElement;
    expect(api.specialistPlans.listSpecialistPlans).toHaveBeenCalledWith({ participantId: 'participant-1', role: 'TRAINER' });
    expect(root.textContent).toContain('Zapisany szkic');
    expect(root.querySelector('article a')?.getAttribute('href')).toBe('/specialist/clients/participant-1/plans/plan-draft/revisions/revision-draft');
  });

  it('round-trips an availability instant through browser-local datetime input without UTC shifting', async () => {
    const { page } = await create();
    const instant = new Date('2026-10-05T12:30:00+02:00');
    expect(new Date(page.dateTime(instant)).getTime()).toBe(instant.getTime());
  });
});
