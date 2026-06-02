# Droidwright

Playwright-inspired Android WebView automation for React Native and Expo.

Droidwright creates offscreen Android `WebView` sessions from a native Expo module and exposes a small TypeScript API that feels familiar if you have used Playwright or Puppeteer.

```ts
import { launch } from 'droidwright';

const page = await launch({ debug: true, forwardConsole: true });

await page.goto('https://example.com');
const title = await page.evaluate(() => document.title);

await page.locator('#email').fill('user@example.com');
await page.getByText('Sign in', { exact: true }).tap();

const text = await page.locator('main').textContent();
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

| Requirement | Supported |
| --- | --- |
| Platform | Android only (no iOS, no web) |
| Expo SDK | 54, 55, 56 |
| React Native | Whatever your Expo SDK bundles (e.g. SDK 56 ships RN 0.85) |
| Android API | minSdk inherited from your app (Expo's default is 24+) |

On web and iOS every call rejects with a clear "runs on Android native WebView" error rather than crashing, so cross-platform apps can guard with a `Platform.OS === 'android'` check.

## What this is

- Android-native browser automation inside a React Native/Expo app.
- A process-bound WebView session that does not require desktop Chrome, ADB, Appium, or a remote server.
- A foundation for on-device scraping, page scripting, repeatable workflows, and agent-driven mobile web automation.

## What this is not

- It does not silently control Chrome or another app's browser tabs.
- It is not guaranteed to keep running after Android kills your app process.
- It is not a true long-running hidden background browser. Android requires foreground services and visible notifications for reliable long-running background work.

## API

```ts
const page = await launch(options);

await page.goto(url, { timeoutMs: 10000 });
await page.evaluate(() => document.body.innerText);
await page.evaluate((selector, attr) => document.querySelector(selector)?.getAttribute(attr), '.next a', 'href');
await page.waitForSelector('.result');
await page.waitForFunction(() => document.readyState === 'complete', { timeoutMs: 5000 });
await page.click('button');
await page.tap('button', { visible: true, enabled: true, stable: true });
await page.fill('input[name="q"]', 'Expo');
await page.textContent('.result');
await page.getByText('Next').click();
await page.hover('a.link');
await page.press('input', 'Enter');
await page.selectOption('select#country', 'us');
await page.scrollBy(0, 600);
await page.scrollIntoView('.footer');
const png = await page.screenshot(); // base64-encoded PNG
await page.localStorage.setItem('seen', 'true');
await page.sessionStorage.snapshot();
await page.getCookies();
await page.setCookie('theme=dark; Path=/');
await page.snapshot();
await page.close();
```

## Events

```ts
NativeDroidwright.addListener('onEvent', (event) => {
  if (event.type === 'request') {
    console.log(event.method, event.url);
  }
});
```

Events currently include navigation, console, request, navigation error, and close notifications.

## Actionability

Interactive actions wait for actionable elements by default:

```ts
await page.click('#submit', {
  timeoutMs: 10000,
  visible: true,
  enabled: true,
  stable: true,
  force: false,
  humanPace: false,
});
```

`click`, `tap`, and `fill` require the element to exist, be visible, be enabled, remain layout-stable across two samples, and not be covered at its center point. `force: true` still requires the element to exist, but skips the visibility, enabled, stable, and covered checks.

Human-paced actions are opt-in:

```ts
await page.tap('.next a', {
  humanPace: {
    enabled: true,
    movementSteps: 7,
    minDelayMs: 120,
    maxDelayMs: 320,
    postActionDelayMs: 240,
  },
});
```

When enabled, Droidwright adds a randomized pre-action wait, dispatches at least five pointer movement events before the press/release sequence, and waits briefly after the action. This is intended for legitimate, less brittle mobile automation and demos.

Structured action failures use these codes:

```txt
ERR_DROIDWRIGHT_NO_ELEMENT
ERR_DROIDWRIGHT_NOT_VISIBLE
ERR_DROIDWRIGHT_NOT_ENABLED
ERR_DROIDWRIGHT_NOT_STABLE
ERR_DROIDWRIGHT_ELEMENT_COVERED
ERR_DROIDWRIGHT_TIMEOUT
```

These are exported as the `DroidwrightErrorCode` constant, and thrown errors carry a `.code`:

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

## Evaluating with arguments

`page.evaluate` does not capture closure variables — pass data explicitly as
JSON-serializable arguments:

```ts
const href = await page.evaluate(
  (selector: string) => document.querySelector(selector)?.getAttribute('href'),
  '.next a'
);

// Poll the page until a condition holds (throws ERR_DROIDWRIGHT_TIMEOUT).
await page.waitForFunction(() => document.querySelectorAll('.product_pod').length > 0, {
  timeoutMs: 10000,
});
```

## Launch options

```ts
type DroidwrightLaunchOptions = {
  userAgent?: string;
  debug?: boolean;
  forwardConsole?: boolean;
  loadImages?: boolean;
  viewportWidth?: number;
  viewportHeight?: number;
};
```

The default offscreen viewport is `1080x1920`.

## Background model

Version 0.1 is intentionally honest: sessions are offscreen, but they live inside the hosting Android app process. If the app is open or kept alive, the WebView can work. If the OS stops the app, the session is gone.

The next serious milestone is a foreground-service runner for jobs that need stronger background guarantees.
