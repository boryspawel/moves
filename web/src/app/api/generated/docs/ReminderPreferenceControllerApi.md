# ReminderPreferenceControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**get**](ReminderPreferenceControllerApi.md#get) | **GET** /api/v1/participant/reminder-preferences |  |
| [**save**](ReminderPreferenceControllerApi.md#save) | **PUT** /api/v1/participant/reminder-preferences |  |



## get

> PreferenceView get()



### Example

```ts
import {
  Configuration,
  ReminderPreferenceControllerApi,
} from '@moves/api-client';
import type { GetRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({ 
  });
  const api = new ReminderPreferenceControllerApi(config);

  try {
    const data = await api.get();
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

[**PreferenceView**](PreferenceView.md)

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


## save

> PreferenceView save(preferenceCommand)



### Example

```ts
import {
  Configuration,
  ReminderPreferenceControllerApi,
} from '@moves/api-client';
import type { SaveRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({ 
  });
  const api = new ReminderPreferenceControllerApi(config);

  const body = {
    // PreferenceCommand
    preferenceCommand: ...,
  } satisfies SaveRequest;

  try {
    const data = await api.save(body);
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
| **preferenceCommand** | [PreferenceCommand](PreferenceCommand.md) |  | |

### Return type

[**PreferenceView**](PreferenceView.md)

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

