import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { ParticipantProgressPage } from './participant-progress.page';

const flush = async () => { await Promise.resolve(); await Promise.resolve(); await Promise.resolve(); };
const deferred = <T>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((next, fail) => { resolve = next; reject = fail; });
  return { promise, resolve, reject };
};

async function page(params: BehaviorSubject<ReturnType<typeof convertToParamMap>>, ownGoals: object, adherence: object) {
  await TestBed.configureTestingModule({
    imports: [ParticipantProgressPage],
    providers: [
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { queryParamMap: params, snapshot: { queryParamMap: params.value } } },
      { provide: ApiFacade, useValue: { ownGoals, adherence } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(ParticipantProgressPage);
  fixture.detectChanges();
  return fixture;
}

describe('ParticipantProgressPage', () => {
  it('loads an own goal from refresh-safe goalId and pages safe observations', async () => {
    const params = new BehaviorSubject(convertToParamMap({ goalId: 'g' }));
    const ownGoals = {
      listOwnParticipantGoals: vi.fn().mockResolvedValue([{ id: 'g', title: 'Cel' }]),
      getOwnParticipantGoal: vi.fn().mockResolvedValue({ id: 'g', title: 'Cel', outcomes: [{ id: 'o', unit: 'km' }] }),
      listOwnParticipantGoalObservations: vi.fn().mockResolvedValue({ items: [{ id: 'x', outcomeId: 'o', value: 3, measuredAt: new Date() }], nextCursor: 'older' }),
    };
    const fixture = await page(params, ownGoals, { summary: vi.fn().mockResolvedValue({ plannedSessions: 0 }) });
    await flush(); fixture.detectChanges();
    expect(ownGoals.getOwnParticipantGoal).toHaveBeenCalledWith({ goalId: 'g' });
    expect(fixture.nativeElement.textContent).toContain('Cel');
  });

  it('renders the goal list when no goalId is selected', async () => {
    const params = new BehaviorSubject(convertToParamMap({}));
    const fixture = await page(params, { listOwnParticipantGoals: vi.fn().mockResolvedValue([]) }, { summary: vi.fn().mockResolvedValue({ plannedSessions: 0 }) });
    await flush(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Cele');
  });

  it('does not append a deferred initial history page after switching goals', async () => {
    const params = new BehaviorSubject(convertToParamMap({ goalId: 'a' }));
    const firstHistory = deferred<any>();
    const ownGoals = {
      listOwnParticipantGoals: vi.fn().mockResolvedValue([{ id: 'a', title: 'A' }, { id: 'b', title: 'B' }]),
      getOwnParticipantGoal: vi.fn(({ goalId }) => Promise.resolve({ id: goalId, title: goalId, outcomes: [{ id: 'outcome', unit: 'km' }] })),
      listOwnParticipantGoalObservations: vi.fn(({ goalId }) => goalId === 'a'
        ? firstHistory.promise
        : Promise.resolve({ items: [{ id: 'b-observation', outcomeId: 'outcome', value: 2, measuredAt: new Date() }] })),
    };
    const fixture = await page(params, ownGoals, { summary: vi.fn().mockResolvedValue({ plannedSessions: 0 }) });
    await flush(); params.next(convertToParamMap({ goalId: 'b' })); await flush();
    firstHistory.resolve({ items: [{ id: 'a-observation', outcomeId: 'outcome', value: 1, measuredAt: new Date() }] });
    await flush();
    expect(fixture.componentInstance.goal()?.id).toBe('b');
    expect(fixture.componentInstance.history().map(item => item.id)).toEqual(['b-observation']);
  });

  it('does not append a deferred older page after switching goals', async () => {
    const params = new BehaviorSubject(convertToParamMap({ goalId: 'a' }));
    const olderHistory = deferred<any>();
    const ownGoals = {
      listOwnParticipantGoals: vi.fn().mockResolvedValue([{ id: 'a', title: 'A' }, { id: 'b', title: 'B' }]),
      getOwnParticipantGoal: vi.fn(({ goalId }) => Promise.resolve({ id: goalId, title: goalId, outcomes: [{ id: 'outcome', unit: 'km' }] })),
      listOwnParticipantGoalObservations: vi.fn(({ goalId, cursor }) => {
        if (goalId === 'a' && cursor) return olderHistory.promise;
        if (goalId === 'a') return Promise.resolve({ items: [{ id: 'a-new', outcomeId: 'outcome', value: 3, measuredAt: new Date() }], nextCursor: 'older-a' });
        return Promise.resolve({ items: [{ id: 'b-new', outcomeId: 'outcome', value: 4, measuredAt: new Date() }] });
      }),
    };
    const fixture = await page(params, ownGoals, { summary: vi.fn().mockResolvedValue({ plannedSessions: 0 }) });
    await flush(); void fixture.componentInstance.loadOlder(); await flush();
    params.next(convertToParamMap({ goalId: 'b' })); await flush();
    olderHistory.resolve({ items: [{ id: 'a-old', outcomeId: 'outcome', value: 1, measuredAt: new Date() }] });
    await flush();
    expect(fixture.componentInstance.goal()?.id).toBe('b');
    expect(fixture.componentInstance.history().map(item => item.id)).toEqual(['b-new']);
    expect(fixture.componentInstance.loadingOlder()).toBe(false);
  });

  it('requests inclusive seven and thirty day periods from the server-provided local end date', async () => {
    const params = new BehaviorSubject(convertToParamMap({}));
    const summary = vi.fn().mockResolvedValue({ to: new Date('2026-09-15T00:00:00.000Z'), plannedSessions: 0 });
    const fixture = await page(params, { listOwnParticipantGoals: vi.fn().mockResolvedValue([]) }, { summary });
    await flush(); await fixture.componentInstance.loadAdherence(7); await fixture.componentInstance.loadAdherence(30);
    expect(summary.mock.calls[1][0]).toMatchObject({ from: new Date('2026-09-09T00:00:00.000Z'), to: new Date('2026-09-15T00:00:00.000Z') });
    expect(summary.mock.calls[2][0]).toMatchObject({ from: new Date('2026-08-17T00:00:00.000Z'), to: new Date('2026-09-15T00:00:00.000Z') });
  });

  it('shows a retryable error and reloads progress', async () => {
    const params = new BehaviorSubject(convertToParamMap({}));
    const ownGoals = { listOwnParticipantGoals: vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue([]) };
    const fixture = await page(params, ownGoals, { summary: vi.fn().mockResolvedValue({ plannedSessions: 0 }) });
    await flush();
    expect(fixture.componentInstance.error()).toContain('Nie udało się wczytać postępu');
    await fixture.componentInstance.retry();
    expect(ownGoals.listOwnParticipantGoals).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.error()).toBe('');
  });
});
