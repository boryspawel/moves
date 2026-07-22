# RecoveryEpisodeControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**choose**](RecoveryEpisodeControllerApi.md#choose) | **POST** /api/v1/participant/recovery-episodes/{episodeId}/choices |  |
| [**current1**](RecoveryEpisodeControllerApi.md#current1) | **GET** /api/v1/participant/recovery-episodes/current |  |



## choose

> RecoveryView choose(episodeId, idempotencyKey, recoveryChoiceCommand)



### Example

```ts
import {
  Configuration,
  RecoveryEpisodeControllerApi,
} from '@moves/api-client';
import type { ChooseRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new RecoveryEpisodeControllerApi(config);

  const body = {
    // string
    episodeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // RecoveryChoiceCommand
    recoveryChoiceCommand: ...,
  } satisfies ChooseRequest;

  try {
    const data = await api.choose(body);
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
| **episodeId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **recoveryChoiceCommand** | [RecoveryChoiceCommand](RecoveryChoiceCommand.md) |  | |

### Return type

[**RecoveryView**](RecoveryView.md)

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


## current1

> RecoveryView current1()



### Example

```ts
import {
  Configuration,
  RecoveryEpisodeControllerApi,
} from '@moves/api-client';
import type { Current1Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new RecoveryEpisodeControllerApi(config);

  try {
    const data = await api.current1();
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

[**RecoveryView**](RecoveryView.md)

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
