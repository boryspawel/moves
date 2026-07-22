# ExerciseVersionReviewControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**diff**](ExerciseVersionReviewControllerApi.md#diff) | **GET** /api/v1/admin/exercise-versions/{id}/diff |  |
| [**publish1**](ExerciseVersionReviewControllerApi.md#publish1) | **POST** /api/v1/admin/exercise-versions/{id}/publish |  |
| [**review**](ExerciseVersionReviewControllerApi.md#review) | **POST** /api/v1/admin/exercise-versions/{id}/reviews |  |
| [**reviews**](ExerciseVersionReviewControllerApi.md#reviews) | **GET** /api/v1/admin/exercise-versions/{id}/reviews |  |



## diff

> VersionDiff diff(id)



### Example

```ts
import {
  Configuration,
  ExerciseVersionReviewControllerApi,
} from '@moves/api-client';
import type { DiffRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseVersionReviewControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies DiffRequest;

  try {
    const data = await api.diff(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**VersionDiff**](VersionDiff.md)

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


## publish1

> PublicationResult publish1(id, exerciseVersionPublishRequest)



### Example

```ts
import {
  Configuration,
  ExerciseVersionReviewControllerApi,
} from '@moves/api-client';
import type { Publish1Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseVersionReviewControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ExerciseVersionPublishRequest (optional)
    exerciseVersionPublishRequest: ...,
  } satisfies Publish1Request;

  try {
    const data = await api.publish1(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **exerciseVersionPublishRequest** | [ExerciseVersionPublishRequest](ExerciseVersionPublishRequest.md) |  | [Optional] |

### Return type

[**PublicationResult**](PublicationResult.md)

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


## review

> ReviewResult review(id, reviewCommand)



### Example

```ts
import {
  Configuration,
  ExerciseVersionReviewControllerApi,
} from '@moves/api-client';
import type { ReviewRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseVersionReviewControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ReviewCommand
    reviewCommand: ...,
  } satisfies ReviewRequest;

  try {
    const data = await api.review(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **reviewCommand** | [ReviewCommand](ReviewCommand.md) |  | |

### Return type

[**ReviewResult**](ReviewResult.md)

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


## reviews

> ReviewResult reviews(id)



### Example

```ts
import {
  Configuration,
  ExerciseVersionReviewControllerApi,
} from '@moves/api-client';
import type { ReviewsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseVersionReviewControllerApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ReviewsRequest;

  try {
    const data = await api.reviews(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**ReviewResult**](ReviewResult.md)

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
