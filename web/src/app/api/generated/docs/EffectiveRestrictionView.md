
# EffectiveRestrictionView


## Properties

Name | Type
------------ | -------------
`restrictionId` | string
`validFrom` | Date
`validTo` | Date
`explanationCode` | string
`target` | [TargetView](TargetView.md)

## Example

```typescript
import type { EffectiveRestrictionView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "restrictionId": null,
  "validFrom": null,
  "validTo": null,
  "explanationCode": null,
  "target": null,
} satisfies EffectiveRestrictionView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EffectiveRestrictionView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
