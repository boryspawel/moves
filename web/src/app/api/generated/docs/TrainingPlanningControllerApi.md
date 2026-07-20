# TrainingPlanningControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**create**](TrainingPlanningControllerApi.md#create) | **POST** /api/v1/training-plans | Create and assign a simple specialist-authored training plan |
| [**sessions**](TrainingPlanningControllerApi.md#sessions) | **GET** /api/v1/planned-sessions | List planned sessions assigned to the current participant |



## create

> PlanBundle create(createPlanCommand)

Create and assign a simple specialist-authored training plan

### Example

```ts
import {
  Configuration,
  TrainingPlanningControllerApi,
} from '@moves/api-client';
import type { CreateRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningControllerApi(config);

  const body = {
    // CreatePlanCommand
    createPlanCommand: ...,
  } satisfies CreateRequest;

  try {
    const data = await api.create(body);
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
