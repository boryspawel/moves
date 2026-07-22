
# ProgressView


## Properties

Name | Type
------------ | -------------
`exercisePrescriptionId` | string
`completed` | boolean
`updatedAt` | Date

## Example

```typescript
import type { ProgressView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "exercisePrescriptionId": null,
  "completed": null,
  "updatedAt": null,
} satisfies ProgressView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ProgressView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
