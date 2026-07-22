# DefineSessionVariantCommand

## Properties

| Name                      | Type                                                     |
| ------------------------- | -------------------------------------------------------- |
| `expectedVersion`         | number                                                   |
| `sessionId`               | string                                                   |
| `type`                    | string                                                   |
| `expectedDurationMinutes` | number                                                   |
| `items`                   | [Array&lt;VariantItemCommand&gt;](VariantItemCommand.md) |

## Example

```typescript
import type { DefineSessionVariantCommand } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  expectedVersion: null,
  sessionId: null,
  type: null,
  expectedDurationMinutes: null,
  items: null,
} satisfies DefineSessionVariantCommand;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as DefineSessionVariantCommand;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
