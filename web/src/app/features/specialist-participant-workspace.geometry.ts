import type { ParticipantTimelineEvent } from '../api/generated/src/models/ParticipantTimelineEvent';

export type WorkspaceRange = '2w' | '3m' | '12m' | 'all';
export type WorkspaceView = 'timeline' | 'list';
export const timelineCategories = ['GOALS', 'PLANS', 'SESSIONS', 'EXECUTION', 'PROGRESS', 'PROBLEMS', 'NOTES', 'DOCUMENTS'] as const;
export type TimelineCategory = typeof timelineCategories[number];

export function rangeDates(range: WorkspaceRange, now = new Date()): { from?: Date; to: Date; granularity: 'DETAIL' | 'WEEK' | 'MONTH' } {
  const to = new Date(now); const from = new Date(now);
  if (range === '2w') { from.setDate(from.getDate() - 14); return { from, to, granularity: 'DETAIL' }; }
  if (range === '3m') { from.setMonth(from.getMonth() - 3); return { from, to, granularity: 'WEEK' }; }
  if (range === '12m') { from.setFullYear(from.getFullYear() - 1); return { from, to, granularity: 'MONTH' }; }
  return { to, granularity: 'MONTH' };
}
export function sortedEvents(events: readonly ParticipantTimelineEvent[]): ParticipantTimelineEvent[] {
  return [...events].sort((a, b) => (b.effectiveFrom?.getTime() ?? b.recordedAt?.getTime() ?? 0) - (a.effectiveFrom?.getTime() ?? a.recordedAt?.getTime() ?? 0));
}
export function periodLabel(event: ParticipantTimelineEvent, granularity: string, locale = 'pl-PL'): string {
  const date = event.effectiveFrom ?? event.recordedAt; if (!date) return 'Bez określonego czasu';
  if (granularity === 'MONTH') return new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(date);
  if (granularity === 'WEEK') return `Tydzień od ${new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'long', year: 'numeric' }).format(date)}`;
  return new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(date);
}
export function groupEvents(events: readonly ParticipantTimelineEvent[], granularity: string): Array<{ label: string; items: ParticipantTimelineEvent[] }> {
  const groups = new Map<string, ParticipantTimelineEvent[]>();
  for (const event of sortedEvents(events)) { const label = periodLabel(event, granularity); groups.set(label, [...(groups.get(label) ?? []), event]); }
  return [...groups].map(([label, items]) => ({ label, items }));
}
export function overviewPosition(date: Date | undefined, from: Date | undefined, to: Date | undefined): number {
  if (!date || !from || !to || to <= from) return 0; return Math.max(0, Math.min(100, ((date.getTime() - from.getTime()) / (to.getTime() - from.getTime())) * 100));
}
