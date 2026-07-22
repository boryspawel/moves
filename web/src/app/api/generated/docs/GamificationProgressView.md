
# GamificationProgressView


## Properties

Name | Type
------------ | -------------
`profile` | [ProfileView](ProfileView.md)
`points` | number
`ledger` | [Array&lt;LedgerView&gt;](LedgerView.md)

## Example

```typescript
import type { GamificationProgressView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "profile": null,
  "points": null,
  "ledger": null,
} satisfies GamificationProgressView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as GamificationProgressView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
