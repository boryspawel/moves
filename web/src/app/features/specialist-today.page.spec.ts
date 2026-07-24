import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { SpecialistTodayApi } from './specialist-today.api';
import { SpecialistTodayPage } from './specialist-today.page';

const emptyView = { generatedAt: '2026-07-24T00:00:00Z', localDate: '2026-07-24', timeZoneId: 'Europe/Warsaw', visibleRange: { startsAt: '2026-07-24T08:00:00Z', endsAt: '2026-07-24T18:00:00Z', recommendedStepMinutes: 30 }, appointments: [], availabilityWindows: [{ startsAt: '2026-07-24T09:00:00Z', endsAt: '2026-07-24T10:00:00Z', type: 'STANDARD_AVAILABILITY' }], attentionItems: [], operationalTasks: [] };

describe('SpecialistTodayPage', () => {
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
