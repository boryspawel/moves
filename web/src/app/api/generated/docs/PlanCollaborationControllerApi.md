# PlanCollaborationControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**addPlanCollaborator**](PlanCollaborationControllerApi.md#addplancollaborator) | **POST** /api/v2/training-plans/{planId}/collaborators |  |
| [**decidePlanReview**](PlanCollaborationControllerApi.md#decideplanreview) | **POST** /api/v2/training-plans/reviews/{reviewId}/decision |  |
| [**endPlanCollaboration**](PlanCollaborationControllerApi.md#endplancollaboration) | **DELETE** /api/v2/training-plans/{planId}/collaborators/{collaboratorId} |  |
| [**requestPlanReview**](PlanCollaborationControllerApi.md#requestplanreview) | **POST** /api/v2/training-plans/revisions/{revisionId}/reviews |  |



## addPlanCollaborator

> CollaboratorView addPlanCollaborator(planId, collaboratorCommand, actingContext)



### Example

```ts
import {
  Configuration,
  PlanCollaborationControllerApi,
} from '@moves/api-client';
import type { AddPlanCollaboratorRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new PlanCollaborationControllerApi(config);

  const body = {
    // string
    planId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CollaboratorCommand
    collaboratorCommand: ...,
    // 'TRAINER' | 'PHYSIOTHERAPIST' (optional)
    actingContext: actingContext_example,
  } satisfies AddPlanCollaboratorRequest;

  try {
    const data = await api.addPlanCollaborator(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **planId** | `string` |  | [Defaults to `undefined`] |
| **collaboratorCommand** | [CollaboratorCommand](CollaboratorCommand.md) |  | |
| **actingContext** | `TRAINER`, `PHYSIOTHERAPIST` |  | [Optional] [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST] |

### Return type

[**CollaboratorView**](CollaboratorView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## decidePlanReview

> ReviewView decidePlanReview(reviewId, actingContext, reviewDecisionCommand)



### Example

```ts
import {
  Configuration,
  PlanCollaborationControllerApi,
} from '@moves/api-client';
import type { DecidePlanReviewRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new PlanCollaborationControllerApi(config);

  const body = {
    // string
    reviewId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'TRAINER' | 'PHYSIOTHERAPIST'
    actingContext: actingContext_example,
    // ReviewDecisionCommand
    reviewDecisionCommand: ...,
  } satisfies DecidePlanReviewRequest;

  try {
    const data = await api.decidePlanReview(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **reviewId** | `string` |  | [Defaults to `undefined`] |
| **actingContext** | `TRAINER`, `PHYSIOTHERAPIST` |  | [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST] |
| **reviewDecisionCommand** | [ReviewDecisionCommand](ReviewDecisionCommand.md) |  | |

### Return type

[**ReviewView**](ReviewView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## endPlanCollaboration

> CollaboratorView endPlanCollaboration(planId, collaboratorId, actingContext)



### Example

```ts
import {
  Configuration,
  PlanCollaborationControllerApi,
} from '@moves/api-client';
import type { EndPlanCollaborationRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new PlanCollaborationControllerApi(config);

  const body = {
    // string
    planId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    collaboratorId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'TRAINER' | 'PHYSIOTHERAPIST' (optional)
    actingContext: actingContext_example,
  } satisfies EndPlanCollaborationRequest;

  try {
    const data = await api.endPlanCollaboration(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **planId** | `string` |  | [Defaults to `undefined`] |
| **collaboratorId** | `string` |  | [Defaults to `undefined`] |
| **actingContext** | `TRAINER`, `PHYSIOTHERAPIST` |  | [Optional] [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST] |

### Return type

[**CollaboratorView**](CollaboratorView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## requestPlanReview

> ReviewView requestPlanReview(revisionId, reviewRequestCommand)



### Example

```ts
import {
  Configuration,
  PlanCollaborationControllerApi,
} from '@moves/api-client';
import type { RequestPlanReviewRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new PlanCollaborationControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ReviewRequestCommand
    reviewRequestCommand: ...,
  } satisfies RequestPlanReviewRequest;

  try {
    const data = await api.requestPlanReview(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **revisionId** | `string` |  | [Defaults to `undefined`] |
| **reviewRequestCommand** | [ReviewRequestCommand](ReviewRequestCommand.md) |  | |

### Return type

[**ReviewView**](ReviewView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
