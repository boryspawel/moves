import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  BarrierReportControllerApi,
  Configuration,
  ExerciseCatalogControllerApi,
  ExerciseCatalogAdminControllerApi,
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
  PlanRevisionWorkflowControllerApi,
  SpecialistParticipantReadControllerApi,
  AppointmentControllerApi,
  SpecialistTodayControllerApi,
  SpecialistAvailableSlotsControllerApi,
  SpecialistClientControllerApi,
  ExerciseSetControllerApi,
  ExerciseCatalogSearchControllerApi,
  AnatomyReferenceControllerApi,
  ParticipantGoalControllerApi,
  ParticipantDocumentationControllerApi,
  SpecialistPlanFacadeControllerApi, ParticipantExerciseSetControllerApi, ParticipantPlanFacadeControllerApi, PracticalPlanResourceControllerApi
  ,ParticipantAccessInvitationControllerApi, ParticipantSelfGoalControllerApi, ParticipantExecutionHistoryControllerApi
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
  readonly catalogAdmin: ExerciseCatalogAdminControllerApi;
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
  /** Read-only bounded client workspace; generated client remains unmodified. */
  readonly participantWorkspace: SpecialistParticipantReadControllerApi;
  readonly appointments: AppointmentControllerApi;
  readonly specialistToday: SpecialistTodayControllerApi;
  readonly specialistAvailableSlots: SpecialistAvailableSlotsControllerApi;
  readonly specialistClients: SpecialistClientControllerApi;
  readonly exerciseSets: ExerciseSetControllerApi;
  readonly catalogSearch: ExerciseCatalogSearchControllerApi;
  readonly anatomyReference: AnatomyReferenceControllerApi;
  readonly participantGoals: ParticipantGoalControllerApi;
  readonly participantDocumentation: ParticipantDocumentationControllerApi;
  /** Participant-scoped specialist planning façade; hierarchy stays server-side. */
  readonly specialistPlans: SpecialistPlanFacadeControllerApi;
  readonly participantAccess: ParticipantAccessInvitationControllerApi;
  readonly ownGoals: ParticipantSelfGoalControllerApi;
  readonly ownExecutionHistory: ParticipantExecutionHistoryControllerApi;
  readonly participantExerciseSets: ParticipantExerciseSetControllerApi;
  /** Own-plan entry points; no specialist acting context is added here. */
  readonly participantPlans: ParticipantPlanFacadeControllerApi;
  readonly practicalPlans: PracticalPlanResourceControllerApi;

  constructor() {
    const auth = inject(AuthService);
    const configuration = new Configuration({
      basePath: normalizeGeneratedApiBasePath(environment.apiBaseUrl),
      accessToken: () => auth.accessToken(),
      middleware: [generatedAuthorizationMiddleware(() => auth.accessToken())]
    });
    this.onboarding = new OnboardingControllerApi(configuration);
    this.catalog = new ExerciseCatalogControllerApi(configuration);
    this.catalogAdmin = new ExerciseCatalogAdminControllerApi(configuration);
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
    this.participantWorkspace = new SpecialistParticipantReadControllerApi(configuration);
    this.appointments = new AppointmentControllerApi(configuration);
    this.specialistToday = new SpecialistTodayControllerApi(configuration);
    this.specialistAvailableSlots = new SpecialistAvailableSlotsControllerApi(configuration);
    this.specialistClients = new SpecialistClientControllerApi(configuration);
    this.exerciseSets = new ExerciseSetControllerApi(configuration);
    this.catalogSearch = new ExerciseCatalogSearchControllerApi(configuration);
    this.anatomyReference = new AnatomyReferenceControllerApi(configuration);
    this.participantGoals = new ParticipantGoalControllerApi(configuration);
    this.participantDocumentation = new ParticipantDocumentationControllerApi(configuration);
    this.specialistPlans = new SpecialistPlanFacadeControllerApi(configuration);
    this.participantAccess = new ParticipantAccessInvitationControllerApi(configuration);
    this.ownGoals = new ParticipantSelfGoalControllerApi(configuration);
    this.ownExecutionHistory = new ParticipantExecutionHistoryControllerApi(configuration);
    this.participantExerciseSets = new ParticipantExerciseSetControllerApi(configuration);
    this.participantPlans = new ParticipantPlanFacadeControllerApi(configuration);
    this.practicalPlans = new PracticalPlanResourceControllerApi(configuration);
  }
}
