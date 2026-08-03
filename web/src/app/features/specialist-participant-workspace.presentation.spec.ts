import { describe, expect, it } from 'vitest';
import { categoryLabel, eventTimeLabel, safeText, sourceLabel, statusLabel, typeLabel } from './specialist-participant-workspace.presentation';

describe('participant workspace presentation', () => {
  it('uses Polish appointment labels and a 24-hour range', () => {
    const event = { category: 'APPOINTMENT', eventType: 'TRAINING', effectiveFrom: new Date('2026-08-03T09:00:00'), effectiveTo: new Date('2026-08-03T10:00:00') };
    expect(categoryLabel(event.category)).toBe('Spotkanie');
    expect(typeLabel(event.eventType)).toBe('Trening');
    expect(eventTimeLabel(event)).toBe('3 sierpnia, 09:00–10:00');
  });

  it('does not expose empty or technical source and marks past scheduled appointments', () => {
    expect(safeText('')).toBeUndefined();
    expect(sourceLabel('SYSTEM_EVENT')).toBeUndefined();
    expect(statusLabel({ status: 'SCHEDULED', effectiveFrom: new Date('2026-08-02T09:00:00') }, new Date('2026-08-03T09:00:00'))).toBe('Termin minął · status nieuzupełniony');
  });
});
