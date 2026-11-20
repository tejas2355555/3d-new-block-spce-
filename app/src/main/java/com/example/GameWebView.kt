package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

private const val GAME_ASSET_FILE = "Block_Space_3D__2__a816ca62.html"
private const val GAME_VIRTUAL_HOST = "blockspace3d.app"
const val GAME_ENTRY_URL = "https://$GAME_VIRTUAL_HOST/index.html"

internal object HtmlAssetAdapter {
    fun loadAdaptedGameHtml(context: Context): ByteArray {
        val raw = context.assets.open(GAME_ASSET_FILE).bufferedReader(StandardCharsets.UTF_8).use {
            it.readText()
        }
        val patched = raw
            // Guard against offline startup before Three.js finishes loading
            .replace(
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js\"></script>",
                """
                <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
                <script>
                if (typeof THREE === 'undefined') {
                  window.__threeMissing = true;
                  if (window.AndroidBridge) {
                    window.AndroidBridge.onEngineError('Unable to load 3D engine (Three.js). Please check your internet connection.');
                  }
                }
                </script>
                """.trimIndent()
            )
            // Skip running game loop if Three.js failed to load
            .replace(
                "<script>\n(() => {",
                "<script>\n(() => {\nif (window.__threeMissing || typeof THREE === 'undefined') return;"
            )
            // Disable forced portrait blocker overlay since Android manages orientation
            .replace(
                "tip.classList.toggle('on', phone && state==='play' && innerHeight>innerWidth);",
                "tip.classList.toggle('on', false);"
            )
            // Route fullscreen toggles through native Android WindowInsetsController
            .replace(
                "function enterFS(){",
                "function enterFS(){if(window.AndroidBridge){window.AndroidBridge.setImmersive(true);return;}"
            )
            .replace(
                "function toggleFS(){",
                "function toggleFS(){if(window.AndroidBridge){window.AndroidBridge.toggleImmersive();return;}"
            )
            // Keep Android UI in sync with current screen ('menu' | 'play' | 'pause')
            .replace(
                "function showScreen(s){",
                "function showScreen(s){if(window.AndroidBridge)window.AndroidBridge.onScreenChanged(s);"
            )
            // Trigger native Android haptics alongside sound events
            .replace(
                "play(n){if(!on||!ac)return;",
                "play(n){if(window.AndroidBridge)window.AndroidBridge.haptic(n);if(!on||!ac)return;"
            )
            // Expose lifecycle & backup helpers right before boot loop starts
            .replace(
                "requestAnimationFrame(loop);",
                """
                window.__bsBridge = {
                  saveGame: saveGame,
                  togglePause: togglePause,
                  getState: function(){ return state; },
                  refreshContinue: function(){ $('continueBtn').classList.toggle('hidden', !wList().length); },
                  notifyToast: function(msg){ toast(msg); }
                };
                if (window.AndroidBridge) window.AndroidBridge.onEngineReady();
                requestAnimationFrame(loop);
                """.trimIndent()
            )
            // Add Android Back, Save, and World Backup JSON helpers
            .replace(
                "</body>",
                """
                <script>
                window.handleAndroidBack = function() {
                  var modals = ['bag', 'chestM', 'wm', 'tasksM', 'ctlM', 'setM'];
                  var closes = {
                    bag: 'bagClose',
                    chestM: 'chestClose',
                    wm: 'wmX',
                    tasksM: 'tasksClose',
                    ctlM: 'ctlClose',
                    setM: 'setClose'
                  };
                  for (var i = 0; i < modals.length; i++) {
                    var id = modals[i];
                    var el = document.getElementById(id);
                    if (el && !el.classList.contains('hidden')) {
                      var btn = document.getElementById(closes[id]);
                      if (btn) btn.click();
                      else el.classList.add('hidden');
                      return 'handled';
                    }
                  }
                  if (window.__bsBridge) {
                    var st = window.__bsBridge.getState();
                    if (st === 'play' || st === 'pause') {
                      window.__bsBridge.togglePause();
                      return 'handled';
                    }
                  }
                  return 'menu';
                };

                window.saveWorldNow = function() {
                  if (window.__bsBridge) window.__bsBridge.saveGame();
                };

                window.exportWorldsJson = function() {
                  try {
                    var data = {};
                    for (var i = 0; i < localStorage.length; i++) {
                      var k = localStorage.key(i);
                      if (k && (k.indexOf('blockspace3d-') === 0 || k.indexOf('bc-') === 0)) {
                        data[k] = localStorage.getItem(k);
                      }
                    }
                    return JSON.stringify({ app: 'Block Space 3D', version: 1, storage: data }, null, 2);
                  } catch (e) {
                    return '';
                  }
                };

                window.importWorldsJson = function(rawJson) {
                  try {
                    var parsed = JSON.parse(rawJson);
                    var storage = parsed && parsed.storage ? parsed.storage : parsed;
                    if (!storage || typeof storage !== 'object') return false;
                    var count = 0;
                    Object.keys(storage).forEach(function(k) {
                      if (k.indexOf('blockspace3d-') === 0 || k.indexOf('bc-') === 0) {
                        localStorage.setItem(k, String(storage[k]));
                        if (k.indexOf('blockspace3d-w-') === 0) count++;
                      }
                    });
                    if (window.__bsBridge) {
                      window.__bsBridge.refreshContinue();
                      window.__bsBridge.notifyToast('Imported ' + count + ' world(s)');
                    }
                    return true;
                  } catch (e) {
                    return false;
                  }
                };
                </script>
                </body>
                """.trimIndent()
            )
        return patched.toByteArray(StandardCharsets.UTF_8)
    }
}

