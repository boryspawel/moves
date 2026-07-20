
# LegalRequest


## Properties

Name | Type
------------ | -------------
`termsAccepted` | boolean
`privacyNoticeAcknowledged` | boolean

## Example

```typescript
import type { LegalRequest } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "termsAccepted": null,
  "privacyNoticeAcknowledged": null,
} satisfies LegalRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as LegalRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
