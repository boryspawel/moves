
# ProfileCommand


## Properties

Name | Type
------------ | -------------
`enabled` | boolean
`pseudonym` | string
`rankingVisible` | boolean

## Example

```typescript
import type { ProfileCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "enabled": null,
  "pseudonym": null,
  "rankingVisible": null,
} satisfies ProfileCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ProfileCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
