
# SessionSnapshot


## Properties

Name | Type
------------ | -------------
`id` | string
`title` | string
`scheduledDate` | Date
`availableFrom` | Date
`availableTo` | Date
`expectedDurationMinutes` | number
`status` | string
`prescriptions` | [Array&lt;PrescriptionSnapshot&gt;](PrescriptionSnapshot.md)
`variants` | [Array&lt;SessionVariantSnapshot&gt;](SessionVariantSnapshot.md)

## Example

```typescript
import type { SessionSnapshot } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "title": null,
  "scheduledDate": null,
  "availableFrom": null,
  "availableTo": null,
  "expectedDurationMinutes": null,
  "status": null,
  "prescriptions": null,
  "variants": null,
} satisfies SessionSnapshot

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SessionSnapshot
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
