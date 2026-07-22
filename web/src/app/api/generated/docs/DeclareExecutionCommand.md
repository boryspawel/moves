
# DeclareExecutionCommand


## Properties

Name | Type
------------ | -------------
`declaredCompletion` | boolean
`results` | [Array&lt;ResultCommand&gt;](ResultCommand.md)
`painLevel` | number
`difficultyLevel` | number
`techniqueConfidenceLevel` | number
`note` | string
`sessionRpe` | number
`observationMode` | string

## Example

```typescript
import type { DeclareExecutionCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "declaredCompletion": null,
  "results": null,
  "painLevel": null,
  "difficultyLevel": null,
  "techniqueConfidenceLevel": null,
  "note": null,
  "sessionRpe": null,
  "observationMode": null,
} satisfies DeclareExecutionCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as DeclareExecutionCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
