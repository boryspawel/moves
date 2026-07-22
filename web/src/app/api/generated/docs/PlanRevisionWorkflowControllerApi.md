# PlanRevisionWorkflowControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**acknowledge**](PlanRevisionWorkflowControllerApi.md#acknowledge) | **POST** /api/v2/training-plans/revisions/{revisionId}/workflow/warning-acknowledgements | Acknowledge warning factors from the current assessment |
| [**activate**](PlanRevisionWorkflowControllerApi.md#activate) | **POST** /api/v2/training-plans/revisions/{revisionId}/workflow/activation | Activate a validated plan revision |
| [**status**](PlanRevisionWorkflowControllerApi.md#status) | **POST** /api/v2/training-plans/revisions/{revisionId}/workflow/status | Read plan revision workflow status and current assessment |
| [**validate**](PlanRevisionWorkflowControllerApi.md#validate) | **POST** /api/v2/training-plans/revisions/{revisionId}/workflow/validation | Validate load and safety for a plan revision |



## acknowledge

> AcknowledgementView acknowledge(revisionId, acknowledgeWarningCommand)

Acknowledge warning factors from the current assessment

### Example

```ts
import {
  Configuration,
  PlanRevisionWorkflowControllerApi,
} from '@moves/api-client';
import type { AcknowledgeRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new PlanRevisionWorkflowControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // AcknowledgeWarningCommand
    acknowledgeWarningCommand: ...,
  } satisfies AcknowledgeRequest;

  try {
    const data = await api.acknowledge(body);
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
| **acknowledgeWarningCommand** | [AcknowledgeWarningCommand](AcknowledgeWarningCommand.md) |  | |

### Return type

[**AcknowledgementView**](AcknowledgementView.md)

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


## activate

> ActivationOutcome activate(revisionId, idempotencyKey, activateWorkflowCommand)

Activate a validated plan revision

### Example

```ts
import {
  Configuration,
  PlanRevisionWorkflowControllerApi,
} from '@moves/api-client';
import type { ActivateRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new PlanRevisionWorkflowControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // ActivateWorkflowCommand
    activateWorkflowCommand: ...,
  } satisfies ActivateRequest;

  try {
    const data = await api.activate(body);
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
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **activateWorkflowCommand** | [ActivateWorkflowCommand](ActivateWorkflowCommand.md) |  | |

### Return type

[**ActivationOutcome**](ActivationOutcome.md)

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


## status

> WorkflowView status(revisionId, activateWorkflowCommand)

Read plan revision workflow status and current assessment

### Example

```ts
import {
  Configuration,
  PlanRevisionWorkflowControllerApi,
} from '@moves/api-client';
import type { StatusRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new PlanRevisionWorkflowControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ActivateWorkflowCommand
    activateWorkflowCommand: ...,
  } satisfies StatusRequest;

  try {
    const data = await api.status(body);
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
| **activateWorkflowCommand** | [ActivateWorkflowCommand](ActivateWorkflowCommand.md) |  | |

### Return type

[**WorkflowView**](WorkflowView.md)

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


## validate

> ValidationView validate(revisionId, validateWorkflowCommand)

Validate load and safety for a plan revision

### Example

```ts
import {
  Configuration,
  PlanRevisionWorkflowControllerApi,
} from '@moves/api-client';
import type { ValidateRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new PlanRevisionWorkflowControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ValidateWorkflowCommand
    validateWorkflowCommand: ...,
  } satisfies ValidateRequest;

  try {
    const data = await api.validate(body);
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
| **validateWorkflowCommand** | [ValidateWorkflowCommand](ValidateWorkflowCommand.md) |  | |

### Return type

[**ValidationView**](ValidationView.md)

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
