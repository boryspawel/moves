import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { SpecialistTodayApi } from './specialist-today.api';
import { SpecialistTodayPage, TodayAppointmentDialogComponent } from './specialist-today.page';
import type { TodayView } from '../api/generated/src/models/TodayView';

const emptyView: TodayView = { generatedAt: new Date('2026-07-24T00:00:00Z'), localDate: new Date('2026-07-24T00:00:00Z'), timeZoneId: 'Europe/Warsaw', visibleRange: { startsAt: new Date('2026-07-24T08:00:00Z'), endsAt: new Date('2026-07-24T18:00:00Z'), recommendedStepMinutes: 30 }, appointments: [], availabilityWindows: [{ startsAt: new Date('2026-07-24T09:00:00Z'), endsAt: new Date('2026-07-24T10:00:00Z'), type: 'STANDARD_AVAILABILITY' }], attentionItems: [], operationalTasks: [] };

describe('SpecialistTodayPage', () => {
  it('selects an active participant without an account ID and submits the canonical participant ID', async () => {
    await TestBed.configureTestingModule({ imports: [TodayAppointmentDialogComponent] }).compileComponents();
    const fixture = TestBed.createComponent(TodayAppointmentDialogComponent);
    const instance = fixture.componentInstance;
    instance.participants = [{ participantId: 'canonical-participant-id', label: 'Uczestnik 1' }];
    instance.slot = { startsAt: '2026-07-24T09:00:00.000Z', endsAt: '2026-07-24T09:30:00.000Z', isPast: false };
    const submitted = vi.fn();
    instance.submitted.subscribe(submitted);
    instance.ngOnChanges();
    fixture.detectChanges();

    const participant = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('select[formControlName="participantId"]')!;
    expect([...participant.options].map(option => option.value)).toContain('canonical-participant-id');
    participant.value = 'canonical-participant-id';
    participant.dispatchEvent(new Event('change'));
    (instance as any).submit();

    expect(submitted).toHaveBeenCalledWith(expect.objectContaining({ participantId: 'canonical-participant-id' }));
  });

  it('renders a valid empty 200 response as a timeline with free slots, not an error', async () => {
    const queryParamMap = new BehaviorSubject(convertToParamMap({ date: '2026-07-24' }));
    const today = { get: vi.fn().mockResolvedValue(emptyView) };
    await TestBed.configureTestingModule({ imports: [SpecialistTodayPage], providers: [{ provide: SpecialistTodayApi, useValue: today }, { provide: ApiFacade, useValue: { specialistParticipants: { activeParticipants: vi.fn() }, appointments: { create: vi.fn() } } }, { provide: ActivatedRoute, useValue: { queryParamMap, snapshot: { queryParamMap: queryParamMap.value } } }, { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } }] }).compileComponents();
    const fixture = TestBed.createComponent(SpecialistTodayPage); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(today.get).toHaveBeenCalledWith('2026-07-24');
    expect(text).toContain('Nie masz jeszcze spotkań');
    expect(text).not.toContain('Nie udało się pobrać planu dnia');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.free-slot').length).toBeGreaterThan(0);
  });
});
