# SessionExecutionControllerApi

All URIs are relative to *http://localhost*

| Method                                                                            | HTTP request                                                                   | Description                                                              |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------ |
| [**correct**](SessionExecutionControllerApi.md#correct)                           | **POST** /api/v1/session-executions/{executionId}/corrections                  | Append an audited correction without changing execution history          |
| [**declare1**](SessionExecutionControllerApi.md#declare1)                         | **POST** /api/v1/planned-sessions/{sessionId}/executions                       | Declare completion of an assigned planned session                        |
| [**post24h**](SessionExecutionControllerApi.md#post24h)                           | **POST** /api/v1/session-executions/{executionId}/post-24h-responses           | Append an idempotent post-24h session response                           |
| [**specialistExecutions**](SessionExecutionControllerApi.md#specialistexecutions) | **GET** /api/v1/specialist/participants/{participantAccountId}/executions      | List executions and alerts for a participant with an active relationship |
| [**transitionAlert**](SessionExecutionControllerApi.md#transitionalert)           | **POST** /api/v1/session-executions/{executionId}/alerts/{alertId}/transitions | Acknowledge, resolve or reopen an execution safety alert                 |

## correct

> ExecutionView correct(executionId, idempotencyKey, correctionCommand)

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
    // string
    idempotencyKey: idempotencyKey_example,
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

| Name                  | Type                                      | Description | Notes                     |
| --------------------- | ----------------------------------------- | ----------- | ------------------------- |
| **executionId**       | `string`                                  |             | [Defaults to `undefined`] |
| **idempotencyKey**    | `string`                                  |             | [Defaults to `undefined`] |
| **correctionCommand** | [CorrectionCommand](CorrectionCommand.md) |             |                           |

### Return type

[**ExecutionView**](ExecutionView.md)

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

## declare1

> ExecutionView declare1(sessionId, idempotencyKey, declareExecutionCommand)

Declare completion of an assigned planned session

### Example

```ts
import {
  Configuration,
  SessionExecutionControllerApi,
} from '@moves/api-client';
import type { Declare1Request } from '@moves/api-client';

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
  } satisfies Declare1Request;

  try {
    const data = await api.declare1(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                        | Type                                                  | Description | Notes                     |
| --------------------------- | ----------------------------------------------------- | ----------- | ------------------------- |
| **sessionId**               | `string`                                              |             | [Defaults to `undefined`] |
| **idempotencyKey**          | `string`                                              |             | [Defaults to `undefined`] |
| **declareExecutionCommand** | [DeclareExecutionCommand](DeclareExecutionCommand.md) |             |                           |

### Return type

[**ExecutionView**](ExecutionView.md)

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

## post24h

> Post24hData post24h(executionId, idempotencyKey, post24hCommand)

Append an idempotent post-24h session response

### Example

```ts
import {
  Configuration,
  SessionExecutionControllerApi,
} from '@moves/api-client';
import type { Post24hRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionControllerApi(config);

  const body = {
    // string
    executionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // Post24hCommand
    post24hCommand: ...,
  } satisfies Post24hRequest;

  try {
    const data = await api.post24h(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name               | Type                                | Description | Notes                     |
| ------------------ | ----------------------------------- | ----------- | ------------------------- |
| **executionId**    | `string`                            |             | [Defaults to `undefined`] |
| **idempotencyKey** | `string`                            |             | [Defaults to `undefined`] |
| **post24hCommand** | [Post24hCommand](Post24hCommand.md) |             |                           |

### Return type

[**Post24hData**](Post24hData.md)

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

| Name                     | Type     | Description | Notes                     |
| ------------------------ | -------- | ----------- | ------------------------- |
| **participantAccountId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**Array&lt;ExecutionView&gt;**](ExecutionView.md)

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

## transitionAlert

> AlertData transitionAlert(executionId, alertId, alertTransitionCommand)

Acknowledge, resolve or reopen an execution safety alert

### Example

```ts
import {
  Configuration,
  SessionExecutionControllerApi,
} from '@moves/api-client';
import type { TransitionAlertRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionControllerApi(config);

  const body = {
    // string
    executionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    alertId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // AlertTransitionCommand
    alertTransitionCommand: ...,
  } satisfies TransitionAlertRequest;

  try {
    const data = await api.transitionAlert(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                       | Type                                                | Description | Notes                     |
| -------------------------- | --------------------------------------------------- | ----------- | ------------------------- |
| **executionId**            | `string`                                            |             | [Defaults to `undefined`] |
| **alertId**                | `string`                                            |             | [Defaults to `undefined`] |
| **alertTransitionCommand** | [AlertTransitionCommand](AlertTransitionCommand.md) |             |                           |

### Return type

[**AlertData**](AlertData.md)

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
