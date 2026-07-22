# ConsentControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**grant**](ConsentControllerApi.md#grant) | **POST** /api/v1/consent/grants |  |
| [**revoke**](ConsentControllerApi.md#revoke) | **POST** /api/v1/consent/grants/{grantId}/revoke |  |
| [**template**](ConsentControllerApi.md#template) | **POST** /api/v1/consent/templates |  |



## grant

> GrantView grant(grantCommand)



### Example

```ts
import {
  Configuration,
  ConsentControllerApi,
} from '@moves/api-client';
import type { GrantRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const api = new ConsentControllerApi();

  const body = {
    // GrantCommand
    grantCommand: ...,
  } satisfies GrantRequest;

  try {
    const data = await api.grant(body);
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
| **grantCommand** | [GrantCommand](GrantCommand.md) |  | |

### Return type

[**GrantView**](GrantView.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## revoke

> GrantView revoke(grantId)



### Example

```ts
import {
  Configuration,
  ConsentControllerApi,
} from '@moves/api-client';
import type { RevokeRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const api = new ConsentControllerApi();

  const body = {
    // string
    grantId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies RevokeRequest;

  try {
    const data = await api.revoke(body);
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
| **grantId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**GrantView**](GrantView.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## template

> TemplateView template(templateCommand)



### Example

```ts
import {
  Configuration,
  ConsentControllerApi,
} from '@moves/api-client';
import type { TemplateRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const api = new ConsentControllerApi();

  const body = {
    // TemplateCommand
    templateCommand: ...,
  } satisfies TemplateRequest;

  try {
    const data = await api.template(body);
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
| **templateCommand** | [TemplateCommand](TemplateCommand.md) |  | |

### Return type

[**TemplateView**](TemplateView.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
