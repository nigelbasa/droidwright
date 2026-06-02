# Droidwright

Playwright-inspired Android WebView automation for React Native and Expo.

Droidwright creates offscreen Android `WebView` sessions from a native Expo module and exposes a TypeScript API that feels familiar if you have used Playwright or Puppeteer. No desktop Chrome, no ADB, no Appium, no remote server — just a process-bound WebView running inside your app.

```ts
import { launch } from 'droidwright';

const page = await launch({ debug: true, forwardConsole: true });

await page.goto('https://example.com');
const title = await page.evaluate(() => document.title);

await page.locator('#email').fill('user@example.com');
await page.getByText('Sign in', { exact: true }).tap();

const text = await page.locator('main').textContent();
const png = await page.screenshot();
const cookies = await page.getCookies();
await page.close();
```

## Installation

```sh
npx expo install droidwright
```

Droidwright is a native Expo module, so it cannot run in Expo Go. Use a development build or a bare/prebuild workflow:

```sh
npx expo prebuild
npx expo run:android
```

## Requirements

| Requirement | Version |
| --- | --- |
| Platform | **Android only** (no iOS, no web) |
| Expo SDK | 54 – 56 |
| React Native | Whatever your Expo SDK bundles (SDK 56 ships RN 0.85) |
| Android minSdk | Inherited from your app (Expo default is 24+) |

On web and iOS every call rejects with a clear `"Droidwright runs on Android native WebView, not on web."` error rather than crashing, so cross-platform apps can guard with `Platform.OS === 'android'`.

## API reference

### Session lifecycle

```ts
import Droidwright, { launch, closeAllSessions } from 'droidwright';

// Create a session — returns a DroidwrightPage.
const page = await launch({
  userAgent: 'MyBot/1.0',   // optional custom UA
  debug: true,               // enable chrome://inspect debugging
  forwardConsole: true,       // surface page console.log as native events
  loadImages: true,           // default true
  viewportWidth: 1080,        // default 1080
  viewportHeight: 1920,       // default 1920
});

// Current page state.
const snap = await page.snapshot();
// { sessionId, url, title, progress, canGoBack, canGoForward, userAgent }

// Tear down.
await page.close();          // close one session
await closeAllSessions();    // close all sessions
```

### Navigation

```ts
await page.goto('https://example.com', { timeoutMs: 15000 });
await page.reload();
await page.goBack();
```

All navigation methods return a `DroidwrightNavigationResult` and throw `ERR_DROIDWRIGHT_TIMEOUT` if the page does not finish loading in time.

### Evaluate

Run arbitrary JavaScript inside the WebView.

```ts
// Simple expression.
const title = await page.evaluate(() => document.title);

// With arguments — closure variables are NOT captured, pass data explicitly.
const href = await page.evaluate(
  (sel: string, attr: string) => document.querySelector(sel)?.getAttribute(attr),
  '.next a',
  'href'
);

// Raw string scripts work too.
const count = await page.evaluate<number>('document.images.length');
```

### Waiting

```ts
// Wait for a CSS selector to appear in the DOM.
await page.waitForSelector('.product_pod', { timeoutMs: 10000 });

// Wait for visible text.
await page.waitForText('Results loaded', { exact: false });

// Wait for an arbitrary JS condition to become truthy.
await page.waitForFunction(
  () => document.querySelectorAll('.item').length > 5,
  { timeoutMs: 10000 }
);
```

### Interactions

