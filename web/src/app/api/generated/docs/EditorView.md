
# EditorView


## Properties

Name | Type
------------ | -------------
`planId` | string
`participantAccountId` | string
`name` | string
`purpose` | string
`ownerAccountId` | string
`mode` | string
`planStatus` | string
`currentRevisionId` | string
`revision` | [PlanRevisionSnapshot](PlanRevisionSnapshot.md)

## Example

```typescript
import type { EditorView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "planId": null,
  "participantAccountId": null,
  "name": null,
  "purpose": null,
  "ownerAccountId": null,
  "mode": null,
  "planStatus": null,
  "currentRevisionId": null,
  "revision": null,
} satisfies EditorView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EditorView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
