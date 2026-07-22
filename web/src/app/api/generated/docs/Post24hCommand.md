
# Post24hCommand


## Properties

Name | Type
------------ | -------------
`painLevel` | number
`difficultyLevel` | number
`note` | string
`observationMode` | string

## Example

```typescript
import type { Post24hCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "painLevel": null,
  "difficultyLevel": null,
  "note": null,
  "observationMode": null,
} satisfies Post24hCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Post24hCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
