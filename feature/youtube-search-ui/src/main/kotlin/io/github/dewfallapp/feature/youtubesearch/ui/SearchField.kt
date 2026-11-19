package io.github.dewfallapp.feature.youtubesearch.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent
import io.github.dewfallapp.feature.youtubesearch.logic.lineBreaksFromLast

/**
 * The search field, after the design system's SearchField, but single-line, as docs/design/brief.md
 * says: a field that allows several lines can make the on-screen keyboard show return instead of
 * search. The keyboard's search key, and Enter on a hardware keyboard, which a single-line field
 * turns into the same action, send the search. A line break in pasted text becomes a space.
 *
 * The field takes focus once, the first time the screen opens. A saved flag remembers that it did,
 * so a configuration change or a return to the screen doesn't open the keyboard again. Sending a
 * search with the on-screen keyboard's search key hides that keyboard, so the results show.
 */
@Composable
internal fun SearchField(
    textFieldState: TextFieldState,
    onIntent: (YouTubeSearchIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            focusRequester.requestFocus()
        }
    }
    // A 2dp primary outline with a 2dp gap shows focus, as the design system's SearchField says.
    // The padding keeps the space for it when the field isn't focused, so nothing moves.
    val outline =
        if (focused) {
            Modifier.border(FocusOutlineWidth, MaterialTheme.colorScheme.primary, CircleShape)
        } else {
            Modifier
        }
    TextField(
        state = textFieldState,
        modifier =
            modifier
                .fillMaxWidth()
                .then(outline)
                .padding(FocusOutlineWidth * 2)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
        placeholder = { Text(stringResource(R.string.youtube_search_placeholder)) },
        leadingIcon = {
            Icon(
                painter = painterResource(DesignR.drawable.ic_search),
                contentDescription = stringResource(R.string.youtube_search_field_icon),
            )
        },
        trailingIcon =
            if (textFieldState.text.isEmpty()) {
                null
            } else {
                { ClearButton(textFieldState = textFieldState, onIntent = onIntent) }
            },
        inputTransformation = NoLineBreaks,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
            onIntent(YouTubeSearchIntent.Submit(textFieldState.text.toString()))
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = CircleShape,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
    )
}

/** Empties the field, and starts over, as the screen was before the first search. */
@Composable
private fun ClearButton(
    textFieldState: TextFieldState,
    onIntent: (YouTubeSearchIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = {
            textFieldState.clearText()
            onIntent(YouTubeSearchIntent.Clear)
        },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_close),
            contentDescription = stringResource(R.string.youtube_search_clear),
        )
    }
}

/** The width of the focus outline, and of the gap between it and the field. */
private val FocusOutlineWidth = 2.dp

/** Turns every line break into a space, so no line break gets into the search text. */
internal object NoLineBreaks : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        for (range in lineBreaksFromLast(asCharSequence())) {
            replace(range.first, range.last + 1, " ")
        }
    }
}
