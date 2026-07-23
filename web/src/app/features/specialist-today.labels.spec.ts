import { describe, expect, it } from 'vitest';
import { appointmentStatusLabel, appointmentTypeLabel, locationModeLabel, priorityLabel } from './specialist-today.labels';

describe('specialist today Polish labels', () => {
  it('does not expose raw known enums', () => {
    expect(appointmentTypeLabel('TRAINING')).toBe('Trening');
    expect(appointmentStatusLabel('IN_PROGRESS')).toBe('Trwa');
    expect(priorityLabel('HIGH')).toBe('Wysoki priorytet');
    expect(locationModeLabel('REMOTE')).toBe('Zdalnie');
    expect(locationModeLabel('PHONE')).toBe('Telefonicznie');
  });
});
