package com.nexabrowser.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexabrowser.app.browser.TabInfo
import com.nexabrowser.app.domain.model.AddressBarStyle
import com.nexabrowser.app.ui.theme.BarSurface
import com.nexabrowser.app.ui.theme.LocalNexaColors
import com.nexabrowser.app.ui.theme.NexaIcons
import com.nexabrowser.app.ui.theme.barBorderColor
import com.nexabrowser.app.ui.theme.barContentColor
import com.nexabrowser.app.ui.theme.barSecondaryColor
import com.nexabrowser.app.util.UrlUtils

/**
 * The centred, pill-shaped address bar. It shows a readable URL while the page
 * is in view and the raw URL the moment it is focused, so editing is never
 * surprising. The lock/globe glyph reflects the real connection scheme.
 *
 * The surface itself is chosen by [style]; the default is an opaque white bar
 * with black text so the bar never disappears against a dark page.
 */
@Composable
fun AddressBar(
    tab: TabInfo?,
    isLoading: Boolean,
    progress: Int,
    style: AddressBarStyle,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extras = LocalNexaColors.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val contentColor = barContentColor(style)
    val secondaryColor = barSecondaryColor(style)

    val rawUrl = tab?.url.orEmpty()
    var focused by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(rawUrl, focused) {
        if (!focused) value = TextFieldValue(UrlUtils.displayUrl(rawUrl))
    }

    val isSecure = UrlUtils.isHttps(rawUrl)
    val isHttp = rawUrl.startsWith("http://")
    val isPrivate = tab?.isPrivate == true

    val animatedProgress by animateFloatAsState(
        targetValue = (progress.coerceIn(0, 100)) / 100f,
        label = "progress"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        BarSurface(
            style = style,
            shape = RoundedCornerShape(26.dp),
            elevation = 12.dp,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isPrivate -> NexaIcons.VisibilityOff
                        isSecure -> Icons.Filled.Lock
                        isHttp -> Icons.Filled.Warning
                        else -> Icons.Filled.Search
                    },
                    contentDescription = when {
                        isPrivate -> "Private tab"
                        isSecure -> "Secure connection"
                        isHttp -> "Not secure"
                        else -> "Search"
                    },
                    tint = when {
                        isPrivate -> MaterialTheme.colorScheme.primary
                        isSecure -> extras.secure
                        isHttp -> extras.insecure
                        else -> secondaryColor
                    },
                    modifier = Modifier.size(17.dp)
                )

                Spacer(Modifier.width(10.dp))

                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = contentColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val text = value.text
                            focusManager.clearFocus()
                            if (text.isNotBlank()) onNavigate(text)
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            focused = state.isFocused
                            if (state.isFocused) {
                                value = TextFieldValue(rawUrl, selection = TextRange(0, rawUrl.length))
                            }
                        },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (focused) {
                                // While editing, the field scrolls with the caret.
                                inner()
                            } else {
                                // At rest, show the address on one line and let it
                                // ellipsise instead of clipping mid-word.
                                val shown = UrlUtils.displayUrl(rawUrl)
                                Text(
                                    text = shown.ifBlank { "Search or enter website" },
                                    style = TextStyle(
                                        color = if (shown.isBlank()) secondaryColor else contentColor,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                )

                Spacer(Modifier.width(4.dp))

                if (isLoading) {
                    IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Stop loading",
                            tint = contentColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onReload, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reload",
                            tint = contentColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isLoading && progress in 1..99,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = barBorderColor(style)
                )
            }
        }
    }
}
