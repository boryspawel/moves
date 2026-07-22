import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  BarrierReportControllerApi,
  Configuration,
  ExerciseCatalogControllerApi,
  GamificationControllerApi,
  ReminderPreferenceControllerApi,
  OnboardingControllerApi,
  ParticipantSafetyControllerApi,
  SessionExecutionAttemptControllerApi,
  SessionExecutionControllerApi,
  SpecialistRelationshipControllerApi,
  SpecialistWorklistControllerApi,
  TodayAgendaControllerApi,
  TrainingPlanningControllerApi,
  TrainingPlanningV2ControllerApi,
  PlanRevisionWorkflowControllerApi
} from '../api/generated/src';
import { Middleware } from '../api/generated/src/runtime';
import { AuthService } from './auth.service';

export function normalizeGeneratedApiBasePath(apiBaseUrl: string): string {
  return apiBaseUrl.replace(/\/api\/?$/, '');
}

export function generatedAuthorizationMiddleware(accessToken: () => Promise<string>): Middleware {
  return { pre: async ({ url, init }) => {
    const token = await accessToken();
    if (!token) return { url, init };
    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${token}`);
    return { url, init: { ...init, headers } };
  }};
}

@Injectable({ providedIn: 'root' })
export class ApiFacade {
  readonly onboarding: OnboardingControllerApi;
  readonly catalog: ExerciseCatalogControllerApi;
  readonly planning: TrainingPlanningControllerApi;
  readonly planningV2: TrainingPlanningV2ControllerApi;
  readonly planWorkflow: PlanRevisionWorkflowControllerApi;
  readonly specialistParticipants: SpecialistRelationshipControllerApi;
  readonly worklist: SpecialistWorklistControllerApi;
  readonly execution: SessionExecutionControllerApi;
  readonly attempts: SessionExecutionAttemptControllerApi;
  readonly today: TodayAgendaControllerApi;
  readonly safety: ParticipantSafetyControllerApi;
  readonly barriers: BarrierReportControllerApi;
  readonly gamification: GamificationControllerApi;
  readonly reminders: ReminderPreferenceControllerApi;

  constructor() {
    const auth = inject(AuthService);
    const configuration = new Configuration({
      basePath: normalizeGeneratedApiBasePath(environment.apiBaseUrl),
      accessToken: () => auth.accessToken(),
      middleware: [generatedAuthorizationMiddleware(() => auth.accessToken())]
    });
    this.onboarding = new OnboardingControllerApi(configuration);
    this.catalog = new ExerciseCatalogControllerApi(configuration);
    this.planning = new TrainingPlanningControllerApi(configuration);
    this.planningV2 = new TrainingPlanningV2ControllerApi(configuration);
    this.planWorkflow = new PlanRevisionWorkflowControllerApi(configuration);
    this.specialistParticipants = new SpecialistRelationshipControllerApi(configuration);
    this.worklist = new SpecialistWorklistControllerApi(configuration);
    this.execution = new SessionExecutionControllerApi(configuration);
    this.attempts = new SessionExecutionAttemptControllerApi(configuration);
    this.today = new TodayAgendaControllerApi(configuration);
    this.safety = new ParticipantSafetyControllerApi(configuration);
    this.barriers = new BarrierReportControllerApi(configuration);
    this.gamification = new GamificationControllerApi(configuration);
    this.reminders = new ReminderPreferenceControllerApi(configuration);
  }
}
