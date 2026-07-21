# OnboardingControllerApi

All URIs are relative to *http://localhost*

| Method                                                                           | HTTP request                                      | Description                        |
| -------------------------------------------------------------------------------- | ------------------------------------------------- | ---------------------------------- |
| [**availability**](OnboardingControllerApi.md#availabilityoperation)             | **PUT** /api/v1/onboarding/availability           |                                    |
| [**legal**](OnboardingControllerApi.md#legaloperation)                           | **PUT** /api/v1/onboarding/legal-acknowledgements |                                    |
| [**participantProfile**](OnboardingControllerApi.md#participantprofileoperation) | **PUT** /api/v1/onboarding/participant-profile    |                                    |
| [**selectProfileType**](OnboardingControllerApi.md#selectprofiletype)            | **PUT** /api/v1/onboarding/profile-type           |                                    |
| [**specialistProfile**](OnboardingControllerApi.md#specialistprofileoperation)   | **PUT** /api/v1/onboarding/specialist-profile     |                                    |
| [**state**](OnboardingControllerApi.md#state)                                    | **GET** /api/v1/onboarding                        | Return role-aware onboarding state |

## availability

> State availability(availabilityRequest)

### Example

```ts
import {
  Configuration,
  OnboardingControllerApi,
} from '@moves/api-client';
import type { AvailabilityOperationRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new OnboardingControllerApi(config);

  const body = {
    // AvailabilityRequest
    availabilityRequest: ...,
  } satisfies AvailabilityOperationRequest;

  try {
    const data = await api.availability(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                    | Type                                          | Description | Notes |
| ----------------------- | --------------------------------------------- | ----------- | ----- |
| **availabilityRequest** | [AvailabilityRequest](AvailabilityRequest.md) |             |       |

### Return type

[**State**](State.md)

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

## legal

> State legal(legalRequest)

### Example

```ts
import {
  Configuration,
  OnboardingControllerApi,
} from '@moves/api-client';
import type { LegalOperationRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new OnboardingControllerApi(config);

  const body = {
    // LegalRequest
    legalRequest: ...,
  } satisfies LegalOperationRequest;

  try {
    const data = await api.legal(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name             | Type                            | Description | Notes |
| ---------------- | ------------------------------- | ----------- | ----- |
| **legalRequest** | [LegalRequest](LegalRequest.md) |             |       |

### Return type

[**State**](State.md)

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

## participantProfile

> State participantProfile(participantProfileRequest)

### Example

```ts
import {
  Configuration,
  OnboardingControllerApi,
} from '@moves/api-client';
import type { ParticipantProfileOperationRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new OnboardingControllerApi(config);

  const body = {
    // ParticipantProfileRequest
    participantProfileRequest: ...,
  } satisfies ParticipantProfileOperationRequest;

  try {
    const data = await api.participantProfile(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                          | Type                                                      | Description | Notes |
| ----------------------------- | --------------------------------------------------------- | ----------- | ----- |
| **participantProfileRequest** | [ParticipantProfileRequest](ParticipantProfileRequest.md) |             |       |

### Return type

[**State**](State.md)

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

## selectProfileType

> State selectProfileType(profileTypeRequest)

### Example

```ts
import {
  Configuration,
  OnboardingControllerApi,
} from '@moves/api-client';
import type { SelectProfileTypeRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new OnboardingControllerApi(config);

  const body = {
    // ProfileTypeRequest
    profileTypeRequest: ...,
  } satisfies SelectProfileTypeRequest;

  try {
    const data = await api.selectProfileType(body);
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
| **profileTypeRequest** | [ProfileTypeRequest](ProfileTypeRequest.md) |             |       |

### Return type

[**State**](State.md)

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

## specialistProfile

> State specialistProfile(specialistProfileRequest)

### Example

```ts
import {
  Configuration,
  OnboardingControllerApi,
} from '@moves/api-client';
import type { SpecialistProfileOperationRequest } from '@moves/api-client';

async function example() {
  console.log("🚀 Testing @moves/api-client SDK...");
  const config = new Configuration({
  });
  const api = new OnboardingControllerApi(config);

  const body = {
    // SpecialistProfileRequest
    specialistProfileRequest: ...,
  } satisfies SpecialistProfileOperationRequest;

  try {
    const data = await api.specialistProfile(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                         | Type                                                    | Description | Notes |
| ---------------------------- | ------------------------------------------------------- | ----------- | ----- |
| **specialistProfileRequest** | [SpecialistProfileRequest](SpecialistProfileRequest.md) |             |       |

### Return type

[**State**](State.md)

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

## state

> State state()

Return role-aware onboarding state

### Example

```ts
import { Configuration, OnboardingControllerApi } from '@moves/api-client';
import type { StateRequest } from '@moves/api-client';

async function example() {
  console.log('🚀 Testing @moves/api-client SDK...');
  const config = new Configuration({});
  const api = new OnboardingControllerApi(config);

  try {
    const data = await api.state();
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

[**State**](State.md)

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
