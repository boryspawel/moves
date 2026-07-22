
# OverrideView


## Properties

Name | Type
------------ | -------------
`id` | string
`assessmentId` | string
`factorId` | string
`reasonCode` | string
`scope` | string
`validFrom` | Date
`validTo` | Date

## Example

```typescript
import type { OverrideView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "assessmentId": null,
  "factorId": null,
  "reasonCode": null,
  "scope": null,
  "validFrom": null,
  "validTo": null,
} satisfies OverrideView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as OverrideView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
