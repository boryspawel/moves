# TodayAgendaControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**today**](TodayAgendaControllerApi.md#today) | **GET** /api/v1/participant/today | Get the signed-in participant\&#39;s daily training agenda |



## today

> TodayAgendaView today()

Get the signed-in participant\&#39;s daily training agenda

### Example

```ts
import {
  Configuration,
  TodayAgendaControllerApi,
} from '@moves/api-client';
import type { TodayRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new TodayAgendaControllerApi(config);

  try {
    const data = await api.today();
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

[**TodayAgendaView**](TodayAgendaView.md)

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
