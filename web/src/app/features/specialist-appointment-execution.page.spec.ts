import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { SpecialistAppointmentExecutionPage } from './specialist-appointment-execution.page';

const executionContext = (overrides: Record<string, unknown> = {}) => ({
  appointmentId: 'appointment-1', participantId: 'participant-actual', participantName: 'Anna Kowalska',
  sessionTitle: 'Sesja mobilności', recordingAllowed: true,
  prescriptions: [
    { id: 'second', position: 2, exerciseName: 'Drugi ruch', canonicalDoseType: 'ENDURANCE', durationSeconds: 60 },
    { id: 'first', position: 1, exerciseName: 'Przysiad', canonicalDoseType: 'STRENGTH', sets: 3, repetitions: 8, externalLoadValue: 20, externalLoadUnit: 'kg' },
  ], ...overrides,
});

async function fixtureFor(context = executionContext()) {
  const specialistAppointmentExecution = { context: vi.fn().mockResolvedValue(context), recordAppointmentExecution: vi.fn().mockResolvedValue({}) };
  const router = { navigate: vi.fn().mockResolvedValue(true) };
  await TestBed.configureTestingModule({
    imports: [SpecialistAppointmentExecutionPage],
    providers: [
      { provide: ApiFacade, useValue: { specialistAppointmentExecution } },
      { provide: Router, useValue: router },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ participantId: 'url-participant', appointmentId: 'appointment-1' }), queryParamMap: convertToParamMap({}) } } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(SpecialistAppointmentExecutionPage);
  await fixture.whenStable(); fixture.detectChanges();
  return { fixture, api: specialistAppointmentExecution, router };
}

describe('SpecialistAppointmentExecutionPage', () => {
  it('renders prescriptions in plan order with human-readable names and planned dose', async () => {
    const { fixture } = await fixtureFor();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text.indexOf('Przysiad')).toBeLessThan(text.indexOf('Drugi ruch'));
    expect(text).toContain('3 × 8, 20 kg');
  });

  it('requires explicit outcomes and pain/difficulty before submission', async () => {
    const { fixture } = await fixtureFor(); const component = fixture.componentInstance as any;
    expect(component.ready()).toBe(false);
    component.select(0, 'AS_PLANNED'); component.select(1, 'AS_PLANNED');
    expect(component.ready()).toBe(false);
    component.sessionForm.patchValue({ painLevel: 0, difficultyLevel: 4 });
    expect(component.ready()).toBe(true);
  });

  it('maps as-planned, changed scalar fields, and skipped fields without fake zeroes', async () => {
    const { fixture } = await fixtureFor(); const component = fixture.componentInstance as any;
    component.select(0, 'AS_PLANNED');
    expect(component.resultFor(0)).toMatchObject({ exercisePrescriptionId: 'first', actualSets: 3, actualRepetitions: 8, actualExternalLoadValue: 20, actualExternalLoadUnit: 'kg', modified: false, skipped: false });
    component.select(0, 'CHANGED'); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Serie');
    component.prescriptionForms()[0].patchValue({ sets: 2, repetitions: 6, externalLoadValue: 15 });
    expect(component.resultFor(0)).toMatchObject({ actualSets: 2, actualRepetitions: 6, actualExternalLoadValue: 15, modified: true, skipped: false });
    component.select(0, 'SKIPPED');
    expect(component.resultFor(0)).toEqual({ exercisePrescriptionId: 'first', observationMode: 'DECLARED', modified: false, skipped: true });
  });

  it('keeps one idempotency key for an uncertain retry and returns to the context participant workspace', async () => {
    const { fixture, api, router } = await fixtureFor(); const component = fixture.componentInstance as any;
    component.select(0, 'AS_PLANNED'); component.select(1, 'AS_PLANNED'); component.sessionForm.patchValue({ painLevel: 1, difficultyLevel: 2 });
    api.recordAppointmentExecution.mockRejectedValueOnce(new Error('uncertain')).mockResolvedValueOnce({});
    await component.save(component.context());
    await component.save(component.context());
    expect(api.recordAppointmentExecution).toHaveBeenCalledTimes(2);
    expect(api.recordAppointmentExecution.mock.calls[0][0].idempotencyKey).toBe(api.recordAppointmentExecution.mock.calls[1][0].idempotencyKey);
    expect(api.recordAppointmentExecution.mock.calls[0][0].declareExecutionCommand.results).toHaveLength(2);
    expect(router.navigate).toHaveBeenCalledWith(['/specialist/clients', 'participant-actual'], { queryParams: { sessionRecorded: '1' } });
  });

  it('shows existing execution read-only and does not expose a second save action', async () => {
    const { fixture } = await fixtureFor(executionContext({ recordedExecution: { executionId: 'execution-1', outcome: 'COMPLETED' } }));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Realizacja została już zapisana');
    expect(text).not.toContain('Zapisz realizację sesji');
  });

  it('does not expose editing when the authoritative context rejects recording', async () => {
    const { fixture } = await fixtureFor(executionContext({ recordingAllowed: false }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nie można teraz zapisać realizacji');
  });

  it('returns to closeout only for the bounded closeout return mode', async () => {
    const { fixture, router } = await fixtureFor(); const component = fixture.componentInstance as any;
    (component as any).route.snapshot.queryParamMap = convertToParamMap({ returnTo: 'closeout' });
    await component.back(component.context());
    expect(router.navigate).toHaveBeenCalledWith(['/specialist/clients', 'participant-actual', 'appointments', 'appointment-1', 'closeout']);
  });
});