class AndroidGameBridge(
    private val view: View,
    private val isHapticsEnabled: () -> Boolean,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
    private val onScreenChanged: (String) -> Unit,
    private val onSetImmersive: (Boolean) -> Unit,
    private val onToggleImmersive: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onEngineReady() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun onEngineError(message: String) {
        mainHandler.post { onError(message) }
    }

    @JavascriptInterface
    fun onScreenChanged(screen: String) {
        mainHandler.post { onScreenChanged(screen) }
    }

    @JavascriptInterface
    fun setImmersive(enabled: Boolean) {
        mainHandler.post { onSetImmersive(enabled) }
    }

    @JavascriptInterface
    fun toggleImmersive() {
        mainHandler.post { onToggleImmersive() }
    }

    @JavascriptInterface
    fun haptic(kind: String) {
        if (!isHapticsEnabled()) return
        mainHandler.post {
            val code = when (kind) {
                "boom", "hurt", "roar" -> HapticFeedbackConstants.LONG_PRESS
                "break", "place", "hit" -> HapticFeedbackConstants.VIRTUAL_KEY
                "jump", "eat", "chirp" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.VIRTUAL_KEY
                    }
                else -> HapticFeedbackConstants.CLOCK_TICK
            }
            view.performHapticFeedback(code)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GameWebView(
    reloadNonce: Int,
    isHapticsEnabled: () -> Boolean,
    onEngineReady: () -> Unit,
    onEngineError: (String) -> Unit,
    onScreenChanged: (String) -> Unit,
    onSetImmersive: (Boolean) -> Unit,
    onToggleImmersive: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#0A0F28"))
                isHapticFeedbackEnabled = true
                overScrollMode = View.OVER_SCROLL_NEVER

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = false
                    allowContentAccess = false
                }

                val bridge = AndroidGameBridge(
                    view = this,
                    isHapticsEnabled = isHapticsEnabled,
                    onReady = onEngineReady,
                    onError = onEngineError,
                    onScreenChanged = onScreenChanged,
                    onSetImmersive = onSetImmersive,
                    onToggleImmersive = onToggleImmersive
                )
                addJavascriptInterface(bridge, "AndroidBridge")

                webChromeClient = WebChromeClient()

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url ?: return null
                        if (url.host == GAME_VIRTUAL_HOST &&
                            (url.path == "/index.html" || url.path == "/" || url.path.isNullOrEmpty())
                        ) {
                            return try {
                                val bytes = HtmlAssetAdapter.loadAdaptedGameHtml(context)
                                WebResourceResponse(
                                    "text/html",
                                    "utf-8",
                                    ByteArrayInputStream(bytes)
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            val msg = error?.description?.toString()
                                ?: "Failed to load Block Space 3D."
                            onEngineError(msg)
                        }
                    }
                }

                tag = reloadNonce
                loadUrl(GAME_ENTRY_URL)
                onWebViewCreated(this)
            }
        },
        update = { webView ->
            if (webView.tag != reloadNonce) {
                webView.tag = reloadNonce
                webView.loadUrl(GAME_ENTRY_URL)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.removeJavascriptInterface("AndroidBridge")
            webView.destroy()
        }
    )
}
