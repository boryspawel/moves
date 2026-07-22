
# TemplateCommand


## Properties

Name | Type
------------ | -------------
`code` | string
`versionNumber` | number
`contentReference` | string
`legalBasis` | string

## Example

```typescript
import type { TemplateCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "code": null,
  "versionNumber": null,
  "contentReference": null,
  "legalBasis": null,
} satisfies TemplateCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TemplateCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
