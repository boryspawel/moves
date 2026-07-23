export interface SpecialistTodayView {
  generatedAt: string;
  localDate: string;
  timeZoneId: string;
  visibleRange: { startsAt: string; endsAt: string; recommendedStepMinutes: number };
  currentAppointment?: TodayAppointment;
  nextAppointment?: TodayAppointment;
  appointments: TodayAppointment[];
  availabilityWindows: AvailabilityWindow[];
  attentionItems: TodayAttentionItem[];
  operationalTasks: TodayOperationalTask[];
  counts?: Record<string, number>;
}

export interface TodayAppointment {
  appointmentId: string;
  participantId?: string;
  participantLabel: string;
  startsAt: string;
  endsAt: string;
  type: string;
  status: string;
  locationMode?: string;
  location?: string;
  shortPurpose?: string;
  isCurrent?: boolean;
  isNext?: boolean;
  availableActions: string[];
  version?: number;
}

export interface AvailabilityWindow { startsAt: string; endsAt: string; type: string; }
export interface TodayAttentionItem {
  id: string; type?: string; priority?: string; participantLabel?: string; title: string;
  reason?: string; createdAt?: string; dueAt?: string; status?: string; availableActions?: string[]; navigationReference?: string;
}
export interface TodayOperationalTask { id: string; title: string; dueAt?: string; availableActions?: string[]; }
