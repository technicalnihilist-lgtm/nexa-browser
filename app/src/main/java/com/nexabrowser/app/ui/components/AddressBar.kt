package com.nexabrowser.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import com.nexabrowser.app.ui.theme.NexaIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexabrowser.app.browser.TabInfo
import com.nexabrowser.app.ui.theme.GlassSurface
import com.nexabrowser.app.ui.theme.LocalNexaColors
import com.nexabrowser.app.util.UrlUtils

/**
 * The centred, pill-shaped address bar. It shows a readable URL while the page
 * is in view and the raw URL the moment it is focused, so editing is never
 * surprising. The lock/globe glyph reflects the real connection scheme.
 */
@Composable
fun AddressBar(
    tab: TabInfo?,
    isLoading: Boolean,
    progress: Int,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extras = LocalNexaColors.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

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

    Box(modifier = modifier.fillMaxWidth().imePadding()) {
        GlassSurface(
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
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(17.dp)
                )

                Spacer(Modifier.width(10.dp))

                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
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
                            if (value.text.isEmpty()) {
                                Text(
                                    text = "Search or enter website",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    }
                )

                Spacer(Modifier.width(4.dp))

                if (isLoading) {
                    IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Stop loading", modifier = Modifier.size(19.dp))
                    }
                } else {
                    IconButton(onClick = onReload, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload", modifier = Modifier.size(19.dp))
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
                    trackColor = extras.glassBorder
                )
            }
        }
    }
}
