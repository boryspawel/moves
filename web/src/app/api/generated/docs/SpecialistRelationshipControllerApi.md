# SpecialistRelationshipControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**activeParticipants**](SpecialistRelationshipControllerApi.md#activeparticipants) | **GET** /api/v1/specialist/participants | List participants with an active specialist relationship for UI selection |



## activeParticipants

> Array&lt;ActiveParticipantView&gt; activeParticipants()

List participants with an active specialist relationship for UI selection

### Example

```ts
import {
  Configuration,
  SpecialistRelationshipControllerApi,
} from '@moves/api-client';
import type { ActiveParticipantsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SpecialistRelationshipControllerApi(config);

  try {
    const data = await api.activeParticipants();
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

[**Array&lt;ActiveParticipantView&gt;**](ActiveParticipantView.md)

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
