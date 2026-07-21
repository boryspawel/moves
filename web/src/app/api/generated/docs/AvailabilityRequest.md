# AvailabilityRequest

## Properties

| Name    | Type                                       |
| ------- | ------------------------------------------ |
| `slots` | [Array&lt;SlotRequest&gt;](SlotRequest.md) |

## Example

```typescript
import type { AvailabilityRequest } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  slots: null,
} satisfies AvailabilityRequest;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AvailabilityRequest;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
