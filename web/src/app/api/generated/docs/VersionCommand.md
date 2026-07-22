
# VersionCommand


## Properties

Name | Type
------------ | -------------
`instruction` | string
`mediaReference` | string
`movementPatterns` | Set&lt;string&gt;
`stimulusType` | string
`fatigueProfile` | string
`technicalLevel` | string
`environment` | string
`requiredEquipment` | Set&lt;string&gt;

## Example

```typescript
import type { VersionCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "instruction": null,
  "mediaReference": null,
  "movementPatterns": null,
  "stimulusType": null,
  "fatigueProfile": null,
  "technicalLevel": null,
  "environment": null,
  "requiredEquipment": null,
} satisfies VersionCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as VersionCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
