package dev.droidwright

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.random.Random

class DroidwrightModule : Module() {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val sessions = ConcurrentHashMap<String, DroidwrightSession>()

  override fun definition() = ModuleDefinition {
    Name("Droidwright")

    Events("onEvent")

    AsyncFunction("createSession") Coroutine { optionsJson: String ->
      createSession(optionsJson)
    }

    AsyncFunction("closeSession") Coroutine { sessionId: String ->
      closeSession(sessionId)
    }

    AsyncFunction("closeAllSessions") Coroutine { ->
      closeAllSessions()
    }

    AsyncFunction("goto") Coroutine { sessionId: String, url: String, timeoutMs: Int ->
      navigate(sessionId, timeoutMs) { it.loadUrl(url) }
    }

    AsyncFunction("reload") Coroutine { sessionId: String, timeoutMs: Int ->
      navigate(sessionId, timeoutMs) { it.reload() }
    }

    AsyncFunction("goBack") Coroutine { sessionId: String, timeoutMs: Int ->
      navigate(sessionId, timeoutMs) { webView ->
        if (!webView.canGoBack()) {
          throw DroidwrightException("ERR_DROIDWRIGHT_CANNOT_GO_BACK", "The WebView has no back entry.")
        }
        webView.goBack()
      }
    }

    AsyncFunction("evaluate") Coroutine { sessionId: String, script: String ->
      val session = requireSession(sessionId)
      evaluateRaw(session, script)
    }

    AsyncFunction("waitForSelector") Coroutine { sessionId: String, selector: String, timeoutMs: Int ->
      waitForSelector(sessionId, selector, timeoutMs)
    }

    AsyncFunction("click") Coroutine { sessionId: String, selector: String, timeoutMs: Int, actionOptionsJson: String ->
      val actionOptions = parseActionOptions(actionOptionsJson)
      waitForActionableSelector(sessionId, selector, actionOptions, timeoutMs)
      val session = requireSession(sessionId)
      runHumanPaceBeforeAction(actionOptions)
      requireActionOk("click", evaluateRaw(session, clickScript(selector, actionOptions)))
      runHumanPaceAfterAction(actionOptions)
    }

    AsyncFunction("tap") Coroutine { sessionId: String, selector: String, timeoutMs: Int, actionOptionsJson: String ->
      val actionOptions = parseActionOptions(actionOptionsJson)
      waitForActionableSelector(sessionId, selector, actionOptions, timeoutMs)
      val session = requireSession(sessionId)
      runHumanPaceBeforeAction(actionOptions)
      requireActionOk("tap", evaluateRaw(session, tapScript(selector, actionOptions)))
      runHumanPaceAfterAction(actionOptions)
    }

    AsyncFunction("fill") Coroutine { sessionId: String, selector: String, value: String, timeoutMs: Int, actionOptionsJson: String ->
      val actionOptions = parseActionOptions(actionOptionsJson)
      waitForActionableSelector(sessionId, selector, actionOptions, timeoutMs)
      val session = requireSession(sessionId)
      runHumanPaceBeforeAction(actionOptions)
      requireActionOk("fill", evaluateRaw(session, fillScript(selector, value)))
      runHumanPaceAfterAction(actionOptions)
    }

    AsyncFunction("textContent") Coroutine { sessionId: String, selector: String, timeoutMs: Int ->
      waitForSelector(sessionId, selector, timeoutMs)
      val session = requireSession(sessionId)
      decodedString(evaluateRaw(session, textContentScript(selector)))
    }

    AsyncFunction("waitForText") Coroutine { sessionId: String, text: String, exact: Boolean, timeoutMs: Int ->
      waitForText(sessionId, text, exact, timeoutMs)
    }

    AsyncFunction("clickText") Coroutine { sessionId: String, text: String, exact: Boolean, timeoutMs: Int, actionOptionsJson: String ->
      val actionOptions = parseActionOptions(actionOptionsJson)
      waitForActionableText(sessionId, text, exact, actionOptions, timeoutMs)
      val session = requireSession(sessionId)
      runHumanPaceBeforeAction(actionOptions)
      requireActionOk("clickText", evaluateRaw(session, clickTextScript(text, exact, actionOptions)))
      runHumanPaceAfterAction(actionOptions)
    }

    AsyncFunction("textContentByText") Coroutine { sessionId: String, text: String, exact: Boolean, timeoutMs: Int ->
      waitForText(sessionId, text, exact, timeoutMs)
      val session = requireSession(sessionId)
      decodedString(evaluateRaw(session, textContentByTextScript(text, exact)))
    }

    AsyncFunction("scrollBy") Coroutine { sessionId: String, x: Int, y: Int ->
      val session = requireSession(sessionId)
      requireActionOk("scrollBy", evaluateRaw(session, scrollByScript(x, y)))
    }

    AsyncFunction("scrollTo") Coroutine { sessionId: String, x: Int, y: Int ->
      val session = requireSession(sessionId)
      requireActionOk("scrollTo", evaluateRaw(session, scrollToScript(x, y)))
    }

    AsyncFunction("scrollIntoView") Coroutine { sessionId: String, selector: String, timeoutMs: Int ->
      waitForSelector(sessionId, selector, timeoutMs)
      val session = requireSession(sessionId)
      requireActionOk("scrollIntoView", evaluateRaw(session, scrollIntoViewScript(selector)))
    }

    AsyncFunction("getCookies") Coroutine { url: String ->
      getCookies(url)
    }

    AsyncFunction("setCookie") Coroutine { url: String, cookie: String ->
      setCookie(url, cookie)
    }

    AsyncFunction("clearCookies") Coroutine { ->
      clearCookies()
    }

    AsyncFunction("snapshot") Coroutine { sessionId: String ->
      val session = requireSession(sessionId)
      withContext(Dispatchers.Main) {
        session.snapshot()
      }
    }

    OnDestroy {
      destroyAllSessionsOnMain()
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun createSession(optionsJson: String): Map<String, Any?> = withContext(Dispatchers.Main) {
    val options = parseOptions(optionsJson)
    val id = UUID.randomUUID().toString()
    val webView = WebView(resolveContext())

    // setWebContentsDebuggingEnabled is a process-global static. Only ever flip
    // it on so a later non-debug session can't silently disable inspection for
    // debug sessions that are still alive.
    if (options.debug) {
      WebView.setWebContentsDebuggingEnabled(true)
    }

    webView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      loadsImagesAutomatically = options.loadImages
      cacheMode = WebSettings.LOAD_DEFAULT
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
      mediaPlaybackRequiresUserGesture = false
      options.userAgent?.let { userAgentString = it }
    }
    webView.measure(
      View.MeasureSpec.makeMeasureSpec(options.viewportWidth, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(options.viewportHeight, View.MeasureSpec.EXACTLY)
    )
    webView.layout(0, 0, options.viewportWidth, options.viewportHeight)

    val session = DroidwrightSession(id, webView)
    sessions[id] = session

    webView.webViewClient = object : WebViewClient() {
      override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        sendSessionEvent(session, "navigationStarted", mapOf("url" to url))
      }

      override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
      ): WebResourceResponse? {
        sendSessionEvent(
          session,
          "request",
          mapOf(
            "url" to request.url?.toString(),
            "method" to request.method,
            "isForMainFrame" to request.isForMainFrame,
            "hasGesture" to request.hasGesture()
          )
        )
        return super.shouldInterceptRequest(view, request)
      }

      override fun onPageFinished(view: WebView, url: String?) {
        val snapshot = session.snapshot()
        session.pendingNavigation?.complete(snapshot)
        session.pendingNavigation = null
        sendSessionEvent(session, "navigationFinished", snapshot)
      }

      override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
      ) {
        if (request.isForMainFrame) {
          val message = error.description?.toString() ?: "Navigation failed."
          session.pendingNavigation?.completeExceptionally(
            DroidwrightException("ERR_DROIDWRIGHT_NAVIGATION", message)
          )
          session.pendingNavigation = null
          sendSessionEvent(
            session,
            "navigationError",
            mapOf(
              "url" to request.url?.toString(),
              "description" to message,
              "errorCode" to error.errorCode
            )
          )
        }
      }
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        if (options.forwardConsole) {
          sendSessionEvent(
            session,
            "console",
            mapOf(
              "level" to consoleMessage.messageLevel().name.lowercase(),
              "message" to consoleMessage.message(),
              "lineNumber" to consoleMessage.lineNumber(),
              "sourceId" to consoleMessage.sourceId()
            )
          )
        }
        return true
      }
    }

    session.snapshot()
  }

  private suspend fun closeSession(sessionId: String) = withContext(Dispatchers.Main) {
    val session = sessions.remove(sessionId) ?: return@withContext
    session.pendingNavigation?.cancel()
    session.pendingNavigation = null
    session.webView.stopLoading()
    session.webView.destroy()
    sendSessionEvent(session, "closed")
  }

  private suspend fun closeAllSessions() = withContext(Dispatchers.Main) {
    sessions.keys.toList().forEach { sessionId ->
      sessions.remove(sessionId)?.let { session ->
        session.pendingNavigation?.cancel()
        session.webView.stopLoading()
        session.webView.destroy()
        sendSessionEvent(session, "closed")
      }
    }
  }

  private suspend fun navigate(
    sessionId: String,
    timeoutMs: Int,
    command: (WebView) -> Unit
  ): Map<String, Any?> {
    val session = requireSession(sessionId)
    val navigation = withContext(Dispatchers.Main) {
      session.pendingNavigation?.cancel()
      val deferred = CompletableDeferred<Map<String, Any?>>()
      session.pendingNavigation = deferred
      try {
        command(session.webView)
      } catch (error: Throwable) {
        // A command that fails synchronously (for example goBack with no
        // history) must not leave a dangling pending navigation behind.
        if (session.pendingNavigation === deferred) {
          session.pendingNavigation = null
        }
        deferred.cancel()
        throw error
      }
      deferred
    }

    val timeout = timeoutMs.coerceAtLeast(1).toLong()
    return try {
      withTimeout(timeout) {
        navigation.await()
      }
    } catch (timeoutError: TimeoutCancellationException) {
      withContext(Dispatchers.Main) {
        if (session.pendingNavigation === navigation) {
          session.pendingNavigation = null
        }
        navigation.cancel()
        session.webView.stopLoading()
      }
      throw DroidwrightException(
        "ERR_DROIDWRIGHT_TIMEOUT",
        "Timed out after ${timeout}ms waiting for navigation to finish."
      )
    }
  }

  private suspend fun waitForSelector(
    sessionId: String,
    selector: String,
    timeoutMs: Int
  ): Map<String, Any?> {
    val session = requireSession(sessionId)
    val timeout = timeoutMs.coerceAtLeast(1).toLong()
    val startedAt = System.currentTimeMillis()

    while (System.currentTimeMillis() - startedAt < timeout) {
      when (val decoded = decodeJsValue(evaluateRaw(session, selectorExistsScript(selector)))) {
        true -> return mapOf(
          "sessionId" to sessionId,
          "selector" to selector,
          "elapsedMs" to (System.currentTimeMillis() - startedAt)
        )
        is String -> {
          if (decoded.startsWith(SELECTOR_ERROR_PREFIX)) {
            throw DroidwrightException(
              "ERR_DROIDWRIGHT_SELECTOR",
              decoded.removePrefix(SELECTOR_ERROR_PREFIX)
            )
          }
        }
      }
      delay(POLL_INTERVAL_MS)
    }

    throw DroidwrightException(
      "ERR_DROIDWRIGHT_TIMEOUT",
      "Timed out after ${timeout}ms waiting for selector '$selector'."
    )
  }

  private suspend fun waitForText(
    sessionId: String,
    text: String,
    exact: Boolean,
    timeoutMs: Int
  ): Map<String, Any?> {
    val session = requireSession(sessionId)
    val timeout = timeoutMs.coerceAtLeast(1).toLong()
    val startedAt = System.currentTimeMillis()

    while (System.currentTimeMillis() - startedAt < timeout) {
      if (decodeJsValue(evaluateRaw(session, textExistsScript(text, exact))) == true) {
        return mapOf(
          "sessionId" to sessionId,
          "text" to text,
          "exact" to exact,
          "elapsedMs" to (System.currentTimeMillis() - startedAt)
        )
      }
      delay(POLL_INTERVAL_MS)
    }

    throw DroidwrightException(
      "ERR_DROIDWRIGHT_TIMEOUT",
      "Timed out after ${timeout}ms waiting for text '$text'."
    )
  }

  private suspend fun waitForActionableSelector(
    sessionId: String,
    selector: String,
    options: ActionOptions,
    timeoutMs: Int
  ): Map<String, Any?> {
    val session = requireSession(sessionId)
    return waitForActionability(
      session = session,
      timeoutMs = timeoutMs,
      options = options,
      describeTarget = "selector '$selector'",
      scriptFactory = { actionabilityScript(selector, options) }
    )
  }

  private suspend fun waitForActionableText(
    sessionId: String,
    text: String,
    exact: Boolean,
    options: ActionOptions,
    timeoutMs: Int
  ): Map<String, Any?> {
    val session = requireSession(sessionId)
    return waitForActionability(
      session = session,
      timeoutMs = timeoutMs,
      options = options,
      describeTarget = "text '$text'",
      scriptFactory = { actionabilityByTextScript(text, exact, options) }
    )
  }

  private suspend fun waitForActionability(
    session: DroidwrightSession,
    timeoutMs: Int,
    options: ActionOptions,
    describeTarget: String,
    scriptFactory: () -> String
  ): Map<String, Any?> {
    val timeout = timeoutMs.coerceAtLeast(1).toLong()
    val startedAt = System.currentTimeMillis()
    var lastFailure: ActionabilityResult? = null

    while (System.currentTimeMillis() - startedAt < timeout) {
      val first = parseActionabilityResult(evaluateRaw(session, scriptFactory()))

      if (first.ok) {
        if (!options.stable || options.force) {
          return first.toMap(session.id, System.currentTimeMillis() - startedAt)
        }

        delay(STABILITY_SAMPLE_MS)
        val second = parseActionabilityResult(evaluateRaw(session, scriptFactory()))
        if (second.ok && first.sameRectAs(second)) {
          return second.toMap(session.id, System.currentTimeMillis() - startedAt)
        }

        lastFailure = ActionabilityResult(
          ok = false,
          code = "ERR_DROIDWRIGHT_NOT_STABLE",
          message = "Element for $describeTarget is moving or changing layout.",
          rect = second.rect
        )
      } else {
        lastFailure = first
      }

      delay(POLL_INTERVAL_MS)
    }

    val failure = lastFailure ?: ActionabilityResult(
      ok = false,
      code = "ERR_DROIDWRIGHT_TIMEOUT",
      message = "Timed out after ${timeout}ms waiting for actionable $describeTarget.",
      rect = null
    )

    throw DroidwrightException(
      failure.code ?: "ERR_DROIDWRIGHT_TIMEOUT",
      failure.message ?: "Timed out after ${timeout}ms waiting for actionable $describeTarget."
    )
  }

  private suspend fun getCookies(url: String): List<Map<String, Any?>> = withContext(Dispatchers.Main) {
    parseCookies(CookieManager.getInstance().getCookie(url) ?: "")
  }

  private suspend fun setCookie(url: String, cookie: String): Map<String, Any?> =
    withContext(Dispatchers.Main) {
      suspendCancellableCoroutine { continuation ->
        CookieManager.getInstance().setCookie(url, cookie) { accepted ->
          CookieManager.getInstance().flush()
          if (continuation.isActive) {
            continuation.resume(mapOf("accepted" to accepted))
          }
        }
      }
    }

  private suspend fun clearCookies(): Map<String, Any?> = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
      CookieManager.getInstance().removeAllCookies { removed ->
        CookieManager.getInstance().flush()
        if (continuation.isActive) {
          continuation.resume(mapOf("removed" to removed))
        }
      }
    }
  }

  private suspend fun runHumanPaceBeforeAction(options: ActionOptions) {
    if (options.humanPace.enabled) {
      delay(Random.nextLong(options.humanPace.minDelayMs.toLong(), options.humanPace.maxDelayMs.toLong() + 1))
    }
  }

  private suspend fun runHumanPaceAfterAction(options: ActionOptions) {
    if (options.humanPace.enabled && options.humanPace.postActionDelayMs > 0) {
      delay(options.humanPace.postActionDelayMs.toLong())
    }
  }

  private suspend fun evaluateRaw(session: DroidwrightSession, script: String): String? =
    withContext(Dispatchers.Main) {
      suspendCancellableCoroutine { continuation ->
        session.webView.evaluateJavascript("${automationPreamble()}\n$script") { value ->
          if (continuation.isActive) {
            continuation.resume(value)
          }
        }
      }
    }

  private fun requireSession(sessionId: String): DroidwrightSession {
    return sessions[sessionId]
      ?: throw DroidwrightException("ERR_DROIDWRIGHT_NO_SESSION", "No Droidwright session exists for '$sessionId'.")
  }

  private fun resolveContext(): Context {
    return appContext.currentActivity ?: appContext.reactContext
      ?: throw DroidwrightException("ERR_DROIDWRIGHT_CONTEXT", "Droidwright needs an Android context to create a WebView.")
  }

  private fun parseOptions(optionsJson: String): SessionOptions {
    val json = runCatching { JSONObject(optionsJson.ifBlank { "{}" }) }.getOrElse { JSONObject() }
    return SessionOptions(
      userAgent = json.optString("userAgent").takeIf { it.isNotBlank() },
      debug = json.optBoolean("debug", false),
      forwardConsole = json.optBoolean("forwardConsole", false),
      loadImages = json.optBoolean("loadImages", true),
      viewportWidth = json.optInt("viewportWidth", DEFAULT_VIEWPORT_WIDTH).coerceAtLeast(1),
      viewportHeight = json.optInt("viewportHeight", DEFAULT_VIEWPORT_HEIGHT).coerceAtLeast(1)
    )
  }

  private fun parseActionOptions(optionsJson: String): ActionOptions {
    val json = runCatching { JSONObject(optionsJson.ifBlank { "{}" }) }.getOrElse { JSONObject() }
    val humanPaceJson = json.optJSONObject("humanPace") ?: JSONObject()
    val humanPaceEnabled = humanPaceJson.optBoolean("enabled", false)
    return ActionOptions(
      visible = json.optBoolean("visible", true),
      enabled = json.optBoolean("enabled", true),
      stable = json.optBoolean("stable", true),
      force = json.optBoolean("force", false),
      humanPace = HumanPaceOptions(
        enabled = humanPaceEnabled,
        movementSteps = humanPaceJson.optInt("movementSteps", DEFAULT_HUMAN_MOVEMENT_STEPS).coerceAtLeast(MIN_HUMAN_MOVEMENT_STEPS),
        minDelayMs = humanPaceJson.optInt("minDelayMs", DEFAULT_HUMAN_MIN_DELAY_MS).coerceAtLeast(0),
        maxDelayMs = humanPaceJson.optInt("maxDelayMs", DEFAULT_HUMAN_MAX_DELAY_MS).coerceAtLeast(humanPaceJson.optInt("minDelayMs", DEFAULT_HUMAN_MIN_DELAY_MS).coerceAtLeast(0)),
        postActionDelayMs = humanPaceJson.optInt("postActionDelayMs", DEFAULT_HUMAN_POST_ACTION_DELAY_MS).coerceAtLeast(0)
      )
    )
  }

  private fun requireActionOk(action: String, raw: String?): Map<String, Any?> {
    val decoded = decodedString(raw)
    val json = runCatching { JSONObject(decoded) }.getOrElse {
      throw DroidwrightException("ERR_DROIDWRIGHT_ACTION", "The $action action returned an invalid result: $decoded")
    }

    if (!json.optBoolean("ok", false)) {
      throw DroidwrightException(
        json.optString("code").takeIf { it.isNotBlank() } ?: "ERR_DROIDWRIGHT_ACTION",
        json.optString("error", "The $action action failed.")
      )
    }

    return jsonToMap(json)
  }

  private fun decodedString(raw: String?): String {
    val decoded = decodeJsValue(raw)
    return decoded?.toString() ?: ""
  }

  private fun decodeJsValue(raw: String?): Any? {
    if (raw == null || raw == "null" || raw == "undefined") {
      return null
    }

    return runCatching {
      val wrapper = JSONObject("{\"value\":$raw}")
      val value = wrapper.get("value")
      if (value == JSONObject.NULL) null else value
    }.getOrElse {
      raw
    }
  }

  private fun jsonToMap(json: JSONObject): Map<String, Any?> {
    return json.keys().asSequence().associateWith { key ->
      val value = json.get(key)
      if (value == JSONObject.NULL) null else value
    }
  }

  private fun parseActionabilityResult(raw: String?): ActionabilityResult {
    val decoded = decodedString(raw)
    val json = runCatching { JSONObject(decoded) }.getOrElse {
      return ActionabilityResult(
        ok = false,
        code = "ERR_DROIDWRIGHT_ACTION",
        message = "Actionability check returned an invalid result: $decoded",
        rect = null
      )
    }

    return ActionabilityResult(
      ok = json.optBoolean("ok", false),
      code = json.optString("code").takeIf { it.isNotBlank() },
      message = json.optString("error", json.optString("message", "")).takeIf { it.isNotBlank() },
      rect = json.optJSONObject("rect")?.let {
        ElementRect(
          x = it.optDouble("x"),
          y = it.optDouble("y"),
          width = it.optDouble("width"),
          height = it.optDouble("height")
        )
      }
    )
  }

  private fun sendSessionEvent(
    session: DroidwrightSession,
    type: String,
    extra: Map<String, Any?> = emptyMap()
  ) {
    sendEvent(
      "onEvent",
      mapOf(
        "sessionId" to session.id,
        "type" to type
      ) + extra
    )
  }

  private fun parseCookies(cookieHeader: String): List<Map<String, Any?>> {
    if (cookieHeader.isBlank()) {
      return emptyList()
    }

    return cookieHeader.split(";").mapNotNull { part ->
      val trimmed = part.trim()
      if (trimmed.isBlank()) {
        return@mapNotNull null
      }
      val separator = trimmed.indexOf("=")
      if (separator < 0) {
        mapOf("name" to trimmed, "value" to "")
      } else {
        mapOf(
          "name" to trimmed.substring(0, separator),
          "value" to trimmed.substring(separator + 1)
        )
      }
    }
  }

  private fun destroyAllSessionsOnMain() {
    mainHandler.post {
      sessions.keys.toList().forEach { sessionId ->
        sessions.remove(sessionId)?.let { session ->
          session.pendingNavigation?.cancel()
          session.webView.stopLoading()
          session.webView.destroy()
        }
      }
    }
  }

  private fun selectorExistsScript(selector: String): String {
    return """
      (function() {
        try {
          return document.querySelector(${quote(selector)}) !== null;
        } catch (error) {
          return ${quote(SELECTOR_ERROR_PREFIX)} + error.message;
        }
      })()
    """.trimIndent()
  }

  private fun actionabilityScript(selector: String, options: ActionOptions): String {
    return """
      (function() {
        return JSON.stringify(window.__droidwrightCheckActionability(
          document.querySelector(${quote(selector)}),
          ${actionOptionsJson(options)},
          ${quote("selector ${escapeForMessage(selector)}")}
        ));
      })()
    """.trimIndent()
  }

  private fun actionabilityByTextScript(text: String, exact: Boolean, options: ActionOptions): String {
    return """
      (function() {
        return JSON.stringify(window.__droidwrightCheckActionability(
          window.__droidwrightFindByText(${quote(text)}, $exact),
          ${actionOptionsJson(options)},
          ${quote("text ${escapeForMessage(text)}")}
        ));
      })()
    """.trimIndent()
  }

  private fun clickScript(selector: String, options: ActionOptions): String {
    return """
      (function() {
        try {
          var element = document.querySelector(${quote(selector)});
          if (!element) {
            return JSON.stringify({ ok: false, error: "No element matches selector ${escapeForMessage(selector)}." });
          }
          window.__droidwrightDispatchTap(element, ${actionOptionsJson(options)});
          return JSON.stringify({ ok: true });
        } catch (error) {
          return JSON.stringify({ ok: false, error: error.message });
        }
      })()
    """.trimIndent()
  }

  private fun tapScript(selector: String, options: ActionOptions): String = clickScript(selector, options)

  private fun fillScript(selector: String, value: String): String {
    return """
      (function() {
        try {
          var element = document.querySelector(${quote(selector)});
          var value = ${quote(value)};
          if (!element) {
            return JSON.stringify({ ok: false, error: "No element matches selector ${escapeForMessage(selector)}." });
          }
          element.scrollIntoView({ block: "center", inline: "center" });
          element.focus();
          if ("value" in element) {
            var prototype = Object.getPrototypeOf(element);
            var descriptor = Object.getOwnPropertyDescriptor(prototype, "value");
            if (descriptor && descriptor.set) {
              descriptor.set.call(element, value);
            } else {
              element.value = value;
            }
          } else if (element.isContentEditable) {
            element.textContent = value;
          } else {
            return JSON.stringify({ ok: false, error: "Element is not editable." });
          }
          element.dispatchEvent(new Event("input", { bubbles: true }));
          element.dispatchEvent(new Event("change", { bubbles: true }));
          return JSON.stringify({ ok: true });
        } catch (error) {
          return JSON.stringify({ ok: false, error: error.message });
        }
      })()
    """.trimIndent()
  }

  private fun textContentScript(selector: String): String {
    return """
      (function() {
        var element = document.querySelector(${quote(selector)});
        if (!element) {
          return "";
        }
        return element.innerText || element.textContent || element.value || "";
      })()
    """.trimIndent()
  }

  private fun textExistsScript(text: String, exact: Boolean): String {
    return """
      (function() {
        return !!window.__droidwrightFindByText(${quote(text)}, $exact);
      })()
    """.trimIndent()
  }

  private fun clickTextScript(text: String, exact: Boolean, options: ActionOptions): String {
    return """
      (function() {
        try {
          var element = window.__droidwrightFindByText(${quote(text)}, $exact);
          if (!element) {
            return JSON.stringify({ ok: false, error: "No element matches text ${escapeForMessage(text)}." });
          }
          window.__droidwrightDispatchTap(element, ${actionOptionsJson(options)});
          return JSON.stringify({ ok: true });
        } catch (error) {
          return JSON.stringify({ ok: false, error: error.message });
        }
      })()
    """.trimIndent()
  }

  private fun textContentByTextScript(text: String, exact: Boolean): String {
    return """
      (function() {
        var element = window.__droidwrightFindByText(${quote(text)}, $exact);
        if (!element) {
          return "";
        }
        return element.innerText || element.textContent || element.value || "";
      })()
    """.trimIndent()
  }

  private fun scrollByScript(x: Int, y: Int): String {
    return """
      (function() {
        window.scrollBy($x, $y);
        return JSON.stringify({ ok: true, scrollX: window.scrollX, scrollY: window.scrollY });
      })()
    """.trimIndent()
  }

  private fun scrollToScript(x: Int, y: Int): String {
    return """
      (function() {
        window.scrollTo($x, $y);
        return JSON.stringify({ ok: true, scrollX: window.scrollX, scrollY: window.scrollY });
      })()
    """.trimIndent()
  }

  private fun scrollIntoViewScript(selector: String): String {
    return """
      (function() {
        try {
          var element = document.querySelector(${quote(selector)});
          if (!element) {
            return JSON.stringify({ ok: false, error: "No element matches selector ${escapeForMessage(selector)}." });
          }
          element.scrollIntoView({ block: "center", inline: "center" });
          return JSON.stringify({ ok: true, scrollX: window.scrollX, scrollY: window.scrollY });
        } catch (error) {
          return JSON.stringify({ ok: false, error: error.message });
        }
      })()
    """.trimIndent()
  }

  private fun automationPreamble(): String {
    return """
      (function() {
        if (!window.__droidwrightFindByText) {
          window.__droidwrightFindByText = function(text, exact) {
            var target = String(text).trim();
            var walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_ELEMENT);
            var best = null;
            while (walker.nextNode()) {
              var element = walker.currentNode;
              var content = (element.innerText || element.textContent || element.value || "").trim();
              if (!content) continue;
              var matches = exact ? content === target : content.indexOf(target) !== -1;
              if (!matches) continue;
              if (!best || content.length < ((best.innerText || best.textContent || best.value || "").trim()).length) {
                best = element;
              }
            }
            return best;
          };
        }
        if (!window.__droidwrightRectPayload) {
          window.__droidwrightRectPayload = function(rect) {
            return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };
          };
        }
        if (!window.__droidwrightIsDescendantOrSelf) {
          window.__droidwrightIsDescendantOrSelf = function(parent, child) {
            while (child) {
              if (child === parent) return true;
              child = child.parentElement;
            }
            return false;
          };
        }
        if (!window.__droidwrightCheckActionability) {
          window.__droidwrightCheckActionability = function(element, options, label) {
            if (!element) {
              return {
                ok: false,
                code: "ERR_DROIDWRIGHT_NO_ELEMENT",
                error: "No element matches " + label + "."
              };
            }

            var rect = element.getBoundingClientRect();
            var rectPayload = window.__droidwrightRectPayload(rect);

            if (options.force) {
              return { ok: true, rect: rectPayload };
            }

            if (options.visible) {
              var style = window.getComputedStyle(element);
              var hasBox = rect.width > 0 && rect.height > 0;
              var styleVisible = style.visibility !== "hidden" && style.display !== "none" && Number(style.opacity || "1") > 0;
              if (!hasBox || !styleVisible) {
                return {
                  ok: false,
                  code: "ERR_DROIDWRIGHT_NOT_VISIBLE",
                  error: "Element for " + label + " is not visible.",
                  rect: rectPayload
                };
              }
            }

            if (options.enabled) {
              var disabled = !!element.disabled || element.getAttribute("aria-disabled") === "true";
              if (disabled) {
                return {
                  ok: false,
                  code: "ERR_DROIDWRIGHT_NOT_ENABLED",
                  error: "Element for " + label + " is disabled.",
                  rect: rectPayload
                };
              }
            }

            // Scroll into view BEFORE the viewport-intersection and covered
            // checks. Otherwise a below-the-fold element that is perfectly
            // actionable after scrolling is wrongly rejected as not visible.
            element.scrollIntoView({ block: "center", inline: "center" });
            rect = element.getBoundingClientRect();
            rectPayload = window.__droidwrightRectPayload(rect);

            if (options.visible) {
              var intersectsViewport = rect.bottom > 0 && rect.right > 0 && rect.top < window.innerHeight && rect.left < window.innerWidth;
              if (!intersectsViewport) {
                return {
                  ok: false,
                  code: "ERR_DROIDWRIGHT_NOT_VISIBLE",
                  error: "Element for " + label + " is not visible.",
                  rect: rectPayload
                };
              }
            }

            var centerX = rect.left + rect.width / 2;
            var centerY = rect.top + rect.height / 2;
            var centerInViewport = centerX >= 0 && centerY >= 0 && centerX <= window.innerWidth && centerY <= window.innerHeight;
            if (options.visible && !centerInViewport) {
              return {
                ok: false,
                code: "ERR_DROIDWRIGHT_NOT_VISIBLE",
                error: "Element for " + label + " is not centered in the viewport.",
                rect: rectPayload
              };
            }

            var topElement = centerInViewport ? document.elementFromPoint(centerX, centerY) : null;
            if (topElement && !window.__droidwrightIsDescendantOrSelf(element, topElement) && !window.__droidwrightIsDescendantOrSelf(topElement, element)) {
              return {
                ok: false,
                code: "ERR_DROIDWRIGHT_ELEMENT_COVERED",
                error: "Element for " + label + " is covered by another element.",
                rect: rectPayload
              };
            }

            return { ok: true, rect: rectPayload };
          };
        }
        if (!window.__droidwrightDispatchTap) {
          window.__droidwrightDispatchTap = function(element, actionOptions) {
            actionOptions = actionOptions || {};
            var humanPace = actionOptions.humanPace || {};
            var movementSteps = humanPace.enabled ? Math.max(5, Number(humanPace.movementSteps || 5)) : 1;
            element.scrollIntoView({ block: "center", inline: "center" });
            var rect = element.getBoundingClientRect();
            var x = rect.left + rect.width / 2;
            var y = rect.top + rect.height / 2;
            var target = document.elementFromPoint(x, y) || element;
            var base = { bubbles: true, cancelable: true, composed: true, view: window, clientX: x, clientY: y };
            var startX = Math.max(1, Math.min(window.innerWidth - 1, x - 28 - Math.random() * 24));
            var startY = Math.max(1, Math.min(window.innerHeight - 1, y - 18 - Math.random() * 24));
            if (typeof PointerEvent === "function") {
              target.dispatchEvent(new PointerEvent("pointerover", Object.assign({}, base, { pointerType: "touch", isPrimary: true, clientX: startX, clientY: startY })));
              for (var index = 1; index <= movementSteps; index += 1) {
                var progress = index / movementSteps;
                var jitterX = (Math.random() - 0.5) * 3;
                var jitterY = (Math.random() - 0.5) * 3;
                target.dispatchEvent(new PointerEvent("pointermove", Object.assign({}, base, {
                  pointerType: "touch",
                  isPrimary: true,
                  clientX: startX + (x - startX) * progress + jitterX,
                  clientY: startY + (y - startY) * progress + jitterY
                })));
              }
              target.dispatchEvent(new PointerEvent("pointerover", Object.assign({}, base, { pointerType: "touch", isPrimary: true })));
              target.dispatchEvent(new PointerEvent("pointerdown", Object.assign({}, base, { pointerType: "touch", isPrimary: true })));
              target.dispatchEvent(new PointerEvent("pointerup", Object.assign({}, base, { pointerType: "touch", isPrimary: true })));
            }
            target.dispatchEvent(new MouseEvent("mouseover", base));
            target.dispatchEvent(new MouseEvent("mousedown", base));
            target.dispatchEvent(new MouseEvent("mouseup", base));
            target.dispatchEvent(new MouseEvent("click", base));
            if (typeof element.click === "function" && target !== element) {
              element.click();
            }
          };
        }
      })();
    """.trimIndent()
  }

  private fun quote(value: String): String = JSONObject.quote(value)

  private fun actionOptionsJson(options: ActionOptions): String {
    return JSONObject(
      mapOf(
        "visible" to options.visible,
        "enabled" to options.enabled,
        "stable" to options.stable,
        "force" to options.force,
        "humanPace" to JSONObject(
          mapOf(
            "enabled" to options.humanPace.enabled,
            "movementSteps" to options.humanPace.movementSteps,
            "minDelayMs" to options.humanPace.minDelayMs,
            "maxDelayMs" to options.humanPace.maxDelayMs,
            "postActionDelayMs" to options.humanPace.postActionDelayMs
          )
        )
      )
    ).toString()
  }

  private fun escapeForMessage(value: String): String {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
  }

  private data class ActionOptions(
    val visible: Boolean,
    val enabled: Boolean,
    val stable: Boolean,
    val force: Boolean,
    val humanPace: HumanPaceOptions
  )

  private data class HumanPaceOptions(
    val enabled: Boolean,
    val movementSteps: Int,
    val minDelayMs: Int,
    val maxDelayMs: Int,
    val postActionDelayMs: Int
  )

  private data class ElementRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
  ) {
    fun closeTo(other: ElementRect): Boolean {
      return kotlin.math.abs(x - other.x) <= RECT_STABILITY_EPSILON &&
        kotlin.math.abs(y - other.y) <= RECT_STABILITY_EPSILON &&
        kotlin.math.abs(width - other.width) <= RECT_STABILITY_EPSILON &&
        kotlin.math.abs(height - other.height) <= RECT_STABILITY_EPSILON
    }
  }

  private data class ActionabilityResult(
    val ok: Boolean,
    val code: String?,
    val message: String?,
    val rect: ElementRect?
  ) {
    fun sameRectAs(other: ActionabilityResult): Boolean {
      val currentRect = rect ?: return false
      val nextRect = other.rect ?: return false
      return currentRect.closeTo(nextRect)
    }

    fun toMap(sessionId: String, elapsedMs: Long): Map<String, Any?> {
      val payload = mutableMapOf<String, Any?>(
        "sessionId" to sessionId,
        "elapsedMs" to elapsedMs,
        "ok" to ok
      )
      rect?.let {
        payload["rect"] = mapOf(
          "x" to it.x,
          "y" to it.y,
          "width" to it.width,
          "height" to it.height
        )
      }
      return payload
    }
  }

  private data class SessionOptions(
    val userAgent: String?,
    val debug: Boolean,
    val forwardConsole: Boolean,
    val loadImages: Boolean,
    val viewportWidth: Int,
    val viewportHeight: Int
  )

  private class DroidwrightSession(
    val id: String,
    val webView: WebView
  ) {
    var pendingNavigation: CompletableDeferred<Map<String, Any?>>? = null

    fun snapshot(): Map<String, Any?> {
      return mapOf(
        "sessionId" to id,
        "url" to (webView.url ?: ""),
        "originalUrl" to (webView.originalUrl ?: ""),
        "title" to (webView.title ?: ""),
        "progress" to webView.progress,
        "canGoBack" to webView.canGoBack(),
        "canGoForward" to webView.canGoForward(),
        "userAgent" to webView.settings.userAgentString
      )
    }
  }

  private class DroidwrightException(
    code: String,
    message: String
  ) : CodedException(code, message, null)

  private companion object {
    const val POLL_INTERVAL_MS = 100L
    const val STABILITY_SAMPLE_MS = 80L
    const val RECT_STABILITY_EPSILON = 0.5
    const val MIN_HUMAN_MOVEMENT_STEPS = 5
    const val DEFAULT_HUMAN_MOVEMENT_STEPS = 5
    const val DEFAULT_HUMAN_MIN_DELAY_MS = 80
    const val DEFAULT_HUMAN_MAX_DELAY_MS = 220
    const val DEFAULT_HUMAN_POST_ACTION_DELAY_MS = 160
    const val DEFAULT_VIEWPORT_WIDTH = 1080
    const val DEFAULT_VIEWPORT_HEIGHT = 1920
    const val SELECTOR_ERROR_PREFIX = "__DROIDWRIGHT_SELECTOR_ERROR__:"
  }
}
