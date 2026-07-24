import { describe, expect, it } from 'vitest';
import { buildFreeSlots } from './specialist-today.slots';
import type { AvailabilityWindow, TodayAppointment } from './specialist-today.types';

const range = { startsAt: '2026-07-24T08:00:00.000Z', endsAt: '2026-07-24T11:00:00.000Z' };
const available: AvailabilityWindow[] = [{ startsAt: range.startsAt, endsAt: range.endsAt, type: 'STANDARD_AVAILABILITY' }];
const appointment = (status: string, startsAt: string, endsAt: string): TodayAppointment => ({ appointmentId: status, participantLabel: status, startsAt, endsAt, type: 'TRAINING', status, availableActions: [] });

describe('buildFreeSlots', () => {
  it('uses only standard availability, aligns slots and subtracts occupied appointments', () => {
    const slots = buildFreeSlots(available, [appointment('SCHEDULED', '2026-07-24T08:30:00.000Z', '2026-07-24T09:30:00.000Z')], range, new Date('2026-07-23T00:00:00Z'), 30);
    expect(slots.map(slot => slot.startsAt)).toEqual(['2026-07-24T08:00:00.000Z', '2026-07-24T09:30:00.000Z', '2026-07-24T10:00:00.000Z', '2026-07-24T10:30:00.000Z']);
  });

  it('does not let cancelled appointments block a slot and rounds interactive slots up to the next grid boundary', () => {
    const slots = buildFreeSlots(available, [appointment('CANCELLED', '2026-07-24T08:00:00.000Z', '2026-07-24T09:00:00.000Z')], range, new Date('2026-07-24T08:45:00Z'), 30);
    expect(slots).toHaveLength(6);
    expect(slots[0].isPast).toBe(true);
    expect(slots[1].isPast).toBe(true);
    expect(slots.filter(slot => !slot.isPast).map(slot => slot.startsAt)).toEqual(['2026-07-24T09:00:00.000Z', '2026-07-24T09:30:00.000Z', '2026-07-24T10:00:00.000Z', '2026-07-24T10:30:00.000Z']);
  });

  it('ignores unavailable windows and returns stable ordering across multiple windows', () => {
    const slots = buildFreeSlots([{ startsAt: '2026-07-24T10:00:00Z', endsAt: '2026-07-24T11:00:00Z', type: 'STANDARD_AVAILABILITY' }, { startsAt: '2026-07-24T08:00:00Z', endsAt: '2026-07-24T09:00:00Z', type: 'STANDARD_AVAILABILITY' }, { startsAt: '2026-07-24T09:00:00Z', endsAt: '2026-07-24T10:00:00Z', type: 'UNAVAILABLE' }], [], range, new Date('2026-07-23T00:00:00Z'), 30);
    expect(slots.map(slot => slot.startsAt)).toEqual(['2026-07-24T08:00:00.000Z', '2026-07-24T08:30:00.000Z', '2026-07-24T10:00:00.000Z', '2026-07-24T10:30:00.000Z']);
  });
});
