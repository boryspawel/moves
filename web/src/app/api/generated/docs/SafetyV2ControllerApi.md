# SafetyV2ControllerApi

All URIs are relative to *http://localhost*

| Method                                                                              | HTTP request                                                                                                 | Description |
| ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ----------- |
| [**clinicalRestriction**](SafetyV2ControllerApi.md#clinicalrestriction)             | **POST** /api/v2/safety/participants/{participantId}/restrictions                                            |             |
| [**clinicalRestrictions**](SafetyV2ControllerApi.md#clinicalrestrictions)           | **GET** /api/v2/safety/participants/{participantId}/clinical-restrictions                                    |             |
| [**declare**](SafetyV2ControllerApi.md#declare)                                     | **POST** /api/v2/safety/me/restrictions                                                                      |             |
| [**effectiveRestrictions**](SafetyV2ControllerApi.md#effectiverestrictions)         | **GET** /api/v2/safety/participants/{participantId}/effective-restrictions                                   |             |
| [**history1**](SafetyV2ControllerApi.md#history1)                                   | **GET** /api/v2/safety/me/restrictions/history                                                               |             |
| [**legacyReport**](SafetyV2ControllerApi.md#legacyreport)                           | **GET** /api/v2/safety/admin/legacy/participant-restrictions                                                 |             |
| [**override**](SafetyV2ControllerApi.md#override)                                   | **POST** /api/v2/safety/participants/{participantId}/assessments/{assessmentId}/factors/{factorId}/overrides |             |
| [**revise**](SafetyV2ControllerApi.md#revise)                                       | **PATCH** /api/v2/safety/me/restrictions/{restrictionId}                                                     |             |
| [**reviseClinicalRestriction**](SafetyV2ControllerApi.md#reviseclinicalrestriction) | **PATCH** /api/v2/safety/participants/{participantId}/restrictions/{restrictionId}                           |             |
| [**withdraw2**](SafetyV2ControllerApi.md#withdraw2)                                 | **DELETE** /api/v2/safety/me/restrictions/{restrictionId}                                                    |             |

## clinicalRestriction

> RestrictionView clinicalRestriction(participantId, actingContext, restrictionCommand)

### Example

```ts
import {
  Configuration,
  SafetyV2ControllerApi,
} from '@moves/api-client';
import type { ClinicalRestrictionRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SafetyV2ControllerApi(config);

  const body = {
    // string
    participantId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'TRAINER' | 'PHYSIOTHERAPIST'
    actingContext: actingContext_example,
    // RestrictionCommand
    restrictionCommand: ...,
  } satisfies ClinicalRestrictionRequest;

  try {
    const data = await api.clinicalRestriction(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                   | Type                                        | Description | Notes                                                      |
| ---------------------- | ------------------------------------------- | ----------- | ---------------------------------------------------------- |
| **participantId**      | `string`                                    |             | [Defaults to `undefined`]                                  |
| **actingContext**      | `TRAINER`, `PHYSIOTHERAPIST`                |             | [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST] |
| **restrictionCommand** | [RestrictionCommand](RestrictionCommand.md) |             |                                                            |

### Return type

[**RestrictionView**](RestrictionView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## clinicalRestrictions

> Array&lt;ClinicalRestrictionView&gt; clinicalRestrictions(participantId, actingContext)

### Example

```ts
import {
  Configuration,
  SafetyV2ControllerApi,
} from '@moves/api-client';
import type { ClinicalRestrictionsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SafetyV2ControllerApi(config);

  const body = {
    // string
    participantId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'TRAINER' | 'PHYSIOTHERAPIST'
    actingContext: actingContext_example,
  } satisfies ClinicalRestrictionsRequest;

  try {
    const data = await api.clinicalRestrictions(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name              | Type                         | Description | Notes                                                      |
| ----------------- | ---------------------------- | ----------- | ---------------------------------------------------------- |
| **participantId** | `string`                     |             | [Defaults to `undefined`]                                  |
| **actingContext** | `TRAINER`, `PHYSIOTHERAPIST` |             | [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST] |

### Return type

[**Array&lt;ClinicalRestrictionView&gt;**](ClinicalRestrictionView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## declare

> RestrictionView declare(restrictionCommand)

### Example

```ts
import {
  Configuration,
  SafetyV2ControllerApi,
} from '@moves/api-client';
import type { DeclareRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SafetyV2ControllerApi(config);

  const body = {
    // RestrictionCommand
    restrictionCommand: ...,
  } satisfies DeclareRequest;

  try {
    const data = await api.declare(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                   | Type                                        | Description | Notes |
| ---------------------- | ------------------------------------------- | ----------- | ----- |
| **restrictionCommand** | [RestrictionCommand](RestrictionCommand.md) |             |       |

### Return type

[**RestrictionView**](RestrictionView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## effectiveRestrictions

> Array&lt;EffectiveRestrictionView&gt; effectiveRestrictions(participantId, actingContext)

### Example

```ts
import {
  Configuration,
  SafetyV2ControllerApi,
} from '@moves/api-client';
import type { EffectiveRestrictionsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SafetyV2ControllerApi(config);

  const body = {
    // string
    participantId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'TRAINER' | 'PHYSIOTHERAPIST'
    actingContext: actingContext_example,
  } satisfies EffectiveRestrictionsRequest;

  try {
    const data = await api.effectiveRestrictions(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name              | Type                         | Description | Notes                                                      |
| ----------------- | ---------------------------- | ----------- | ---------------------------------------------------------- |
| **participantId** | `string`                     |             | [Defaults to `undefined`]                                  |
| **actingContext** | `TRAINER`, `PHYSIOTHERAPIST` |             | [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST] |

### Return type

[**Array&lt;EffectiveRestrictionView&gt;**](EffectiveRestrictionView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## history1

> Array&lt;RestrictionView&gt; history1()

### Example

```ts
import { Configuration, SafetyV2ControllerApi } from '@moves/api-client';
import type { History1Request } from '@moves/api-client';

async function example() {
  console.log('🚀 Testing @moves/api-client SDK...');
  const config = new Configuration({});
  const api = new SafetyV2ControllerApi(config);

  try {
    const data = await api.history1();
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

[**Array&lt;RestrictionView&gt;**](RestrictionView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## legacyReport

> LegacyReport legacyReport()

### Example

```ts
import { Configuration, SafetyV2ControllerApi } from '@moves/api-client';
import type { LegacyReportRequest } from '@moves/api-client';

async function example() {
  console.log('🚀 Testing @moves/api-client SDK...');
  const config = new Configuration({});
  const api = new SafetyV2ControllerApi(config);

  try {
    const data = await api.legacyReport();
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

[**LegacyReport**](LegacyReport.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## override

> OverrideView override(participantId, assessmentId, factorId, actingContext, overrideCommand)

### Example

```ts
import {
  Configuration,
  SafetyV2ControllerApi,
} from '@moves/api-client';
import type { OverrideRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SafetyV2ControllerApi(config);

  const body = {
    // string
    participantId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    assessmentId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    factorId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'TRAINER' | 'PHYSIOTHERAPIST'
    actingContext: actingContext_example,
    // OverrideCommand
    overrideCommand: ...,
  } satisfies OverrideRequest;

  try {
    const data = await api.override(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                | Type                                  | Description | Notes                                                      |
| ------------------- | ------------------------------------- | ----------- | ---------------------------------------------------------- |
| **participantId**   | `string`                              |             | [Defaults to `undefined`]                                  |
| **assessmentId**    | `string`                              |             | [Defaults to `undefined`]                                  |
| **factorId**        | `string`                              |             | [Defaults to `undefined`]                                  |
| **actingContext**   | `TRAINER`, `PHYSIOTHERAPIST`          |             | [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST] |
| **overrideCommand** | [OverrideCommand](OverrideCommand.md) |             |                                                            |

### Return type

[**OverrideView**](OverrideView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## revise

> RestrictionView revise(restrictionId, restrictionCommand)

### Example

```ts
import {
  Configuration,
  SafetyV2ControllerApi,
} from '@moves/api-client';
import type { ReviseRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SafetyV2ControllerApi(config);

  const body = {
    // string
    restrictionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // RestrictionCommand
    restrictionCommand: ...,
  } satisfies ReviseRequest;

  try {
    const data = await api.revise(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                   | Type                                        | Description | Notes                     |
| ---------------------- | ------------------------------------------- | ----------- | ------------------------- |
| **restrictionId**      | `string`                                    |             | [Defaults to `undefined`] |
| **restrictionCommand** | [RestrictionCommand](RestrictionCommand.md) |             |                           |

### Return type

[**RestrictionView**](RestrictionView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## reviseClinicalRestriction

> RestrictionView reviseClinicalRestriction(participantId, restrictionId, actingContext, restrictionCommand)

### Example

```ts
import {
  Configuration,
  SafetyV2ControllerApi,
} from '@moves/api-client';
import type { ReviseClinicalRestrictionRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SafetyV2ControllerApi(config);

  const body = {
    // string
    participantId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    restrictionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'TRAINER' | 'PHYSIOTHERAPIST'
    actingContext: actingContext_example,
    // RestrictionCommand
    restrictionCommand: ...,
  } satisfies ReviseClinicalRestrictionRequest;

  try {
    const data = await api.reviseClinicalRestriction(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                   | Type                                        | Description | Notes                                                      |
| ---------------------- | ------------------------------------------- | ----------- | ---------------------------------------------------------- |
| **participantId**      | `string`                                    |             | [Defaults to `undefined`]                                  |
| **restrictionId**      | `string`                                    |             | [Defaults to `undefined`]                                  |
| **actingContext**      | `TRAINER`, `PHYSIOTHERAPIST`                |             | [Defaults to `undefined`] [Enum: TRAINER, PHYSIOTHERAPIST] |
| **restrictionCommand** | [RestrictionCommand](RestrictionCommand.md) |             |                                                            |

### Return type

[**RestrictionView**](RestrictionView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## withdraw2

> RestrictionView withdraw2(restrictionId)

### Example

```ts
import {
  Configuration,
  SafetyV2ControllerApi,
} from '@moves/api-client';
import type { Withdraw2Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new SafetyV2ControllerApi(config);

  const body = {
    // string
    restrictionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies Withdraw2Request;

  try {
    const data = await api.withdraw2(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name              | Type     | Description | Notes                     |
| ----------------- | -------- | ----------- | ------------------------- |
| **restrictionId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**RestrictionView**](RestrictionView.md)

### Authorization

[oidc](../README.md#oidc)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
