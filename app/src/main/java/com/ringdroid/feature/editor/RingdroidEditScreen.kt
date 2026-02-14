package com.ringdroid.feature.editor

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ringdroid.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingdroidEditScreen(
    title: String?,
    startText: String,
    endText: String,
    infoText: String,
    isPlaying: Boolean,
    onStartTextChange: (String) -> Unit,
    onStartFocusChange: (Boolean) -> Unit,
    onEndTextChange: (String) -> Unit,
    onEndFocusChange: (Boolean) -> Unit,
    onMarkStart: () -> Unit,
    onMarkEnd: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    waveformContent: @Composable () -> Unit,
) {
    val defaultTitle = stringResource(id = R.string.edit_intent)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val controlsScrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title ?: defaultTitle, maxLines = 1) },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = stringResource(R.string.menu_save),
                        )
                    }
                    IconButton(onClick = onReset) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.menu_reset),
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { waveformContent() }

            if (isLandscape) {
                if (infoText.isNotBlank()) {
                    Text(
                        text = infoText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier.horizontalScroll(controlsScrollState).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MarkerInputs(
                            startText = startText,
                            endText = endText,
                            onStartTextChange = onStartTextChange,
                            onStartFocusChange = onStartFocusChange,
                            onMarkStart = onMarkStart,
                            onEndTextChange = onEndTextChange,
                            onEndFocusChange = onEndFocusChange,
                            onMarkEnd = onMarkEnd,
                        )

                        Spacer(modifier = Modifier.size(12.dp))

                        PlaybackControls(
                            isPlaying = isPlaying,
                            onRewind = onRewind,
                            onPlayPause = onPlayPause,
                            onFastForward = onFastForward,
                            onZoomOut = onZoomOut,
                            onZoomIn = onZoomIn,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (infoText.isNotBlank()) {
                        Text(
                            text = infoText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        MarkerInputs(
                            startText = startText,
                            endText = endText,
                            onStartTextChange = onStartTextChange,
                            onStartFocusChange = onStartFocusChange,
                            onMarkStart = onMarkStart,
                            onEndTextChange = onEndTextChange,
                            onEndFocusChange = onEndFocusChange,
                            onMarkEnd = onMarkEnd,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaybackControls(
                            isPlaying = isPlaying,
                            onRewind = onRewind,
                            onPlayPause = onPlayPause,
                            onFastForward = onFastForward,
                            onZoomOut = onZoomOut,
                            onZoomIn = onZoomIn,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkerInputs(
    startText: String,
    endText: String,
    onStartTextChange: (String) -> Unit,
    onStartFocusChange: (Boolean) -> Unit,
    onMarkStart: () -> Unit,
    onEndTextChange: (String) -> Unit,
    onEndFocusChange: (Boolean) -> Unit,
    onMarkEnd: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkerInput(
            label = stringResource(R.string.start_label),
            value = startText,
            onValueChange = onStartTextChange,
            onFocusChange = onStartFocusChange,
            onClick = onMarkStart,
        )

        MarkerInput(
            label = stringResource(R.string.end_label),
            value = endText,
            onValueChange = onEndTextChange,
            onFocusChange = onEndFocusChange,
            onClick = onMarkEnd,
        )
    }
}

@Composable
private fun MarkerInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(
        LocalTextSelectionColors provides
            TextSelectionColors(
                handleColor = Color.Transparent,
                backgroundColor = LocalTextSelectionColors.current.backgroundColor,
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier.widthIn(min = 56.dp, max = 75.dp).wrapContentHeight().onFocusChanged {
                    onFocusChange(it.isFocused)
                },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            label = {
                Text(text = label, modifier = Modifier.fillMaxWidth().clickable { onClick() })
            },
        )
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onFastForward: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onZoomOut) {
            Icon(
                imageVector = Icons.Filled.ZoomOut,
                contentDescription = stringResource(R.string.zoom_out),
            )
        }

        IconButton(onClick = onRewind) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.rewind),
            )
        }

        Button(onClick = onPlayPause) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = stringResource(R.string.stop),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.play),
                )
            }
        }

        IconButton(onClick = onFastForward) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.ffwd),
            )
        }

        IconButton(onClick = onZoomIn) {
            Icon(
                imageVector = Icons.Filled.ZoomIn,
                contentDescription = stringResource(R.string.zoom_in),
            )
        }
    }
}
