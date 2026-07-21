# CheckInRequest

## Properties

| Name             | Type   |
| ---------------- | ------ |
| `painLevel`      | number |
| `readinessLevel` | number |
| `painArea`       | string |

## Example

```typescript
import type { CheckInRequest } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  painLevel: null,
  readinessLevel: null,
  painArea: null,
} satisfies CheckInRequest;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CheckInRequest;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
