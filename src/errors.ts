/**
 * Structured error codes raised by Droidwright. Native actionability/navigation
 * failures arrive with these codes attached, and the TypeScript layer throws
 * them for client-side waits (for example {@link DroidwrightError} from
 * `waitForFunction`).
 */
export const DroidwrightErrorCode = {
  NoElement: 'ERR_DROIDWRIGHT_NO_ELEMENT',
  NotVisible: 'ERR_DROIDWRIGHT_NOT_VISIBLE',
  NotEnabled: 'ERR_DROIDWRIGHT_NOT_ENABLED',
  NotStable: 'ERR_DROIDWRIGHT_NOT_STABLE',
  ElementCovered: 'ERR_DROIDWRIGHT_ELEMENT_COVERED',
  Timeout: 'ERR_DROIDWRIGHT_TIMEOUT',
  NoSession: 'ERR_DROIDWRIGHT_NO_SESSION',
  Navigation: 'ERR_DROIDWRIGHT_NAVIGATION',
  Selector: 'ERR_DROIDWRIGHT_SELECTOR',
} as const;

// Intentional value + type sharing the same name (idiomatic const-enum pattern).
// eslint-disable-next-line @typescript-eslint/no-redeclare
export type DroidwrightErrorCode = (typeof DroidwrightErrorCode)[keyof typeof DroidwrightErrorCode];

/** Error thrown by the Droidwright TypeScript layer, carrying a structured code. */
export class DroidwrightError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'DroidwrightError';
    this.code = code;
    // Restore the prototype chain for instanceof across transpilation targets.
    Object.setPrototypeOf(this, DroidwrightError.prototype);
  }
}

/** Returns the structured code of a thrown error, or undefined if absent. */
export function getDroidwrightErrorCode(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = (error as { code?: unknown }).code;
    return typeof code === 'string' ? code : undefined;
  }
  return undefined;
}
