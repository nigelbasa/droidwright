import {
  DroidwrightActionOptions,
  DroidwrightCookie,
  DroidwrightLaunchOptions,
  DroidwrightNavigationResult,
  DroidwrightStorageArea,
  DroidwrightTextLocatorOptions,
} from './Droidwright.types';
import NativeDroidwright from './DroidwrightModule';
import { DroidwrightError, DroidwrightErrorCode } from './errors';
import {
  normalizeScript,
  parseAndroidEvaluationResult,
  serializeActionOptions,
  serializeCookie,
  timeout,
} from './serialize';

export { NativeDroidwright };
export * from './Droidwright.types';
export * from './errors';

export async function launch(options: DroidwrightLaunchOptions = {}) {
  const session = await NativeDroidwright.createSession(JSON.stringify(options));
  return new DroidwrightPage(session.sessionId);
}

export async function closeAllSessions() {
  await NativeDroidwright.closeAllSessions();
}

export async function getCookies(url: string) {
  return NativeDroidwright.getCookies(url);
}

export async function setCookie(url: string, cookie: string | DroidwrightCookie) {
  return NativeDroidwright.setCookie(url, serializeCookie(cookie));
}

export async function clearCookies() {
  return NativeDroidwright.clearCookies();
}

export class DroidwrightPage {
  constructor(readonly sessionId: string) {}

  async goto(url: string, options: DroidwrightActionOptions = {}) {
    return NativeDroidwright.goto(this.sessionId, url, timeout(options));
  }

  async reload(options: DroidwrightActionOptions = {}) {
    return NativeDroidwright.reload(this.sessionId, timeout(options));
  }

  async goBack(options: DroidwrightActionOptions = {}) {
    return NativeDroidwright.goBack(this.sessionId, timeout(options));
  }

  async evaluate<T = unknown, A extends unknown[] = []>(
    script: string | ((...args: A) => T),
    ...args: A
  ): Promise<T> {
    const raw = await NativeDroidwright.evaluate(this.sessionId, normalizeScript(script, args));
    return parseAndroidEvaluationResult(raw) as T;
  }

  async waitForFunction<T = unknown>(
    script: string | (() => T),
    options: DroidwrightActionOptions = {}
  ): Promise<T> {
    const timeoutMs = timeout(options);
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeoutMs) {
      const result = await this.evaluate<T>(script);
      if (result) {
        return result;
      }
      await delay(WAIT_FUNCTION_POLL_MS);
    }
    throw new DroidwrightError(
      DroidwrightErrorCode.Timeout,
      `Timed out after ${timeoutMs}ms waiting for function to return a truthy value.`
    );
  }

  async waitForSelector(selector: string, options: DroidwrightActionOptions = {}) {
    return NativeDroidwright.waitForSelector(this.sessionId, selector, timeout(options));
  }

  locator(selector: string) {
    return new DroidwrightLocator(this, selector);
  }

  getByText(text: string, options: DroidwrightTextLocatorOptions = {}) {
    return new DroidwrightTextLocator(this, text, options.exact ?? false);
  }

  async click(selector: string, options: DroidwrightActionOptions = {}) {
    await NativeDroidwright.click(
      this.sessionId,
      selector,
      timeout(options),
      serializeActionOptions(options)
    );
  }

  async tap(selector: string, options: DroidwrightActionOptions = {}) {
    await NativeDroidwright.tap(
      this.sessionId,
      selector,
      timeout(options),
      serializeActionOptions(options)
    );
  }

  async fill(selector: string, value: string, options: DroidwrightActionOptions = {}) {
    await NativeDroidwright.fill(
      this.sessionId,
      selector,
      value,
      timeout(options),
      serializeActionOptions(options)
    );
  }

  async textContent(selector: string, options: DroidwrightActionOptions = {}) {
    return NativeDroidwright.textContent(this.sessionId, selector, timeout(options));
  }

  async waitForText(text: string, options: DroidwrightTextLocatorOptions = {}) {
    return NativeDroidwright.waitForText(
      this.sessionId,
      text,
      options.exact ?? false,
      timeout(options)
    );
  }

  async clickText(text: string, options: DroidwrightTextLocatorOptions = {}) {
    await NativeDroidwright.clickText(
      this.sessionId,
      text,
      options.exact ?? false,
      timeout(options),
      serializeActionOptions(options)
    );
  }

  async textContentByText(text: string, options: DroidwrightTextLocatorOptions = {}) {
    return NativeDroidwright.textContentByText(
      this.sessionId,
      text,
      options.exact ?? false,
      timeout(options)
    );
  }

  async scrollBy(x: number, y: number) {
    return NativeDroidwright.scrollBy(this.sessionId, x, y);
  }

  async scrollTo(x: number, y: number) {
    return NativeDroidwright.scrollTo(this.sessionId, x, y);
  }

  async scrollIntoView(selector: string, options: DroidwrightActionOptions = {}) {
    return NativeDroidwright.scrollIntoView(this.sessionId, selector, timeout(options));
  }

  async getCookies(url?: string) {
    return NativeDroidwright.getCookies(url ?? (await this.snapshot()).url);
  }

  async setCookie(cookie: string | DroidwrightCookie, url?: string) {
    return NativeDroidwright.setCookie(url ?? (await this.snapshot()).url, serializeCookie(cookie));
  }

  async clearCookies() {
    return NativeDroidwright.clearCookies();
  }

  storage(area: DroidwrightStorageArea) {
    return new DroidwrightStorage(this, area);
  }

  get localStorage() {
    return this.storage('localStorage');
  }

  get sessionStorage() {
    return this.storage('sessionStorage');
  }

  async snapshot(): Promise<DroidwrightNavigationResult> {
    return NativeDroidwright.snapshot(this.sessionId);
  }

  async close() {
    await NativeDroidwright.closeSession(this.sessionId);
  }
}

