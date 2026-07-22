
# TargetView


## Properties

Name | Type
------------ | -------------
`structureId` | string
`movementPattern` | string
`channel` | string
`loadCharacteristic` | string
`side` | string
`rangeOfMotion` | string
`contractionType` | string
`limitLow` | number
`limitHigh` | number
`unit` | string
`minimumRecoveryHours` | number

## Example

```typescript
import type { TargetView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "structureId": null,
  "movementPattern": null,
  "channel": null,
  "loadCharacteristic": null,
  "side": null,
  "rangeOfMotion": null,
  "contractionType": null,
  "limitLow": null,
  "limitHigh": null,
  "unit": null,
  "minimumRecoveryHours": null,
} satisfies TargetView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TargetView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
