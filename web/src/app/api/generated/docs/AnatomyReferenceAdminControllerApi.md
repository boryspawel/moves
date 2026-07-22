# AnatomyReferenceAdminControllerApi

All URIs are relative to *http://localhost*

| Method                                                                        | HTTP request                                                        | Description                                                   |
| ----------------------------------------------------------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------- |
| [**addRelation**](AnatomyReferenceAdminControllerApi.md#addrelationoperation) | **POST** /api/v1/admin/anatomical-structures/relations              | Add an acyclic parent-child relation between draft structures |
| [**ancestors**](AnatomyReferenceAdminControllerApi.md#ancestors)              | **GET** /api/v1/admin/anatomical-structures/{structureId}/ancestors |                                                               |
| [**create1**](AnatomyReferenceAdminControllerApi.md#create1)                  | **POST** /api/v1/admin/anatomical-structures                        | Create a draft anatomical structure                           |
| [**get1**](AnatomyReferenceAdminControllerApi.md#get1)                        | **GET** /api/v1/admin/anatomical-structures/{structureId}           |                                                               |
| [**publish2**](AnatomyReferenceAdminControllerApi.md#publish2)                | **POST** /api/v1/admin/anatomical-structures/{structureId}/publish  | Publish an immutable anatomical structure                     |
| [**withdraw1**](AnatomyReferenceAdminControllerApi.md#withdraw1)              | **POST** /api/v1/admin/anatomical-structures/{structureId}/withdraw | Withdraw a published anatomical structure                     |

## addRelation

> RelationSnapshot addRelation(addRelationRequest)

Add an acyclic parent-child relation between draft structures

### Example

```ts
import {
  Configuration,
  AnatomyReferenceAdminControllerApi,
} from '@moves/api-client';
import type { AddRelationOperationRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new AnatomyReferenceAdminControllerApi(config);

  const body = {
    // AddRelationRequest
    addRelationRequest: ...,
  } satisfies AddRelationOperationRequest;

  try {
    const data = await api.addRelation(body);
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
| **addRelationRequest** | [AddRelationRequest](AddRelationRequest.md) |             |       |

### Return type

[**RelationSnapshot**](RelationSnapshot.md)

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

## ancestors

> Array&lt;AncestorPath&gt; ancestors(structureId)

### Example

```ts
import {
  Configuration,
  AnatomyReferenceAdminControllerApi,
} from '@moves/api-client';
import type { AncestorsRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new AnatomyReferenceAdminControllerApi(config);

  const body = {
    // string
    structureId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies AncestorsRequest;

  try {
    const data = await api.ancestors(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name            | Type     | Description | Notes                     |
| --------------- | -------- | ----------- | ------------------------- |
| **structureId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**Array&lt;AncestorPath&gt;**](AncestorPath.md)

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

## create1

> AnatomicalStructureSnapshot create1(createStructureRequest)

Create a draft anatomical structure

### Example

```ts
import {
  Configuration,
  AnatomyReferenceAdminControllerApi,
} from '@moves/api-client';
import type { Create1Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new AnatomyReferenceAdminControllerApi(config);

  const body = {
    // CreateStructureRequest
    createStructureRequest: ...,
  } satisfies Create1Request;

  try {
    const data = await api.create1(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                       | Type                                                | Description | Notes |
| -------------------------- | --------------------------------------------------- | ----------- | ----- |
| **createStructureRequest** | [CreateStructureRequest](CreateStructureRequest.md) |             |       |

### Return type

[**AnatomicalStructureSnapshot**](AnatomicalStructureSnapshot.md)

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

## get1

> AnatomicalStructureSnapshot get1(structureId)

### Example

```ts
import {
  Configuration,
  AnatomyReferenceAdminControllerApi,
} from '@moves/api-client';
import type { Get1Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new AnatomyReferenceAdminControllerApi(config);

  const body = {
    // string
    structureId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies Get1Request;

  try {
    const data = await api.get1(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name            | Type     | Description | Notes                     |
| --------------- | -------- | ----------- | ------------------------- |
| **structureId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**AnatomicalStructureSnapshot**](AnatomicalStructureSnapshot.md)

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

## publish2

> AnatomicalStructureSnapshot publish2(structureId)

Publish an immutable anatomical structure

### Example

```ts
import {
  Configuration,
  AnatomyReferenceAdminControllerApi,
} from '@moves/api-client';
import type { Publish2Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new AnatomyReferenceAdminControllerApi(config);

  const body = {
    // string
    structureId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies Publish2Request;

  try {
    const data = await api.publish2(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name            | Type     | Description | Notes                     |
| --------------- | -------- | ----------- | ------------------------- |
| **structureId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**AnatomicalStructureSnapshot**](AnatomicalStructureSnapshot.md)

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

## withdraw1

> AnatomicalStructureSnapshot withdraw1(structureId)

Withdraw a published anatomical structure

### Example

```ts
import {
  Configuration,
  AnatomyReferenceAdminControllerApi,
} from '@moves/api-client';
import type { Withdraw1Request } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new AnatomyReferenceAdminControllerApi(config);

  const body = {
    // string
    structureId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies Withdraw1Request;

  try {
    const data = await api.withdraw1(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name            | Type     | Description | Notes                     |
| --------------- | -------- | ----------- | ------------------------- |
| **structureId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**AnatomicalStructureSnapshot**](AnatomicalStructureSnapshot.md)

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
