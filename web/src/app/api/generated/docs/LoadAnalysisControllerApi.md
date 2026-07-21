# LoadAnalysisControllerApi

All URIs are relative to *http://localhost*

| Method                                              | HTTP request                                                       | Description |
| --------------------------------------------------- | ------------------------------------------------------------------ | ----------- |
| [**preview**](LoadAnalysisControllerApi.md#preview) | **GET** /api/v1/training-plans/revisions/{revisionId}/load-preview |             |

## preview

> LoadProfile preview(revisionId, algorithmVersion, configurationVersion)

### Example

```ts
import {
  Configuration,
  LoadAnalysisControllerApi,
} from '@moves/api-client';
import type { PreviewRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const api = new LoadAnalysisControllerApi();

  const body = {
    // string
    revisionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    algorithmVersion: algorithmVersion_example,
    // string (optional)
    configurationVersion: configurationVersion_example,
  } satisfies PreviewRequest;

  try {
    const data = await api.preview(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                     | Type     | Description | Notes                                           |
| ------------------------ | -------- | ----------- | ----------------------------------------------- |
| **revisionId**           | `string` |             | [Defaults to `undefined`]                       |
| **algorithmVersion**     | `string` |             | [Optional] [Defaults to `&#39;LOAD_V1&#39;`]    |
| **configurationVersion** | `string` |             | [Optional] [Defaults to `&#39;DEFAULT_V1&#39;`] |

### Return type

[**LoadProfile**](LoadProfile.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
