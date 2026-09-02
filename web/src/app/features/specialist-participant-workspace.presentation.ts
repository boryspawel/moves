import type { ParticipantTimelineEvent } from '../api/generated/src/models/ParticipantTimelineEvent';
import type { Question } from '../api/generated/src/models/Question';

const categoryLabels: Record<string, string> = { APPOINTMENT: 'Spotkanie', SESSION: 'Planowana sesja', EXECUTION: 'Wykonanie', INTERVIEW: 'Wywiad', NOTE: 'Notatka' };
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
const interviewEventLabels: Record<string, string> = {
  STARTED: 'Rozpoczęto wywiad',
  UPDATED: 'Zaktualizowano wywiad',
  COMPLETED: 'Zakończono wywiad',
  SUPERSEDED: 'Dodano nowszy wywiad',
  INTERVIEW_STARTED: 'Rozpoczęto wywiad',
  INTERVIEW_UPDATED: 'Zaktualizowano wywiad',
  INTERVIEW_COMPLETED: 'Zakończono wywiad',
  INTERVIEW_SUPERSEDED: 'Dodano nowszy wywiad',
};
const questionPresentation: Record<string, { title: string; section: string; options?: Record<string, string> }> = {
  presenting_concern: { title: 'Z czym uczestnik zgłasza się dziś?', section: 'Cel wizyty' },
  goals: { title: 'Co uczestnik chce osiągnąć?', section: 'Cel wizyty' },
  medical_history: { title: 'Istotna historia zdrowia', section: 'Zdrowie i bezpieczeństwo' },
  medications: { title: 'Aktualnie przyjmowane leki', section: 'Zdrowie i bezpieczeństwo' },
  pain_level: { title: 'Aktualny poziom bólu', section: 'Ból i urazy' },
  injury_date: { title: 'Data istotnego urazu', section: 'Ból i urazy' },
  activity_level: { title: 'Aktualny poziom aktywności', section: 'Aktywność i codzienność', options: { LOW: 'Niski', MODERATE: 'Umiarkowany', HIGH: 'Wysoki' } },
  sleep: { title: 'Sen i regeneracja', section: 'Aktywność i codzienność', options: { POOR: 'Niewystarczający', ADEQUATE: 'Wystarczający', GOOD: 'Dobry' } },
  consents: { title: 'Omówione zgody', section: 'Zgody i ustalenia', options: { DATA_PROCESSING: 'Przetwarzanie danych', CONTACT: 'Kontakt', PLAN_SHARING: 'Udostępnianie planu' } },
};
const interviewStatusLabels: Record<string, string> = { DRAFT: 'Wywiad w trakcie', COMPLETED: 'Wywiad zakończony', SUPERSEDED: 'Zastąpiony nowszym wywiadem' };
const noteCategoryLabels: Record<string, string> = { GENERAL: 'Notatka ogólna', SESSION: 'Notatka ze spotkania', HEALTH: 'Notatka zdrowotna', OTHER: 'Inna notatka' };
const noteStatusLabels: Record<string, string> = { DRAFT: 'W trakcie', FINALISED: 'Zakończona', FINALIZED: 'Zakończona', ARCHIVED: 'Zarchiwizowana' };

export const supportedTimelineCategories = ['APPOINTMENT', 'SESSION', 'EXECUTION', 'INTERVIEW', 'NOTE'] as const;
export type TimelineCategory = typeof supportedTimelineCategories[number];

export function safeText(value?: string): string | undefined {
  const text = value?.trim();
  return text && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(text) ? text : undefined;
}

