package com.opensubsonic.client.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.util.BulkDownloadState
import com.opensubsonic.client.util.StorageLocation
import com.opensubsonic.client.util.TranscodeBitrate
import com.opensubsonic.client.util.TranscodeFormat

@Composable
fun SettingsScreen(
    server: ServerConfig?,
    bulkDownloadState: BulkDownloadState,
    storageLocation: StorageLocation,
    hasSDCard: Boolean,
    onStorageLocationChange: (StorageLocation) -> Unit,
    onDownloadsClick: () -> Unit,
    onSoundEffectsClick: () -> Unit,
    onDownloadAllAlbums: () -> Unit,
    onScanDownloads: () -> Unit,
    streamFormat: TranscodeFormat,
    streamBitrate: TranscodeBitrate,
    downloadFormat: TranscodeFormat,
    downloadBitrate: TranscodeBitrate,
    onStreamFormatChange: (TranscodeFormat) -> Unit,
    onStreamBitrateChange: (TranscodeBitrate) -> Unit,
    onDownloadFormatChange: (TranscodeFormat) -> Unit,
    onDownloadBitrateChange: (TranscodeBitrate) -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Text(
                text = "MORE",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
            )
        }

        // Server info
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SERVER",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = server?.url ?: "Not connected",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = server?.username ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Downloads
        item {
            SettingsItem(
                icon = Icons.Filled.Download,
                title = "DOWNLOADS",
                subtitle = "Manage downloaded music",
                onClick = onDownloadsClick
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Download all albums
        item {
            if (bulkDownloadState.isDownloading) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "DOWNLOADING ALL ALBUMS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${bulkDownloadState.completedTracks} / ${bulkDownloadState.totalTracks} TRACKS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        bulkDownloadState.currentTrackName?.let { trackName ->
                            Text(
                                text = trackName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        bulkDownloadState.currentAlbumName?.let { albumName ->
                            Text(
                                text = albumName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (bulkDownloadState.totalTracks > 0) {
                                    bulkDownloadState.completedTracks.toFloat() / bulkDownloadState.totalTracks
                                } else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                SettingsItem(
                    icon = Icons.Filled.CloudDownload,
                    title = "DOWNLOAD ALL ALBUMS",
                    subtitle = "Download entire library for offline use",
                    onClick = onDownloadAllAlbums
                )
            }
        }

        // Scan existing downloads
        item {
            SettingsItem(
                icon = Icons.Filled.FolderOpen,
                title = "SCAN DOWNLOADS",
                subtitle = "Re-link previously downloaded files",
                onClick = onScanDownloads
            )
        }

        // Sound effects
        item {
            SettingsItem(
                icon = Icons.Filled.Equalizer,
                title = "SOUND EFFECTS",
                subtitle = "Equalizer, bass boost, crossfeed, surround",
                onClick = onSoundEffectsClick
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Streaming quality - inlined directly
        item(key = "streaming_quality") {
            var streamFormatExpanded by remember { mutableStateOf(false) }
            var streamBitrateExpanded by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "STREAMING QUALITY",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Transcode when streaming from server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "FORMAT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (streamFormatExpanded) {
                        Column {
                            TranscodeFormat.values().forEach { format ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onStreamFormatChange(format)
                                            streamFormatExpanded = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = format == streamFormat,
                                        onClick = {
                                            onStreamFormatChange(format)
                                            streamFormatExpanded = false
                                        }
                                    )
                                    Text(
                                        text = format.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { streamFormatExpanded = true },
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = streamFormat.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "MAX BITRATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (streamBitrateExpanded && streamFormat != TranscodeFormat.RAW) {
                        Column {
                            TranscodeBitrate.values().forEach { bitrate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onStreamBitrateChange(bitrate)
                                            streamBitrateExpanded = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = bitrate == streamBitrate,
                                        onClick = {
                                            onStreamBitrateChange(bitrate)
                                            streamBitrateExpanded = false
                                        }
                                    )
                                    Text(
                                        text = bitrate.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = streamFormat != TranscodeFormat.RAW) {
                                    streamBitrateExpanded = true
                                },
                            color = if (streamFormat != TranscodeFormat.RAW)
                                MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (streamFormat == TranscodeFormat.RAW) "N/A" else streamBitrate.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (streamFormat != TranscodeFormat.RAW)
                                        MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Download quality - inlined directly
        item(key = "download_quality") {
            var dlFormatExpanded by remember { mutableStateOf(false) }
            var dlBitrateExpanded by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DOWNLOAD QUALITY",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Transcode when downloading from server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "FORMAT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (dlFormatExpanded) {
                        Column {
                            TranscodeFormat.values().forEach { format ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDownloadFormatChange(format)
                                            dlFormatExpanded = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = format == downloadFormat,
                                        onClick = {
                                            onDownloadFormatChange(format)
                                            dlFormatExpanded = false
                                        }
                                    )
                                    Text(
                                        text = format.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dlFormatExpanded = true },
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = downloadFormat.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "MAX BITRATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (dlBitrateExpanded && downloadFormat != TranscodeFormat.RAW) {
                        Column {
                            TranscodeBitrate.values().forEach { bitrate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDownloadBitrateChange(bitrate)
                                            dlBitrateExpanded = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = bitrate == downloadBitrate,
                                        onClick = {
                                            onDownloadBitrateChange(bitrate)
                                            dlBitrateExpanded = false
                                        }
                                    )
                                    Text(
                                        text = bitrate.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = downloadFormat != TranscodeFormat.RAW) {
                                    dlBitrateExpanded = true
                                },
                            color = if (downloadFormat != TranscodeFormat.RAW)
                                MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (downloadFormat == TranscodeFormat.RAW) "N/A" else downloadBitrate.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (downloadFormat != TranscodeFormat.RAW)
                                        MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Storage location
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "STORAGE LOCATION",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = storageLocation == StorageLocation.INTERNAL,
                            onClick = { onStorageLocationChange(StorageLocation.INTERNAL) }
                        )
                        Text(
                            text = "Internal storage",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = storageLocation == StorageLocation.SD_CARD,
                            onClick = { onStorageLocationChange(StorageLocation.SD_CARD) },
                            enabled = hasSDCard
                        )
                        Text(
                            text = if (hasSDCard) "SD card" else "SD card (not detected)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasSDCard) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Logout
        item {
            SettingsItem(
                icon = Icons.Filled.Logout,
                title = "DISCONNECT",
                subtitle = "Disconnect from server",
                onClick = onLogout,
                isDestructive = true
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "SUBTUNE v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}
