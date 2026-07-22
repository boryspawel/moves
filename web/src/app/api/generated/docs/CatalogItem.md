
# CatalogItem


## Properties

Name | Type
------------ | -------------
`exerciseId` | string
`canonicalName` | string
`versionId` | string
`versionNumber` | number
`primaryMovementPattern` | string
`technicalLevel` | string
`environment` | string

## Example

```typescript
import type { CatalogItem } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "exerciseId": null,
  "canonicalName": null,
  "versionId": null,
  "versionNumber": null,
  "primaryMovementPattern": null,
  "technicalLevel": null,
  "environment": null,
} satisfies CatalogItem

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CatalogItem
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
