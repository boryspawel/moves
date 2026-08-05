import type { ParticipantTimelineEvent } from '../api/generated/src/models/ParticipantTimelineEvent';

const categoryLabels: Record<string, string> = { APPOINTMENT: 'Spotkanie', SESSION: 'Planowana sesja', EXECUTION: 'Wykonanie' };
const typeLabels: Record<string, string> = { TRAINING: 'Trening', PHYSIOTHERAPY: 'Fizjoterapia', ASSESSMENT: 'Ocena', CONSULTATION: 'Konsultacja' };
const statusLabels: Record<string, string> = { SCHEDULED: 'Zaplanowane', STARTED: 'Rozpoczęte', COMPLETED: 'Ukończone', SKIPPED: 'Pominięte', CANCELLED: 'Odwołane', NO_SHOW: 'Nieobecność' };
const bodyCircumferenceLabels: Record<string, string> = {
  WAIST: 'Obwód talii',
  HIPS: 'Obwód bioder',
  CHEST: 'Obwód klatki piersiowej',
  ARM: 'Obwód ramienia',
  THIGH: 'Obwód uda',
  CALF: 'Obwód łydki',
  NECK: 'Obwód szyi',
  OTHER: 'Obwód ciała',
};

export const supportedTimelineCategories = ['APPOINTMENT', 'SESSION', 'EXECUTION'] as const;
export type TimelineCategory = typeof supportedTimelineCategories[number];

export function safeText(value?: string): string | undefined {
  const text = value?.trim();
  return text && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(text) ? text : undefined;
}

export function categoryLabel(value?: string): string { return categoryLabels[value ?? ''] ?? 'Zdarzenie'; }
export function typeLabel(value?: string): string | undefined { return typeLabels[value ?? '']; }
export function outcomeMetricLabel(metricCode?: string): string {
  const normalized = metricCode?.trim().toUpperCase().replace(/[-_]/g, '_');
  if (normalized === 'BODY_WEIGHT') return 'Masa ciała';
  const qualifier = /^BODY_CIRCUMFERENCE(?:[:_](.+))?$/.exec(normalized ?? '')?.[1];
  return qualifier ? bodyCircumferenceLabels[qualifier] ?? 'Obwód ciała' : normalized === 'BODY_CIRCUMFERENCE' ? 'Obwód ciała' : 'Wynik';
}
export function appointmentTypeLabel(event: ParticipantTimelineEvent): string | undefined { return event.category === 'APPOINTMENT' ? typeLabel(event.eventType) : undefined; }
export function humanEventTitle(event: ParticipantTimelineEvent): string {
  const rawTitle = safeText(event.title);
  const normalizedTitle = typeLabel(rawTitle);
  if (event.category === 'APPOINTMENT') return appointmentTypeLabel(event) ?? normalizedTitle ?? (rawTitle && !/^[A-Z0-9_]+$/.test(rawTitle) ? rawTitle : 'Spotkanie');
  return normalizedTitle ?? rawTitle ?? typeLabel(event.eventType) ?? categoryLabel(event.category);
}
export function eventDescription(event: ParticipantTimelineEvent): string | undefined { return safeText(event.summary); }
export function appointmentLocation(_event: ParticipantTimelineEvent): string | undefined { return undefined; }
export function appointmentPurpose(_event: ParticipantTimelineEvent): string | undefined { return undefined; }
export function isPastScheduled(event: ParticipantTimelineEvent, now = new Date()): boolean {
  return event.status === 'SCHEDULED' && !!event.effectiveFrom && event.effectiveFrom < now;
}
export function statusLabel(event: ParticipantTimelineEvent, now = new Date()): string | undefined {
  if (isPastScheduled(event, now)) return 'Termin minął · status nieuzupełniony';
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

function plural(count: number, singular: string, few: string, many: string): string {
  const remainder = count % 10;
  const teens = count % 100;
  return count === 1 ? singular : remainder >= 2 && remainder <= 4 && (teens < 12 || teens > 14) ? few : many;
}
export function goalsSummary(count?: number): string {
  if (!count) return 'Brak aktywnych celów';
  return `${count} ${plural(count, 'aktywny cel', 'aktywne cele', 'aktywnych celów')}`;
}
export function realizationSummary(count?: number): string {
  if (count == null) return 'Brak danych';
  if (!count) return 'Brak wykonanych sesji';
  return `${count} ${plural(count, 'wykonana sesja', 'wykonane sesje', 'wykonanych sesji')}`;
}
export function attentionSummary(count?: number): string {
  if (!count) return 'Brak problemów';
  return `${count} ${plural(count, 'problem', 'problemy', 'problemów')}`;
}
