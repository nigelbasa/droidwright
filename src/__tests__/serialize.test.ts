import {
  DEFAULT_TIMEOUT_MS,
  normalizeScript,
  parseAndroidEvaluationResult,
  serializeActionOptions,
  serializeCookie,
  timeout,
} from '../serialize';

describe('timeout', () => {
  it('falls back to the default when unset', () => {
    expect(timeout({})).toBe(DEFAULT_TIMEOUT_MS);
  });

  it('uses the provided timeout', () => {
    expect(timeout({ timeoutMs: 2500 })).toBe(2500);
  });
});

describe('serializeCookie', () => {
  it('passes through a raw cookie string', () => {
    expect(serializeCookie('theme=dark; Path=/')).toBe('theme=dark; Path=/');
  });

  it('serializes a name/value object', () => {
    expect(serializeCookie({ name: 'sid', value: 'abc123' })).toBe('sid=abc123');
  });
});

describe('normalizeScript', () => {
  it('passes through a string script unchanged', () => {
    expect(normalizeScript('document.title')).toBe('document.title');
  });

  it('wraps a function into an immediately-invoked expression', () => {
    const normalized = normalizeScript(() => 1 + 1);
    expect(normalized.startsWith('(')).toBe(true);
    expect(normalized.endsWith('()')).toBe(true);
    // The wrapped script should be valid and evaluate to the original result.
    // eslint-disable-next-line no-eval
    expect(eval(normalized)).toBe(2);
  });

  it('applies JSON-serialized arguments to a function', () => {
    const normalized = normalizeScript((a: number, b: number) => a + b, [2, 3]);
    expect(normalized).toContain('2, 3');
    // eslint-disable-next-line no-eval
    expect(eval(normalized)).toBe(5);
  });

  it('serializes string and object arguments safely', () => {
    const normalized = normalizeScript(
      (s: string, o: { k: number }) => `${s}:${o.k}`,
      ['x', { k: 7 }]
    );
    // eslint-disable-next-line no-eval
    expect(eval(normalized)).toBe('x:7');
  });

  it('ignores arguments for raw string scripts', () => {
    expect(normalizeScript('1 + 1', [42])).toBe('1 + 1');
  });
});

describe('parseAndroidEvaluationResult', () => {
  it.each([null, 'null', 'undefined'])('returns null for %p', (raw) => {
    expect(parseAndroidEvaluationResult(raw)).toBeNull();
  });

  it('parses JSON objects and arrays', () => {
    expect(parseAndroidEvaluationResult('{"a":1}')).toEqual({ a: 1 });
    expect(parseAndroidEvaluationResult('[1,2,3]')).toEqual([1, 2, 3]);
  });

  it('parses JSON primitives', () => {
    expect(parseAndroidEvaluationResult('"hello"')).toBe('hello');
    expect(parseAndroidEvaluationResult('42')).toBe(42);
    expect(parseAndroidEvaluationResult('true')).toBe(true);
  });

  it('falls back to the raw string when not valid JSON', () => {
    expect(parseAndroidEvaluationResult('not json')).toBe('not json');
  });
});

describe('serializeActionOptions', () => {
  it('applies actionability defaults', () => {
    expect(JSON.parse(serializeActionOptions({}))).toEqual({
      visible: true,
      enabled: true,
      stable: true,
      force: false,
      humanPace: {
        enabled: false,
        movementSteps: 5,
        minDelayMs: 80,
        maxDelayMs: 220,
        postActionDelayMs: 160,
      },
    });
  });

  it('honors explicit actionability flags', () => {
    const parsed = JSON.parse(
      serializeActionOptions({ visible: false, enabled: false, stable: false, force: true })
    );
    expect(parsed).toMatchObject({ visible: false, enabled: false, stable: false, force: true });
  });

  it('enables human pace with defaults when humanPace is true', () => {
    const parsed = JSON.parse(serializeActionOptions({ humanPace: true }));
    expect(parsed.humanPace).toEqual({
      enabled: true,
      movementSteps: 5,
      minDelayMs: 80,
      maxDelayMs: 220,
      postActionDelayMs: 160,
    });
  });

  it('merges a partial human pace object over defaults', () => {
    const parsed = JSON.parse(
      serializeActionOptions({ humanPace: { movementSteps: 7, minDelayMs: 120 } })
    );
    expect(parsed.humanPace).toEqual({
      enabled: true,
      movementSteps: 7,
      minDelayMs: 120,
      maxDelayMs: 220,
      postActionDelayMs: 160,
    });
  });
});
