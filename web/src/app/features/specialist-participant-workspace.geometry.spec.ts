import { describe, expect, it } from 'vitest';
import { groupEvents, overviewPosition, rangeDates, sortedEvents } from './specialist-participant-workspace.geometry';

describe('participant workspace timeline geometry', () => {
  it('maps ranges to bounded server granularities', () => {
    expect(rangeDates('2w', new Date('2026-07-24')).granularity).toBe('DETAIL');
    expect(rangeDates('3m', new Date('2026-07-24')).granularity).toBe('WEEK');
    expect(rangeDates('12m', new Date('2026-07-24')).granularity).toBe('MONTH');
    expect(rangeDates('all', new Date('2026-07-24')).from).toBeUndefined();
  });

  it('sorts newest first and groups without exposing technical identifiers', () => {
    const events = sortedEvents([{ eventId: 'a', effectiveFrom: new Date('2026-07-20') }, { eventId: 'b', effectiveFrom: new Date('2026-07-22') }]);
    expect(events.map(event => event.eventId)).toEqual(['b', 'a']);
    expect(groupEvents(events, 'MONTH')).toHaveLength(1);
  });

  it('clamps overview markers to the selected interval', () => {
    const from = new Date('2026-07-01'); const to = new Date('2026-07-11');
    expect(overviewPosition(new Date('2026-07-06'), from, to)).toBe(50);
    expect(overviewPosition(new Date('2026-06-01'), from, to)).toBe(0);
    expect(overviewPosition(new Date('2026-08-01'), from, to)).toBe(100);
  });
});
