import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { SpecialistTodayApi } from './specialist-today.api';
import { DayTimelineComponent, SpecialistTodayPage, TodayAppointmentDialogComponent, TodayOperationalTasksComponent } from './specialist-today.page';
import type { TodayView } from '../api/generated/src/models/TodayView';

const emptyView: TodayView = { generatedAt: new Date('2026-07-24T00:00:00Z'), localDate: new Date('2026-07-24T00:00:00Z'), timeZoneId: 'Europe/Warsaw', visibleRange: { startsAt: new Date('2026-07-24T08:00:00Z'), endsAt: new Date('2026-07-24T18:00:00Z'), recommendedStepMinutes: 30 }, appointments: [], availabilityWindows: [{ startsAt: new Date('2026-07-24T09:00:00Z'), endsAt: new Date('2026-07-24T10:00:00Z'), type: 'STANDARD_AVAILABILITY' }], attentionItems: [], operationalTasks: [] };

describe('SpecialistTodayPage', () => {
  it('renders operational tasks only when present and navigates through their references', async () => {
    await TestBed.configureTestingModule({ imports: [TodayOperationalTasksComponent, RouterTestingModule] }).compileComponents();
    const fixture = TestBed.createComponent(TodayOperationalTasksComponent);
    fixture.componentInstance.tasks = [];
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Do uzupełnienia');
    fixture.componentRef.setInput('tasks', [{ title: 'Uzupełnij wynik spotkania', navigationReference: '/specialist/participants/p/events/e' }]);
    fixture.detectChanges();
    const link = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>('a')!;
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Do uzupełnienia');
    expect(link.textContent).toContain('Uzupełnij wynik spotkania');
    expect(link.getAttribute('href')).toContain('/specialist/participants/p/events/e');
  });
  it('submits an arbitrary, non-grid appointment time for the canonical participant ID', async () => {
    await TestBed.configureTestingModule({ imports: [TodayAppointmentDialogComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TodayAppointmentDialogComponent);
    const instance = fixture.componentInstance;
    instance.participants = [{ participantId: 'canonical-participant-id', label: 'Uczestnik 1' }];
    instance.date = '2026-07-24';
    const submitted = vi.fn();
    instance.submitted.subscribe(submitted);
    instance.timeZone = 'Europe/Warsaw';
    fixture.detectChanges();

    const participant = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('select[formControlName="participantId"]')!;
    expect([...participant.options].map(option => option.value)).toContain('canonical-participant-id');
    participant.value = 'canonical-participant-id';
    participant.dispatchEvent(new Event('change'));
    setTime(fixture, 'startTime', '09:10');
    numberInput(fixture).value = '45';
    numberInput(fixture).dispatchEvent(new Event('input'));
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(submitted).toHaveBeenCalledWith(expect.objectContaining({ participantId: 'canonical-participant-id', startsAt: new Date('2026-07-24T07:10:00.000Z'), endsAt: new Date('2026-07-24T07:55:00.000Z') }));
  });

  it('derives the end instant from the selected start and duration', async () => {
    await TestBed.configureTestingModule({ imports: [TodayAppointmentDialogComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TodayAppointmentDialogComponent);
    const instance = fixture.componentInstance;
    instance.date = '2026-07-24';
    fixture.detectChanges();
    const submitted = vi.fn(); instance.submitted.subscribe(submitted);
    setTime(fixture, 'startTime', '09:10');
    numberInput(fixture).value = '45'; numberInput(fixture).dispatchEvent(new Event('input'));
    const participant = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('select[formControlName="participantId"]')!;
    instance.participants = [{ participantId: 'participant', label: 'P' }]; fixture.detectChanges(); participant.value = 'participant'; participant.dispatchEvent(new Event('change'));
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
    expect(submitted).toHaveBeenCalledWith(expect.objectContaining({ startsAt: new Date('2026-07-24T07:10:00.000Z'), endsAt: new Date('2026-07-24T07:55:00.000Z') }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('godziny w formacie 24-godzinnym.');
  });

  it('rejects zero and fractional meeting durations', async () => {
    await TestBed.configureTestingModule({ imports: [TodayAppointmentDialogComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TodayAppointmentDialogComponent); const instance = fixture.componentInstance;
    instance.date = '2026-07-24'; instance.participants = [{ participantId: 'participant', label: 'P' }];
    const submitted = vi.fn(); instance.submitted.subscribe(submitted); fixture.detectChanges();
    const participant = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('select[formControlName="participantId"]')!; participant.value = 'participant'; participant.dispatchEvent(new Event('change'));
    for (const duration of ['0', '45.5']) {
      numberInput(fixture).value = duration; numberInput(fixture).dispatchEvent(new Event('input'));
      (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit')); fixture.detectChanges();
      expect(submitted).not.toHaveBeenCalled();
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('Podaj dodatnią liczbę całkowitą minut.');
    }
  });

  it('uses elapsed duration across the spring DST transition', async () => {
    await TestBed.configureTestingModule({ imports: [TodayAppointmentDialogComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TodayAppointmentDialogComponent); const instance = fixture.componentInstance;
    instance.date = '2026-03-29'; instance.timeZone = 'Europe/Warsaw'; instance.participants = [{ participantId: 'participant', label: 'P' }];
    instance.selectedStart = new Date('2026-03-29T01:30:00Z'); instance.selectedEnd = new Date('2026-03-29T02:30:00Z');
    instance.ngOnChanges({ selectedStart: {} } as any);
    const submitted = vi.fn(); instance.submitted.subscribe(submitted); fixture.detectChanges();
    const participant = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('select[formControlName="participantId"]')!; participant.value = 'participant'; participant.dispatchEvent(new Event('change'));
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
    expect(submitted).toHaveBeenCalledWith(expect.objectContaining({ startsAt: new Date('2026-03-29T01:30:00Z'), endsAt: new Date('2026-03-29T02:30:00Z') }));
  });

  it('renders availability as one non-interactive background range, without generated free-slot cards', async () => {
    const queryParamMap = new BehaviorSubject(convertToParamMap({ date: '2026-07-24' }));
    const today = { get: vi.fn().mockResolvedValue(emptyView), availableSlots: vi.fn().mockResolvedValue({ slots: [{ startAt: new Date('2026-07-24T09:00:00.000Z'), endAt: new Date('2026-07-24T09:30:00.000Z') }] }) };
    await TestBed.configureTestingModule({ imports: [SpecialistTodayPage], providers: [{ provide: SpecialistTodayApi, useValue: today }, { provide: ApiFacade, useValue: { specialistParticipants: { activeParticipants: vi.fn().mockResolvedValue([]) }, appointments: { create2: vi.fn() } } }, { provide: ActivatedRoute, useValue: { queryParamMap, snapshot: { queryParamMap: queryParamMap.value } } }, { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } }] }).compileComponents();
    const fixture = TestBed.createComponent(SpecialistTodayPage); fixture.detectChanges(); await fixture.whenStable(); await new Promise(resolve => setTimeout(resolve)); fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(today.get).toHaveBeenCalledWith('2026-07-24');
    expect(text).toContain('Nie masz jeszcze spotkań');
    expect(text).not.toContain('Nie udało się pobrać planu dnia');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.availability').length).toBe(1);
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.free-slot').length).toBe(0);
    expect((fixture.nativeElement as HTMLElement).querySelector('.availability')?.getAttribute('aria-hidden')).toBe('true');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Edytuj dostępność');
  });

  it('keeps appointment buttons above availability and exposes their participant labels', async () => {
    await TestBed.configureTestingModule({ imports: [DayTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(DayTimelineComponent);
    fixture.componentRef.setInput('view', { ...emptyView, appointments: [{ appointmentId: 'a', participantId: 'participant-a', startsAt: new Date('2026-07-24T09:30:00Z'), endsAt: new Date('2026-07-24T10:00:00Z'), type: 'TRAINING', status: 'SCHEDULED', availableActions: [] }] });
    fixture.componentRef.setInput('selectedDate', '2026-07-24');
    fixture.componentRef.setInput('todayDate', '2026-07-24');
    fixture.componentRef.setInput('participantLabels', new Map([['participant-a', 'Anna Nowak']]));
    fixture.detectChanges();
    const appointment = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.appointment')!;
    expect(appointment.getAttribute('aria-label')).toContain('Anna Nowak');
    expect((fixture.nativeElement as HTMLElement).querySelector('.availability')).toBeTruthy();
    expect((fixture.nativeElement as HTMLElement).querySelector('.free-slot')).toBeNull();
  });

  it('updates the now line every minute only for the current specialist calendar day', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-24T09:00:00.000Z'));
    await TestBed.configureTestingModule({ imports: [DayTimelineComponent] }).compileComponents();
    const fixture = TestBed.createComponent(DayTimelineComponent);
    fixture.componentRef.setInput('view', emptyView);
    fixture.componentRef.setInput('selectedDate', '2026-07-24');
    fixture.componentRef.setInput('todayDate', '2026-07-24');
    fixture.detectChanges();
    const initialPosition = (fixture.componentInstance as any).nowLinePosition;
    await vi.advanceTimersByTimeAsync(60_000);
    fixture.detectChanges();
    expect((fixture.componentInstance as any).showNowLine).toBe(true);
    expect((fixture.componentInstance as any).nowLinePosition).toBeGreaterThan(initialPosition);
    fixture.componentRef.setInput('selectedDate', '2026-07-23');
    fixture.detectChanges();
    expect((fixture.componentInstance as any).showNowLine).toBe(false);
    fixture.destroy();
    vi.useRealTimers();
  });

  it('opens a direct-time scheduling dialog from the timeline header without rendering timeline slots', async () => {
    const queryParamMap = new BehaviorSubject(convertToParamMap({ date: '2026-07-24' }));
    const today = { get: vi.fn().mockResolvedValue(emptyView), availableSlots: vi.fn().mockResolvedValue({ slots: [] }) };
    await TestBed.configureTestingModule({ imports: [SpecialistTodayPage], providers: [{ provide: SpecialistTodayApi, useValue: today }, { provide: ApiFacade, useValue: { specialistParticipants: { activeParticipants: vi.fn().mockResolvedValue([]) }, appointments: { create2: vi.fn() } } }, { provide: ActivatedRoute, useValue: { queryParamMap, snapshot: { queryParamMap: queryParamMap.value } } }, { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } }] }).compileComponents();
    const fixture = TestBed.createComponent(SpecialistTodayPage); fixture.detectChanges(); await fixture.whenStable(); await new Promise(resolve => setTimeout(resolve)); fixture.detectChanges();
    const button = [...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button')].find(item => item.textContent?.includes('Zaplanuj spotkanie'))!;
    button.click(); await fixture.whenStable(); fixture.detectChanges();
    expect(today.availableSlots).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).querySelector('.appointment-dialog')).toBeTruthy();
    expect((fixture.nativeElement as HTMLElement).querySelector('.free-slot')).toBeNull();
  });

  it('opens the scheduling dialog with a clicked availability slot prefilled', async () => {
    const queryParamMap = new BehaviorSubject(convertToParamMap({ date: '2026-07-24' }));
    const view = { ...emptyView, bookableSlots: [{ startsAt: new Date('2026-07-24T07:10:00Z'), endsAt: new Date('2026-07-24T08:00:00Z') }] };
    const today = { get: vi.fn().mockResolvedValue(view), availableSlots: vi.fn() };
    await TestBed.configureTestingModule({ imports: [SpecialistTodayPage], providers: [{ provide: SpecialistTodayApi, useValue: today }, { provide: ApiFacade, useValue: { specialistParticipants: { activeParticipants: vi.fn().mockResolvedValue([]) }, appointments: { create2: vi.fn() } } }, { provide: ActivatedRoute, useValue: { queryParamMap, snapshot: { queryParamMap: queryParamMap.value } } }, { provide: Router, useValue: { navigate: vi.fn() } }] }).compileComponents();
    const fixture = TestBed.createComponent(SpecialistTodayPage); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.free-slot')!.click(); await fixture.whenStable(); fixture.detectChanges();
    expect(timeInput(fixture, 'startTime').value).toBe('09:10');
    expect(numberInput(fixture).value).toBe('50');
  });

  it('clears the selected appointment when the route date changes and passes the selected date to weekly availability editing', async () => {
    const queryParamMap = new BehaviorSubject(convertToParamMap({ date: '2026-07-24' }));
    const router = { navigate: vi.fn().mockResolvedValue(true) };
    const today = { get: vi.fn().mockResolvedValue(emptyView), availableSlots: vi.fn().mockResolvedValue({ slots: [] }) };
    await TestBed.configureTestingModule({ imports: [SpecialistTodayPage], providers: [{ provide: SpecialistTodayApi, useValue: today }, { provide: ApiFacade, useValue: { specialistParticipants: { activeParticipants: vi.fn().mockResolvedValue([]) }, appointments: { create2: vi.fn() } } }, { provide: ActivatedRoute, useValue: { queryParamMap, snapshot: { queryParamMap: queryParamMap.value } } }, { provide: Router, useValue: router }] }).compileComponents();
    const fixture = TestBed.createComponent(SpecialistTodayPage);
    fixture.detectChanges(); await fixture.whenStable();
    (fixture.componentInstance as any).openDetails({ appointmentId: 'a', participantId: 'p', startsAt: new Date('2026-07-24T09:00:00Z'), endsAt: new Date('2026-07-24T09:30:00Z'), type: 'TRAINING', status: 'SCHEDULED', availableActions: [] });
    queryParamMap.next(convertToParamMap({ date: '2026-07-25' }));
    expect((fixture.componentInstance as any).selected()).toBeNull();
    (fixture.componentInstance as any).editAvailability();
    expect(router.navigate).toHaveBeenCalledWith(['/onboarding'], { queryParams: { availabilityEdit: 'true', date: '2026-07-25' } });
  });
});

function timeInput(fixture: ReturnType<typeof TestBed.createComponent>, control: 'startTime' | 'endTime'): HTMLInputElement {
  return (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(`input[formControlName="${control}"]`)!;
}

function numberInput(fixture: ReturnType<typeof TestBed.createComponent>): HTMLInputElement {
  return (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type="number"][formControlName="durationMinutes"]')!;
}

function setTime(fixture: ReturnType<typeof TestBed.createComponent>, control: 'startTime' | 'endTime', value: string): void {
  const input = timeInput(fixture, control);
  input.value = value;
  input.dispatchEvent(new Event('input'));
  fixture.detectChanges();
}
