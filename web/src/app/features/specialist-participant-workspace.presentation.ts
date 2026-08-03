import type { ParticipantTimelineEvent } from '../api/generated/src/models/ParticipantTimelineEvent';

const categoryLabels: Record<string, string> = { APPOINTMENT: 'Spotkanie', SESSION: 'Planowana sesja', EXECUTION: 'Wykonanie' };
const typeLabels: Record<string, string> = { TRAINING: 'Trening', PHYSIOTHERAPY: 'Fizjoterapia', ASSESSMENT: 'Ocena', CONSULTATION: 'Konsultacja' };
const statusLabels: Record<string, string> = { SCHEDULED: 'Zaplanowane', STARTED: 'Rozpoczęte', COMPLETED: 'Ukończone', SKIPPED: 'Pominięte', CANCELLED: 'Odwołane', NO_SHOW: 'Nieobecność' };
const sourceLabels: Record<string, string> = { APPOINTMENT: 'Spotkanie', SPECIALIST: 'Specjalista', PARTICIPANT: 'Uczestnik' };

export const supportedTimelineCategories = ['APPOINTMENT', 'SESSION', 'EXECUTION'] as const;
export type TimelineCategory = typeof supportedTimelineCategories[number];

export function safeText(value?: string): string | undefined {
  const text = value?.trim();
  return text && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(text) ? text : undefined;
}

export function categoryLabel(value?: string): string { return categoryLabels[value ?? ''] ?? 'Zdarzenie'; }
export function typeLabel(value?: string): string | undefined { return typeLabels[value ?? '']; }
export function sourceLabel(value?: string): string | undefined {
  return sourceLabels[value ?? ''] ?? (value && /^[A-Z0-9_]+$/.test(value) ? undefined : safeText(value));
}
export function statusLabel(event: ParticipantTimelineEvent, now = new Date()): string | undefined {
  if (event.status === 'SCHEDULED' && event.effectiveFrom && event.effectiveFrom < now) return 'Termin minął · status nieuzupełniony';
  return statusLabels[event.status ?? ''];
}
export function eventTimeLabel(event: ParticipantTimelineEvent, locale = 'pl-PL'): string | undefined {
  const from = event.effectiveFrom ?? event.recordedAt;
  if (!from) return undefined;
  const date = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'long' }).format(from);
  const start = new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(from);
  const end = event.effectiveTo && new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(event.effectiveTo);
  return `${date}, ${start}${end ? `–${end}` : ''}`;
}
