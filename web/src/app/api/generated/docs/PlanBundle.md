
# PlanBundle


## Properties

Name | Type
------------ | -------------
`goal` | [TrainingGoal](TrainingGoal.md)
`plan` | [TrainingPlan](TrainingPlan.md)
`cycle` | [TrainingCycle](TrainingCycle.md)
`microcycle` | [Microcycle](Microcycle.md)
`session` | [PlannedSession](PlannedSession.md)
`prescriptions` | [Array&lt;ExercisePrescription&gt;](ExercisePrescription.md)

## Example

```typescript
import type { PlanBundle } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "goal": null,
  "plan": null,
  "cycle": null,
  "microcycle": null,
  "session": null,
  "prescriptions": null,
} satisfies PlanBundle

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PlanBundle
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
