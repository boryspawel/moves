import { registerLocaleData } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import localePl from '@angular/common/locales/pl';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { ResponseError } from '../api/generated/src/runtime';
import {
  PatientTimelineEventPanelComponent,
  ParticipantGoalsComponent,
  SpecialistParticipantWorkspacePage,
  TimelineEventComponent,
} from './specialist-participant-workspace.page';

registerLocaleData(localePl);

describe('ParticipantGoalsComponent', () => {
  it('creates a catalog preset with a derived title and no technical metadata fields', async () => {
    const createParticipantGoalFromPreset = vi.fn().mockResolvedValue({});
    const listParticipantGoalMetricPresets = vi.fn().mockResolvedValue([
      { id: 'COMPLETION_TIME', label: 'Czas wykonania', allowedUnits: ['s'], requiredContextFields: ['ACTIVITY'], baselineSupported: true },
    ]);
    const fixture = await participantGoalsFixture({ createParticipantGoalFromPreset, listParticipantGoalMetricPresets });
    const component = fixture.componentInstance as any;

    component.openCreate();
    await fixture.whenStable();
    component.choosePreset(component.presets()[0]);
    component.createForm.patchValue({ activity: 'Bieg', baselineInput: '12:30', targetInput: '10:00' });
    fixture.detectChanges();
    const dialog = fixture.nativeElement as HTMLElement;
    expect(dialog.querySelector('input[formcontrolname="titleOverride"]')).toBeNull();
    expect(dialog.querySelector('textarea[formcontrolname="description"]')).toBeNull();
    expect(dialog.querySelector('input[formcontrolname="targetDate"]')).toBeNull();

    await component.create();

    expect(createParticipantGoalFromPreset).toHaveBeenCalledWith(expect.objectContaining({
      createFromPresetRequest: expect.objectContaining({ presetId: 'COMPLETION_TIME', baselineValue: 750, targetValue: 600, activity: 'Bieg' }),
    }));
  });

  it('does not expose priority as a preset-creation field', async () => {
    const createParticipantGoal = vi.fn();
    const fixture = await participantGoalsFixture({ createParticipantGoal });
    const component = fixture.componentInstance as any;

    component.openCreate();
    fixture.detectChanges();
    expect(component.createForm.controls.priority).toBeUndefined();
    expect((fixture.nativeElement as HTMLElement).querySelector('input[formcontrolname="priority"]')).toBeNull();

    await component.create();
    expect(createParticipantGoal).not.toHaveBeenCalled();
  });

  it('edits supported active-goal metadata with the current version and update action gate', async () => {
    const goal = {
      id: 'goal-1',
      category: 'PERFORMANCE',
      title: 'Pierwotny tytuł',
      description: 'Pierwotny opis',
      priority: 5,
      status: 'ACTIVE',
      outcomes: [{ id: 'outcome-1', metricCode: 'TIME', targetValue: 10, unit: 'min' }],
      availableActions: ['UPDATE'],
      version: 7,
    };
    const updateParticipantGoal = vi.fn().mockResolvedValue(goal);
    const fixture = await participantGoalsFixture({
      getParticipantGoal: vi.fn().mockResolvedValue(goal),
      updateParticipantGoal,
    });
    const component = fixture.componentInstance as any;

    await component.open(goal);
    component.panelMode.set('edit');
    fixture.detectChanges();
    const editor = fixture.nativeElement as HTMLElement;
    expect(editor.querySelector('input[formcontrolname="category"]')?.getAttribute('readonly')).not.toBeNull();
    expect(editor.querySelector('select[formcontrolname="perspective"]')).toBeNull();
    expect(editor.querySelector('[formarrayname="outcomes"]')).toBeNull();

    component.updateForm.patchValue({
      title: 'Zmieniony tytuł',
      description: 'Zmieniony opis',
      priority: 10,
      targetDate: '2026-10-15',
    });
    await component.update();

    expect(updateParticipantGoal).toHaveBeenCalledWith(
      expect.objectContaining({
        participantId: 'participant-1',
        goalId: 'goal-1',
        actingContext: 'TRAINER',
        updateParticipantGoalRequest: {
          title: 'Zmieniony tytuł',
          description: 'Zmieniony opis',
          priority: 10,
          targetDate: new Date('2026-10-15'),
          expectedVersion: 7,
        },
      }),
    );
  });

  it('renders an accessible, semantic read-only goal panel without technical metadata', async () => {
    const goal = {
      id: 'goal-1',
      category: 'PERFORMANCE',
      title: 'Pobiec 5 km',
      priority: 42,
      status: 'ACTIVE',
      targetDate: new Date('2026-10-15'),
      outcomes: [{
        id: 'outcome-1',
        metricCode: 'BODY_CIRCUMFERENCE:WAIST',
        baseline: 4,
        targetValue: 5,
        targetComparator: 'AT_LEAST',
        unit: 'km',
        progressState: 'IN_PROGRESS',
      }],
      availableActions: ['UPDATE', 'RECORD_OBSERVATION'],
    };
    const fixture = await participantGoalsFixture({ getParticipantGoal: vi.fn().mockResolvedValue(goal) });
    const component = fixture.componentInstance as any;

    component.goals.set([goal]);
    component.state.set('loaded');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.goal-card')?.textContent).toContain('Obwód talii');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('BODY_CIRCUMFERENCE:WAIST');

    await component.open(goal);
    fixture.detectChanges();

    const panel = (fixture.nativeElement as HTMLElement).querySelector('.goal-panel')!;
    expect(panel.getAttribute('role')).toBe('dialog');
    expect(panel.querySelector('.goal-panel-header h2')?.textContent).toContain('Pobiec 5 km');
    expect(panel.querySelector('button[aria-label="Zamknij szczegóły celu"]')?.hasAttribute('mat-icon-button')).toBe(true);
    expect(panel.querySelector('button[aria-label="Zamknij szczegóły celu"] svg')).not.toBeNull();
    expect(panel.querySelectorAll('.goal-panel-section h3')).toHaveLength(3);
    expect(panel.textContent).toContain('Pobiec 5 km');
    expect(panel.textContent).toContain('Aktywny · Wynik sportowy');
    expect(panel.querySelector('.goal-facts dd')?.textContent).toContain('4 km');
    expect(panel.textContent).toContain('Wartość docelowa:5 km');
    expect(panel.textContent).toContain('Porównanie:co najmniej');
    expect(panel.textContent).toContain('Brak zapisanych pomiarów.');
    expect(panel.textContent).toContain('Stan:W trakcie');
    expect(panel.textContent).toContain('Liczba pomiarów:0');
    expect(panel.textContent).not.toContain('BODY_CIRCUMFERENCE:WAIST');
    expect(panel.textContent).not.toContain('42');
    expect(panel.querySelector('input, textarea, form')).toBeNull();
    expect(panel.textContent).toContain('Edytuj');
    expect(panel.textContent).toContain('Dodaj pomiar');

    component.panelMode.set('observation');
    fixture.detectChanges();
    expect(panel.querySelector('option')?.textContent).toContain('Obwód talii (km)');
  });

  it('switches to action-gated edit and observation forms, then cancels back to view', async () => {
    const goal = {
      id: 'goal-1', category: 'PERFORMANCE', title: 'Cel', priority: 1, status: 'ACTIVE',
      outcomes: [{ id: 'outcome-1', targetValue: 5, unit: 'km' }],
      availableActions: ['UPDATE', 'RECORD_OBSERVATION'],
    };
    const fixture = await participantGoalsFixture({ getParticipantGoal: vi.fn().mockResolvedValue(goal) });
    const component = fixture.componentInstance as any;
    await component.open(goal);
    fixture.detectChanges();

    const panel = () => (fixture.nativeElement as HTMLElement).querySelector('.goal-panel')!;
    (Array.from(panel().querySelectorAll('button')).find((button) => button.textContent?.includes('Edytuj')) as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(panel().querySelector('input[formcontrolname="title"]')).not.toBeNull();
    expect(panel().querySelector('.goal-panel-form')).not.toBeNull();
    expect(panel().querySelector('.goal-form-actions button[type="submit"]')).not.toBeNull();
    (Array.from(panel().querySelectorAll('button')).find((button) => button.textContent?.includes('Anuluj')) as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.panelMode()).toBe('view');
    expect(component.selected()?.id).toBe('goal-1');

    (Array.from(panel().querySelectorAll('button')).find((button) => button.textContent?.includes('Dodaj pomiar')) as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(panel().querySelector('input[formcontrolname="measuredAt"]')).not.toBeNull();
    (Array.from(panel().querySelectorAll('button')).find((button) => button.textContent?.includes('Anuluj')) as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.panelMode()).toBe('view');
    expect(component.selected()?.id).toBe('goal-1');
  });

  it('does not offer mutations for a terminal goal even when actions are present', async () => {
    const goal = {
      id: 'goal-1', category: 'PERFORMANCE', title: 'Cel', status: 'ACHIEVED',
      outcomes: [], availableActions: ['UPDATE', 'RECORD_OBSERVATION', 'ACHIEVE', 'CANCEL'],
    };
    const fixture = await participantGoalsFixture({ getParticipantGoal: vi.fn().mockResolvedValue(goal) });
    await (fixture.componentInstance as any).open(goal);
    fixture.detectChanges();
    const text = ((fixture.nativeElement as HTMLElement).querySelector('.goal-panel')?.textContent ?? '');
    expect(text).not.toContain('Edytuj');
    expect(text).not.toContain('Dodaj pomiar');
    expect(text).not.toContain('Oznacz jako osiągnięty');
    expect(text).not.toContain('Anuluj cel');
  });
});

describe('PatientTimelineEventPanelComponent', () => {
  it('gates appointment outcomes exclusively on current server-provided actions', async () => {
    await TestBed.configureTestingModule({
      imports: [PatientTimelineEventPanelComponent],
    }).compileComponents();
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
    await TestBed.configureTestingModule({
      imports: [PatientTimelineEventPanelComponent],
    }).compileComponents();
    const fixture = TestBed.createComponent(PatientTimelineEventPanelComponent);
    fixture.componentInstance.event = {
      title: 'Wpis',
      actor: '123e4567-e89b-12d3-a456-426614174000',
      source: 'SYSTEM_EVENT',
    };
    fixture.componentInstance.participantDisplayName = 'Anna Kowalska';
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Uczestnik');
    expect(text).not.toContain('Źródło');
    expect(text).not.toContain('123e4567-e89b-12d3-a456-426614174000');
  });

  it('does not render UUID-only timeline metadata and retains human-readable text', async () => {
    const uuid = '123e4567-e89b-12d3-a456-426614174000';
    await TestBed.configureTestingModule({
      imports: [PatientTimelineEventPanelComponent, TimelineEventComponent],
    }).compileComponents();

    const event = { title: uuid, summary: uuid, source: uuid };
    const timelineFixture = TestBed.createComponent(TimelineEventComponent);
    timelineFixture.componentInstance.event = event;
    timelineFixture.detectChanges();
    const panelFixture = TestBed.createComponent(PatientTimelineEventPanelComponent);
    panelFixture.componentInstance.event = event;
    panelFixture.detectChanges();

    for (const text of [
      (timelineFixture.nativeElement as HTMLElement).textContent ?? '',
      (panelFixture.nativeElement as HTMLElement).textContent ?? '',
    ]) {
      expect(text).not.toContain(uuid);
    }
    expect((timelineFixture.nativeElement as HTMLElement).textContent).toContain('Zdarzenie');
    expect((timelineFixture.nativeElement as HTMLElement).textContent).not.toContain(
      'Brak krótkiego podsumowania',
    );
    expect((panelFixture.nativeElement as HTMLElement).textContent).toContain('Zdarzenie');
    expect((panelFixture.nativeElement as HTMLElement).textContent).not.toContain(
      'Brak dodatkowego opisu.',
    );

    const readableEvent = {
      title: 'Rozmowa kontrolna',
      summary: 'Ustalono dalsze kroki.',
      source: 'Notatka specjalisty',
    };
    timelineFixture.componentRef.setInput('event', readableEvent);
    timelineFixture.detectChanges();
    panelFixture.componentRef.setInput('event', readableEvent);
    panelFixture.detectChanges();
    for (const text of [
      (timelineFixture.nativeElement as HTMLElement).textContent ?? '',
      (panelFixture.nativeElement as HTMLElement).textContent ?? '',
    ]) {
      expect(text).toContain('Rozmowa kontrolna');
      expect(text).toContain('Ustalono dalsze kroki.');
      expect(text).not.toContain('Notatka specjalisty');
    }
  });

  it('uses the localized appointment title and renders stale status', async () => {
    await TestBed.configureTestingModule({
      imports: [PatientTimelineEventPanelComponent, TimelineEventComponent],
    }).compileComponents();
    const event = {
      category: 'APPOINTMENT',
      title: 'TRAINING',
      status: 'SCHEDULED',
      effectiveFrom: new Date('2026-01-01T09:00:00'),
    };
    const timelineFixture = TestBed.createComponent(TimelineEventComponent);
    timelineFixture.componentInstance.event = event;
    timelineFixture.detectChanges();
    const panelFixture = TestBed.createComponent(PatientTimelineEventPanelComponent);
    panelFixture.componentInstance.event = event;
    panelFixture.detectChanges();

    expect((timelineFixture.nativeElement as HTMLElement).textContent).toContain('Trening');
    expect(
      (timelineFixture.nativeElement as HTMLElement).querySelector('.event-status.stale'),
    ).not.toBeNull();
    expect((panelFixture.nativeElement as HTMLElement).textContent).toContain('Trening');
    expect((panelFixture.nativeElement as HTMLElement).querySelector('dd.stale')).not.toBeNull();
  });
});

describe('SpecialistParticipantWorkspacePage appointment outcomes', () => {
  it('uses generated complete with the current version and opens the newest refreshed event', async () => {
    const event = appointmentEvent('event-old', 'appointment-1', '2026-07-01T10:00:00Z');
    const newest = appointmentEvent('event-new', 'appointment-1', '2026-07-02T10:00:00Z');
    const complete = vi.fn().mockResolvedValue({});
    const getSpecialistAppointment = vi
      .fn()
      .mockResolvedValue({
        appointmentId: 'appointment-1',
        version: 7,
        availableActions: ['COMPLETE'],
      });
    const { fixture, router } = await pageFixture([event], [newest], {
      complete,
      getSpecialistAppointment,
    });
    (fixture.componentInstance as any).selected.set(event);
    (fixture.componentInstance as any).currentAppointment.set({
      appointmentId: 'appointment-1',
      version: 7,
      availableActions: ['COMPLETE'],
    });
    await (fixture.componentInstance as any).recordOutcome('COMPLETE');
    expect(complete).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 'appointment-1',
        appointmentVersionCommand: { version: 7 },
        idempotencyKey: expect.any(String),
      }),
    );
    expect(router.navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { eventId: 'event-new' } }),
    );
    expect((fixture.componentInstance as any).selected()).toBe(newest);
  });

  it('refreshes the current appointment without retrying after a conflict', async () => {
    const event = appointmentEvent('event-old', 'appointment-1', '2026-07-01T10:00:00Z');
    const complete = vi
      .fn()
      .mockRejectedValue(new ResponseError(new Response(null, { status: 409 })));
    const getSpecialistAppointment = vi
      .fn()
      .mockResolvedValue({ appointmentId: 'appointment-1', version: 8, availableActions: [] });
    const { fixture } = await pageFixture([event], [event], { complete, getSpecialistAppointment });
    (fixture.componentInstance as any).selected.set(event);
    (fixture.componentInstance as any).currentAppointment.set({
      appointmentId: 'appointment-1',
      version: 7,
      availableActions: ['COMPLETE'],
    });
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

    await (fixture.componentInstance as any).resolveSelection('participant-1', 'event-in-list', [
      event,
    ]);

    expect((fixture.componentInstance as any).selected()).toBe(event);
    expect(api.participantWorkspace.timelineEvent).not.toHaveBeenCalled();
  });

  it('opens an out-of-range event from context without adding it to the timeline', async () => {
    const listed = appointmentEvent('event-listed', 'appointment-1', '2026-07-01T10:00:00Z');
    const outside = appointmentEvent('event-outside', 'appointment-2', '2025-01-01T10:00:00Z');
    const { fixture } = await pageFixture([listed], [listed], appointmentApi(), {
      timelineEvent: vi.fn().mockResolvedValue(outside),
    });
    await (fixture.componentInstance as any).load('participant-1', null);

    await (fixture.componentInstance as any).resolveSelection('participant-1', 'event-outside', [
      listed,
    ]);
    fixture.detectChanges();

    expect((fixture.componentInstance as any).selected()).toBe(outside);
    expect((fixture.componentInstance as any).events()).toEqual([listed]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Zdarzenie znajduje się poza aktualnie wybranym zakresem historii.',
    );
  });

  it('clears only an unavailable eventId from the URL with replaceUrl', async () => {
    const { fixture, router } = await pageFixture([], [], appointmentApi(), {
      timelineEvent: vi
        .fn()
        .mockRejectedValue(new ResponseError(new Response(null, { status: 404 }))),
    });

    await (fixture.componentInstance as any).resolveSelection('participant-1', 'missing', []);

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { eventId: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      }),
    );
    expect((fixture.componentInstance as any).announcement()).toBe(
      'Wybrane zdarzenie jest niedostępne.',
    );
  });

  it('does not reload the workspace when only eventId changes', async () => {
    const event = appointmentEvent('event-in-list', 'appointment-1', '2026-07-01T10:00:00Z');
    const { fixture, api, params } = await pageFixture([event], [event], appointmentApi());
    await (fixture.componentInstance as any).load('participant-1', null);
    const timelineCalls = api.participantWorkspace.timeline.mock.calls.length;

    params.next(convertToParamMap({ eventId: 'event-in-list' }));
    await fixture.whenStable();

    expect(api.participantWorkspace.timeline).toHaveBeenCalledTimes(timelineCalls);
    expect((fixture.componentInstance as any).selected()).toBe(event);
  });

  it('uses the list instance when a previously contextual event enters the current timeline', async () => {
    const contextual = appointmentEvent('event-shared', 'appointment-1', '2025-01-01T10:00:00Z');
    const listed = { ...contextual, effectiveFrom: new Date('2026-07-01T10:00:00Z') };
    const { fixture } = await pageFixture([], [], appointmentApi(), {
      timelineEvent: vi.fn().mockResolvedValue(contextual),
    });
    await (fixture.componentInstance as any).load('participant-1', null);
    await (fixture.componentInstance as any).resolveSelection('participant-1', 'event-shared', []);
    (fixture.componentInstance as any).events.set([listed]);

    await (fixture.componentInstance as any).resolveSelection('participant-1', 'event-shared', [
      listed,
    ]);

    expect((fixture.componentInstance as any).selected()).toBe(listed);
    expect((fixture.componentInstance as any).events()).toEqual([listed]);
  });

  it('closes a direct-link panel without requiring an opener', async () => {
    const { fixture, router } = await pageFixture([], [], appointmentApi());
    (fixture.componentInstance as any).opener = null;

    (fixture.componentInstance as any).close();
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { eventId: null }, queryParamsHandling: 'merge' }),
    );
  });

  it('clears the direct-link URL and returns focus to the event opener on close', async () => {
    const { fixture, router } = await pageFixture([], [], appointmentApi());
    const opener = document.createElement('button');
    document.body.append(opener);
    (fixture.componentInstance as any).opener = opener;

    (fixture.componentInstance as any).close();
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { eventId: null }, queryParamsHandling: 'merge' }),
    );
    expect(document.activeElement).toBe(opener);
    opener.remove();
  });

  it('preserves the historical goal event while loading its current goal snapshot by referenceId', async () => {
    const event = goalEvent('goal-event-1', 'goal-1');
    const getParticipantGoal = vi
      .fn()
      .mockResolvedValue({
        id: 'goal-1',
        title: 'Aktualny cel',
        status: 'ACTIVE',
        category: 'PERFORMANCE',
      });
    const { fixture, api } = await pageFixture(
      [event],
      [event],
      appointmentApi(),
      {},
      getParticipantGoal,
    );
    await (fixture.componentInstance as any).load('participant-1', null);
    (fixture.componentInstance as any).actingContext.set('TRAINER');

    await (fixture.componentInstance as any).resolveSelection('participant-1', 'goal-event-1', [
      event,
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.participantGoals.getParticipantGoal).toHaveBeenCalledWith({
      participantId: 'participant-1',
      goalId: 'goal-1',
      actingContext: 'TRAINER',
    });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aktualne dane celu');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aktualny cel');
  });

  it('keeps a historical goal event open when its current goal is no longer available', async () => {
    const event = goalEvent('goal-event-1', 'goal-1');
    const unavailable = vi
      .fn()
      .mockRejectedValue(new ResponseError(new Response(null, { status: 404 })));
    const { fixture } = await pageFixture([event], [event], appointmentApi(), {}, unavailable);
    await (fixture.componentInstance as any).load('participant-1', null);
    (fixture.componentInstance as any).actingContext.set('TRAINER');

    await (fixture.componentInstance as any).resolveSelection('participant-1', 'goal-event-1', [
      event,
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.componentInstance as any).selected()).toBe(event);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Aktualne dane celu nie są dostępne.',
    );
  });
});

