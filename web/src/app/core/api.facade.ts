import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  BarrierReportControllerApi,
  Configuration,
  ExerciseCatalogControllerApi,
  GamificationControllerApi,
  OnboardingControllerApi,
  ParticipantSafetyControllerApi,
  SessionExecutionAttemptControllerApi,
  SessionExecutionControllerApi,
  TodayAgendaControllerApi,
  TrainingPlanningControllerApi
} from '../api/generated/src';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class ApiFacade {
  readonly onboarding: OnboardingControllerApi;
  readonly catalog: ExerciseCatalogControllerApi;
  readonly planning: TrainingPlanningControllerApi;
  readonly execution: SessionExecutionControllerApi;
  readonly attempts: SessionExecutionAttemptControllerApi;
  readonly today: TodayAgendaControllerApi;
  readonly safety: ParticipantSafetyControllerApi;
  readonly barriers: BarrierReportControllerApi;
  readonly gamification: GamificationControllerApi;

  constructor() {
    const auth = inject(AuthService);
    const configuration = new Configuration({
      basePath: environment.apiBaseUrl,
      accessToken: () => auth.accessToken()
    });
    this.onboarding = new OnboardingControllerApi(configuration);
    this.catalog = new ExerciseCatalogControllerApi(configuration);
    this.planning = new TrainingPlanningControllerApi(configuration);
    this.execution = new SessionExecutionControllerApi(configuration);
    this.attempts = new SessionExecutionAttemptControllerApi(configuration);
    this.today = new TodayAgendaControllerApi(configuration);
    this.safety = new ParticipantSafetyControllerApi(configuration);
    this.barriers = new BarrierReportControllerApi(configuration);
    this.gamification = new GamificationControllerApi(configuration);
  }
}
