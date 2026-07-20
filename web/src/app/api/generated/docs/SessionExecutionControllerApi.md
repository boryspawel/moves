# SessionExecutionControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**correct**](SessionExecutionControllerApi.md#correct) | **POST** /api/v1/session-executions/{executionId}/corrections | Append an audited correction without changing execution history |
| [**declare**](SessionExecutionControllerApi.md#declare) | **POST** /api/v1/planned-sessions/{sessionId}/executions | Declare completion of an assigned planned session |
| [**specialistExecutions**](SessionExecutionControllerApi.md#specialistexecutions) | **GET** /api/v1/specialist/participants/{participantAccountId}/executions | List executions and alerts for a participant with an active relationship |



## correct

> ExecutionView correct(executionId, correctionCommand)

Append an audited correction without changing execution history

### Example

```ts
import {
  Configuration,
  SessionExecutionControllerApi,
} from '@moves/api-client';
import type { CorrectRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionControllerApi(config);

  const body = {
    // string
    executionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CorrectionCommand
    correctionCommand: ...,
  } satisfies CorrectRequest;

  try {
    const data = await api.correct(body);
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
| **executionId** | `string` |  | [Defaults to `undefined`] |
| **correctionCommand** | [CorrectionCommand](CorrectionCommand.md) |  | |

### Return type

[**ExecutionView**](ExecutionView.md)

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


## declare

> ExecutionView declare(sessionId, idempotencyKey, declareExecutionCommand)

Declare completion of an assigned planned session

### Example

```ts
import {
  Configuration,
  SessionExecutionControllerApi,
} from '@moves/api-client';
import type { DeclareRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionControllerApi(config);

  const body = {
    // string
    sessionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // DeclareExecutionCommand
    declareExecutionCommand: ...,
  } satisfies DeclareRequest;

  try {
    const data = await api.declare(body);
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
| **sessionId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **declareExecutionCommand** | [DeclareExecutionCommand](DeclareExecutionCommand.md) |  | |

### Return type

[**ExecutionView**](ExecutionView.md)

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


## specialistExecutions

> Array&lt;ExecutionView&gt; specialistExecutions(participantAccountId)

List executions and alerts for a participant with an active relationship

### Example

```ts
import {
  Configuration,
  SessionExecutionControllerApi,
} from '@moves/api-client';
import type { SpecialistExecutionsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionControllerApi(config);

  const body = {
    // string
    participantAccountId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies SpecialistExecutionsRequest;

  try {
    const data = await api.specialistExecutions(body);
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
| **participantAccountId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**Array&lt;ExecutionView&gt;**](ExecutionView.md)

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
