
# CreateDraftCommand


## Properties

Name | Type
------------ | -------------
`participantAccountId` | string
`name` | string
`purpose` | string
`mode` | string
`phaseIntent` | string
`validFrom` | Date
`validTo` | Date
`actingContext` | [ActingContext](ActingContext.md)

## Example

```typescript
import type { CreateDraftCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "participantAccountId": null,
  "name": null,
  "purpose": null,
  "mode": null,
  "phaseIntent": null,
  "validFrom": null,
  "validTo": null,
  "actingContext": null,
} satisfies CreateDraftCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreateDraftCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
