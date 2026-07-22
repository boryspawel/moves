# SourceView

## Properties

| Name              | Type    |
| ----------------- | ------- |
| `id`              | string  |
| `code`            | string  |
| `displayName`     | string  |
| `defaultLocale`   | string  |
| `licenseCode`     | string  |
| `licenseVerified` | boolean |
| `active`          | boolean |
| `createdAt`       | Date    |
| `version`         | number  |

## Example

```typescript
import type { SourceView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  code: null,
  displayName: null,
  defaultLocale: null,
  licenseCode: null,
  licenseVerified: null,
  active: null,
  createdAt: null,
  version: null,
} satisfies SourceView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SourceView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
