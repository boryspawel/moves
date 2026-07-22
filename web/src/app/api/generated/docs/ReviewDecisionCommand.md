
# ReviewDecisionCommand


## Properties

Name | Type
------------ | -------------
`decision` | string
`decisionReference` | string

## Example

```typescript
import type { ReviewDecisionCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "decision": null,
  "decisionReference": null,
} satisfies ReviewDecisionCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ReviewDecisionCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