All interactive actions perform **actionability checks** before acting (see [Actionability](#actionability) below).

```ts
// Click / tap (dispatches pointer + mouse + click events).
await page.click('#submit');
await page.tap('#submit');

// Fill an input or contentEditable element.
await page.fill('input[name="q"]', 'Expo');

// Hover (dispatches pointerenter + mouseover + pointermove).
await page.hover('a.link');

// Press a key on a focused element (dispatches keydown + keypress + keyup).
await page.press('input', 'Enter');

// Select an <option> by value or visible text.
await page.selectOption('select#country', 'us');

// Read text content.
const text = await page.textContent('.result');
```

### Locators

Locators are lazy — they do not query the DOM until you call an action.

```ts
// CSS selector locator.
const input = page.locator('#email');
await input.fill('user@example.com');
await input.hover();
await input.press('Tab');
await input.selectOption('option-value');
await input.scrollIntoView();
const text = await input.textContent();

// Text locator (finds the smallest element containing the text).
const button = page.getByText('Sign in', { exact: true });
await button.tap();
await button.waitFor({ timeoutMs: 5000 });
```

### Scrolling

```ts
await page.scrollBy(0, 600);            // relative scroll
await page.scrollTo(0, 0);              // absolute scroll
await page.scrollIntoView('.footer');    // scroll element to center
```

### Screenshot

Captures the offscreen WebView as a base64-encoded PNG string. The WebView uses a software rendering layer so the capture contains real rendered pixels.

```ts
const base64png = await page.screenshot();
```

### Cookies

```ts
// Page-scoped (uses the page's current URL).
const cookies = await page.getCookies();
await page.setCookie('theme=dark; Path=/');
await page.setCookie({ name: 'sid', value: 'abc123' });
await page.clearCookies();

// Global (requires an explicit URL).
import { getCookies, setCookie, clearCookies } from 'droidwright';
const all = await getCookies('https://example.com');
```

### Storage

```ts
// localStorage and sessionStorage, per session.
await page.localStorage.setItem('seen', 'true');
const val = await page.localStorage.getItem('seen');
await page.localStorage.removeItem('seen');
await page.localStorage.clear();
const allItems = await page.localStorage.snapshot(); // { key: value, ... }

// sessionStorage has the same API.
await page.sessionStorage.setItem('temp', 'data');
```

### Events

```ts
import { NativeDroidwright } from 'droidwright';

NativeDroidwright.addListener('onEvent', (event) => {
  switch (event.type) {
    case 'navigationStarted':
    case 'navigationFinished':
      console.log(event.url, event.title);
      break;
    case 'navigationError':
      console.log(event.description, event.errorCode);
      break;
    case 'console':
      console.log(event.level, event.message);
      break;
    case 'request':
      console.log(event.method, event.url);
      break;
    case 'closed':
      break;
  }
});
```

## Actionability

Interactive actions (`click`, `tap`, `fill`, `hover`, `press`) wait for the element to be **actionable** before proceeding. An element is actionable when it:

1. **Exists** in the DOM.
2. **Is visible** — has a non-zero bounding box, computed `display` is not `none`, `visibility` is not `hidden`, `opacity` is greater than 0, and intersects the viewport after being scrolled into view.
3. **Is enabled** — not `disabled` and `aria-disabled` is not `"true"`.
4. **Is stable** — its bounding rect has not changed between two samples 80ms apart.
5. **Is not covered** — `document.elementFromPoint` at the element center returns the element itself or a descendant/ancestor.

`force: true` skips checks 2–5 but still requires the element to exist.

```ts
await page.click('#submit', {
  timeoutMs: 10000,    // how long to wait for actionability (default 10s)
  visible: true,       // check visibility (default true)
  enabled: true,       // check enabled (default true)
  stable: true,        // check layout stability (default true)
  force: false,        // skip all checks except existence (default false)
});
```

### Human-paced actions

Opt-in randomized timing to make automation less mechanical:

```ts
await page.tap('.next a', {
  humanPace: {
    enabled: true,
    movementSteps: 7,     // pointer movements before press (min 5)
    minDelayMs: 120,       // random pre-action delay range
    maxDelayMs: 320,
    postActionDelayMs: 240,
  },
});
```

### Error codes

Structured action failures are thrown with a `.code` property:

| Code | Meaning |
| --- | --- |
| `ERR_DROIDWRIGHT_NO_ELEMENT` | No element matches the selector or text. |
| `ERR_DROIDWRIGHT_NOT_VISIBLE` | Element exists but is not visible. |
| `ERR_DROIDWRIGHT_NOT_ENABLED` | Element is disabled. |
| `ERR_DROIDWRIGHT_NOT_STABLE` | Element layout is still changing. |
| `ERR_DROIDWRIGHT_ELEMENT_COVERED` | Another element covers the center point. |
| `ERR_DROIDWRIGHT_TIMEOUT` | Operation timed out. |
| `ERR_DROIDWRIGHT_NO_SESSION` | The session ID does not exist. |
| `ERR_DROIDWRIGHT_NAVIGATION` | Navigation failed (network error, etc). |

These are exported as the `DroidwrightErrorCode` constant for programmatic matching:

```ts
import { DroidwrightErrorCode, getDroidwrightErrorCode } from 'droidwright';

try {
  await page.click('#submit', { timeoutMs: 2000 });
} catch (error) {
  if (getDroidwrightErrorCode(error) === DroidwrightErrorCode.ElementCovered) {
    await page.click('#submit', { force: true });
  }
}
```

## Background model

Version 0.1 is intentionally honest: sessions are offscreen, but they live inside the hosting Android app process. If the app is open or kept alive, the WebView can work. If the OS stops the app, the session is gone.

A foreground-service runner for jobs that need stronger background guarantees is planned for a future release.

## License

Apache-2.0
