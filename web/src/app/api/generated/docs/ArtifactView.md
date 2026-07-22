
# ArtifactView


## Properties

Name | Type
------------ | -------------
`originalFilename` | string
`mediaType` | string
`byteSize` | number
`sha256` | string

## Example

```typescript
import type { ArtifactView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "originalFilename": null,
  "mediaType": null,
  "byteSize": null,
  "sha256": null,
} satisfies ArtifactView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ArtifactView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
