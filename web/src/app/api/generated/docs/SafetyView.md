
# SafetyView


## Properties

Name | Type
------------ | -------------
`contraindicationTags` | Array&lt;string&gt;
`latestCheckIn` | [CheckInView](CheckInView.md)
`notice` | string

## Example

```typescript
import type { SafetyView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "contraindicationTags": null,
  "latestCheckIn": null,
  "notice": null,
} satisfies SafetyView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SafetyView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
