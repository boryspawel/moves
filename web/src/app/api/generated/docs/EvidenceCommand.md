
# EvidenceCommand


## Properties

Name | Type
------------ | -------------
`citation` | string
`sourceUri` | string
`evidenceGrade` | string

## Example

```typescript
import type { EvidenceCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "citation": null,
  "sourceUri": null,
  "evidenceGrade": null,
} satisfies EvidenceCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EvidenceCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
