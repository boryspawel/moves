# TrainingPlanningV2ControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**addCycle**](TrainingPlanningV2ControllerApi.md#addcycle) | **POST** /api/v2/training-plans/revisions/{revisionId}/cycles |  |
| [**addGoal**](TrainingPlanningV2ControllerApi.md#addgoal) | **POST** /api/v2/training-plans/revisions/{revisionId}/goals |  |
| [**addLoadBudget**](TrainingPlanningV2ControllerApi.md#addloadbudget) | **POST** /api/v2/training-plans/revisions/{revisionId}/load-budgets |  |
| [**addMicrocycle**](TrainingPlanningV2ControllerApi.md#addmicrocycle) | **POST** /api/v2/training-plans/revisions/{revisionId}/microcycles |  |
| [**addPrescription**](TrainingPlanningV2ControllerApi.md#addprescription) | **POST** /api/v2/training-plans/revisions/{revisionId}/prescriptions |  |
| [**addSession**](TrainingPlanningV2ControllerApi.md#addsession) | **POST** /api/v2/training-plans/revisions/{revisionId}/sessions |  |
| [**createDraft**](TrainingPlanningV2ControllerApi.md#createdraft) | **POST** /api/v2/training-plans | Create an inactive training plan draft |
| [**createRevision**](TrainingPlanningV2ControllerApi.md#createrevision) | **POST** /api/v2/training-plans/{planId}/revisions |  |
| [**defineSessionVariant**](TrainingPlanningV2ControllerApi.md#definesessionvariant) | **POST** /api/v2/training-plans/revisions/{revisionId}/session-variants |  |
| [**editor**](TrainingPlanningV2ControllerApi.md#editor) | **GET** /api/v2/training-plans/revisions/{revisionId} |  |
| [**history**](TrainingPlanningV2ControllerApi.md#history) | **GET** /api/v2/training-plans/{planId}/revisions |  |
| [**reorder**](TrainingPlanningV2ControllerApi.md#reorder) | **PUT** /api/v2/training-plans/revisions/{revisionId}/prescriptions/order |  |
| [**validateStructurally**](TrainingPlanningV2ControllerApi.md#validatestructurally) | **POST** /api/v2/training-plans/revisions/{revisionId}/structural-validation |  |



## addCycle

> EditorView addCycle(revisionId, addCycleCommand)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { AddCycleRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // AddCycleCommand
    addCycleCommand: ...,
  } satisfies AddCycleRequest;

  try {
    const data = await api.addCycle(body);
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
| **addCycleCommand** | [AddCycleCommand](AddCycleCommand.md) |  | |

### Return type

[**EditorView**](EditorView.md)

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


## addGoal

> EditorView addGoal(revisionId, addGoalCommand)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { AddGoalRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // AddGoalCommand
    addGoalCommand: ...,
  } satisfies AddGoalRequest;

  try {
    const data = await api.addGoal(body);
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
| **addGoalCommand** | [AddGoalCommand](AddGoalCommand.md) |  | |

### Return type

[**EditorView**](EditorView.md)

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


## addLoadBudget

> EditorView addLoadBudget(revisionId, addLoadBudgetCommand)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { AddLoadBudgetRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // AddLoadBudgetCommand
    addLoadBudgetCommand: ...,
  } satisfies AddLoadBudgetRequest;

  try {
    const data = await api.addLoadBudget(body);
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
| **addLoadBudgetCommand** | [AddLoadBudgetCommand](AddLoadBudgetCommand.md) |  | |

### Return type

[**EditorView**](EditorView.md)

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


## addMicrocycle

> EditorView addMicrocycle(revisionId, addMicrocycleCommand)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { AddMicrocycleRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // AddMicrocycleCommand
    addMicrocycleCommand: ...,
  } satisfies AddMicrocycleRequest;

  try {
    const data = await api.addMicrocycle(body);
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
| **addMicrocycleCommand** | [AddMicrocycleCommand](AddMicrocycleCommand.md) |  | |

### Return type

[**EditorView**](EditorView.md)

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


## addPrescription

> EditorView addPrescription(revisionId, addPrescriptionCommand)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { AddPrescriptionRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // AddPrescriptionCommand
    addPrescriptionCommand: ...,
  } satisfies AddPrescriptionRequest;

  try {
    const data = await api.addPrescription(body);
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
| **addPrescriptionCommand** | [AddPrescriptionCommand](AddPrescriptionCommand.md) |  | |

### Return type

[**EditorView**](EditorView.md)

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


## addSession

> EditorView addSession(revisionId, addSessionCommand)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { AddSessionRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // AddSessionCommand
    addSessionCommand: ...,
  } satisfies AddSessionRequest;

  try {
    const data = await api.addSession(body);
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
| **addSessionCommand** | [AddSessionCommand](AddSessionCommand.md) |  | |

### Return type

[**EditorView**](EditorView.md)

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


## createDraft

> EditorView createDraft(createDraftCommand)

Create an inactive training plan draft

### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { CreateDraftRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // CreateDraftCommand
    createDraftCommand: ...,
  } satisfies CreateDraftRequest;

  try {
    const data = await api.createDraft(body);
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
| **createDraftCommand** | [CreateDraftCommand](CreateDraftCommand.md) |  | |

### Return type

[**EditorView**](EditorView.md)

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


## createRevision

> EditorView createRevision(planId, createRevisionCommand)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { CreateRevisionRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    planId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CreateRevisionCommand
    createRevisionCommand: ...,
  } satisfies CreateRevisionRequest;

  try {
    const data = await api.createRevision(body);
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
| **createRevisionCommand** | [CreateRevisionCommand](CreateRevisionCommand.md) |  | |

### Return type

[**EditorView**](EditorView.md)

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


## defineSessionVariant

> EditorView defineSessionVariant(revisionId, defineSessionVariantCommand)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { DefineSessionVariantRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // DefineSessionVariantCommand
    defineSessionVariantCommand: ...,
  } satisfies DefineSessionVariantRequest;

  try {
    const data = await api.defineSessionVariant(body);
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
| **defineSessionVariantCommand** | [DefineSessionVariantCommand](DefineSessionVariantCommand.md) |  | |

### Return type

[**EditorView**](EditorView.md)

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


## editor

> EditorView editor(revisionId)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { EditorRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies EditorRequest;

  try {
    const data = await api.editor(body);
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

### Return type

[**EditorView**](EditorView.md)

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


## history

> Array&lt;RevisionHistoryItem&gt; history(planId)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { HistoryRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    planId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies HistoryRequest;

  try {
    const data = await api.history(body);
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

### Return type

[**Array&lt;RevisionHistoryItem&gt;**](RevisionHistoryItem.md)

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


## reorder

> EditorView reorder(revisionId, reorderCommand)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { ReorderRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ReorderCommand
    reorderCommand: ...,
  } satisfies ReorderRequest;

  try {
    const data = await api.reorder(body);
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
| **reorderCommand** | [ReorderCommand](ReorderCommand.md) |  | |

### Return type

[**EditorView**](EditorView.md)

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


## validateStructurally

> StructuralValidationView validateStructurally(revisionId, validateCommand)



### Example

```ts
import {
  Configuration,
  TrainingPlanningV2ControllerApi,
} from '@moves/api-client';
import type { ValidateStructurallyRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TrainingPlanningV2ControllerApi(config);

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ValidateCommand
    validateCommand: ...,
  } satisfies ValidateStructurallyRequest;

  try {
    const data = await api.validateStructurally(body);
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
| **validateCommand** | [ValidateCommand](ValidateCommand.md) |  | |

### Return type

[**StructuralValidationView**](StructuralValidationView.md)

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
