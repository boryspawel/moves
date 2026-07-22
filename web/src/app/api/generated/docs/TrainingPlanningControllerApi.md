# TrainingPlanningControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createLegacyTrainingPlan**](TrainingPlanningControllerApi.md#createlegacytrainingplan) | **POST** /api/v1/training-plans | Deprecated V1 plan creation endpoint |
| [**sessions**](TrainingPlanningControllerApi.md#sessions) | **GET** /api/v1/planned-sessions | List planned sessions assigned to the current participant |



## createLegacyTrainingPlan

> PlanBundle createLegacyTrainingPlan(createPlanCommand)

Deprecated V1 plan creation endpoint

### Example

```ts
import {
  Configuration,
  TrainingPlanningControllerApi,
} from '@moves/api-client';
import type { CreateLegacyTrainingPlanRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningControllerApi(config);

  const body = {
    // CreatePlanCommand
    createPlanCommand: ...,
  } satisfies CreateLegacyTrainingPlanRequest;

  try {
    const data = await api.createLegacyTrainingPlan(body);
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
| **createPlanCommand** | [CreatePlanCommand](CreatePlanCommand.md) |  | |

### Return type

[**PlanBundle**](PlanBundle.md)

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


## sessions

> Array&lt;SessionView&gt; sessions()

List planned sessions assigned to the current participant

### Example

```ts
import {
  Configuration,
  TrainingPlanningControllerApi,
} from '@moves/api-client';
import type { SessionsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningControllerApi(config);

  try {
    const data = await api.sessions();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**Array&lt;SessionView&gt;**](SessionView.md)

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
