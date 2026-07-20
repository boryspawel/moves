# ExerciseCatalogAdminControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**create1**](ExerciseCatalogAdminControllerApi.md#create1) | **POST** /api/v1/admin/exercises |  |
| [**createVersion**](ExerciseCatalogAdminControllerApi.md#createversion) | **POST** /api/v1/admin/exercises/{exerciseId}/versions |  |
| [**publish**](ExerciseCatalogAdminControllerApi.md#publish) | **POST** /api/v1/admin/exercises/versions/{versionId}/publish |  |
| [**update**](ExerciseCatalogAdminControllerApi.md#update) | **PUT** /api/v1/admin/exercises/versions/{versionId} |  |
| [**versions**](ExerciseCatalogAdminControllerApi.md#versions) | **GET** /api/v1/admin/exercises/{exerciseId}/versions |  |
| [**withdraw**](ExerciseCatalogAdminControllerApi.md#withdraw) | **POST** /api/v1/admin/exercises/versions/{versionId}/withdraw |  |



## create1

> VersionView create1(createRequest)



### Example

```ts
import {
  Configuration,
  ExerciseCatalogAdminControllerApi,
} from '@moves/api-client';
import type { Create1Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogAdminControllerApi(config);

  const body = {
    // CreateRequest
    createRequest: ...,
  } satisfies Create1Request;

  try {
    const data = await api.create1(body);
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
| **createRequest** | [CreateRequest](CreateRequest.md) |  | |

### Return type

[**VersionView**](VersionView.md)

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


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | `string` |  | [Defaults to `undefined`] |
| **versionCommand** | [VersionCommand](VersionCommand.md) |  | |

### Return type

[**VersionView**](VersionView.md)

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


## publish

> VersionView publish(versionId)



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


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **versionId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**VersionView**](VersionView.md)

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


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **versionId** | `string` |  | [Defaults to `undefined`] |
| **versionCommand** | [VersionCommand](VersionCommand.md) |  | |

### Return type

[**VersionView**](VersionView.md)

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


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**Array&lt;VersionView&gt;**](VersionView.md)

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


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **versionId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**VersionView**](VersionView.md)

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
