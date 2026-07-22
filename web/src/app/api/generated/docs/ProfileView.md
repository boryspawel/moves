
# ProfileView


## Properties

Name | Type
------------ | -------------
`enabled` | boolean
`pseudonym` | string
`rankingVisible` | boolean
`enabledAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { ProfileView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "enabled": null,
  "pseudonym": null,
  "rankingVisible": null,
  "enabledAt": null,
  "updatedAt": null,
} satisfies ProfileView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ProfileView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
