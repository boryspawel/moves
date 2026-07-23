import { Injectable, inject } from '@angular/core';
import { Configuration } from '../api/generated/src';
import { BaseAPI, type RequestOpts } from '../api/generated/src/runtime';
import { AuthService } from '../core/auth.service';
import { generatedAuthorizationMiddleware, normalizeGeneratedApiBasePath } from '../core/api.facade';
import { environment } from '../../environments/environment';
import type { SpecialistTodayView } from './specialist-today.types';

@Injectable({ providedIn: 'root' })
export class SpecialistTodayApi {
  private readonly auth = inject(AuthService);
  private readonly client = new SpecialistTodayGeneratedStyleClient(new Configuration({
    basePath: normalizeGeneratedApiBasePath(environment.apiBaseUrl),
    accessToken: () => this.auth.accessToken(),
    middleware: [generatedAuthorizationMiddleware(() => this.auth.accessToken())],
  }));

  async get(date?: string): Promise<SpecialistTodayView> {
    return this.normalize(await this.client.today(date));
  }

  private normalize(value: any): SpecialistTodayView {
    const appointment = (item: any) => ({
      ...item,
      appointmentId: item.appointmentId ?? item.id,
      participantLabel: item.participantLabel ?? 'Dane uczestnika niedostępne',
      startsAt: item.startsAt,
      endsAt: item.endsAt,
      availableActions: item.availableActions ?? [],
    });
    return {
      ...value,
      localDate: value.localDate,
      timeZoneId: value.timeZoneId ?? 'Europe/Warsaw',
      visibleRange: value.visibleRange,
      appointments: (value.appointments ?? []).map(appointment),
      currentAppointment: value.currentAppointment ? appointment(value.currentAppointment) : undefined,
      nextAppointment: value.nextAppointment ? appointment(value.nextAppointment) : undefined,
      availabilityWindows: value.availabilityWindows ?? [],
      attentionItems: (value.attentionItems ?? []).map((item: any) => ({ ...item, id: item.id ?? item.itemId, title: item.title ?? item.shortTitle ?? 'Sprawa wymaga uwagi' })),
      operationalTasks: value.operationalTasks ?? [],
    } as SpecialistTodayView;
  }
}

/** Temporary generated-client-shaped wrapper until `api:refresh` can run against the canonical snapshot. */
class SpecialistTodayGeneratedStyleClient extends BaseAPI {
  async today(date?: string): Promise<any> {
    const request: RequestOpts = { path: '/api/v1/specialist/today', method: 'GET', headers: {}, query: date ? { date } : undefined };
    const response = await this.request(request);
    return response.json();
  }
}
