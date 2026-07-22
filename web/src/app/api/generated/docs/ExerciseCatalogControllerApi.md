# ExerciseCatalogControllerApi

All URIs are relative to *http://localhost*

| Method                                                 | HTTP request                                   | Description                                                         |
| ------------------------------------------------------ | ---------------------------------------------- | ------------------------------------------------------------------- |
| [**list**](ExerciseCatalogControllerApi.md#list)       | **GET** /api/v1/exercises                      | Search published exercise versions using explicitly allowed filters |
| [**version**](ExerciseCatalogControllerApi.md#version) | **GET** /api/v1/exercises/versions/{versionId} | Read a public detail projection of one published exercise version   |

## list

> CatalogPage list(query, movementPattern, technicalLevel, equipment, page, size)

Search published exercise versions using explicitly allowed filters

### Example

```ts
import { Configuration, ExerciseCatalogControllerApi } from '@moves/api-client';
import type { ListRequest } from '@moves/api-client';

async function example() {
  console.log('🚀 Testing @moves/api-client SDK...');
  const config = new Configuration({});
  const api = new ExerciseCatalogControllerApi(config);

  const body = {
    // string (optional)
    query: query_example,
    // 'SQUAT' | 'HINGE' | 'PUSH' | 'PULL' | 'LUNGE' | 'CARRY' | 'ROTATION' | 'LOCOMOTION' | 'BREATHING' | 'MOBILITY' | 'OTHER' (optional)
    movementPattern: movementPattern_example,
    // 'FOUNDATIONAL' | 'INTERMEDIATE' | 'ADVANCED' (optional)
    technicalLevel: technicalLevel_example,
    // string (optional)
    equipment: equipment_example,
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
  } satisfies ListRequest;

  try {
    const data = await api.list(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                | Type                                                                                                           | Description | Notes                                                                                                                                 |
| ------------------- | -------------------------------------------------------------------------------------------------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| **query**           | `string`                                                                                                       |             | [Optional] [Defaults to `undefined`]                                                                                                  |
| **movementPattern** | `SQUAT`, `HINGE`, `PUSH`, `PULL`, `LUNGE`, `CARRY`, `ROTATION`, `LOCOMOTION`, `BREATHING`, `MOBILITY`, `OTHER` |             | [Optional] [Defaults to `undefined`] [Enum: SQUAT, HINGE, PUSH, PULL, LUNGE, CARRY, ROTATION, LOCOMOTION, BREATHING, MOBILITY, OTHER] |
| **technicalLevel**  | `FOUNDATIONAL`, `INTERMEDIATE`, `ADVANCED`                                                                     |             | [Optional] [Defaults to `undefined`] [Enum: FOUNDATIONAL, INTERMEDIATE, ADVANCED]                                                     |
| **equipment**       | `string`                                                                                                       |             | [Optional] [Defaults to `undefined`]                                                                                                  |
| **page**            | `number`                                                                                                       |             | [Optional] [Defaults to `0`]                                                                                                          |
| **size**            | `number`                                                                                                       |             | [Optional] [Defaults to `20`]                                                                                                         |

### Return type

[**CatalogPage**](CatalogPage.md)

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

## version

> ExerciseCatalogDetailView version(versionId)

Read a public detail projection of one published exercise version

### Example

```ts
import {
  Configuration,
  ExerciseCatalogControllerApi,
} from '@moves/api-client';
import type { VersionRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new ExerciseCatalogControllerApi(config);

  const body = {
    // string
    versionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies VersionRequest;

  try {
    const data = await api.version(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name          | Type     | Description | Notes                     |
| ------------- | -------- | ----------- | ------------------------- |
| **versionId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**ExerciseCatalogDetailView**](ExerciseCatalogDetailView.md)

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
