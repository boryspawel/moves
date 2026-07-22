# ExerciseImportAdminControllerApi

All URIs are relative to *http://localhost*

| Method                                                               | HTTP request                                                     | Description |
| -------------------------------------------------------------------- | ---------------------------------------------------------------- | ----------- |
| [**batch**](ExerciseImportAdminControllerApi.md#batch)               | **GET** /api/v1/admin/exercise-import/batches/{id}               |             |
| [**createSource**](ExerciseImportAdminControllerApi.md#createsource) | **POST** /api/v1/admin/exercise-import/sources                   |             |
| [**draft**](ExerciseImportAdminControllerApi.md#draft)               | **POST** /api/v1/admin/exercise-import/records/{id}/create-draft |             |
| [**issues**](ExerciseImportAdminControllerApi.md#issues)             | **GET** /api/v1/admin/exercise-import/batches/{id}/issues        |             |
| [**mapping**](ExerciseImportAdminControllerApi.md#mapping)           | **POST** /api/v1/admin/exercise-import/mappings/{id}/decision    |             |
| [**match**](ExerciseImportAdminControllerApi.md#match)               | **POST** /api/v1/admin/exercise-import/records/{id}/match        |             |
| [**record**](ExerciseImportAdminControllerApi.md#record)             | **GET** /api/v1/admin/exercise-import/records/{id}               |             |
| [**records**](ExerciseImportAdminControllerApi.md#records)           | **GET** /api/v1/admin/exercise-import/batches/{id}/records       |             |
| [**restart**](ExerciseImportAdminControllerApi.md#restart)           | **POST** /api/v1/admin/exercise-import/batches/{id}/restart      |             |
| [**sources**](ExerciseImportAdminControllerApi.md#sources)           | **GET** /api/v1/admin/exercise-import/sources                    |             |
| [**upload**](ExerciseImportAdminControllerApi.md#upload)             | **POST** /api/v1/admin/exercise-import/batches                   |             |

## batch

> BatchView batch(id)

### Example

```ts
import {
  Configuration,
  ExerciseImportAdminControllerApi,
} from '@moves/api-client';
import type { BatchRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseImportAdminControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies BatchRequest;

  try {
    const data = await api.batch(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name   | Type     | Description | Notes                     |
| ------ | -------- | ----------- | ------------------------- |
| **id** | `string` |             | [Defaults to `undefined`] |

### Return type

[**BatchView**](BatchView.md)

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

## createSource

> SourceView createSource(createSource)

### Example

```ts
import {
  Configuration,
  ExerciseImportAdminControllerApi,
} from '@moves/api-client';
import type { CreateSourceRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseImportAdminControllerApi(config);

  const body = {
    // CreateSource
    createSource: ...,
  } satisfies CreateSourceRequest;

  try {
    const data = await api.createSource(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name             | Type                            | Description | Notes |
| ---------------- | ------------------------------- | ----------- | ----- |
| **createSource** | [CreateSource](CreateSource.md) |             |       |

### Return type

[**SourceView**](SourceView.md)

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

## draft

> { [key: string]: string; } draft(id)

### Example

```ts
import {
  Configuration,
  ExerciseImportAdminControllerApi,
} from '@moves/api-client';
import type { DraftRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseImportAdminControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies DraftRequest;

  try {
    const data = await api.draft(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name   | Type     | Description | Notes                     |
| ------ | -------- | ----------- | ------------------------- |
| **id** | `string` |             | [Defaults to `undefined`] |

### Return type

**{ [key: string]: string; }**

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

## issues

> string issues(id, format, severity)

### Example

```ts
import {
  Configuration,
  ExerciseImportAdminControllerApi,
} from '@moves/api-client';
import type { IssuesRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseImportAdminControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    format: format_example,
    // string (optional)
    severity: severity_example,
  } satisfies IssuesRequest;

  try {
    const data = await api.issues(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name         | Type     | Description | Notes                                      |
| ------------ | -------- | ----------- | ------------------------------------------ |
| **id**       | `string` |             | [Defaults to `undefined`]                  |
| **format**   | `string` |             | [Optional] [Defaults to `&#39;jsonl&#39;`] |
| **severity** | `string` |             | [Optional] [Defaults to `undefined`]       |

### Return type

**string**

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

## mapping

> MappingView mapping(id, mappingDecision)

### Example

```ts
import {
  Configuration,
  ExerciseImportAdminControllerApi,
} from '@moves/api-client';
import type { MappingRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseImportAdminControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // MappingDecision
    mappingDecision: ...,
  } satisfies MappingRequest;

  try {
    const data = await api.mapping(body);
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
| **id**              | `string`                              |             | [Defaults to `undefined`] |
| **mappingDecision** | [MappingDecision](MappingDecision.md) |             |                           |

### Return type

[**MappingView**](MappingView.md)

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

## match

> RecordDetail match(id, matchDecision)

### Example

```ts
import {
  Configuration,
  ExerciseImportAdminControllerApi,
} from '@moves/api-client';
import type { MatchRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseImportAdminControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // MatchDecision
    matchDecision: ...,
  } satisfies MatchRequest;

  try {
    const data = await api.match(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name              | Type                              | Description | Notes                     |
| ----------------- | --------------------------------- | ----------- | ------------------------- |
| **id**            | `string`                          |             | [Defaults to `undefined`] |
| **matchDecision** | [MatchDecision](MatchDecision.md) |             |                           |

### Return type

[**RecordDetail**](RecordDetail.md)

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

## record

> RecordDetail record(id)

### Example

```ts
import {
  Configuration,
  ExerciseImportAdminControllerApi,
} from '@moves/api-client';
import type { RecordRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseImportAdminControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies RecordRequest;

  try {
    const data = await api.record(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name   | Type     | Description | Notes                     |
| ------ | -------- | ----------- | ------------------------- |
| **id** | `string` |             | [Defaults to `undefined`] |

### Return type

[**RecordDetail**](RecordDetail.md)

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

## records

> RecordPage records(id, status, severity, page, size)

### Example

```ts
import {
  Configuration,
  ExerciseImportAdminControllerApi,
} from '@moves/api-client';
import type { RecordsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseImportAdminControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    status: status_example,
    // string (optional)
    severity: severity_example,
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
  } satisfies RecordsRequest;

  try {
    const data = await api.records(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name         | Type     | Description | Notes                                |
| ------------ | -------- | ----------- | ------------------------------------ |
| **id**       | `string` |             | [Defaults to `undefined`]            |
| **status**   | `string` |             | [Optional] [Defaults to `undefined`] |
| **severity** | `string` |             | [Optional] [Defaults to `undefined`] |
| **page**     | `number` |             | [Optional] [Defaults to `0`]         |
| **size**     | `number` |             | [Optional] [Defaults to `50`]        |

### Return type

[**RecordPage**](RecordPage.md)

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

## restart

> restart(id)

### Example

```ts
import {
  Configuration,
  ExerciseImportAdminControllerApi,
} from '@moves/api-client';
import type { RestartRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseImportAdminControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies RestartRequest;

  try {
    const data = await api.restart(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name   | Type     | Description | Notes                     |
| ------ | -------- | ----------- | ------------------------- |
| **id** | `string` |             | [Defaults to `undefined`] |

### Return type

`void` (Empty response body)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## sources

> Array&lt;SourceView&gt; sources()

### Example

```ts
import { Configuration, ExerciseImportAdminControllerApi } from '@moves/api-client';
import type { SourcesRequest } from '@moves/api-client';

async function example() {
  console.log('🚀 Testing @moves/api-client SDK...');
  const config = new Configuration({});
  const api = new ExerciseImportAdminControllerApi(config);

  try {
    const data = await api.sources();
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

[**Array&lt;SourceView&gt;**](SourceView.md)

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

## upload

> UploadAccepted upload(idempotencyKey, sourceId, file, forceReprocess)

### Example

```ts
import {
  Configuration,
  ExerciseImportAdminControllerApi,
} from '@moves/api-client';
import type { UploadRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseImportAdminControllerApi(config);

  const body = {
    // string
    idempotencyKey: idempotencyKey_example,
    // string
    sourceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // Blob
    file: BINARY_DATA_HERE,
    // boolean (optional)
    forceReprocess: true,
  } satisfies UploadRequest;

  try {
    const data = await api.upload(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name               | Type      | Description | Notes                            |
| ------------------ | --------- | ----------- | -------------------------------- |
| **idempotencyKey** | `string`  |             | [Defaults to `undefined`]        |
| **sourceId**       | `string`  |             | [Defaults to `undefined`]        |
| **file**           | `Blob`    |             | [Defaults to `undefined`]        |
| **forceReprocess** | `boolean` |             | [Optional] [Defaults to `false`] |

### Return type

[**UploadAccepted**](UploadAccepted.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `multipart/form-data`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