export class DroidwrightLocator {
  constructor(
    private readonly page: DroidwrightPage,
    readonly selector: string
  ) {}

  async waitFor(options: DroidwrightActionOptions = {}) {
    return this.page.waitForSelector(this.selector, options);
  }

  async click(options: DroidwrightActionOptions = {}) {
    return this.page.click(this.selector, options);
  }

  async tap(options: DroidwrightActionOptions = {}) {
    return this.page.tap(this.selector, options);
  }

  async fill(value: string, options: DroidwrightActionOptions = {}) {
    return this.page.fill(this.selector, value, options);
  }

  async textContent(options: DroidwrightActionOptions = {}) {
    return this.page.textContent(this.selector, options);
  }

  async scrollIntoView(options: DroidwrightActionOptions = {}) {
    return this.page.scrollIntoView(this.selector, options);
  }
}

export class DroidwrightTextLocator {
  constructor(
    private readonly page: DroidwrightPage,
    readonly text: string,
    readonly exact: boolean
  ) {}

  async waitFor(options: DroidwrightActionOptions = {}) {
    return this.page.waitForText(this.text, { ...options, exact: this.exact });
  }

  async click(options: DroidwrightActionOptions = {}) {
    return this.page.clickText(this.text, { ...options, exact: this.exact });
  }

  async tap(options: DroidwrightActionOptions = {}) {
    return this.click(options);
  }

  async textContent(options: DroidwrightActionOptions = {}) {
    return this.page.textContentByText(this.text, { ...options, exact: this.exact });
  }
}

export class DroidwrightStorage {
  constructor(
    private readonly page: DroidwrightPage,
    readonly area: DroidwrightStorageArea
  ) {}

  async getItem(key: string): Promise<string | null> {
    return this.page.evaluate<string | null>(
      `(function() { return ${this.area}.getItem(${JSON.stringify(key)}); })()`
    );
  }

  async setItem(key: string, value: string) {
    await this.page.evaluate(
      `(function() { ${this.area}.setItem(${JSON.stringify(key)}, ${JSON.stringify(value)}); return true; })()`
    );
  }

  async removeItem(key: string) {
    await this.page.evaluate(
      `(function() { ${this.area}.removeItem(${JSON.stringify(key)}); return true; })()`
    );
  }

  async clear() {
    await this.page.evaluate(`(function() { ${this.area}.clear(); return true; })()`);
  }

  async snapshot(): Promise<Record<string, string>> {
    return this.page.evaluate<Record<string, string>>(`(function() {
      var storage = ${this.area};
      var out = {};
      for (var index = 0; index < storage.length; index += 1) {
        var key = storage.key(index);
        if (key != null) {
          out[key] = storage.getItem(key);
        }
      }
      return out;
    })()`);
  }
}

export type DroidwrightApi = {
  launch: typeof launch;
  closeAllSessions: typeof closeAllSessions;
  getCookies: typeof getCookies;
  setCookie: typeof setCookie;
  clearCookies: typeof clearCookies;
};

const Droidwright: DroidwrightApi = {
  launch,
  closeAllSessions,
  getCookies,
  setCookie,
  clearCookies,
};

export default Droidwright;

const WAIT_FUNCTION_POLL_MS = 100;

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
