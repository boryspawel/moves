import type { AvailabilityWindow, TodayAppointment } from './specialist-today.types';

export interface FreeSlot { startsAt: string; endsAt: string; isPast: boolean; }
export interface SlotVisibleRange { startsAt: string; endsAt: string; }

const blockingStatuses = new Set(['SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW']);
const isAvailability = (type: string) => type === 'STANDARD_AVAILABILITY' || type === 'EXCEPTION_AVAILABLE';

/** Produces non-overlapping, step-aligned appointment-sized slots from explicitly available periods only. */
export function buildFreeSlots(availabilityWindows: AvailabilityWindow[], appointments: TodayAppointment[], visibleRange: SlotVisibleRange, currentTime: Date, stepMinutes = 30): FreeSlot[] {
  const step = Math.max(1, stepMinutes || 30) * 60_000;
  const rangeStart = new Date(visibleRange.startsAt).getTime();
  const rangeEnd = new Date(visibleRange.endsAt).getTime();
  if (!Number.isFinite(rangeStart) || !Number.isFinite(rangeEnd) || rangeEnd <= rangeStart) return [];
  const occupied = appointments.filter(item => blockingStatuses.has(item.status)).map(item => ({ start: new Date(item.startsAt).getTime(), end: new Date(item.endsAt).getTime() })).filter(item => Number.isFinite(item.start) && Number.isFinite(item.end) && item.end > item.start).sort((a, b) => a.start - b.start || a.end - b.end);
  const firstInteractiveStart = Math.ceil(currentTime.getTime() / step) * step;
  const result: FreeSlot[] = [];
  for (const window of availabilityWindows.filter(item => isAvailability(item.type))) {
    const start = Math.max(rangeStart, new Date(window.startsAt).getTime()); const end = Math.min(rangeEnd, new Date(window.endsAt).getTime());
    if (!Number.isFinite(start) || !Number.isFinite(end) || end - start < step) continue;
    const alignedStart = Math.ceil(start / step) * step;
    for (let cursor = alignedStart; cursor + step <= end; cursor += step) {
      if (occupied.some(item => item.start < cursor + step && item.end > cursor)) continue;
      const isPast = cursor < firstInteractiveStart;
      result.push({ startsAt: new Date(cursor).toISOString(), endsAt: new Date(cursor + step).toISOString(), isPast });
    }
  }
  return result.sort((a, b) => a.startsAt.localeCompare(b.startsAt)).filter((slot, index, items) => index === 0 || slot.startsAt !== items[index - 1].startsAt);
}
