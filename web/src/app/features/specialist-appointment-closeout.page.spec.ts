import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { SpecialistAppointmentCloseoutPage } from './specialist-appointment-closeout.page';

const closeout = (overrides: Record<string, unknown> = {}) => ({ appointmentId: 'appointment-1', participantId: 'participant-actual', participantName: 'Anna', status: 'IN_PROGRESS', version: 3, executionRequired: false, executionRecorded: false, canComplete: true, measurements: [], ...overrides });
async function fixtureFor(context = closeout()) {
  const api = { onboarding: { state: vi.fn().mockResolvedValue({ profile: { specialistKind: 'TRAINER' } }) }, appointmentCloseout: { getSpecialistAppointmentCloseoutContext: vi.fn().mockResolvedValue(context) }, participantMeasurements: { participantMeasurementCatalog: vi.fn().mockResolvedValue([]), recordParticipantMeasurement: vi.fn().mockResolvedValue({}) }, participantDocumentation: { createParticipantDocumentationNote: vi.fn().mockResolvedValue({}) }, appointments: { complete: vi.fn().mockResolvedValue({}) } };
  const router = { navigate: vi.fn().mockResolvedValue(true) };
  await TestBed.configureTestingModule({ imports: [SpecialistAppointmentCloseoutPage], providers: [{ provide: ApiFacade, useValue: api }, { provide: Router, useValue: router }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ appointmentId: 'appointment-1', participantId: 'url-participant' }) } } }] }).compileComponents();
  const fixture = TestBed.createComponent(SpecialistAppointmentCloseoutPage); await fixture.whenStable(); fixture.detectChanges(); return { fixture, api, router };
}
describe('SpecialistAppointmentCloseoutPage', () => {
  it('loads refresh-safe context using the active specialist role and routes missing execution through the bounded return', async () => { const { fixture, api, router } = await fixtureFor(closeout({ executionRequired: true, canComplete: false })); const component = fixture.componentInstance as any; expect(api.appointmentCloseout.getSpecialistAppointmentCloseoutContext).toHaveBeenCalledWith({ appointmentId: 'appointment-1', actingContext: 'TRAINER' }); await component.openExecution(component.context()); expect(router.navigate).toHaveBeenCalledWith(['/specialist/clients', 'participant-actual', 'appointments', 'appointment-1', 'session'], { queryParams: { returnTo: 'closeout' } }); });
  it('keeps optional measurements and saved summary visible while completing with existing lifecycle version', async () => { const { fixture, api } = await fixtureFor(closeout({ executionRecorded: true, execution: { outcome: 'PARTIAL' }, measurements: [{ measurementId: 'm-1', value: 75.8, unit: 'kg' },], summaryNote: { title: 'Podsumowanie spotkania', content: 'Bez zmian' } })); const component = fixture.componentInstance as any; expect((fixture.nativeElement as HTMLElement).textContent).toContain('75.8 kg'); await component.complete(component.context()); expect(api.participantDocumentation.createParticipantDocumentationNote).not.toHaveBeenCalled(); expect(api.appointments.complete).toHaveBeenCalledWith(expect.objectContaining({ id: 'appointment-1', appointmentVersionCommand: { version: 3 } })); });
});
