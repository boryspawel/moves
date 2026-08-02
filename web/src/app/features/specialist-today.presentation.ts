export const SPECIALIST_TIME_ZONE = 'Europe/Warsaw';

/** Returns the calendar date for an instant in the specialist's configured application zone. */
export function localCalendarDate(value: Date, timeZone = SPECIALIST_TIME_ZONE): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(value);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find(item => item.type === type)?.value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}

/** Adds days to an ISO calendar date without depending on the browser time zone. */
export function addCalendarDays(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number);
  const shifted = new Date(Date.UTC(year, month - 1, day + days));
  return shifted.toISOString().slice(0, 10);
}

/** Validates an ISO calendar date without interpreting it in the browser's local zone. */
export function isLocalCalendarDate(value: string | null | undefined): value is string {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

/** Formats a route date without ever interpreting it in the browser's local time zone. */
export function todayHeading(date: string, now = new Date(), timeZone = SPECIALIST_TIME_ZONE): string {
  const today = localCalendarDate(now, timeZone);
  const tomorrow = addCalendarDays(today, 1);
  const [year, month, day] = date.split('-').map(Number);
  const calendarDate = new Date(Date.UTC(year, month - 1, day, 12));
  const label = new Intl.DateTimeFormat('pl-PL', {
    timeZone,
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    ...(date.slice(0, 4) !== today.slice(0, 4) ? { year: 'numeric' } : {}),
  }).format(calendarDate);
  const capitalized = `${label.charAt(0).toLocaleUpperCase('pl-PL')}${label.slice(1)}`;
  if (date === today) return `Dzisiaj, ${label}`;
  if (date === tomorrow) return `Jutro, ${label}`;
  return capitalized;
}

/** Converts the SDK's date-only value into the route representation used by the page. */
export function todayRouteDate(value: Date | undefined): string | null {
  if (!(value instanceof Date) || Number.isNaN(value.getTime())) return null;
  return `${value.getUTCFullYear()}-${String(value.getUTCMonth() + 1).padStart(2, '0')}-${String(value.getUTCDate()).padStart(2, '0')}`;
}
