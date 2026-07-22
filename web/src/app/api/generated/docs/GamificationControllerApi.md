# GamificationControllerApi

All URIs are relative to *http://localhost*

| Method                                                      | HTTP request                                                          | Description                                                   |
| ----------------------------------------------------------- | --------------------------------------------------------------------- | ------------------------------------------------------------- |
| [**profile**](GamificationControllerApi.md#profile)         | **PUT** /api/v1/gamification/me/profile                               | Enable, disable or configure the private gamification profile |
| [**progress1**](GamificationControllerApi.md#progress1)     | **GET** /api/v1/gamification/me                                       | Return private points and a non-medical ledger view           |
| [**publishRule**](GamificationControllerApi.md#publishrule) | **POST** /api/v1/admin/gamification/rules                             | Publish an immutable point rule version                       |
| [**qualify**](GamificationControllerApi.md#qualify)         | **POST** /api/v1/gamification/executions/{executionId}/qualifications | Qualify a declared execution for points                       |
| [**ranking**](GamificationControllerApi.md#ranking)         | **GET** /api/v1/gamification/ranking                                  | Return the opt-in pseudonymous ranking                        |
| [**rebuild**](GamificationControllerApi.md#rebuild)         | **POST** /api/v1/admin/gamification/ranking/rebuild                   | Rebuild the ranking projection from the point ledger          |
| [**reverse**](GamificationControllerApi.md#reverse)         | **POST** /api/v1/admin/gamification/ledger/{entryId}/reversals        | Append a point reversal without changing ledger history       |

## profile

> ProfileView profile(profileCommand)

Enable, disable or configure the private gamification profile

### Example

```ts
import {
  Configuration,
  GamificationControllerApi,
} from '@moves/api-client';
import type { ProfileRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new GamificationControllerApi(config);

  const body = {
    // ProfileCommand
    profileCommand: ...,
  } satisfies ProfileRequest;

  try {
    const data = await api.profile(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name               | Type                                | Description | Notes |
| ------------------ | ----------------------------------- | ----------- | ----- |
| **profileCommand** | [ProfileCommand](ProfileCommand.md) |             |       |

### Return type

[**ProfileView**](ProfileView.md)

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

## progress1

> ProgressView progress1()

Return private points and a non-medical ledger view

### Example

```ts
import { Configuration, GamificationControllerApi } from '@moves/api-client';
import type { Progress1Request } from '@moves/api-client';

async function example() {
  console.log('🚀 Testing @moves/api-client SDK...');
  const config = new Configuration({});
  const api = new GamificationControllerApi(config);

  try {
    const data = await api.progress1();
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

[**ProgressView**](ProgressView.md)

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

## publishRule

> RuleView publishRule(ruleCommand)

Publish an immutable point rule version

### Example

```ts
import {
  Configuration,
  GamificationControllerApi,
} from '@moves/api-client';
import type { PublishRuleRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new GamificationControllerApi(config);

  const body = {
    // RuleCommand
    ruleCommand: ...,
  } satisfies PublishRuleRequest;

  try {
    const data = await api.publishRule(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name            | Type                          | Description | Notes |
| --------------- | ----------------------------- | ----------- | ----- |
| **ruleCommand** | [RuleCommand](RuleCommand.md) |             |       |

### Return type

[**RuleView**](RuleView.md)

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

## qualify

> QualificationView qualify(executionId, idempotencyKey)

Qualify a declared execution for points

### Example

```ts
import {
  Configuration,
  GamificationControllerApi,
} from '@moves/api-client';
import type { QualifyRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new GamificationControllerApi(config);

  const body = {
    // string
    executionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
  } satisfies QualifyRequest;

  try {
    const data = await api.qualify(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name               | Type     | Description | Notes                     |
| ------------------ | -------- | ----------- | ------------------------- |
| **executionId**    | `string` |             | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |             | [Defaults to `undefined`] |

### Return type

[**QualificationView**](QualificationView.md)

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

## ranking

> Array&lt;RankingRow&gt; ranking()

Return the opt-in pseudonymous ranking

### Example

```ts
import { Configuration, GamificationControllerApi } from '@moves/api-client';
import type { RankingRequest } from '@moves/api-client';

async function example() {
  console.log('🚀 Testing @moves/api-client SDK...');
  const config = new Configuration({});
  const api = new GamificationControllerApi(config);

  try {
    const data = await api.ranking();
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

[**Array&lt;RankingRow&gt;**](RankingRow.md)

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

## rebuild

> Array&lt;RankingRow&gt; rebuild()

Rebuild the ranking projection from the point ledger

### Example

```ts
import { Configuration, GamificationControllerApi } from '@moves/api-client';
import type { RebuildRequest } from '@moves/api-client';

async function example() {
  console.log('🚀 Testing @moves/api-client SDK...');
  const config = new Configuration({});
  const api = new GamificationControllerApi(config);

  try {
    const data = await api.rebuild();
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

[**Array&lt;RankingRow&gt;**](RankingRow.md)

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

## reverse

> LedgerView reverse(entryId, reversalCommand)

Append a point reversal without changing ledger history

### Example

```ts
import {
  Configuration,
  GamificationControllerApi,
} from '@moves/api-client';
import type { ReverseRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new GamificationControllerApi(config);

  const body = {
    // string
    entryId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ReversalCommand
    reversalCommand: ...,
  } satisfies ReverseRequest;

  try {
    const data = await api.reverse(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                | Type                                  | Description | Notes                     |
| ------------------- | ------------------------------------- | ----------- | ------------------------- |
| **entryId**         | `string`                              |             | [Defaults to `undefined`] |
| **reversalCommand** | [ReversalCommand](ReversalCommand.md) |             |                           |

### Return type

[**LedgerView**](LedgerView.md)

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
