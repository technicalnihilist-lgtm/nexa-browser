package com.nexabrowser.app.ui.components

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Attaches the active tab's [WebView] to the composition. The WebView is owned
 * by the tab manager, so this composable only moves it in and out of a host
 * FrameLayout — it never creates or destroys one. That is what lets tabs keep
 * their scroll position and history across switches without leaking.
 */
@Composable
fun WebViewHost(
    webView: WebView?,
    modifier: Modifier = Modifier,
    onScrolled: (scrollY: Int, delta: Int) -> Unit = { _, _ -> }
) {
    val latestOnScrolled by rememberUpdatedState(onScrolled)
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier,
        update = { frame ->
            // Purely visual: lets the chrome collapse and return as the page scrolls.
            webView?.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                latestOnScrolled(scrollY, scrollY - oldScrollY)
            }
            val current = if (frame.childCount > 0) frame.getChildAt(0) else null
            if (current !== webView) {
                frame.removeAllViews()
                if (webView != null) {
                    // Detach from any previous host before re-parenting.
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    frame.addView(
                        webView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }
    )
}
