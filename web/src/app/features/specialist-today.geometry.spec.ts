import { describe, expect, it } from 'vitest';
import { clampToVisibleRange, heightPercent, layoutOverlappingAppointments, minutesSinceRangeStart, positionPercent } from './specialist-today.geometry';
import type { TodayAppointment } from './specialist-today.types';

const range = { start: new Date('2026-07-23T08:00:00Z'), end: new Date('2026-07-23T18:00:00Z') };
const appointment = (id: string, startsAt: string, endsAt: string): TodayAppointment => ({ appointmentId: id, participantLabel: id, startsAt, endsAt, type: 'TRAINING', status: 'SCHEDULED', availableActions: [] });

describe('specialist today geometry', () => {
  it('calculates and clamps timeline geometry', () => {
    expect(minutesSinceRangeStart('2026-07-23T09:00:00Z', range)).toBe(60);
    expect(positionPercent('2026-07-23T13:00:00Z', range)).toBe(50);
    expect(heightPercent('2026-07-23T09:00:00Z', '2026-07-23T10:00:00Z', range)).toBe(10);
    expect(clampToVisibleRange('2026-07-23T07:00:00Z', '2026-07-23T09:00:00Z', range)).toEqual({ start: range.start, end: new Date('2026-07-23T09:00:00Z') });
  });

  it('lays overlapping appointments into deterministic neighbouring columns', () => {
    const items = [appointment('b', '2026-07-23T09:00:00Z', '2026-07-23T11:00:00Z'), appointment('a', '2026-07-23T09:30:00Z', '2026-07-23T10:30:00Z'), appointment('c', '2026-07-23T11:00:00Z', '2026-07-23T12:00:00Z')];
    const layout = layoutOverlappingAppointments(items, range);
    expect(layout.map(item => [item.appointment.appointmentId, item.column, item.columns])).toEqual([['b', 0, 2], ['a', 1, 2], ['c', 0, 1]]);
  });
});
