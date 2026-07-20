
# RuleView


## Properties

Name | Type
------------ | -------------
`id` | string
`versionName` | string
`basePoints` | number
`dailyLimit` | number
`weeklyLimit` | number
`cooldownSeconds` | number
`repeatWindowDays` | number
`fullRewardOccurrences` | number
`reducedRewardPercent` | number

## Example

```typescript
import type { RuleView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "versionName": null,
  "basePoints": null,
  "dailyLimit": null,
  "weeklyLimit": null,
  "cooldownSeconds": null,
  "repeatWindowDays": null,
  "fullRewardOccurrences": null,
  "reducedRewardPercent": null,
} satisfies RuleView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RuleView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
