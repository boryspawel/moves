# ParticipantSafetyControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**checkIn**](ParticipantSafetyControllerApi.md#checkinoperation) | **POST** /api/v1/safety/me/check-ins |  |
| [**current**](ParticipantSafetyControllerApi.md#current) | **GET** /api/v1/safety/me | Return only the authenticated participant\&#39;s non-diagnostic safety inputs |
| [**restrictions**](ParticipantSafetyControllerApi.md#restrictions) | **PUT** /api/v1/safety/me/restrictions |  |



## checkIn

> SafetyView checkIn(checkInRequest)



### Example

```ts
import {
  Configuration,
  ParticipantSafetyControllerApi,
} from '@moves/api-client';
import type { CheckInOperationRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ParticipantSafetyControllerApi(config);

  const body = {
    // CheckInRequest
    checkInRequest: ...,
  } satisfies CheckInOperationRequest;

  try {
    const data = await api.checkIn(body);
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
| **checkInRequest** | [CheckInRequest](CheckInRequest.md) |  | |

### Return type

[**SafetyView**](SafetyView.md)

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


## current

> SafetyView current()

Return only the authenticated participant\&#39;s non-diagnostic safety inputs

### Example

```ts
import {
  Configuration,
  ParticipantSafetyControllerApi,
} from '@moves/api-client';
import type { CurrentRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ParticipantSafetyControllerApi(config);

  try {
    const data = await api.current();
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

[**SafetyView**](SafetyView.md)

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


## restrictions

> SafetyView restrictions(restrictionRequest)



### Example

```ts
import {
  Configuration,
  ParticipantSafetyControllerApi,
} from '@moves/api-client';
import type { RestrictionsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ParticipantSafetyControllerApi(config);

  const body = {
    // RestrictionRequest
    restrictionRequest: ...,
  } satisfies RestrictionsRequest;

  try {
    const data = await api.restrictions(body);
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
| **restrictionRequest** | [RestrictionRequest](RestrictionRequest.md) |  | |

### Return type

[**SafetyView**](SafetyView.md)

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
