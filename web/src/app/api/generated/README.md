# @moves/api-client@1.0.0

A TypeScript SDK client for the localhost API.

## Usage

First, install the SDK from npm.

```bash
npm install @moves/api-client --save
```

Next, try it out.


```ts
import {
  Configuration,
  AnatomyReferenceAdminControllerApi,
} from '@moves/api-client';
import type { AddRelationOperationRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new AnatomyReferenceAdminControllerApi(config);

  const body = {
    // AddRelationRequest
    addRelationRequest: ...,
  } satisfies AddRelationOperationRequest;

  try {
    const data = await api.addRelation(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```


## Documentation

### API Endpoints

All URIs are relative to *http://localhost*

| Class | Method | HTTP request | Description
| ----- | ------ | ------------ | -------------
*AnatomyReferenceAdminControllerApi* | [**addRelation**](docs/AnatomyReferenceAdminControllerApi.md#addrelationoperation) | **POST** /api/v1/admin/anatomical-structures/relations | Add an acyclic parent-child relation between draft structures
*AnatomyReferenceAdminControllerApi* | [**ancestors**](docs/AnatomyReferenceAdminControllerApi.md#ancestors) | **GET** /api/v1/admin/anatomical-structures/{structureId}/ancestors |
*AnatomyReferenceAdminControllerApi* | [**create1**](docs/AnatomyReferenceAdminControllerApi.md#create1) | **POST** /api/v1/admin/anatomical-structures | Create a draft anatomical structure
*AnatomyReferenceAdminControllerApi* | [**get2**](docs/AnatomyReferenceAdminControllerApi.md#get2) | **GET** /api/v1/admin/anatomical-structures/{structureId} |
*AnatomyReferenceAdminControllerApi* | [**publish2**](docs/AnatomyReferenceAdminControllerApi.md#publish2) | **POST** /api/v1/admin/anatomical-structures/{structureId}/publish | Publish an immutable anatomical structure
*AnatomyReferenceAdminControllerApi* | [**withdraw1**](docs/AnatomyReferenceAdminControllerApi.md#withdraw1) | **POST** /api/v1/admin/anatomical-structures/{structureId}/withdraw | Withdraw a published anatomical structure
*BarrierReportControllerApi* | [**report**](docs/BarrierReportControllerApi.md#report) | **POST** /api/v1/participant/barrier-reports | Report a session barrier and receive deterministic, safe options
*ConsentControllerApi* | [**grant**](docs/ConsentControllerApi.md#grant) | **POST** /api/v1/consent/grants |
*ConsentControllerApi* | [**revoke**](docs/ConsentControllerApi.md#revoke) | **POST** /api/v1/consent/grants/{grantId}/revoke |
*ConsentControllerApi* | [**template**](docs/ConsentControllerApi.md#template) | **POST** /api/v1/consent/templates |
*CurrentIdentityControllerApi* | [**current2**](docs/CurrentIdentityControllerApi.md#current2) | **GET** /api/v1/identity/me | Return the authenticated external identity
*ExerciseCatalogAdminControllerApi* | [**addContribution**](docs/ExerciseCatalogAdminControllerApi.md#addcontribution) | **POST** /api/v1/admin/exercises/versions/{versionId}/contributions |
*ExerciseCatalogAdminControllerApi* | [**addEvidence**](docs/ExerciseCatalogAdminControllerApi.md#addevidence) | **POST** /api/v1/admin/exercises/versions/{versionId}/evidence |
*ExerciseCatalogAdminControllerApi* | [**approve**](docs/ExerciseCatalogAdminControllerApi.md#approve) | **POST** /api/v1/admin/exercises/versions/{versionId}/approve |
*ExerciseCatalogAdminControllerApi* | [**create**](docs/ExerciseCatalogAdminControllerApi.md#createoperation) | **POST** /api/v1/admin/exercises |
*ExerciseCatalogAdminControllerApi* | [**createVersion**](docs/ExerciseCatalogAdminControllerApi.md#createversion) | **POST** /api/v1/admin/exercises/{exerciseId}/versions |
*ExerciseCatalogAdminControllerApi* | [**editor1**](docs/ExerciseCatalogAdminControllerApi.md#editor1) | **GET** /api/v1/admin/exercises/versions/{versionId}/editor |
*ExerciseCatalogAdminControllerApi* | [**legacyContraindications**](docs/ExerciseCatalogAdminControllerApi.md#legacycontraindications) | **GET** /api/v1/admin/exercises/legacy/contraindications |
*ExerciseCatalogAdminControllerApi* | [**publish**](docs/ExerciseCatalogAdminControllerApi.md#publish) | **POST** /api/v1/admin/exercises/versions/{versionId}/publish |
*ExerciseCatalogAdminControllerApi* | [**replaceLoadCharacteristics**](docs/ExerciseCatalogAdminControllerApi.md#replaceloadcharacteristics) | **PUT** /api/v1/admin/exercises/versions/{versionId}/load-characteristics |
*ExerciseCatalogAdminControllerApi* | [**requestChanges**](docs/ExerciseCatalogAdminControllerApi.md#requestchanges) | **POST** /api/v1/admin/exercises/versions/{versionId}/request-changes |
*ExerciseCatalogAdminControllerApi* | [**submitReview**](docs/ExerciseCatalogAdminControllerApi.md#submitreview) | **POST** /api/v1/admin/exercises/versions/{versionId}/submit-review |
*ExerciseCatalogAdminControllerApi* | [**update**](docs/ExerciseCatalogAdminControllerApi.md#update) | **PUT** /api/v1/admin/exercises/versions/{versionId} |
*ExerciseCatalogAdminControllerApi* | [**versions**](docs/ExerciseCatalogAdminControllerApi.md#versions) | **GET** /api/v1/admin/exercises/{exerciseId}/versions |
*ExerciseCatalogAdminControllerApi* | [**withdraw**](docs/ExerciseCatalogAdminControllerApi.md#withdraw) | **POST** /api/v1/admin/exercises/versions/{versionId}/withdraw |
*ExerciseCatalogControllerApi* | [**list**](docs/ExerciseCatalogControllerApi.md#list) | **GET** /api/v1/exercises | Search published exercise versions using explicitly allowed filters
*ExerciseCatalogControllerApi* | [**version**](docs/ExerciseCatalogControllerApi.md#version) | **GET** /api/v1/exercises/versions/{versionId} | Read a public detail projection of one published exercise version
*ExerciseImportAdminControllerApi* | [**batch**](docs/ExerciseImportAdminControllerApi.md#batch) | **GET** /api/v1/admin/exercise-import/batches/{id} |
*ExerciseImportAdminControllerApi* | [**createSource**](docs/ExerciseImportAdminControllerApi.md#createsource) | **POST** /api/v1/admin/exercise-import/sources |
*ExerciseImportAdminControllerApi* | [**draft**](docs/ExerciseImportAdminControllerApi.md#draft) | **POST** /api/v1/admin/exercise-import/records/{id}/create-draft |
*ExerciseImportAdminControllerApi* | [**issues**](docs/ExerciseImportAdminControllerApi.md#issues) | **GET** /api/v1/admin/exercise-import/batches/{id}/issues |
*ExerciseImportAdminControllerApi* | [**mapping**](docs/ExerciseImportAdminControllerApi.md#mapping) | **POST** /api/v1/admin/exercise-import/mappings/{id}/decision |
*ExerciseImportAdminControllerApi* | [**match**](docs/ExerciseImportAdminControllerApi.md#match) | **POST** /api/v1/admin/exercise-import/records/{id}/match |
*ExerciseImportAdminControllerApi* | [**record**](docs/ExerciseImportAdminControllerApi.md#record) | **GET** /api/v1/admin/exercise-import/records/{id} |
*ExerciseImportAdminControllerApi* | [**records**](docs/ExerciseImportAdminControllerApi.md#records) | **GET** /api/v1/admin/exercise-import/batches/{id}/records |
*ExerciseImportAdminControllerApi* | [**restart**](docs/ExerciseImportAdminControllerApi.md#restart) | **POST** /api/v1/admin/exercise-import/batches/{id}/restart |
*ExerciseImportAdminControllerApi* | [**sources**](docs/ExerciseImportAdminControllerApi.md#sources) | **GET** /api/v1/admin/exercise-import/sources |
*ExerciseImportAdminControllerApi* | [**upload**](docs/ExerciseImportAdminControllerApi.md#upload) | **POST** /api/v1/admin/exercise-import/batches |
*ExerciseVersionReviewControllerApi* | [**diff**](docs/ExerciseVersionReviewControllerApi.md#diff) | **GET** /api/v1/admin/exercise-versions/{id}/diff |
*ExerciseVersionReviewControllerApi* | [**publish1**](docs/ExerciseVersionReviewControllerApi.md#publish1) | **POST** /api/v1/admin/exercise-versions/{id}/publish |
*ExerciseVersionReviewControllerApi* | [**review**](docs/ExerciseVersionReviewControllerApi.md#review) | **POST** /api/v1/admin/exercise-versions/{id}/reviews |
*ExerciseVersionReviewControllerApi* | [**reviews**](docs/ExerciseVersionReviewControllerApi.md#reviews) | **GET** /api/v1/admin/exercise-versions/{id}/reviews |
*GamificationControllerApi* | [**gamificationProgress**](docs/GamificationControllerApi.md#gamificationprogress) | **GET** /api/v1/gamification/me | Return private points and a non-medical ledger view
*GamificationControllerApi* | [**profile**](docs/GamificationControllerApi.md#profile) | **PUT** /api/v1/gamification/me/profile | Enable, disable or configure the private gamification profile
*GamificationControllerApi* | [**publishRule**](docs/GamificationControllerApi.md#publishrule) | **POST** /api/v1/admin/gamification/rules | Publish an immutable point rule version
*GamificationControllerApi* | [**qualify**](docs/GamificationControllerApi.md#qualify) | **POST** /api/v1/gamification/executions/{executionId}/qualifications | Qualify a declared execution for points
*GamificationControllerApi* | [**ranking**](docs/GamificationControllerApi.md#ranking) | **GET** /api/v1/gamification/ranking | Return the opt-in pseudonymous ranking
*GamificationControllerApi* | [**rebuild**](docs/GamificationControllerApi.md#rebuild) | **POST** /api/v1/admin/gamification/ranking/rebuild | Rebuild the ranking projection from the point ledger
*GamificationControllerApi* | [**reverse**](docs/GamificationControllerApi.md#reverse) | **POST** /api/v1/admin/gamification/ledger/{entryId}/reversals | Append a point reversal without changing ledger history
*LoadAnalysisControllerApi* | [**preview**](docs/LoadAnalysisControllerApi.md#preview) | **GET** /api/v1/training-plans/revisions/{revisionId}/load-preview |
*OnboardingControllerApi* | [**availability**](docs/OnboardingControllerApi.md#availabilityoperation) | **PUT** /api/v1/onboarding/availability |
*OnboardingControllerApi* | [**legal**](docs/OnboardingControllerApi.md#legaloperation) | **PUT** /api/v1/onboarding/legal-acknowledgements |
*OnboardingControllerApi* | [**participantProfile**](docs/OnboardingControllerApi.md#participantprofileoperation) | **PUT** /api/v1/onboarding/participant-profile |
*OnboardingControllerApi* | [**selectProfileType**](docs/OnboardingControllerApi.md#selectprofiletype) | **PUT** /api/v1/onboarding/profile-type |
*OnboardingControllerApi* | [**specialistProfile**](docs/OnboardingControllerApi.md#specialistprofileoperation) | **PUT** /api/v1/onboarding/specialist-profile |
*OnboardingControllerApi* | [**state**](docs/OnboardingControllerApi.md#state) | **GET** /api/v1/onboarding | Return role-aware onboarding state
*ParticipantSafetyControllerApi* | [**checkIn**](docs/ParticipantSafetyControllerApi.md#checkinoperation) | **POST** /api/v1/safety/me/check-ins |
*ParticipantSafetyControllerApi* | [**current**](docs/ParticipantSafetyControllerApi.md#current) | **GET** /api/v1/safety/me | Return only the authenticated participant\&#39;s non-diagnostic safety inputs
*PlanCollaborationControllerApi* | [**addPlanCollaborator**](docs/PlanCollaborationControllerApi.md#addplancollaborator) | **POST** /api/v2/training-plans/{planId}/collaborators |
*PlanCollaborationControllerApi* | [**decidePlanReview**](docs/PlanCollaborationControllerApi.md#decideplanreview) | **POST** /api/v2/training-plans/reviews/{reviewId}/decision |
*PlanCollaborationControllerApi* | [**endPlanCollaboration**](docs/PlanCollaborationControllerApi.md#endplancollaboration) | **DELETE** /api/v2/training-plans/{planId}/collaborators/{collaboratorId} |
*PlanCollaborationControllerApi* | [**requestPlanReview**](docs/PlanCollaborationControllerApi.md#requestplanreview) | **POST** /api/v2/training-plans/revisions/{revisionId}/reviews |
*PlanRevisionWorkflowControllerApi* | [**acknowledge**](docs/PlanRevisionWorkflowControllerApi.md#acknowledge) | **POST** /api/v2/training-plans/revisions/{revisionId}/workflow/warning-acknowledgements | Acknowledge warning factors from the current assessment
*PlanRevisionWorkflowControllerApi* | [**activate**](docs/PlanRevisionWorkflowControllerApi.md#activate) | **POST** /api/v2/training-plans/revisions/{revisionId}/workflow/activation | Activate a validated plan revision
*PlanRevisionWorkflowControllerApi* | [**status**](docs/PlanRevisionWorkflowControllerApi.md#status) | **POST** /api/v2/training-plans/revisions/{revisionId}/workflow/status | Read plan revision workflow status and current assessment
*PlanRevisionWorkflowControllerApi* | [**validate**](docs/PlanRevisionWorkflowControllerApi.md#validate) | **POST** /api/v2/training-plans/revisions/{revisionId}/workflow/validation | Validate load and safety for a plan revision
*RecoveryEpisodeControllerApi* | [**choose**](docs/RecoveryEpisodeControllerApi.md#choose) | **POST** /api/v1/participant/recovery-episodes/{episodeId}/choices |
*RecoveryEpisodeControllerApi* | [**current1**](docs/RecoveryEpisodeControllerApi.md#current1) | **GET** /api/v1/participant/recovery-episodes/current |
*ReminderPreferenceControllerApi* | [**get**](docs/ReminderPreferenceControllerApi.md#get) | **GET** /api/v1/participant/reminder-preferences |
*ReminderPreferenceControllerApi* | [**save**](docs/ReminderPreferenceControllerApi.md#save) | **PUT** /api/v1/participant/reminder-preferences |
*SafetyV2ControllerApi* | [**clinicalRestriction**](docs/SafetyV2ControllerApi.md#clinicalrestriction) | **POST** /api/v2/safety/participants/{participantId}/restrictions |
*SafetyV2ControllerApi* | [**clinicalRestrictions**](docs/SafetyV2ControllerApi.md#clinicalrestrictions) | **GET** /api/v2/safety/participants/{participantId}/clinical-restrictions |
*SafetyV2ControllerApi* | [**declare**](docs/SafetyV2ControllerApi.md#declare) | **POST** /api/v2/safety/me/restrictions |
*SafetyV2ControllerApi* | [**effectiveRestrictions**](docs/SafetyV2ControllerApi.md#effectiverestrictions) | **GET** /api/v2/safety/participants/{participantId}/effective-restrictions |
*SafetyV2ControllerApi* | [**history1**](docs/SafetyV2ControllerApi.md#history1) | **GET** /api/v2/safety/me/restrictions/history |
*SafetyV2ControllerApi* | [**legacyReport**](docs/SafetyV2ControllerApi.md#legacyreport) | **GET** /api/v2/safety/admin/legacy/participant-restrictions |
*SafetyV2ControllerApi* | [**override**](docs/SafetyV2ControllerApi.md#override) | **POST** /api/v2/safety/participants/{participantId}/assessments/{assessmentId}/factors/{factorId}/overrides |
*SafetyV2ControllerApi* | [**revise**](docs/SafetyV2ControllerApi.md#revise) | **PATCH** /api/v2/safety/me/restrictions/{restrictionId} |
*SafetyV2ControllerApi* | [**reviseClinicalRestriction**](docs/SafetyV2ControllerApi.md#reviseclinicalrestriction) | **PATCH** /api/v2/safety/participants/{participantId}/restrictions/{restrictionId} |
*SafetyV2ControllerApi* | [**withdraw2**](docs/SafetyV2ControllerApi.md#withdraw2) | **DELETE** /api/v2/safety/me/restrictions/{restrictionId} |
*SessionExecutionAttemptControllerApi* | [**abandon**](docs/SessionExecutionAttemptControllerApi.md#abandon) | **POST** /api/v1/participant/session-attempts/{attemptId}/abandon |
*SessionExecutionAttemptControllerApi* | [**complete**](docs/SessionExecutionAttemptControllerApi.md#complete) | **POST** /api/v1/participant/session-attempts/{attemptId}/complete |
*SessionExecutionAttemptControllerApi* | [**get1**](docs/SessionExecutionAttemptControllerApi.md#get1) | **GET** /api/v1/participant/session-attempts/{attemptId} |
*SessionExecutionAttemptControllerApi* | [**pause**](docs/SessionExecutionAttemptControllerApi.md#pause) | **POST** /api/v1/participant/session-attempts/{attemptId}/pause |
*SessionExecutionAttemptControllerApi* | [**progress**](docs/SessionExecutionAttemptControllerApi.md#progress) | **PUT** /api/v1/participant/session-attempts/{attemptId}/progress |
*SessionExecutionAttemptControllerApi* | [**resume**](docs/SessionExecutionAttemptControllerApi.md#resume) | **POST** /api/v1/participant/session-attempts/{attemptId}/resume |
*SessionExecutionAttemptControllerApi* | [**start**](docs/SessionExecutionAttemptControllerApi.md#start) | **POST** /api/v1/participant/session-attempts | Start or return the participant\&#39;s active session attempt
*SessionExecutionControllerApi* | [**correct**](docs/SessionExecutionControllerApi.md#correct) | **POST** /api/v1/session-executions/{executionId}/corrections | Append an audited correction without changing execution history
*SessionExecutionControllerApi* | [**declare1**](docs/SessionExecutionControllerApi.md#declare1) | **POST** /api/v1/planned-sessions/{sessionId}/executions | Declare completion of an assigned planned session
*SessionExecutionControllerApi* | [**post24h**](docs/SessionExecutionControllerApi.md#post24h) | **POST** /api/v1/session-executions/{executionId}/post-24h-responses | Append an idempotent post-24h session response
*SessionExecutionControllerApi* | [**specialistExecutions**](docs/SessionExecutionControllerApi.md#specialistexecutions) | **GET** /api/v1/specialist/participants/{participantAccountId}/executions | List executions and alerts for a participant with an active relationship
*SessionExecutionControllerApi* | [**transitionAlert**](docs/SessionExecutionControllerApi.md#transitionalert) | **POST** /api/v1/session-executions/{executionId}/alerts/{alertId}/transitions | Acknowledge, resolve or reopen an execution safety alert
*SpecialistRelationshipControllerApi* | [**activeParticipants**](docs/SpecialistRelationshipControllerApi.md#activeparticipants) | **GET** /api/v1/specialist/participants | List participants with an active specialist relationship for UI selection
*SpecialistWorklistControllerApi* | [**actOnWorklist**](docs/SpecialistWorklistControllerApi.md#actonworklist) | **POST** /api/v1/specialist/worklist/{itemId}/actions |
*SpecialistWorklistControllerApi* | [**listWorklist**](docs/SpecialistWorklistControllerApi.md#listworklist) | **GET** /api/v1/specialist/worklist |
*SpecialistWorklistControllerApi* | [**replyToIssue**](docs/SpecialistWorklistControllerApi.md#replytoissue) | **POST** /api/v1/specialist/worklist/{itemId}/reply |
*SpecialistWorklistControllerApi* | [**reportParticipantIssue**](docs/SpecialistWorklistControllerApi.md#reportparticipantissue) | **POST** /api/v1/participant/issues |
*TodayAgendaControllerApi* | [**today**](docs/TodayAgendaControllerApi.md#today) | **GET** /api/v1/participant/today | Get the signed-in participant\&#39;s daily training agenda
*TrainingPlanningControllerApi* | [**createLegacyTrainingPlan**](docs/TrainingPlanningControllerApi.md#createlegacytrainingplan) | **POST** /api/v1/training-plans | Deprecated V1 plan creation endpoint
*TrainingPlanningControllerApi* | [**sessions**](docs/TrainingPlanningControllerApi.md#sessions) | **GET** /api/v1/planned-sessions | List planned sessions assigned to the current participant
*TrainingPlanningV2ControllerApi* | [**addCycle**](docs/TrainingPlanningV2ControllerApi.md#addcycle) | **POST** /api/v2/training-plans/revisions/{revisionId}/cycles |
*TrainingPlanningV2ControllerApi* | [**addGoal**](docs/TrainingPlanningV2ControllerApi.md#addgoal) | **POST** /api/v2/training-plans/revisions/{revisionId}/goals |
*TrainingPlanningV2ControllerApi* | [**addLoadBudget**](docs/TrainingPlanningV2ControllerApi.md#addloadbudget) | **POST** /api/v2/training-plans/revisions/{revisionId}/load-budgets |
*TrainingPlanningV2ControllerApi* | [**addMicrocycle**](docs/TrainingPlanningV2ControllerApi.md#addmicrocycle) | **POST** /api/v2/training-plans/revisions/{revisionId}/microcycles |
*TrainingPlanningV2ControllerApi* | [**addPrescription**](docs/TrainingPlanningV2ControllerApi.md#addprescription) | **POST** /api/v2/training-plans/revisions/{revisionId}/prescriptions |
*TrainingPlanningV2ControllerApi* | [**addSession**](docs/TrainingPlanningV2ControllerApi.md#addsession) | **POST** /api/v2/training-plans/revisions/{revisionId}/sessions |
*TrainingPlanningV2ControllerApi* | [**createDraft**](docs/TrainingPlanningV2ControllerApi.md#createdraft) | **POST** /api/v2/training-plans | Create an inactive training plan draft
*TrainingPlanningV2ControllerApi* | [**createRevision**](docs/TrainingPlanningV2ControllerApi.md#createrevision) | **POST** /api/v2/training-plans/{planId}/revisions |
*TrainingPlanningV2ControllerApi* | [**defineSessionVariant**](docs/TrainingPlanningV2ControllerApi.md#definesessionvariant) | **POST** /api/v2/training-plans/revisions/{revisionId}/session-variants |
*TrainingPlanningV2ControllerApi* | [**editor**](docs/TrainingPlanningV2ControllerApi.md#editor) | **GET** /api/v2/training-plans/revisions/{revisionId} |
*TrainingPlanningV2ControllerApi* | [**history**](docs/TrainingPlanningV2ControllerApi.md#history) | **GET** /api/v2/training-plans/{planId}/revisions |
*TrainingPlanningV2ControllerApi* | [**reorder**](docs/TrainingPlanningV2ControllerApi.md#reorder) | **PUT** /api/v2/training-plans/revisions/{revisionId}/prescriptions/order |
*TrainingPlanningV2ControllerApi* | [**validateStructurally**](docs/TrainingPlanningV2ControllerApi.md#validatestructurally) | **POST** /api/v2/training-plans/revisions/{revisionId}/structural-validation |


### Models

- [AbandonAttemptCommand](docs/AbandonAttemptCommand.md)
- [AcknowledgeWarningCommand](docs/AcknowledgeWarningCommand.md)
- [AcknowledgementView](docs/AcknowledgementView.md)
- [ActingContext](docs/ActingContext.md)
- [ActionCommand](docs/ActionCommand.md)
- [ActivateWorkflowCommand](docs/ActivateWorkflowCommand.md)
- [ActivationOutcome](docs/ActivationOutcome.md)
- [ActiveParticipantView](docs/ActiveParticipantView.md)
- [ActivePlanView](docs/ActivePlanView.md)
- [AddCycleCommand](docs/AddCycleCommand.md)
- [AddGoalCommand](docs/AddGoalCommand.md)
- [AddLoadBudgetCommand](docs/AddLoadBudgetCommand.md)
- [AddMicrocycleCommand](docs/AddMicrocycleCommand.md)
- [AddPrescriptionCommand](docs/AddPrescriptionCommand.md)
- [AddRelationRequest](docs/AddRelationRequest.md)
- [AddSessionCommand](docs/AddSessionCommand.md)
- [AgendaSessionView](docs/AgendaSessionView.md)
- [Aggregate](docs/Aggregate.md)
- [AlertData](docs/AlertData.md)
- [AlertTransitionCommand](docs/AlertTransitionCommand.md)
- [AnatomicalStructureSnapshot](docs/AnatomicalStructureSnapshot.md)
- [AncestorPath](docs/AncestorPath.md)
- [AncestorStep](docs/AncestorStep.md)
- [ArtifactView](docs/ArtifactView.md)
- [AssessmentSnapshot](docs/AssessmentSnapshot.md)
- [AttemptDetailView](docs/AttemptDetailView.md)
- [AttemptView](docs/AttemptView.md)
- [AvailabilityRequest](docs/AvailabilityRequest.md)
- [BarrierReportCommand](docs/BarrierReportCommand.md)
- [BarrierReportView](docs/BarrierReportView.md)
- [BatchView](docs/BatchView.md)
- [CandidateView](docs/CandidateView.md)
- [CatalogItem](docs/CatalogItem.md)
- [CatalogPage](docs/CatalogPage.md)
- [CheckInRequest](docs/CheckInRequest.md)
- [CheckInView](docs/CheckInView.md)
- [ClinicalRestrictionView](docs/ClinicalRestrictionView.md)
- [CollaboratorCommand](docs/CollaboratorCommand.md)
- [CollaboratorView](docs/CollaboratorView.md)
- [ContributionCommand](docs/ContributionCommand.md)
- [ContributionView](docs/ContributionView.md)
- [CorrectionCommand](docs/CorrectionCommand.md)
- [CorrectionView](docs/CorrectionView.md)
- [CreateDraftCommand](docs/CreateDraftCommand.md)
- [CreatePlanCommand](docs/CreatePlanCommand.md)
- [CreateRequest](docs/CreateRequest.md)
- [CreateRevisionCommand](docs/CreateRevisionCommand.md)
- [CreateSource](docs/CreateSource.md)
- [CreateStructureRequest](docs/CreateStructureRequest.md)
- [CycleSnapshot](docs/CycleSnapshot.md)
- [DeclareExecutionCommand](docs/DeclareExecutionCommand.md)
- [DefineSessionVariantCommand](docs/DefineSessionVariantCommand.md)
- [EditorView](docs/EditorView.md)
- [EffectiveRestrictionView](docs/EffectiveRestrictionView.md)
- [EvidenceCommand](docs/EvidenceCommand.md)
- [EvidenceView](docs/EvidenceView.md)
- [ExecutionView](docs/ExecutionView.md)
- [ExerciseCatalogDetailView](docs/ExerciseCatalogDetailView.md)
- [ExercisePrescription](docs/ExercisePrescription.md)
- [ExerciseVersionPublishRequest](docs/ExerciseVersionPublishRequest.md)
- [FactorSnapshot](docs/FactorSnapshot.md)
- [GamificationProgressView](docs/GamificationProgressView.md)
- [GoalOutcomeSnapshot](docs/GoalOutcomeSnapshot.md)
- [GoalSnapshot](docs/GoalSnapshot.md)
- [GrantCommand](docs/GrantCommand.md)
- [GrantView](docs/GrantView.md)
- [IdentityResponse](docs/IdentityResponse.md)
- [IssueView](docs/IssueView.md)
- [JsonNode](docs/JsonNode.md)
- [LedgerView](docs/LedgerView.md)
- [LegacyContraindicationReportItem](docs/LegacyContraindicationReportItem.md)
- [LegacyReport](docs/LegacyReport.md)
- [LegalRequest](docs/LegalRequest.md)
- [LoadBudgetSnapshot](docs/LoadBudgetSnapshot.md)
- [LoadCharacteristicCommand](docs/LoadCharacteristicCommand.md)
- [LoadProfile](docs/LoadProfile.md)
- [MappingDecision](docs/MappingDecision.md)
- [MappingView](docs/MappingView.md)
- [MatchDecision](docs/MatchDecision.md)
- [Microcycle](docs/Microcycle.md)
- [MicrocycleSnapshot](docs/MicrocycleSnapshot.md)
- [Observation](docs/Observation.md)
- [OutcomeCommand](docs/OutcomeCommand.md)
- [OverrideCommand](docs/OverrideCommand.md)
- [OverrideView](docs/OverrideView.md)
- [ParticipantIssueCommand](docs/ParticipantIssueCommand.md)
- [ParticipantProfileRequest](docs/ParticipantProfileRequest.md)
- [PlanBundle](docs/PlanBundle.md)
- [PlanRevisionSnapshot](docs/PlanRevisionSnapshot.md)
- [PlannedSession](docs/PlannedSession.md)
- [Post24hCommand](docs/Post24hCommand.md)
- [Post24hData](docs/Post24hData.md)
- [PreferenceCommand](docs/PreferenceCommand.md)
- [PreferenceView](docs/PreferenceView.md)
- [PrescriptionCommand](docs/PrescriptionCommand.md)
- [PrescriptionSnapshot](docs/PrescriptionSnapshot.md)
- [PrescriptionView](docs/PrescriptionView.md)
- [ProfileCommand](docs/ProfileCommand.md)
- [ProfileSummary](docs/ProfileSummary.md)
- [ProfileTypeRequest](docs/ProfileTypeRequest.md)
- [ProfileView](docs/ProfileView.md)
- [ProgressCommand](docs/ProgressCommand.md)
- [ProgressView](docs/ProgressView.md)
- [PublicAnatomyContributionView](docs/PublicAnatomyContributionView.md)
- [PublicEvidenceView](docs/PublicEvidenceView.md)
- [PublicLoadCharacteristicView](docs/PublicLoadCharacteristicView.md)
- [PublicationResult](docs/PublicationResult.md)
- [QualificationView](docs/QualificationView.md)
- [RankingRow](docs/RankingRow.md)
- [RecordDetail](docs/RecordDetail.md)
- [RecordPage](docs/RecordPage.md)
- [RecordSummary](docs/RecordSummary.md)
- [RecoveryChoiceCommand](docs/RecoveryChoiceCommand.md)
- [RecoveryView](docs/RecoveryView.md)
- [RelationSnapshot](docs/RelationSnapshot.md)
- [ReorderCommand](docs/ReorderCommand.md)
- [ReplyCommand](docs/ReplyCommand.md)
- [ReplyView](docs/ReplyView.md)
- [RestrictionCommand](docs/RestrictionCommand.md)
- [RestrictionView](docs/RestrictionView.md)
- [ResultCommand](docs/ResultCommand.md)
- [ResultView](docs/ResultView.md)
- [ReversalCommand](docs/ReversalCommand.md)
- [ReviewCommand](docs/ReviewCommand.md)
- [ReviewDecisionCommand](docs/ReviewDecisionCommand.md)
- [ReviewItem](docs/ReviewItem.md)
- [ReviewRequestCommand](docs/ReviewRequestCommand.md)
- [ReviewResult](docs/ReviewResult.md)
- [ReviewView](docs/ReviewView.md)
- [RevisionHistoryItem](docs/RevisionHistoryItem.md)
- [RuleCommand](docs/RuleCommand.md)
- [RuleView](docs/RuleView.md)
- [SafetyView](docs/SafetyView.md)
- [SessionSnapshot](docs/SessionSnapshot.md)
- [SessionVariantItemSnapshot](docs/SessionVariantItemSnapshot.md)
- [SessionVariantSnapshot](docs/SessionVariantSnapshot.md)
- [SessionView](docs/SessionView.md)
- [Slot](docs/Slot.md)
- [SlotRequest](docs/SlotRequest.md)
- [SourceView](docs/SourceView.md)
- [SpecialistProfileRequest](docs/SpecialistProfileRequest.md)
- [StartAttemptCommand](docs/StartAttemptCommand.md)
- [State](docs/State.md)
- [StructuralValidationView](docs/StructuralValidationView.md)
- [TargetCommand](docs/TargetCommand.md)
- [TargetView](docs/TargetView.md)
- [TemplateCommand](docs/TemplateCommand.md)
- [TemplateView](docs/TemplateView.md)
- [TodayAgendaView](docs/TodayAgendaView.md)
- [TrainingCycle](docs/TrainingCycle.md)
- [TrainingGoal](docs/TrainingGoal.md)
- [TrainingPlan](docs/TrainingPlan.md)
- [UploadAccepted](docs/UploadAccepted.md)
- [ValidateCommand](docs/ValidateCommand.md)
- [ValidateWorkflowCommand](docs/ValidateWorkflowCommand.md)
- [ValidationView](docs/ValidationView.md)
- [VariantItemCommand](docs/VariantItemCommand.md)
- [VersionCommand](docs/VersionCommand.md)
- [VersionDiff](docs/VersionDiff.md)
- [VersionView](docs/VersionView.md)
- [View](docs/View.md)
- [WorkflowState](docs/WorkflowState.md)
- [WorkflowView](docs/WorkflowView.md)
- [WorklistItemView](docs/WorklistItemView.md)

### Authorization


Authentication schemes defined for the API:
<a id="oidc"></a>
#### oidc


## About

This TypeScript SDK client supports the [Fetch API](https://fetch.spec.whatwg.org/)
and is automatically generated by the
[OpenAPI Generator](https://openapi-generator.tech) project:

- API version: `v1`
- Package version: `1.0.0`
- Generator version: `7.24.0`
- Build package: `org.openapitools.codegen.languages.TypeScriptFetchClientCodegen`

The generated npm module supports the following:

- Environments
  * Node.js
  * Webpack
  * Browserify
- Language levels
  * ES5 - you must have a Promises/A+ library installed
  * ES6
- Module systems
  * CommonJS
  * ES6 module system


## Development

### Building

To build the TypeScript source code, you need to have Node.js and npm installed.
After cloning the repository, navigate to the project directory and run:

```bash
npm install
npm run build
```

### Publishing

Once you've built the package, you can publish it to npm:

```bash
npm publish
```

## License

[]()
