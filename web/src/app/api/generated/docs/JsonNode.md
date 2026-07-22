# JsonNode

## Properties

| Name                  | Type    |
| --------------------- | ------- |
| `empty`               | boolean |
| `array`               | boolean |
| `_null`               | boolean |
| `object`              | boolean |
| `_float`              | boolean |
| `valueNode`           | boolean |
| `missingNode`         | boolean |
| `nodeType`            | string  |
| `string`              | boolean |
| `integralNumber`      | boolean |
| `pojo`                | boolean |
| `floatingPointNumber` | boolean |
| `_short`              | boolean |
| `_int`                | boolean |
| `_long`               | boolean |
| `_double`             | boolean |
| `bigDecimal`          | boolean |
| `bigInteger`          | boolean |
| `textual`             | boolean |
| `_boolean`            | boolean |
| `binary`              | boolean |
| `container`           | boolean |
| `number`              | boolean |
| `embeddedValue`       | boolean |

## Example

```typescript
import type { JsonNode } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  empty: null,
  array: null,
  _null: null,
  object: null,
  _float: null,
  valueNode: null,
  missingNode: null,
  nodeType: null,
  string: null,
  integralNumber: null,
  pojo: null,
  floatingPointNumber: null,
  _short: null,
  _int: null,
  _long: null,
  _double: null,
  bigDecimal: null,
  bigInteger: null,
  textual: null,
  _boolean: null,
  binary: null,
  container: null,
  number: null,
  embeddedValue: null,
} satisfies JsonNode;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as JsonNode;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
