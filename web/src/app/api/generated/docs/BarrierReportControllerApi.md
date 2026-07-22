# BarrierReportControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**report**](BarrierReportControllerApi.md#report) | **POST** /api/v1/participant/barrier-reports | Report a session barrier and receive deterministic, safe options |



## report

> BarrierReportView report(idempotencyKey, barrierReportCommand)

Report a session barrier and receive deterministic, safe options

### Example

```ts
import {
  Configuration,
  BarrierReportControllerApi,
} from '@moves/api-client';
import type { ReportRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new BarrierReportControllerApi(config);

  const body = {
    // string
    idempotencyKey: idempotencyKey_example,
    // BarrierReportCommand
    barrierReportCommand: ...,
  } satisfies ReportRequest;

  try {
    const data = await api.report(body);
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
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **barrierReportCommand** | [BarrierReportCommand](BarrierReportCommand.md) |  | |

### Return type

[**BarrierReportView**](BarrierReportView.md)

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
