import { Injectable, inject } from '@angular/core';
import { ApiFacade } from '../core/api.facade';
import type { TodayView } from '../api/generated/src/models/TodayView';
import type { AvailableSlotsView } from '../api/generated/src/models/AvailableSlotsView';

@Injectable({ providedIn: 'root' })
export class SpecialistTodayApi {
  private readonly api = inject(ApiFacade);

  async get(date?: string): Promise<TodayView> {
    return this.api.specialistToday.getToday({ date: date ? new Date(`${date}T12:00:00`) : undefined });
  }

  async availableSlots(date: string, durationMinutes: number): Promise<AvailableSlotsView> {
    return this.api.specialistAvailableSlots.list2({ date: new Date(`${date}T12:00:00`), durationMinutes });
  }
}
