import { DroidwrightActionOptions, DroidwrightCookie } from './Droidwright.types';

export const DEFAULT_TIMEOUT_MS = 10_000;

type ScriptFunction = (...args: any[]) => unknown;

/**
 * Turns a page function or raw script into a string for the native WebView.
 *
 * Function arguments are JSON-serialized and applied, so `evaluate` can pass
 * data into the page (closure variables are NOT captured — only the passed
 * args are available). Raw string scripts are returned unchanged; any args are
 * ignored for them.
 */
export function normalizeScript(script: string | ScriptFunction, args: unknown[] = []): string {
  if (typeof script === 'function') {
    const serializedArgs = args.map((arg) => JSON.stringify(arg) ?? 'undefined').join(', ');
    return `(${script.toString()})(${serializedArgs})`;
  }
  return script;
}

export function parseAndroidEvaluationResult(raw: string | null): unknown {
  if (raw == null || raw === 'null' || raw === 'undefined') {
    return null;
  }

  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

export function timeout(options: DroidwrightActionOptions): number {
  return options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
}

export function serializeCookie(cookie: string | DroidwrightCookie): string {
  if (typeof cookie === 'string') {
    return cookie;
  }

  return `${cookie.name}=${cookie.value}`;
}

export function serializeActionOptions(options: DroidwrightActionOptions): string {
  const humanPace =
    typeof options.humanPace === 'object'
      ? {
          enabled: options.humanPace.enabled ?? true,
          movementSteps: options.humanPace.movementSteps ?? 5,
          minDelayMs: options.humanPace.minDelayMs ?? 80,
          maxDelayMs: options.humanPace.maxDelayMs ?? 220,
          postActionDelayMs: options.humanPace.postActionDelayMs ?? 160,
        }
      : {
          enabled: options.humanPace ?? false,
          movementSteps: 5,
          minDelayMs: 80,
          maxDelayMs: 220,
          postActionDelayMs: 160,
        };

  return JSON.stringify({
    visible: options.visible ?? true,
    enabled: options.enabled ?? true,
    stable: options.stable ?? true,
    force: options.force ?? false,
    humanPace,
  });
}
