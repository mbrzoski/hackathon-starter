import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { EventEnvelope } from '../api/model/models';

/** Where `npm run generate:api` puts the contract schemas (components.schemas of contracts/openapi.yaml). */
export const CONTRACT_SCHEMAS_URL = 'assets/contracts/openapi-schemas.json';

export type EventValidator = (data: unknown) => data is EventEnvelope;

/** Builds a validator for EventEnvelope from the copied contract document (FE-13). */
export function createEventValidator(contract: { $id: string }): EventValidator {
  const ajv = new Ajv2020({ allErrors: true });
  addFormats(ajv);
  // The contract keeps schemas under the OpenAPI "components" container, which is not a JSON Schema keyword.
  ajv.addKeyword('components');
  ajv.addSchema(contract);
  const validate = ajv.getSchema(`${contract.$id}#/components/schemas/EventEnvelope`);
  if (!validate) {
    throw new Error('EventEnvelope schema not found in the contract');
  }
  return (data: unknown): data is EventEnvelope => {
    if (validate(data)) {
      return true;
    }
    console.warn('Rejected /ws/events message that does not match EventEnvelope', validate.errors, data);
    return false;
  };
}
