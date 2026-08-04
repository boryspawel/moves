import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { ResponseError } from '../api/generated/src/runtime';
import { PatientTimelineEventPanelComponent, SpecialistParticipantWorkspacePage, TimelineEventComponent } from './specialist-participant-workspace.page';

describe('PatientTimelineEventPanelComponent', () => {
  it('gates appointment outcomes exclusively on current server-provided actions', async () => {
    await TestBed.configureTestingModule({ imports: [PatientTimelineEventPanelComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PatientTimelineEventPanelComponent);
    fixture.componentInstance.event = { category: 'APPOINTMENT', title: 'Spotkanie' };
    fixture.componentInstance.appointment = { availableActions: ['COMPLETE'] };
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Odbyło się');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Nieobecność');
    fixture.componentRef.setInput('appointment', { availableActions: ['MARK_NO_SHOW'] });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Odbyło się');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nieobecność');
    fixture.componentRef.setInput('event', { category: 'SESSION', title: 'Sesja' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Nieobecność');
  });
  it('does not render participant or technical source metadata in the detail panel', async () => {
    await TestBed.configureTestingModule({ imports: [PatientTimelineEventPanelComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PatientTimelineEventPanelComponent);
    fixture.componentInstance.event = { title: 'Wpis', actor: '123e4567-e89b-12d3-a456-426614174000', source: 'SYSTEM_EVENT' };
    fixture.componentInstance.participantDisplayName = 'Anna Kowalska';
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Uczestnik');
    expect(text).not.toContain('Źródło');
    expect(text).not.toContain('123e4567-e89b-12d3-a456-426614174000');
  });

  it('does not render UUID-only timeline metadata and retains human-readable text', async () => {
    const uuid = '123e4567-e89b-12d3-a456-426614174000';
    await TestBed.configureTestingModule({ imports: [PatientTimelineEventPanelComponent, TimelineEventComponent] }).compileComponents();

    const event = { title: uuid, summary: uuid, source: uuid };
    const timelineFixture = TestBed.createComponent(TimelineEventComponent);
    timelineFixture.componentInstance.event = event;
    timelineFixture.detectChanges();
    const panelFixture = TestBed.createComponent(PatientTimelineEventPanelComponent);
    panelFixture.componentInstance.event = event;
    panelFixture.detectChanges();

    for (const text of [(timelineFixture.nativeElement as HTMLElement).textContent ?? '', (panelFixture.nativeElement as HTMLElement).textContent ?? '']) {
      expect(text).not.toContain(uuid);
    }
    expect((timelineFixture.nativeElement as HTMLElement).textContent).toContain('Zdarzenie');
    expect((timelineFixture.nativeElement as HTMLElement).textContent).not.toContain('Brak krótkiego podsumowania');
    expect((panelFixture.nativeElement as HTMLElement).textContent).toContain('Zdarzenie');
    expect((panelFixture.nativeElement as HTMLElement).textContent).not.toContain('Brak dodatkowego opisu.');

    const readableEvent = { title: 'Rozmowa kontrolna', summary: 'Ustalono dalsze kroki.', source: 'Notatka specjalisty' };
    timelineFixture.componentRef.setInput('event', readableEvent);
    timelineFixture.detectChanges();
    panelFixture.componentRef.setInput('event', readableEvent);
    panelFixture.detectChanges();
    for (const text of [(timelineFixture.nativeElement as HTMLElement).textContent ?? '', (panelFixture.nativeElement as HTMLElement).textContent ?? '']) {
      expect(text).toContain('Rozmowa kontrolna');
      expect(text).toContain('Ustalono dalsze kroki.');
      expect(text).not.toContain('Notatka specjalisty');
    }
  });

  it('uses the localized appointment title and renders stale status', async () => {
    await TestBed.configureTestingModule({ imports: [PatientTimelineEventPanelComponent, TimelineEventComponent] }).compileComponents();
    const event = { category: 'APPOINTMENT', title: 'TRAINING', status: 'SCHEDULED', effectiveFrom: new Date('2026-01-01T09:00:00') };
    const timelineFixture = TestBed.createComponent(TimelineEventComponent);
    timelineFixture.componentInstance.event = event;
    timelineFixture.detectChanges();
    const panelFixture = TestBed.createComponent(PatientTimelineEventPanelComponent);
    panelFixture.componentInstance.event = event;
    panelFixture.detectChanges();

    expect((timelineFixture.nativeElement as HTMLElement).textContent).toContain('Trening');
    expect((timelineFixture.nativeElement as HTMLElement).querySelector('.event-status.stale')).not.toBeNull();
    expect((panelFixture.nativeElement as HTMLElement).textContent).toContain('Trening');
    expect((panelFixture.nativeElement as HTMLElement).querySelector('dd.stale')).not.toBeNull();
  });
});

describe('SpecialistParticipantWorkspacePage appointment outcomes', () => {
  it('uses generated complete with the current version and opens the newest refreshed event', async () => {
    const event = appointmentEvent('event-old', 'appointment-1', '2026-07-01T10:00:00Z');
    const newest = appointmentEvent('event-new', 'appointment-1', '2026-07-02T10:00:00Z');
    const complete = vi.fn().mockResolvedValue({}); const getSpecialistAppointment = vi.fn().mockResolvedValue({ appointmentId: 'appointment-1', version: 7, availableActions: ['COMPLETE'] });
    const { fixture, router } = await pageFixture([event], [newest], { complete, getSpecialistAppointment });
    (fixture.componentInstance as any).selected.set(event); (fixture.componentInstance as any).currentAppointment.set({ appointmentId: 'appointment-1', version: 7, availableActions: ['COMPLETE'] });
    await (fixture.componentInstance as any).recordOutcome('COMPLETE');
    expect(complete).toHaveBeenCalledWith(expect.objectContaining({ id: 'appointment-1', appointmentVersionCommand: { version: 7 }, idempotencyKey: expect.any(String) }));
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { eventId: 'event-new' } }));
    expect((fixture.componentInstance as any).selected()).toBe(newest);
  });

  it('refreshes the current appointment without retrying after a conflict', async () => {
    const event = appointmentEvent('event-old', 'appointment-1', '2026-07-01T10:00:00Z');
    const complete = vi.fn().mockRejectedValue(new ResponseError(new Response(null, { status: 409 }))); const getSpecialistAppointment = vi.fn().mockResolvedValue({ appointmentId: 'appointment-1', version: 8, availableActions: [] });
    const { fixture } = await pageFixture([event], [event], { complete, getSpecialistAppointment });
    (fixture.componentInstance as any).selected.set(event); (fixture.componentInstance as any).currentAppointment.set({ appointmentId: 'appointment-1', version: 7, availableActions: ['COMPLETE'] });
    await (fixture.componentInstance as any).recordOutcome('COMPLETE');
    expect(complete).toHaveBeenCalledTimes(1);
    expect(getSpecialistAppointment).toHaveBeenCalledWith({ id: 'appointment-1' });
    expect((fixture.componentInstance as any).currentAppointment().version).toBe(8);
    expect((fixture.componentInstance as any).announcement()).toContain('zmieniło się');
  });
});

describe('SpecialistParticipantWorkspacePage event deep links', () => {
  it('opens an in-list event without requesting its context', async () => {
    const event = appointmentEvent('event-in-list', 'appointment-1', '2026-07-01T10:00:00Z');
    const { fixture, api } = await pageFixture([event], [event], appointmentApi());
    await (fixture.componentInstance as any).load('participant-1', null);

    await (fixture.componentInstance as any).resolveSelection('participant-1', 'event-in-list', [event]);

    expect((fixture.componentInstance as any).selected()).toBe(event);
    expect(api.participantWorkspace.timelineEvent).not.toHaveBeenCalled();
  });

  it('opens an out-of-range event from context without adding it to the timeline', async () => {
    const listed = appointmentEvent('event-listed', 'appointment-1', '2026-07-01T10:00:00Z'); const outside = appointmentEvent('event-outside', 'appointment-2', '2025-01-01T10:00:00Z');
    const { fixture } = await pageFixture([listed], [listed], appointmentApi(), { timelineEvent: vi.fn().mockResolvedValue(outside) });
    await (fixture.componentInstance as any).load('participant-1', null);

    await (fixture.componentInstance as any).resolveSelection('participant-1', 'event-outside', [listed]);
    fixture.detectChanges();

    expect((fixture.componentInstance as any).selected()).toBe(outside);
    expect((fixture.componentInstance as any).events()).toEqual([listed]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Zdarzenie znajduje się poza aktualnie wybranym zakresem historii.');
  });

  it('clears only an unavailable eventId from the URL with replaceUrl', async () => {
    const { fixture, router } = await pageFixture([], [], appointmentApi(), { timelineEvent: vi.fn().mockRejectedValue(new ResponseError(new Response(null, { status: 404 }))) });

    await (fixture.componentInstance as any).resolveSelection('participant-1', 'missing', []);

    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { eventId: null }, queryParamsHandling: 'merge', replaceUrl: true }));
    expect((fixture.componentInstance as any).announcement()).toBe('Wybrane zdarzenie jest niedostępne.');
  });

  it('does not reload the workspace when only eventId changes', async () => {
    const event = appointmentEvent('event-in-list', 'appointment-1', '2026-07-01T10:00:00Z');
    const { fixture, api, params } = await pageFixture([event], [event], appointmentApi());
    await (fixture.componentInstance as any).load('participant-1', null); const timelineCalls = api.participantWorkspace.timeline.mock.calls.length;

    params.next(convertToParamMap({ eventId: 'event-in-list' }));
    await fixture.whenStable();

    expect(api.participantWorkspace.timeline).toHaveBeenCalledTimes(timelineCalls);
    expect((fixture.componentInstance as any).selected()).toBe(event);
  });

  it('uses the list instance when a previously contextual event enters the current timeline', async () => {
    const contextual = appointmentEvent('event-shared', 'appointment-1', '2025-01-01T10:00:00Z'); const listed = { ...contextual, effectiveFrom: new Date('2026-07-01T10:00:00Z') };
    const { fixture } = await pageFixture([], [], appointmentApi(), { timelineEvent: vi.fn().mockResolvedValue(contextual) });
    await (fixture.componentInstance as any).load('participant-1', null);
    await (fixture.componentInstance as any).resolveSelection('participant-1', 'event-shared', []);
    (fixture.componentInstance as any).events.set([listed]);

    await (fixture.componentInstance as any).resolveSelection('participant-1', 'event-shared', [listed]);

    expect((fixture.componentInstance as any).selected()).toBe(listed);
    expect((fixture.componentInstance as any).events()).toEqual([listed]);
  });

  it('closes a direct-link panel without requiring an opener', async () => {
    const { fixture, router } = await pageFixture([], [], appointmentApi());
    (fixture.componentInstance as any).opener = null;

    (fixture.componentInstance as any).close();
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { eventId: null }, queryParamsHandling: 'merge' }));
  });
});