function appointmentEvent(eventId: string, detailResourceId: string, effectiveFrom: string) {
  return {
    eventId,
    category: 'APPOINTMENT',
    detail: { detailResourceId },
    effectiveFrom: new Date(effectiveFrom),
  };
}
function goalEvent(eventId: string, referenceId: string) {
  return {
    eventId,
    category: 'GOAL',
    title: 'Wcześniejsza nazwa celu',
    detail: { detailResourceId: eventId, referenceId },
  };
}

function appointmentApi() {
  return {
    complete: vi.fn().mockResolvedValue({}),
    getSpecialistAppointment: vi.fn().mockResolvedValue({}),
    noShow: vi.fn(),
    create2: vi.fn(),
  };
}

async function participantGoalsFixture(
  participantGoals: Record<string, ReturnType<typeof vi.fn>> = {},
) {
  const api = {
    participantGoals: {
      listParticipantGoals: vi.fn().mockResolvedValue([]),
      getParticipantGoal: vi.fn(),
      createParticipantGoal: vi.fn(),
      createParticipantGoalFromPreset: vi.fn(),
      listParticipantGoalMetricPresets: vi.fn().mockResolvedValue([]),
      updateParticipantGoal: vi.fn(),
      listParticipantGoalObservations: vi.fn().mockResolvedValue({ items: [] }),
      recordParticipantGoalObservation: vi.fn(),
      achieveParticipantGoal: vi.fn(),
      cancelParticipantGoal: vi.fn(),
      ...participantGoals,
    },
  };
  await TestBed.configureTestingModule({
    imports: [ParticipantGoalsComponent],
    providers: [{ provide: ApiFacade, useValue: api }],
  }).compileComponents();
  const fixture = TestBed.createComponent(ParticipantGoalsComponent);
  fixture.componentInstance.participantId = 'participant-1';
  fixture.componentInstance.role = 'TRAINER';
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

async function pageFixture(
  initialItems: any[],
  refreshedItems: any[],
  appointments: {
    complete: ReturnType<typeof vi.fn>;
    getSpecialistAppointment: ReturnType<typeof vi.fn>;
  },
  workspaceOverrides: { timelineEvent?: ReturnType<typeof vi.fn> } = {},
  getParticipantGoal = vi.fn(),
) {
  const params = new BehaviorSubject(convertToParamMap({}));
  const router = { navigate: vi.fn().mockResolvedValue(true) };
  let timelineCalls = 0;
  const api = {
    participantWorkspace: {
      workspace: vi.fn().mockResolvedValue({}),
      timeline: vi
        .fn()
        .mockImplementation(() =>
          Promise.resolve({ items: timelineCalls++ ? refreshedItems : initialItems }),
        ),
      timelineEvent: workspaceOverrides.timelineEvent ?? vi.fn(),
    },
    specialistClients: { list1: vi.fn().mockResolvedValue([]) },
    onboarding: { state: vi.fn().mockResolvedValue({ profile: { specialistKind: 'TRAINER' } }) },
    appointments: { ...appointments, noShow: vi.fn(), create2: vi.fn() },
    participantGoals: { getParticipantGoal, listParticipantGoals: vi.fn().mockResolvedValue([]) },
  };
  await TestBed.configureTestingModule({
    imports: [SpecialistParticipantWorkspacePage],
    providers: [
      { provide: ApiFacade, useValue: api },
      {
        provide: ActivatedRoute,
        useValue: {
          queryParamMap: params,
          snapshot: {
            paramMap: convertToParamMap({ participantId: 'participant-1' }),
            queryParamMap: params.value,
          },
        },
      },
      { provide: Router, useValue: router },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(SpecialistParticipantWorkspacePage);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, router, api, params };
}
