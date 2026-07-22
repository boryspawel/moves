# TodayAgendaView

## Properties

| Name         | Type                                                   |
| ------------ | ------------------------------------------------------ |
| `timeZone`   | string                                                 |
| `localDate`  | Date                                                   |
| `activePlan` | [ActivePlanView](ActivePlanView.md)                    |
| `sessions`   | [Array&lt;AgendaSessionView&gt;](AgendaSessionView.md) |
| `state`      | string                                                 |
| `recovery`   | [RecoveryView](RecoveryView.md)                        |

## Example

```typescript
import type { TodayAgendaView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  timeZone: null,
  localDate: null,
  activePlan: null,
  sessions: null,
  state: null,
  recovery: null,
} satisfies TodayAgendaView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TodayAgendaView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
