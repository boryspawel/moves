# CurrentIdentityControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**current1**](CurrentIdentityControllerApi.md#current1) | **GET** /api/v1/identity/me | Return the authenticated external identity |



## current1

> IdentityResponse current1()

Return the authenticated external identity

### Example

```ts
import {
  Configuration,
  CurrentIdentityControllerApi,
} from '@moves/api-client';
import type { Current1Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new CurrentIdentityControllerApi(config);

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

[**IdentityResponse**](IdentityResponse.md)

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
