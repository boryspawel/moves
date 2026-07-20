# @moves/api-client@v1

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
  CurrentIdentityControllerApi,
} from '@moves/api-client';
import type { Current1Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new CurrentIdentityControllerApi(config);

  try {
    const data = await api.current1();
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
*CurrentIdentityControllerApi* | [**current1**](docs/CurrentIdentityControllerApi.md#current1) | **GET** /api/v1/identity/me | Return the authenticated external identity
*ExerciseCatalogAdminControllerApi* | [**create1**](docs/ExerciseCatalogAdminControllerApi.md#create1) | **POST** /api/v1/admin/exercises |
*ExerciseCatalogAdminControllerApi* | [**createVersion**](docs/ExerciseCatalogAdminControllerApi.md#createversion) | **POST** /api/v1/admin/exercises/{exerciseId}/versions |
*ExerciseCatalogAdminControllerApi* | [**publish**](docs/ExerciseCatalogAdminControllerApi.md#publish) | **POST** /api/v1/admin/exercises/versions/{versionId}/publish |
*ExerciseCatalogAdminControllerApi* | [**update**](docs/ExerciseCatalogAdminControllerApi.md#update) | **PUT** /api/v1/admin/exercises/versions/{versionId} |
*ExerciseCatalogAdminControllerApi* | [**versions**](docs/ExerciseCatalogAdminControllerApi.md#versions) | **GET** /api/v1/admin/exercises/{exerciseId}/versions |
*ExerciseCatalogAdminControllerApi* | [**withdraw**](docs/ExerciseCatalogAdminControllerApi.md#withdraw) | **POST** /api/v1/admin/exercises/versions/{versionId}/withdraw |
*ExerciseCatalogControllerApi* | [**list**](docs/ExerciseCatalogControllerApi.md#list) | **GET** /api/v1/exercises | Search published exercise versions using explicitly allowed filters
*ExerciseCatalogControllerApi* | [**version**](docs/ExerciseCatalogControllerApi.md#version) | **GET** /api/v1/exercises/versions/{versionId} |
*GamificationControllerApi* | [**profile**](docs/GamificationControllerApi.md#profile) | **PUT** /api/v1/gamification/me/profile | Enable, disable or configure the private gamification profile
*GamificationControllerApi* | [**progress**](docs/GamificationControllerApi.md#progress) | **GET** /api/v1/gamification/me | Return private points and a non-medical ledger view
*GamificationControllerApi* | [**publishRule**](docs/GamificationControllerApi.md#publishrule) | **POST** /api/v1/admin/gamification/rules | Publish an immutable point rule version
*GamificationControllerApi* | [**qualify**](docs/GamificationControllerApi.md#qualify) | **POST** /api/v1/gamification/executions/{executionId}/qualifications | Qualify a declared execution for points
*GamificationControllerApi* | [**ranking**](docs/GamificationControllerApi.md#ranking) | **GET** /api/v1/gamification/ranking | Return the opt-in pseudonymous ranking
*GamificationControllerApi* | [**rebuild**](docs/GamificationControllerApi.md#rebuild) | **POST** /api/v1/admin/gamification/ranking/rebuild | Rebuild the ranking projection from the point ledger
*GamificationControllerApi* | [**reverse**](docs/GamificationControllerApi.md#reverse) | **POST** /api/v1/admin/gamification/ledger/{entryId}/reversals | Append a point reversal without changing ledger history
*OnboardingControllerApi* | [**availability**](docs/OnboardingControllerApi.md#availabilityoperation) | **PUT** /api/v1/onboarding/availability |
*OnboardingControllerApi* | [**legal**](docs/OnboardingControllerApi.md#legaloperation) | **PUT** /api/v1/onboarding/legal-acknowledgements |
*OnboardingControllerApi* | [**participantProfile**](docs/OnboardingControllerApi.md#participantprofileoperation) | **PUT** /api/v1/onboarding/participant-profile |
*OnboardingControllerApi* | [**selectProfileType**](docs/OnboardingControllerApi.md#selectprofiletype) | **PUT** /api/v1/onboarding/profile-type |
*OnboardingControllerApi* | [**specialistProfile**](docs/OnboardingControllerApi.md#specialistprofileoperation) | **PUT** /api/v1/onboarding/specialist-profile |
*OnboardingControllerApi* | [**state**](docs/OnboardingControllerApi.md#state) | **GET** /api/v1/onboarding | Return role-aware onboarding state
*ParticipantSafetyControllerApi* | [**checkIn**](docs/ParticipantSafetyControllerApi.md#checkinoperation) | **POST** /api/v1/safety/me/check-ins |
*ParticipantSafetyControllerApi* | [**current**](docs/ParticipantSafetyControllerApi.md#current) | **GET** /api/v1/safety/me | Return only the authenticated participant\&#39;s non-diagnostic safety inputs
*ParticipantSafetyControllerApi* | [**restrictions**](docs/ParticipantSafetyControllerApi.md#restrictions) | **PUT** /api/v1/safety/me/restrictions |
*SessionExecutionControllerApi* | [**correct**](docs/SessionExecutionControllerApi.md#correct) | **POST** /api/v1/session-executions/{executionId}/corrections | Append an audited correction without changing execution history
*SessionExecutionControllerApi* | [**declare**](docs/SessionExecutionControllerApi.md#declare) | **POST** /api/v1/planned-sessions/{sessionId}/executions | Declare completion of an assigned planned session
*SessionExecutionControllerApi* | [**specialistExecutions**](docs/SessionExecutionControllerApi.md#specialistexecutions) | **GET** /api/v1/specialist/participants/{participantAccountId}/executions | List executions and alerts for a participant with an active relationship
*TrainingPlanningControllerApi* | [**create**](docs/TrainingPlanningControllerApi.md#create) | **POST** /api/v1/training-plans | Create and assign a simple specialist-authored training plan
*TrainingPlanningControllerApi* | [**sessions**](docs/TrainingPlanningControllerApi.md#sessions) | **GET** /api/v1/planned-sessions | List planned sessions assigned to the current participant


### Models

- [AvailabilityRequest](docs/AvailabilityRequest.md)
- [CheckInRequest](docs/CheckInRequest.md)
- [CheckInView](docs/CheckInView.md)
- [CorrectionCommand](docs/CorrectionCommand.md)
- [CorrectionView](docs/CorrectionView.md)
- [CreatePlanCommand](docs/CreatePlanCommand.md)
- [CreateRequest](docs/CreateRequest.md)
- [DeclareExecutionCommand](docs/DeclareExecutionCommand.md)
- [ExecutionView](docs/ExecutionView.md)
- [ExercisePrescription](docs/ExercisePrescription.md)
- [IdentityResponse](docs/IdentityResponse.md)
- [LedgerView](docs/LedgerView.md)
- [LegalRequest](docs/LegalRequest.md)
- [Microcycle](docs/Microcycle.md)
- [ParticipantProfileRequest](docs/ParticipantProfileRequest.md)
- [PlanBundle](docs/PlanBundle.md)
- [PlannedSession](docs/PlannedSession.md)
- [PrescriptionCommand](docs/PrescriptionCommand.md)
- [PrescriptionView](docs/PrescriptionView.md)
- [ProfileCommand](docs/ProfileCommand.md)
- [ProfileSummary](docs/ProfileSummary.md)
- [ProfileTypeRequest](docs/ProfileTypeRequest.md)
- [ProfileView](docs/ProfileView.md)
- [ProgressView](docs/ProgressView.md)
- [QualificationView](docs/QualificationView.md)
- [RankingRow](docs/RankingRow.md)
- [RestrictionRequest](docs/RestrictionRequest.md)
- [ResultCommand](docs/ResultCommand.md)
- [ResultView](docs/ResultView.md)
- [ReversalCommand](docs/ReversalCommand.md)
- [RuleCommand](docs/RuleCommand.md)
- [RuleView](docs/RuleView.md)
- [SafetyView](docs/SafetyView.md)
- [SessionView](docs/SessionView.md)
- [Slot](docs/Slot.md)
- [SlotRequest](docs/SlotRequest.md)
- [SpecialistProfileRequest](docs/SpecialistProfileRequest.md)
- [State](docs/State.md)
- [TrainingCycle](docs/TrainingCycle.md)
- [TrainingGoal](docs/TrainingGoal.md)
- [TrainingPlan](docs/TrainingPlan.md)
- [VersionCommand](docs/VersionCommand.md)
- [VersionView](docs/VersionView.md)
- [View](docs/View.md)

### Authorization


Authentication schemes defined for the API:
<a id="oidc"></a>
#### oidc


## About

This TypeScript SDK client supports the [Fetch API](https://fetch.spec.whatwg.org/)
and is automatically generated by the
[OpenAPI Generator](https://openapi-generator.tech) project:

- API version: `v1`
- Package version: `v1`
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
