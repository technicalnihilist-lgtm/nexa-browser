package com.nexabrowser.app.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexabrowser.app.browser.TabInfo
import com.nexabrowser.app.domain.model.AddressBarStyle
import com.nexabrowser.app.ui.theme.BarSurface
import com.nexabrowser.app.ui.theme.NexaIcons
import com.nexabrowser.app.ui.theme.barContentColor
import com.nexabrowser.app.ui.theme.barSecondaryColor
import com.nexabrowser.app.util.UrlUtils

/**
 * The address bar, laid out like the reference design: a floating glass pill
 * that lives at the *bottom* of the screen, with the "aA" site button, a lock,
 * a centred address and a reload control.
 */
@Composable
fun AddressBar(
    tab: TabInfo?,
    style: AddressBarStyle,
    isLoading: Boolean,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onSiteSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rawUrl = tab?.url.orEmpty()
    val contentColor = barContentColor(style)
    val secondaryColor = barSecondaryColor(style)
    val isSecure = UrlUtils.isHttps(rawUrl)
    val isHttp = rawUrl.startsWith("http://")
    val isPrivate = tab?.isPrivate == true

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue("")) }

    BarSurface(
        style = style,
        shape = RoundedCornerShape(26.dp),
        elevation = 10.dp,
        modifier = modifier.fillMaxWidth().height(50.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "aA" — site settings / page options, as in the reference.
            Text(
                text = "aA",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = contentColor.copy(alpha = 0.85f),
                modifier = Modifier
                    .clickable { onSiteSettings() }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )

            Spacer(Modifier.width(4.dp))

            Icon(
                imageVector = when {
                    isPrivate -> NexaIcons.VisibilityOff
                    isSecure -> Icons.Filled.Lock
                    isHttp -> Icons.Filled.Warning
                    else -> Icons.Filled.Lock
                },
                contentDescription = when {
                    isPrivate -> "Private tab"
                    isSecure -> "Secure connection"
                    isHttp -> "Not secure"
                    else -> "Address"
                },
                tint = when {
                    isPrivate -> MaterialTheme.colorScheme.primary
                    isHttp -> secondaryColor
                    else -> contentColor.copy(alpha = 0.70f)
                },
                modifier = Modifier.size(12.dp)
            )

            Spacer(Modifier.width(6.dp))

            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = contentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = if (focused) TextAlign.Start else TextAlign.Center
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
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (focused) Alignment.CenterStart else Alignment.Center) {
                        if (focused) {
                            inner()
                        } else {
                            val shown = UrlUtils.displayUrl(rawUrl)
                            Text(
                                text = shown.ifBlank { "Search or enter website name" },
                                style = TextStyle(
                                    color = if (shown.isBlank()) secondaryColor else contentColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            )

            if (isLoading) {
                IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Stop loading",
                        tint = contentColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(onClick = onReload, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Reload",
                        tint = contentColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