export function safeSnapshotText(value?: string, fallback = 'Nie podano'): string {
  const text = safeText(value);
  return text && !/^[A-Z0-9_:-]+$/.test(text) ? text : fallback;
}
export function questionTitle(question: Question): string { return questionPresentation[question.code ?? '']?.title ?? safeSnapshotText(question.title); }
export function questionSection(question: Question): string { return questionPresentation[question.code ?? '']?.section ?? safeSnapshotText(question.section, 'Pozostałe informacje'); }
export function questionOptions(question: Question): { value: string; label: string }[] {
  return Object.entries(questionPresentation[question.code ?? '']?.options ?? {}).map(([value, label]) => ({ value, label }));
}
export function selectionLabel(question: Question, selection?: string): string { return questionPresentation[question.code ?? '']?.options?.[selection ?? ''] ?? 'Nie podano'; }
export function interviewStatusLabel(status?: string): string { return interviewStatusLabels[status ?? ''] ?? 'Status wywiadu nieznany'; }
export function noteCategoryLabel(category?: string): string { return noteCategoryLabels[category ?? ''] ?? 'Notatka'; }
export function noteStatusLabel(status?: string): string { return noteStatusLabels[status ?? ''] ?? 'Status notatki nieznany'; }

export function isInterviewEvent(event: ParticipantTimelineEvent): boolean {
  const category = event.category?.trim().toUpperCase().replace(/[-\s]/g, '_') ?? '';
  const type = event.eventType?.trim().toUpperCase().replace(/[-\s]/g, '_') ?? '';
  return category === 'INTERVIEW' || category.endsWith('_INTERVIEW') || type.includes('INTERVIEW');
}
export function categoryLabel(value?: string): string {
  const normalized = value?.trim().toUpperCase().replace(/[-\s]/g, '_') ?? '';
  return normalized === 'INTERVIEW' || normalized.endsWith('_INTERVIEW') ? 'Wywiad' : categoryLabels[normalized] ?? 'Zdarzenie';
}
export function typeLabel(value?: string): string | undefined { return typeLabels[value ?? '']; }
export function outcomeMetricLabel(metricCode?: string): string {
  const normalized = metricCode?.trim().toUpperCase().replace(/[-_]/g, '_');
  if (normalized === 'BODY_WEIGHT') return 'Masa ciała';
  const qualifier = /^BODY_CIRCUMFERENCE(?:[:_](.+))?$/.exec(normalized ?? '')?.[1];
  return qualifier ? bodyCircumferenceLabels[qualifier] ?? 'Obwód ciała' : normalized === 'BODY_CIRCUMFERENCE' ? 'Obwód ciała' : 'Wynik';
}
export function appointmentTypeLabel(event: ParticipantTimelineEvent): string | undefined { return event.category === 'APPOINTMENT' ? typeLabel(event.eventType) : undefined; }
export function humanEventTitle(event: ParticipantTimelineEvent): string {
  if (isInterviewEvent(event)) {
    const normalizedType = event.eventType?.trim().toUpperCase().replace(/[-\s]/g, '_').replace(/^(?:PARTICIPANT_)?INTERVIEW_/, '') ?? '';
    return interviewEventLabels[normalizedType] ?? 'Wywiad';
  }
  if (event.category === 'NOTE') return 'Notatka';
  const rawTitle = safeText(event.title);
  const normalizedTitle = typeLabel(rawTitle);
  if (event.category === 'APPOINTMENT') return appointmentTypeLabel(event) ?? normalizedTitle ?? (rawTitle && !/^[A-Z0-9_]+$/.test(rawTitle) ? rawTitle : 'Spotkanie');
  return normalizedTitle ?? rawTitle ?? typeLabel(event.eventType) ?? categoryLabel(event.category);
}
export function eventDescription(event: ParticipantTimelineEvent): string | undefined { return isInterviewEvent(event) ? undefined : safeText(event.summary); }
export function appointmentLocation(_event: ParticipantTimelineEvent): string | undefined { return undefined; }
export function appointmentPurpose(_event: ParticipantTimelineEvent): string | undefined { return undefined; }
export function isPastScheduled(event: ParticipantTimelineEvent, now = new Date()): boolean {
  return event.status === 'SCHEDULED' && !!event.effectiveFrom && event.effectiveFrom < now;
}
export function statusLabel(event: ParticipantTimelineEvent, now = new Date()): string | undefined {
  if (isInterviewEvent(event)) return undefined;
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
