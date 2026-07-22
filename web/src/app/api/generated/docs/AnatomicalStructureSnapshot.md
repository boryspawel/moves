
# AnatomicalStructureSnapshot


## Properties

Name | Type
------------ | -------------
`id` | string
`code` | string
`type` | string
`displayName` | string
`sidePolicy` | string
`status` | string
`externalOntology` | string
`externalOntologyId` | string
`taxonomyVersion` | number
`createdAt` | Date
`publishedAt` | Date
`withdrawnAt` | Date

## Example

```typescript
import type { AnatomicalStructureSnapshot } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "code": null,
  "type": null,
  "displayName": null,
  "sidePolicy": null,
  "status": null,
  "externalOntology": null,
  "externalOntologyId": null,
  "taxonomyVersion": null,
  "createdAt": null,
  "publishedAt": null,
  "withdrawnAt": null,
} satisfies AnatomicalStructureSnapshot

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AnatomicalStructureSnapshot
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
