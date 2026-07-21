# State

## Properties

| Name                           | Type                                |
| ------------------------------ | ----------------------------------- |
| `stage`                        | string                              |
| `missingSteps`                 | Array&lt;string&gt;                 |
| `profileType`                  | string                              |
| `profile`                      | [ProfileSummary](ProfileSummary.md) |
| `currentLegalAcknowledgements` | [Array&lt;View&gt;](View.md)        |
| `availability`                 | [Array&lt;Slot&gt;](Slot.md)        |

## Example

```typescript
import type { State } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  stage: null,
  missingSteps: null,
  profileType: null,
  profile: null,
  currentLegalAcknowledgements: null,
  availability: null,
} satisfies State;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as State;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
