import { EventEnvelope } from '../api/model/models';
import { validate } from '../api/event-envelope.validator';

export type EventValidator = (data: unknown) => data is EventEnvelope;

/**
 * Validator for EventEnvelope (FE-13). Compiled from contracts/openapi.yaml by `npm run generate:api`, so nothing is
 * compiled in the browser (no eval, CSP of WEB-05).
 */
export function createEventValidator(): EventValidator {
  return (data: unknown): data is EventEnvelope => {
    if (validate(data)) {
      return true;
    }
    // Only the type and the errors: the payload may hold a transcript (FF-09).
    const type = (data as { type?: unknown } | null)?.type;
    const label = typeof type === 'string' ? type : '?';
    console.warn('Rejected /ws/events message that does not match EventEnvelope', label, validate.errors);
    return false;
  };
}
