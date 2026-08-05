import { describe, expect, it } from 'vitest';
import { attentionSummary, categoryLabel, eventTimeLabel, goalsSummary, humanEventTitle, outcomeMetricLabel, realizationSummary, safeText, statusLabel, typeLabel } from './specialist-participant-workspace.presentation';

describe('participant workspace presentation', () => {
  it('uses Polish appointment labels and a 24-hour range', () => {
    const event = { category: 'APPOINTMENT', eventType: 'TRAINING', effectiveFrom: new Date('2026-08-03T09:00:00'), effectiveTo: new Date('2026-08-03T10:00:00') };
    expect(categoryLabel(event.category)).toBe('Spotkanie');
    expect(typeLabel(event.eventType)).toBe('Trening');
    expect(eventTimeLabel(event)).toBe('3 sierpnia, 09:00–10:00');
  });

  it('omits blank text and marks past scheduled appointments', () => {
    expect(safeText('')).toBeUndefined();
    expect(statusLabel({ status: 'SCHEDULED', effectiveFrom: new Date('2026-08-02T09:00:00') }, new Date('2026-08-03T09:00:00'))).toBe('Termin minął · status nieuzupełniony');
  });

  it('normalizes raw appointment titles and uses the Polish fallback for unknown appointment enums', () => {
    expect(humanEventTitle({ category: 'APPOINTMENT', title: 'TRAINING' })).toBe('Trening');
    expect(humanEventTitle({ category: 'APPOINTMENT', title: 'UNRECOGNIZED_TYPE' })).toBe('Spotkanie');
  });

  it('uses localized plural summary messages', () => {
    expect(goalsSummary()).toBe('Brak aktywnych celów');
    expect(goalsSummary(1)).toBe('1 aktywny cel');
    expect(goalsSummary(2)).toBe('2 aktywne cele');
    expect(realizationSummary()).toBe('Brak danych');
    expect(realizationSummary(0)).toBe('Brak wykonanych sesji');
    expect(realizationSummary(1)).toBe('1 wykonana sesja');
    expect(realizationSummary(5)).toBe('5 wykonanych sesji');
    expect(attentionSummary(0)).toBe('Brak problemów');
    expect(attentionSummary(3)).toBe('3 problemy');
  });

  it('uses nontechnical labels for body metrics and unknown outcome codes', () => {
    expect(outcomeMetricLabel('body-weight')).toBe('Masa ciała');
    expect(outcomeMetricLabel('body-circumference:waist')).toBe('Obwód talii');
    expect(outcomeMetricLabel('BODY_CIRCUMFERENCE:HIPS')).toBe('Obwód bioder');
    expect(outcomeMetricLabel('BODY_CIRCUMFERENCE:CHEST')).toBe('Obwód klatki piersiowej');
    expect(outcomeMetricLabel('INTERNAL_METRIC')).toBe('Wynik');
  });
});
