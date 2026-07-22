# RecoveryView

## Properties

| Name              | Type                |
| ----------------- | ------------------- |
| `episodeId`       | string              |
| `state`           | string              |
| `messageCode`     | string              |
| `policyVersion`   | string              |
| `openedAt`        | Date                |
| `gapDays`         | number              |
| `targetSessionId` | string              |
| `offerId`         | string              |
| `options`         | Array&lt;string&gt; |
| `selectedPath`    | string              |
| `returnState`     | string              |
| `version`         | number              |

## Example

```typescript
import type { RecoveryView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  episodeId: null,
  state: null,
  messageCode: null,
  policyVersion: null,
  openedAt: null,
  gapDays: null,
  targetSessionId: null,
  offerId: null,
  options: null,
  selectedPath: null,
  returnState: null,
  version: null,
} satisfies RecoveryView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RecoveryView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
