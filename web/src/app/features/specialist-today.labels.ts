const labels = {
  appointmentType: { TRAINING: 'Trening', PHYSIOTHERAPY: 'Fizjoterapia', ASSESSMENT: 'Ocena', CONSULTATION: 'Konsultacja' },
  appointmentStatus: { SCHEDULED: 'Zaplanowane', CONFIRMED: 'Potwierdzone', IN_PROGRESS: 'Trwa', COMPLETED: 'Zakończone', CANCELLED: 'Odwołane', NO_SHOW: 'Nieobecność' },
  locationMode: { IN_PERSON: 'Stacjonarnie', ONLINE: 'Online', REMOTE: 'Zdalnie', PHONE: 'Telefonicznie', HOME_VISIT: 'Wizyta domowa' },
  priority: { HIGH: 'Wysoki priorytet', MEDIUM: 'Średni priorytet', LOW: 'Niski priorytet' },
  action: { OPEN_APPOINTMENT: 'Otwórz', OPEN_PARTICIPANT: 'Otwórz klienta', START_SESSION: 'Rozpocznij sesję', RESCHEDULE: 'Zmień termin', CANCEL: 'Odwołaj', MARK_NO_SHOW: 'Oznacz nieobecność' },
} as const;

function label(group: Record<string, string>, value?: string): string { return value ? group[value] ?? 'Nieznane' : 'Nie podano'; }
export const appointmentTypeLabel = (value?: string) => label(labels.appointmentType, value);
export const appointmentStatusLabel = (value?: string) => label(labels.appointmentStatus, value);
export const locationModeLabel = (value?: string) => label(labels.locationMode, value);
export const priorityLabel = (value?: string) => label(labels.priority, value);
export const actionLabel = (value?: string) => label(labels.action, value);
