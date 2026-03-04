@file:Suppress("DEPRECATION")

package com.opensubsonic.client.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opensubsonic.client.data.model.PlaybackMode
import com.opensubsonic.client.service.PlayerController
import com.opensubsonic.client.service.PlayerState
import com.opensubsonic.client.ui.components.CoverArtImage
import com.opensubsonic.client.ui.components.formatDurationMs

@Composable
fun PlayerScreen(
    playerController: PlayerController,
    coverArtUrl: String?,
    onBack: () -> Unit
) {
    val playerState by playerController.playerState.collectAsState()

    // Update position periodically
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            playerController.updatePosition()
            kotlinx.coroutines.delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cover art
        CoverArtImage(
            url = coverArtUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(horizontal = 16.dp),
            contentDescription = playerState.currentSong?.title
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Song info
        Text(
            text = playerState.currentSong?.title ?: "---",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = playerState.currentSong?.artist ?: "---",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Progress bar
        val progress = if (playerState.duration > 0) {
            playerState.position.toFloat() / playerState.duration.toFloat()
        } else 0f

        Slider(
            value = progress,
            onValueChange = { fraction ->
                playerController.seekTo((fraction * playerState.duration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.secondary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationMs(playerState.position),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDurationMs(playerState.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playback mode
            IconButton(onClick = {
                val next = when (playerState.playbackMode) {
                    PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ALL
                    PlaybackMode.REPEAT_ALL -> PlaybackMode.REPEAT_ONE
                    PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
                    PlaybackMode.SHUFFLE -> PlaybackMode.SEQUENTIAL
                }
                playerController.setPlaybackMode(next)
            }) {
                Icon(
                    when (playerState.playbackMode) {
                        PlaybackMode.SEQUENTIAL -> Icons.Filled.ArrowForward
                        PlaybackMode.REPEAT_ALL -> Icons.Filled.Repeat
                        PlaybackMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                        PlaybackMode.SHUFFLE -> Icons.Filled.Shuffle
                    },
                    contentDescription = "Playback mode",
                    tint = if (playerState.playbackMode != PlaybackMode.SEQUENTIAL)
                        MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            // Previous
            IconButton(onClick = { playerController.previous() }) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(36.dp)
                )
            }

            // Play/Pause
            FilledIconButton(
                onClick = {
                    if (playerState.isPlaying) playerController.pause()
                    else playerController.play()
                },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(
                    if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Next
            IconButton(onClick = { playerController.next() }) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp)
                )
            }

            // Queue indicator
            IconButton(onClick = { /* queue view toggle */ }) {
                Icon(
                    Icons.Filled.QueueMusic,
                    contentDescription = "Queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Queue info
        if (playerState.queue.isNotEmpty()) {
            Text(
                text = "${playerState.currentIndex + 1} / ${playerState.queue.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Mini player for bottom bar
@Composable
fun MiniPlayer(
    playerState: PlayerState,
    coverArtUrl: String?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (playerState.currentSong == null) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column {
            // Progress line
            val progress = if (playerState.duration > 0) {
                playerState.position.toFloat() / playerState.duration.toFloat()
            } else 0f
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.outline,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArtImage(
                    url = coverArtUrl,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playerState.currentSong.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playerState.currentSong.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}
