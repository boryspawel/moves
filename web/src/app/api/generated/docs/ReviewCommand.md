
# ReviewCommand


## Properties

Name | Type
------------ | -------------
`area` | string
`decision` | string
`comment` | string
`expectedVersion` | number

## Example

```typescript
import type { ReviewCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "area": null,
  "decision": null,
  "comment": null,
  "expectedVersion": null,
} satisfies ReviewCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ReviewCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
