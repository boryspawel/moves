import type { TodayAppointment } from './specialist-today.types';

export interface VisibleRange { start: Date; end: Date; }
export interface AppointmentLayout { appointment: TodayAppointment; top: number; height: number; column: number; columns: number; }

export function minutesSinceRangeStart(value: string | Date, range: VisibleRange): number { return (new Date(value).getTime() - range.start.getTime()) / 60_000; }
export function clampToVisibleRange(start: string | Date, end: string | Date, range: VisibleRange): VisibleRange | null {
  const clippedStart = new Date(Math.max(new Date(start).getTime(), range.start.getTime()));
  const clippedEnd = new Date(Math.min(new Date(end).getTime(), range.end.getTime()));
  return clippedEnd > clippedStart ? { start: clippedStart, end: clippedEnd } : null;
}
export function positionPercent(value: string | Date, range: VisibleRange): number { return Math.max(0, Math.min(100, minutesSinceRangeStart(value, range) / minutesSinceRangeStart(range.end, range) * 100)); }
export function heightPercent(start: string | Date, end: string | Date, range: VisibleRange): number {
  const clipped = clampToVisibleRange(start, end, range); return clipped ? Math.max(0.8, positionPercent(clipped.end, range) - positionPercent(clipped.start, range)) : 0;
}
export function defaultVisibleRange(appointments: TodayAppointment[], availability: { startsAt: string; endsAt: string }[], fallbackDate: string): VisibleRange {
  const times = [...appointments, ...availability].flatMap(item => [new Date(item.startsAt), new Date(item.endsAt)]).filter(value => !Number.isNaN(value.getTime()));
  const base = `${fallbackDate}T`;
  if (!times.length) return { start: new Date(`${base}08:00:00`), end: new Date(`${base}18:00:00`) };
  const start = new Date(Math.max(new Date(`${base}06:00:00`).getTime(), Math.min(...times.map(value => value.getTime())) - 30 * 60_000));
  const end = new Date(Math.min(new Date(`${base}22:00:00`).getTime(), Math.max(...times.map(value => value.getTime())) + 30 * 60_000));
  return end > start ? { start, end } : { start: new Date(`${base}08:00:00`), end: new Date(`${base}18:00:00`) };
}
export function layoutOverlappingAppointments(appointments: TodayAppointment[], range: VisibleRange): AppointmentLayout[] {
  const visible = appointments.filter(item => item.status !== 'CANCELLED' && clampToVisibleRange(item.startsAt, item.endsAt, range));
  const sorted = [...visible].sort((a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime() || new Date(a.endsAt).getTime() - new Date(b.endsAt).getTime() || a.appointmentId.localeCompare(b.appointmentId));
  const groups: TodayAppointment[][] = []; let group: TodayAppointment[] = []; let groupEnd = -Infinity;
  for (const item of sorted) { const start = new Date(item.startsAt).getTime(); if (group.length && start >= groupEnd) { groups.push(group); group = []; groupEnd = -Infinity; } group.push(item); groupEnd = Math.max(groupEnd, new Date(item.endsAt).getTime()); }
  if (group.length) groups.push(group);
  return groups.flatMap(items => {
    const columns: number[] = [];
    const assigned = items.map(appointment => { const start = new Date(appointment.startsAt).getTime(); const end = new Date(appointment.endsAt).getTime(); let column = columns.findIndex(lastEnd => lastEnd <= start); if (column < 0) { column = columns.length; columns.push(end); } else columns[column] = end; return { appointment, column }; });
    const count = Math.min(columns.length, 4);
    return assigned.map(({ appointment, column }) => ({ appointment, top: positionPercent(appointment.startsAt, range), height: heightPercent(appointment.startsAt, appointment.endsAt, range), column: Math.min(column, count - 1), columns: count }));
  });
}
