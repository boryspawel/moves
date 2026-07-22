
# PreferenceCommand


## Properties

Name | Type
------------ | -------------
`timeZone` | string
`preferredWindowStart` | string
`preferredWindowEnd` | string
`channel` | string
`quietHoursStart` | string
`quietHoursEnd` | string
`muted` | boolean
`maxMessagesPerWeek` | number
`remindersEnabled` | boolean
`gentleReturnConsent` | boolean

## Example

```typescript
import type { PreferenceCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "timeZone": null,
  "preferredWindowStart": null,
  "preferredWindowEnd": null,
  "channel": null,
  "quietHoursStart": null,
  "quietHoursEnd": null,
  "muted": null,
  "maxMessagesPerWeek": null,
  "remindersEnabled": null,
  "gentleReturnConsent": null,
} satisfies PreferenceCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PreferenceCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


