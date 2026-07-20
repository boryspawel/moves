import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  Configuration,
  ExerciseCatalogControllerApi,
  GamificationControllerApi,
  OnboardingControllerApi,
  SessionExecutionControllerApi,
  TrainingPlanningControllerApi
} from '../api/generated/src';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class ApiFacade {
  readonly onboarding: OnboardingControllerApi;
  readonly catalog: ExerciseCatalogControllerApi;
  readonly planning: TrainingPlanningControllerApi;
  readonly execution: SessionExecutionControllerApi;
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
    this.gamification = new GamificationControllerApi(configuration);
  }
}
