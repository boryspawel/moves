
# CreateRevisionCommand


## Properties

Name | Type
------------ | -------------
`basedOnRevisionId` | string
`actingContext` | [ActingContext](ActingContext.md)

## Example

```typescript
import type { CreateRevisionCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "basedOnRevisionId": null,
  "actingContext": null,
} satisfies CreateRevisionCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreateRevisionCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
