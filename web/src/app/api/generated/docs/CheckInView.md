
# CheckInView


## Properties

Name | Type
------------ | -------------
`id` | string
`painLevel` | number
`readinessLevel` | number
`painArea` | string
`recordedAt` | Date

## Example

```typescript
import type { CheckInView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "painLevel": null,
  "readinessLevel": null,
  "painArea": null,
  "recordedAt": null,
} satisfies CheckInView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CheckInView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
