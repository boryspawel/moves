import { describe, expect, it } from 'vitest';
import { addCalendarDays, isLocalCalendarDate, localCalendarDate, todayHeading } from './specialist-today.presentation';

describe('specialist today date presentation', () => {
  const now = new Date('2026-07-28T21:30:00.000Z');

  it('uses the specialist time zone when determining today', () => {
    expect(localCalendarDate(now, 'Europe/Warsaw')).toBe('2026-07-28');
    expect(todayHeading('2026-07-28', now)).toMatch(/^Dzisiaj,/);
  });

  it('does not call a past or future date today', () => {
    expect(todayHeading('2026-08-03', now)).toMatch(/^Poniedziałek,/);
    expect(todayHeading('2026-07-27', now)).toMatch(/^Poniedziałek,/);
  });

  it('includes the year outside the current specialist calendar year', () => {
    expect(todayHeading('2027-08-03', now)).toContain('2027');
  });

  it('moves ISO calendar dates across DST independently from the browser zone', () => {
    expect(addCalendarDays('2026-10-25', 1)).toBe('2026-10-26');
  });

  it('accepts only real ISO local calendar dates', () => {
    expect(isLocalCalendarDate('2026-08-03')).toBe(true);
    expect(isLocalCalendarDate('2026-02-30')).toBe(false);
    expect(isLocalCalendarDate('03-08-2026')).toBe(false);
  });
});
