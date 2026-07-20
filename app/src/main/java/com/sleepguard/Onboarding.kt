// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Olaf Drechsler
//
// This file is part of SleepGuard, free software licensed under the GNU General
// Public License v3.0 or later. See the LICENSE file in the project root.

package com.sleepguard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Geführter Erststart: zuerst Sprachauswahl, dann Willkommen und die drei
 * Berechtigungen mit Erklärung. Nicht-blockierend — jede Seite lässt sich auch
 * ohne Erteilen weiterklicken (der Hauptscreen prüft beim Start erneut).
 *
 * Seiten: 0 Sprache · 1 Willkommen · 2 Overlay · 3 Geräteadmin · 4 Akku ·
 * 5 Benachrichtigung · 6 Fertig
 */
@Composable
fun OnboardingScreen(
    startPage: Int,
    permissionTick: Int,
    languageLabels: List<String>,
    audioMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    isOverlayGranted: () -> Boolean,
    isAdminGranted: () -> Boolean,
    isBatteryGranted: () -> Boolean,
    isNotifGranted: () -> Boolean,
    onPickLanguage: (Int) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAdmin: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestNotif: () -> Unit,
    onFinish: () -> Unit
) {
    var page by rememberSaveable { mutableIntStateOf(startPage) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (page) {
                    0 -> LanguagePage(languageLabels) { index ->
                        page = 1            // ueberlebt die Activity-Neuerstellung (rememberSaveable)
                        onPickLanguage(index)
                    }

                    1 -> InfoPage(
                        icon = Icons.Rounded.Bedtime,
                        title = stringResource(R.string.onb_welcome_title),
                        body = stringResource(R.string.onb_welcome_body)
                    )

                    2 -> ModePage(audioMode = audioMode, onModeChange = onModeChange)

                    3 -> PermissionPage(
                        icon = Icons.Rounded.Layers,
                        title = stringResource(R.string.onb_overlay_title),
                        body = stringResource(R.string.onb_overlay_body) + "\n\n" +
                            stringResource(R.string.onb_overlay_list_hint, stringResource(R.string.onb_grant)),
                        permissionTick = permissionTick,
                        isGranted = isOverlayGranted,
                        onGrant = onRequestOverlay
                    )

                    4 -> PermissionPage(
                        icon = Icons.Rounded.AdminPanelSettings,
                        title = stringResource(R.string.onb_admin_title),
                        body = stringResource(R.string.onb_admin_body),
                        permissionTick = permissionTick,
                        isGranted = isAdminGranted,
                        onGrant = onRequestAdmin
                    )

                    5 -> PermissionPage(
                        icon = Icons.Rounded.BatteryFull,
                        title = stringResource(R.string.onb_battery_title),
                        body = stringResource(R.string.onb_battery_body),
                        permissionTick = permissionTick,
                        isGranted = isBatteryGranted,
                        onGrant = onRequestBattery
                    )

                    6 -> PermissionPage(
                        icon = Icons.Rounded.Notifications,
                        title = stringResource(R.string.onb_notif_title),
                        body = stringResource(R.string.onb_notif_body),
                        permissionTick = permissionTick,
                        isGranted = isNotifGranted,
                        onGrant = onRequestNotif
                    )

                    else -> InfoPage(
                        icon = Icons.Rounded.CheckCircle,
                        title = stringResource(R.string.onb_done_title),
                        body = stringResource(R.string.onb_done_body)
                    )
                }
            }

            // Untere Navigation (Seite 0 steuert sich über die Sprachauswahl selbst).
            if (page != 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zurueck fuehrt von Seite 1 aus zur Sprachauswahl (Seite 0).
                    TextButton(onClick = { page-- }) {
                        Text(stringResource(R.string.onb_back))
                    }
                    if (page >= 7) {
                        Button(onClick = onFinish) {
                            Text(stringResource(R.string.onb_finish))
                        }
                    } else {
                        Button(onClick = { page++ }) {
                            Text(stringResource(R.string.onb_next))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePage(
    languageLabels: List<String>,
    onPick: (Int) -> Unit
) {
    Icon(
        Icons.Rounded.Language,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onb_language_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))
    languageLabels.forEachIndexed { index, label ->
        OutlinedButton(
            onClick = { onPick(index) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(label)
        }
    }
}

@Composable
private fun ModePage(audioMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Icon(
        Icons.Rounded.Tune,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onb_mode_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!audioMode) {
            Button(onClick = { onModeChange(false) }, modifier = Modifier.weight(1f)) { Text("Video") }
        } else {
            OutlinedButton(onClick = { onModeChange(false) }, modifier = Modifier.weight(1f)) { Text("Video") }
        }
        if (audioMode) {
            Button(onClick = { onModeChange(true) }, modifier = Modifier.weight(1f)) { Text("Audio") }
        } else {
            OutlinedButton(onClick = { onModeChange(true) }, modifier = Modifier.weight(1f)) { Text("Audio") }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(if (audioMode) R.string.mode_audio_desc else R.string.mode_video_desc),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun InfoPage(icon: ImageVector, title: String, body: String) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PermissionPage(
    icon: ImageVector,
    title: String,
    body: String,
    permissionTick: Int,
    isGranted: () -> Boolean,
    onGrant: () -> Unit
) {
    val granted = remember(permissionTick) { isGranted() }
    InfoPage(icon = icon, title = title, body = body)
    Spacer(Modifier.height(24.dp))
    if (granted) {
        FilledTonalButton(onClick = {}, enabled = false) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onb_granted))
        }
    } else {
        Button(onClick = onGrant) {
            Text(stringResource(R.string.onb_grant))
        }
    }
}
