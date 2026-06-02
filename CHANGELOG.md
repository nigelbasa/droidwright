# Changelog

## 0.1.0 (2026-06-02)

Initial release. Playwright-inspired Android WebView automation for React Native and Expo.

### Core

- `launch(options)` creates an offscreen Android `WebView` session and returns a `DroidwrightPage`.
- Default viewport is 1080x1920. Configurable via `viewportWidth` / `viewportHeight`.
- Sessions are process-bound: alive while the hosting app is alive, cleaned up on destroy.

### Navigation

- `page.goto(url)`, `page.reload()`, `page.goBack()` — all with configurable timeout.
- Navigation failures throw `ERR_DROIDWRIGHT_NAVIGATION`; timeouts throw `ERR_DROIDWRIGHT_TIMEOUT` (not a raw Kotlin `TimeoutCancellationException`).

### Evaluate

- `page.evaluate(script)` — run a string or function in the WebView, returns parsed JSON.
- `page.evaluate(fn, ...args)` — pass JSON-serializable arguments to a page function (closure variables are not captured).
- `page.waitForFunction(fn, options)` — poll until a function returns truthy, throws on timeout.

### Interactions

- `page.click(selector)`, `page.tap(selector)` — actionability-gated pointer/mouse/click dispatch.
- `page.fill(selector, value)` — set input values via the native property descriptor, dispatches `input` + `change`.
- `page.hover(selector)` — dispatches pointer/mouse enter, over, and move events.
- `page.press(selector, key)` — dispatches `keydown`, `keypress`, `keyup` on a focused element.
- `page.selectOption(selector, value)` — selects an `<option>` by value or visible text, dispatches `input` + `change`.

### Locators

- `page.locator(selector)` — CSS selector locator with `click`, `tap`, `fill`, `hover`, `press`, `selectOption`, `textContent`, `scrollIntoView`, and `waitFor`.
- `page.getByText(text, { exact })` — text locator (smallest matching element) with `click`, `tap`, `textContent`, and `waitFor`.

### Actionability

- Before `click`, `tap`, `fill`, `hover`, and `press`, the element is checked for: existence, visibility, enabled state, layout stability (two samples 80ms apart), and not-covered (elementFromPoint). Failures throw structured error codes.
- `force: true` skips all checks except existence.
- Elements are scrolled into view before the viewport-intersection and covered checks (matching Playwright semantics).

### Human-paced actions

- Opt-in via `humanPace: true` or a `{ enabled, movementSteps, minDelayMs, maxDelayMs, postActionDelayMs }` object.
- Adds randomized pre-action delay, at least 5 jittered pointer-movement events, and a post-action delay.

### Screenshot

- `page.screenshot()` — renders the offscreen WebView to a base64-encoded PNG via a software Canvas layer.
- The WebView uses `LAYER_TYPE_SOFTWARE` for its entire lifetime so hardware-accelerated blank-frame capture is avoided.

### Scrolling

- `page.scrollBy(x, y)`, `page.scrollTo(x, y)`, `page.scrollIntoView(selector)`.

### Cookies

- `page.getCookies()`, `page.setCookie(cookie)`, `page.clearCookies()` — page-scoped (uses current URL).
- `getCookies(url)`, `setCookie(url, cookie)`, `clearCookies()` — global, exported from the package root.

### Storage

- `page.localStorage` / `page.sessionStorage` — `getItem`, `setItem`, `removeItem`, `clear`, `snapshot`.

### Events

- `NativeDroidwright.addListener('onEvent', handler)` — receives `navigationStarted`, `navigationFinished`, `navigationError`, `console`, `request`, and `closed` events.

### Errors

- `DroidwrightError` class with a `.code` property.
- `DroidwrightErrorCode` constant map (`NoElement`, `NotVisible`, `NotEnabled`, `NotStable`, `ElementCovered`, `Timeout`, `NoSession`, `Navigation`, `Selector`).
- `getDroidwrightErrorCode(error)` helper for programmatic error matching.

### Packaging

- Targets Expo SDK 54–56 (`peerDependencies`).
- Android-only (`expo-module.config.json` declares `platforms: ["android"]`).
- Web shim rejects all calls with a clear error message.
- Ships prebuilt JS + declarations in `build/`, native Kotlin in `android/`.
