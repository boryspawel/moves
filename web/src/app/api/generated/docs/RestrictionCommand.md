
# RestrictionCommand


## Properties

Name | Type
------------ | -------------
`semanticType` | string
`validFrom` | Date
`validTo` | Date
`participantExplanation` | string
`clinicalRationaleRef` | string
`target` | [TargetCommand](TargetCommand.md)

## Example

```typescript
import type { RestrictionCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "semanticType": null,
  "validFrom": null,
  "validTo": null,
  "participantExplanation": null,
  "clinicalRationaleRef": null,
  "target": null,
} satisfies RestrictionCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RestrictionCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
