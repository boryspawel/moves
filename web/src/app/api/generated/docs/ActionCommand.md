
# ActionCommand


## Properties

Name | Type
------------ | -------------
`action` | string
`snoozedUntil` | Date
`usefulnessOutcome` | string

## Example

```typescript
import type { ActionCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "action": null,
  "snoozedUntil": null,
  "usefulnessOutcome": null,
} satisfies ActionCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ActionCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
