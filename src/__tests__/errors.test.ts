import { DroidwrightError, DroidwrightErrorCode, getDroidwrightErrorCode } from '../errors';

describe('DroidwrightError', () => {
  it('is an Error subclass carrying a structured code', () => {
    const error = new DroidwrightError(DroidwrightErrorCode.Timeout, 'timed out');
    expect(error).toBeInstanceOf(Error);
    expect(error).toBeInstanceOf(DroidwrightError);
    expect(error.name).toBe('DroidwrightError');
    expect(error.code).toBe('ERR_DROIDWRIGHT_TIMEOUT');
    expect(error.message).toBe('timed out');
  });
});

describe('getDroidwrightErrorCode', () => {
  it('reads a string code from an error-like object', () => {
    expect(getDroidwrightErrorCode({ code: DroidwrightErrorCode.NotVisible })).toBe(
      'ERR_DROIDWRIGHT_NOT_VISIBLE'
    );
    expect(getDroidwrightErrorCode(new DroidwrightError(DroidwrightErrorCode.NoElement, 'x'))).toBe(
      'ERR_DROIDWRIGHT_NO_ELEMENT'
    );
  });

  it('returns undefined when there is no string code', () => {
    expect(getDroidwrightErrorCode(new Error('plain'))).toBeUndefined();
    expect(getDroidwrightErrorCode(null)).toBeUndefined();
    expect(getDroidwrightErrorCode({ code: 123 })).toBeUndefined();
  });
});
