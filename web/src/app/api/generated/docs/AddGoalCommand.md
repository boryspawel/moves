
# AddGoalCommand


## Properties

Name | Type
------------ | -------------
`expectedVersion` | number
`perspective` | string
`category` | string
`title` | string
`description` | string
`priority` | number
`status` | string
`targetDate` | Date
`outcomes` | [Array&lt;OutcomeCommand&gt;](OutcomeCommand.md)

## Example

```typescript
import type { AddGoalCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "expectedVersion": null,
  "perspective": null,
  "category": null,
  "title": null,
  "description": null,
  "priority": null,
  "status": null,
  "targetDate": null,
  "outcomes": null,
} satisfies AddGoalCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AddGoalCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
