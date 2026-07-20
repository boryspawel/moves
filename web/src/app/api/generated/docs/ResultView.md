
# ResultView


## Properties

Name | Type
------------ | -------------
`exercisePrescriptionId` | string
`actualRepetitions` | number
`actualDurationSeconds` | number
`actualLoadKg` | number

## Example

```typescript
import type { ResultView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "exercisePrescriptionId": null,
  "actualRepetitions": null,
  "actualDurationSeconds": null,
  "actualLoadKg": null,
} satisfies ResultView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ResultView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
