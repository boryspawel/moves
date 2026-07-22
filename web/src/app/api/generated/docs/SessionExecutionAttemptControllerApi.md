# SessionExecutionAttemptControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**abandon**](SessionExecutionAttemptControllerApi.md#abandon) | **POST** /api/v1/participant/session-attempts/{attemptId}/abandon |  |
| [**complete**](SessionExecutionAttemptControllerApi.md#complete) | **POST** /api/v1/participant/session-attempts/{attemptId}/complete |  |
| [**get1**](SessionExecutionAttemptControllerApi.md#get1) | **GET** /api/v1/participant/session-attempts/{attemptId} |  |
| [**pause**](SessionExecutionAttemptControllerApi.md#pause) | **POST** /api/v1/participant/session-attempts/{attemptId}/pause |  |
| [**progress**](SessionExecutionAttemptControllerApi.md#progress) | **PUT** /api/v1/participant/session-attempts/{attemptId}/progress |  |
| [**resume**](SessionExecutionAttemptControllerApi.md#resume) | **POST** /api/v1/participant/session-attempts/{attemptId}/resume |  |
| [**start**](SessionExecutionAttemptControllerApi.md#start) | **POST** /api/v1/participant/session-attempts | Start or return the participant\&#39;s active session attempt |



## abandon

> AttemptView abandon(attemptId, abandonAttemptCommand)



### Example

```ts
import {
  Configuration,
  SessionExecutionAttemptControllerApi,
} from '@moves/api-client';
import type { AbandonRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionAttemptControllerApi(config);

  const body = {
    // string
    attemptId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // AbandonAttemptCommand (optional)
    abandonAttemptCommand: ...,
  } satisfies AbandonRequest;

  try {
    const data = await api.abandon(body);
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
| **attemptId** | `string` |  | [Defaults to `undefined`] |
| **abandonAttemptCommand** | [AbandonAttemptCommand](AbandonAttemptCommand.md) |  | [Optional] |

### Return type

[**AttemptView**](AttemptView.md)

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


## complete

> ExecutionView complete(attemptId, idempotencyKey, declareExecutionCommand)



### Example

```ts
import {
  Configuration,
  SessionExecutionAttemptControllerApi,
} from '@moves/api-client';
import type { CompleteRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionAttemptControllerApi(config);

  const body = {
    // string
    attemptId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // DeclareExecutionCommand
    declareExecutionCommand: ...,
  } satisfies CompleteRequest;

  try {
    const data = await api.complete(body);
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
| **attemptId** | `string` |  | [Defaults to `undefined`] |
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


## get1

> AttemptDetailView get1(attemptId)



### Example

```ts
import {
  Configuration,
  SessionExecutionAttemptControllerApi,
} from '@moves/api-client';
import type { Get1Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionAttemptControllerApi(config);

  const body = {
    // string
    attemptId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies Get1Request;

  try {
    const data = await api.get1(body);
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
| **attemptId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**AttemptDetailView**](AttemptDetailView.md)

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


## pause

> AttemptView pause(attemptId)



### Example

```ts
import {
  Configuration,
  SessionExecutionAttemptControllerApi,
} from '@moves/api-client';
import type { PauseRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionAttemptControllerApi(config);

  const body = {
    // string
    attemptId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies PauseRequest;

  try {
    const data = await api.pause(body);
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
| **attemptId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**AttemptView**](AttemptView.md)

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


## progress

> AttemptView progress(attemptId, progressCommand)



### Example

```ts
import {
  Configuration,
  SessionExecutionAttemptControllerApi,
} from '@moves/api-client';
import type { ProgressRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionAttemptControllerApi(config);

  const body = {
    // string
    attemptId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ProgressCommand
    progressCommand: ...,
  } satisfies ProgressRequest;

  try {
    const data = await api.progress(body);
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
| **attemptId** | `string` |  | [Defaults to `undefined`] |
| **progressCommand** | [ProgressCommand](ProgressCommand.md) |  | |

### Return type

[**AttemptView**](AttemptView.md)

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


## resume

> AttemptView resume(attemptId)



### Example

```ts
import {
  Configuration,
  SessionExecutionAttemptControllerApi,
} from '@moves/api-client';
import type { ResumeRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionAttemptControllerApi(config);

  const body = {
    // string
    attemptId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ResumeRequest;

  try {
    const data = await api.resume(body);
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
| **attemptId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**AttemptView**](AttemptView.md)

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


## start

> AttemptView start(idempotencyKey, startAttemptCommand)

Start or return the participant\&#39;s active session attempt

### Example

```ts
import {
  Configuration,
  SessionExecutionAttemptControllerApi,
} from '@moves/api-client';
import type { StartRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SessionExecutionAttemptControllerApi(config);

  const body = {
    // string
    idempotencyKey: idempotencyKey_example,
    // StartAttemptCommand
    startAttemptCommand: ...,
  } satisfies StartRequest;

  try {
    const data = await api.start(body);
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
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **startAttemptCommand** | [StartAttemptCommand](StartAttemptCommand.md) |  | |

### Return type

[**AttemptView**](AttemptView.md)

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
