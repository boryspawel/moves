
# GoalOutcomeSnapshot


## Properties

Name | Type
------------ | -------------
`id` | string
`metricCode` | string
`baseline` | number
`target` | number
`unit` | string
`measurementMethod` | string
`evidenceSource` | string

## Example

```typescript
import type { GoalOutcomeSnapshot } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "metricCode": null,
  "baseline": null,
  "target": null,
  "unit": null,
  "measurementMethod": null,
  "evidenceSource": null,
} satisfies GoalOutcomeSnapshot

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as GoalOutcomeSnapshot
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
