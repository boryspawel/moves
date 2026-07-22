# ExerciseCatalogAdminControllerApi

All URIs are relative to *http://localhost*

| Method                                                                                            | HTTP request                                                              | Description |
| ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- | ----------- |
| [**addContribution**](ExerciseCatalogAdminControllerApi.md#addcontribution)                       | **POST** /api/v1/admin/exercises/versions/{versionId}/contributions       |             |
| [**addEvidence**](ExerciseCatalogAdminControllerApi.md#addevidence)                               | **POST** /api/v1/admin/exercises/versions/{versionId}/evidence            |             |
| [**approve**](ExerciseCatalogAdminControllerApi.md#approve)                                       | **POST** /api/v1/admin/exercises/versions/{versionId}/approve             |             |
| [**create**](ExerciseCatalogAdminControllerApi.md#createoperation)                                | **POST** /api/v1/admin/exercises                                          |             |
| [**createVersion**](ExerciseCatalogAdminControllerApi.md#createversion)                           | **POST** /api/v1/admin/exercises/{exerciseId}/versions                    |             |
| [**editor1**](ExerciseCatalogAdminControllerApi.md#editor1)                                       | **GET** /api/v1/admin/exercises/versions/{versionId}/editor               |             |
| [**legacyContraindications**](ExerciseCatalogAdminControllerApi.md#legacycontraindications)       | **GET** /api/v1/admin/exercises/legacy/contraindications                  |             |
| [**publish**](ExerciseCatalogAdminControllerApi.md#publish)                                       | **POST** /api/v1/admin/exercises/versions/{versionId}/publish             |             |
| [**replaceLoadCharacteristics**](ExerciseCatalogAdminControllerApi.md#replaceloadcharacteristics) | **PUT** /api/v1/admin/exercises/versions/{versionId}/load-characteristics |             |
| [**requestChanges**](ExerciseCatalogAdminControllerApi.md#requestchanges)                         | **POST** /api/v1/admin/exercises/versions/{versionId}/request-changes     |             |
| [**submitReview**](ExerciseCatalogAdminControllerApi.md#submitreview)                             | **POST** /api/v1/admin/exercises/versions/{versionId}/submit-review       |             |
| [**update**](ExerciseCatalogAdminControllerApi.md#update)                                         | **PUT** /api/v1/admin/exercises/versions/{versionId}                      |             |
| [**versions**](ExerciseCatalogAdminControllerApi.md#versions)                                     | **GET** /api/v1/admin/exercises/{exerciseId}/versions                     |             |
| [**withdraw**](ExerciseCatalogAdminControllerApi.md#withdraw)                                     | **POST** /api/v1/admin/exercises/versions/{versionId}/withdraw            |             |

## addContribution

> ContributionView addContribution(versionId, contributionCommand)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { AddContributionRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ContributionCommand
    contributionCommand: ...,
  } satisfies AddContributionRequest;

  try {
    const data = await api.addContribution(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                    | Type                                          | Description | Notes                     |
| ----------------------- | --------------------------------------------- | ----------- | ------------------------- |
| **versionId**           | `string`                                      |             | [Defaults to `undefined`] |
| **contributionCommand** | [ContributionCommand](ContributionCommand.md) |             |                           |

### Return type

[**ContributionView**](ContributionView.md)

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

## addEvidence

> EvidenceView addEvidence(versionId, evidenceCommand)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { AddEvidenceRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // EvidenceCommand
    evidenceCommand: ...,
  } satisfies AddEvidenceRequest;

  try {
    const data = await api.addEvidence(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                | Type                                  | Description | Notes                     |
| ------------------- | ------------------------------------- | ----------- | ------------------------- |
| **versionId**       | `string`                              |             | [Defaults to `undefined`] |
| **evidenceCommand** | [EvidenceCommand](EvidenceCommand.md) |             |                           |

### Return type

[**EvidenceView**](EvidenceView.md)

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

## approve

> ReviewResult approve(versionId)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { ApproveRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ApproveRequest;

  try {
    const data = await api.approve(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name          | Type     | Description | Notes                     |
| ------------- | -------- | ----------- | ------------------------- |
| **versionId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**ReviewResult**](ReviewResult.md)

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

## create

> VersionView create(createRequest)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { CreateOperationRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // CreateRequest
    createRequest: ...,
  } satisfies CreateOperationRequest;

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

| Name              | Type                              | Description | Notes |
| ----------------- | --------------------------------- | ----------- | ----- |
| **createRequest** | [CreateRequest](CreateRequest.md) |             |       |

### Return type

[**VersionView**](VersionView.md)

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

## createVersion

> VersionView createVersion(exerciseId, versionCommand)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { CreateVersionRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    exerciseId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // VersionCommand
    versionCommand: ...,
  } satisfies CreateVersionRequest;

  try {
    const data = await api.createVersion(body);
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
| **exerciseId**     | `string`                            |             | [Defaults to `undefined`] |
| **versionCommand** | [VersionCommand](VersionCommand.md) |             |                           |

### Return type

[**VersionView**](VersionView.md)

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

## editor1

> EditorView editor1(versionId)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { Editor1Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies Editor1Request;

  try {
    const data = await api.editor1(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name          | Type     | Description | Notes                     |
| ------------- | -------- | ----------- | ------------------------- |
| **versionId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**EditorView**](EditorView.md)

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

## legacyContraindications

> Array&lt;LegacyContraindicationReportItem&gt; legacyContraindications()

### Example

```ts
import { Configuration, ExerciseCatalogAdminControllerApi } from '@moves/api-client';
import type { LegacyContraindicationsRequest } from '@moves/api-client';

async function example() {
  console.log('🚀 Testing @moves/api-client SDK...');
  const config = new Configuration({});
  const api = new ExerciseCatalogAdminControllerApi(config);

  try {
    const data = await api.legacyContraindications();
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

[**Array&lt;LegacyContraindicationReportItem&gt;**](LegacyContraindicationReportItem.md)

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

## publish

> PublicationResult publish(versionId)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { PublishRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies PublishRequest;

  try {
    const data = await api.publish(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name          | Type     | Description | Notes                     |
| ------------- | -------- | ----------- | ------------------------- |
| **versionId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**PublicationResult**](PublicationResult.md)

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

## replaceLoadCharacteristics

> EditorView replaceLoadCharacteristics(versionId, loadCharacteristicCommand)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { ReplaceLoadCharacteristicsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // Array<LoadCharacteristicCommand>
    loadCharacteristicCommand: ...,
  } satisfies ReplaceLoadCharacteristicsRequest;

  try {
    const data = await api.replaceLoadCharacteristics(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                          | Type                               | Description | Notes                     |
| ----------------------------- | ---------------------------------- | ----------- | ------------------------- |
| **versionId**                 | `string`                           |             | [Defaults to `undefined`] |
| **loadCharacteristicCommand** | `Array<LoadCharacteristicCommand>` |             |                           |

### Return type

[**EditorView**](EditorView.md)

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

## requestChanges

> VersionView requestChanges(versionId)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { RequestChangesRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies RequestChangesRequest;

  try {
    const data = await api.requestChanges(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name          | Type     | Description | Notes                     |
| ------------- | -------- | ----------- | ------------------------- |
| **versionId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**VersionView**](VersionView.md)

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

## submitReview

> VersionView submitReview(versionId)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { SubmitReviewRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies SubmitReviewRequest;

  try {
    const data = await api.submitReview(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name          | Type     | Description | Notes                     |
| ------------- | -------- | ----------- | ------------------------- |
| **versionId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**VersionView**](VersionView.md)

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

## update

> VersionView update(versionId, versionCommand)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { UpdateRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // VersionCommand
    versionCommand: ...,
  } satisfies UpdateRequest;

  try {
    const data = await api.update(body);
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
| **versionId**      | `string`                            |             | [Defaults to `undefined`] |
| **versionCommand** | [VersionCommand](VersionCommand.md) |             |                           |

### Return type

[**VersionView**](VersionView.md)

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

## versions

> Array&lt;VersionView&gt; versions(exerciseId)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { VersionsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    exerciseId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies VersionsRequest;

  try {
    const data = await api.versions(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name           | Type     | Description | Notes                     |
| -------------- | -------- | ----------- | ------------------------- |
| **exerciseId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**Array&lt;VersionView&gt;**](VersionView.md)

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

## withdraw

> VersionView withdraw(versionId)

### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { WithdrawRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies WithdrawRequest;

  try {
    const data = await api.withdraw(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name          | Type     | Description | Notes                     |
| ------------- | -------- | ----------- | ------------------------- |
| **versionId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**VersionView**](VersionView.md)

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
