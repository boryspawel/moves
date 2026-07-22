# SpecialistWorklistControllerApi

All URIs are relative to *http://localhost*

| Method                                                                                  | HTTP request                                          | Description |
| --------------------------------------------------------------------------------------- | ----------------------------------------------------- | ----------- |
| [**actOnWorklist**](SpecialistWorklistControllerApi.md#actonworklist)                   | **POST** /api/v1/specialist/worklist/{itemId}/actions |             |
| [**listWorklist**](SpecialistWorklistControllerApi.md#listworklist)                     | **GET** /api/v1/specialist/worklist                   |             |
| [**replyToIssue**](SpecialistWorklistControllerApi.md#replytoissue)                     | **POST** /api/v1/specialist/worklist/{itemId}/reply   |             |
| [**reportParticipantIssue**](SpecialistWorklistControllerApi.md#reportparticipantissue) | **POST** /api/v1/participant/issues                   |             |

## actOnWorklist

> WorklistItemView actOnWorklist(itemId, actingContext, purpose, actionCommand)

### Example

```ts
import {
  Configuration,
  SpecialistWorklistControllerApi,
} from '@moves/api-client';
import type { ActOnWorklistRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SpecialistWorklistControllerApi(config);

  const body = {
    // string
    itemId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'TRAINER' | 'PHYSIOTHERAPIST'
    actingContext: actingContext_example,
    // 'PERFORMANCE_PLANNING' | 'FUNCTIONAL_RECOVERY' | 'CLINICAL_REVIEW'
    purpose: purpose_example,
    // ActionCommand
    actionCommand: ...,
  } satisfies ActOnWorklistRequest;

  try {
    const data = await api.actOnWorklist(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name              | Type                                                             | Description | Notes                                                                                        |
| ----------------- | ---------------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------- |
| **itemId**        | `string`                                                         |             | [Defaults to `undefined`]                                                                    |
| **actingContext** | `TRAINER`, `PHYSIOTHERAPIST`                                     |             | [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST]                                   |
| **purpose**       | `PERFORMANCE_PLANNING`, `FUNCTIONAL_RECOVERY`, `CLINICAL_REVIEW` |             | [Defaults to `undefined`] [Enum: PERFORMANCE_PLANNING, FUNCTIONAL_RECOVERY, CLINICAL_REVIEW] |
| **actionCommand** | [ActionCommand](ActionCommand.md)                                |             |                                                                                              |

### Return type

[**WorklistItemView**](WorklistItemView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## listWorklist

> Array&lt;WorklistItemView&gt; listWorklist(actingContext, purpose)

### Example

```ts
import { Configuration, SpecialistWorklistControllerApi } from '@moves/api-client';
import type { ListWorklistRequest } from '@moves/api-client';

async function example() {
  console.log('🚀 Testing @moves/api-client SDK...');
  const config = new Configuration({});
  const api = new SpecialistWorklistControllerApi(config);

  const body = {
    // 'TRAINER' | 'PHYSIOTHERAPIST'
    actingContext: actingContext_example,
    // 'PERFORMANCE_PLANNING' | 'FUNCTIONAL_RECOVERY' | 'CLINICAL_REVIEW'
    purpose: purpose_example,
  } satisfies ListWorklistRequest;

  try {
    const data = await api.listWorklist(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name              | Type                                                             | Description | Notes                                                                                        |
| ----------------- | ---------------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------- |
| **actingContext** | `TRAINER`, `PHYSIOTHERAPIST`                                     |             | [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST]                                   |
| **purpose**       | `PERFORMANCE_PLANNING`, `FUNCTIONAL_RECOVERY`, `CLINICAL_REVIEW` |             | [Defaults to `undefined`] [Enum: PERFORMANCE_PLANNING, FUNCTIONAL_RECOVERY, CLINICAL_REVIEW] |

### Return type

[**Array&lt;WorklistItemView&gt;**](WorklistItemView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## replyToIssue

> ReplyView replyToIssue(itemId, actingContext, purpose, replyCommand)

### Example

```ts
import {
  Configuration,
  SpecialistWorklistControllerApi,
} from '@moves/api-client';
import type { ReplyToIssueRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SpecialistWorklistControllerApi(config);

  const body = {
    // string
    itemId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'TRAINER' | 'PHYSIOTHERAPIST'
    actingContext: actingContext_example,
    // 'PERFORMANCE_PLANNING' | 'FUNCTIONAL_RECOVERY' | 'CLINICAL_REVIEW'
    purpose: purpose_example,
    // ReplyCommand
    replyCommand: ...,
  } satisfies ReplyToIssueRequest;

  try {
    const data = await api.replyToIssue(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name              | Type                                                             | Description | Notes                                                                                        |
| ----------------- | ---------------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------- |
| **itemId**        | `string`                                                         |             | [Defaults to `undefined`]                                                                    |
| **actingContext** | `TRAINER`, `PHYSIOTHERAPIST`                                     |             | [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST]                                   |
| **purpose**       | `PERFORMANCE_PLANNING`, `FUNCTIONAL_RECOVERY`, `CLINICAL_REVIEW` |             | [Defaults to `undefined`] [Enum: PERFORMANCE_PLANNING, FUNCTIONAL_RECOVERY, CLINICAL_REVIEW] |
| **replyCommand**  | [ReplyCommand](ReplyCommand.md)                                  |             |                                                                                              |

### Return type

[**ReplyView**](ReplyView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## reportParticipantIssue

> WorklistItemView reportParticipantIssue(participantIssueCommand)

### Example

```ts
import {
  Configuration,
  SpecialistWorklistControllerApi,
} from '@moves/api-client';
import type { ReportParticipantIssueRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SpecialistWorklistControllerApi(config);

  const body = {
    // ParticipantIssueCommand
    participantIssueCommand: ...,
  } satisfies ReportParticipantIssueRequest;

  try {
    const data = await api.reportParticipantIssue(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                        | Type                                                  | Description | Notes |
| --------------------------- | ----------------------------------------------------- | ----------- | ----- |
| **participantIssueCommand** | [ParticipantIssueCommand](ParticipantIssueCommand.md) |             |       |

### Return type

[**WorklistItemView**](WorklistItemView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