function appointmentEvent(eventId: string, detailResourceId: string, effectiveFrom: string) { return { eventId, category: 'APPOINTMENT', detail: { detailResourceId }, effectiveFrom: new Date(effectiveFrom) }; }

function appointmentApi() { return { complete: vi.fn().mockResolvedValue({}), getSpecialistAppointment: vi.fn().mockResolvedValue({}), noShow: vi.fn(), create2: vi.fn() }; }

async function pageFixture(initialItems: ReturnType<typeof appointmentEvent>[], refreshedItems: ReturnType<typeof appointmentEvent>[], appointments: { complete: ReturnType<typeof vi.fn>; getSpecialistAppointment: ReturnType<typeof vi.fn> }, workspaceOverrides: { timelineEvent?: ReturnType<typeof vi.fn> } = {}) {
  const params = new BehaviorSubject(convertToParamMap({})); const router = { navigate: vi.fn().mockResolvedValue(true) }; let timelineCalls = 0;
  const api = { participantWorkspace: { workspace: vi.fn().mockResolvedValue({}), timeline: vi.fn().mockImplementation(() => Promise.resolve({ items: timelineCalls++ ? refreshedItems : initialItems })), timelineEvent: workspaceOverrides.timelineEvent ?? vi.fn() }, specialistClients: { list1: vi.fn().mockResolvedValue([]) }, appointments: { ...appointments, noShow: vi.fn(), create2: vi.fn() } };
  await TestBed.configureTestingModule({ imports: [SpecialistParticipantWorkspacePage], providers: [{ provide: ApiFacade, useValue: api }, { provide: ActivatedRoute, useValue: { queryParamMap: params, snapshot: { paramMap: convertToParamMap({ participantId: 'participant-1' }), queryParamMap: params.value } } }, { provide: Router, useValue: router }] }).compileComponents();
  const fixture = TestBed.createComponent(SpecialistParticipantWorkspacePage); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges(); return { fixture, router, api, params };
}
