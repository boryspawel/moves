import { Injectable, inject } from '@angular/core';
import { ApiFacade } from '../core/api.facade';
import type { TodayView } from '../api/generated/src/models/TodayView';
import type { SpecialistTodayView } from './specialist-today.types';

@Injectable({ providedIn: 'root' })
export class SpecialistTodayApi {
  private readonly api = inject(ApiFacade);

  async get(date?: string): Promise<SpecialistTodayView> {
    return this.normalize(await this.api.specialistToday.getToday({ date: date ? new Date(`${date}T12:00:00`) : undefined }));
  }

  private normalize(value: TodayView): SpecialistTodayView {
    const iso = (date?: Date) => date?.toISOString() ?? '';
    const appointment = (item: NonNullable<TodayView['appointments']>[number]) => ({
      appointmentId: item.appointmentId ?? '',
      participantId: item.participantId,
      participantLabel: 'Dane uczestnika niedostępne',
      startsAt: iso(item.startsAt),
      endsAt: iso(item.endsAt),
      type: item.type ?? 'CONSULTATION', status: item.status ?? 'SCHEDULED', locationMode: item.locationMode, location: item.location,
      shortPurpose: item.shortPurpose, isCurrent: item.isCurrent, isNext: item.isNext,
      availableActions: item.availableActions ?? [],
      version: item.version,
    });
    return {
      generatedAt: iso(value.generatedAt),
      localDate: value.localDate ? iso(value.localDate).slice(0, 10) : '',
      timeZoneId: value.timeZoneId ?? 'Europe/Warsaw',
      visibleRange: { startsAt: iso(value.visibleRange?.startsAt), endsAt: iso(value.visibleRange?.endsAt), recommendedStepMinutes: value.visibleRange?.recommendedStepMinutes ?? 30 },
      appointments: (value.appointments ?? []).map(appointment),
      currentAppointment: value.currentAppointment ? appointment(value.currentAppointment) : undefined,
      nextAppointment: value.nextAppointment ? appointment(value.nextAppointment) : undefined,
      availabilityWindows: (value.availabilityWindows ?? []).map(item => ({ startsAt: iso(item.startsAt), endsAt: iso(item.endsAt), type: item.type ?? '' })),
      attentionItems: (value.attentionItems ?? []).map(item => ({ id: item.id ?? '', type: item.type, priority: item.priority, participantLabel: item.participantLabel, title: item.title ?? 'Sprawa wymaga uwagi', reason: item.neutralReason, createdAt: iso(item.createdAt), dueAt: item.dueAt ? iso(item.dueAt) : undefined, status: item.status, availableActions: item.availableActions, navigationReference: item.navigationReference })),
      operationalTasks: (value.operationalTasks ?? []).map(item => ({ id: item.type ?? item.title ?? '', title: item.title ?? 'Zadanie operacyjne' })),
      counts: value.counts ? { appointments: value.counts.appointments ?? 0, attentionItems: value.counts.attentionItems ?? 0, operationalTasks: value.counts.operationalTasks ?? 0, currentAppointments: value.counts.currentAppointments ?? 0 } : undefined,
    };
  }
}
