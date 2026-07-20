
# CorrectionView


## Properties

Name | Type
------------ | -------------
`id` | string
`reason` | string
`correctedPainLevel` | number
`correctedDifficultyLevel` | number
`correctedAt` | Date

## Example

```typescript
import type { CorrectionView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "reason": null,
  "correctedPainLevel": null,
  "correctedDifficultyLevel": null,
  "correctedAt": null,
} satisfies CorrectionView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CorrectionView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
