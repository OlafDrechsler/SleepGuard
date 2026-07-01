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
 * Seiten: 0 Sprache · 1 Willkommen · 2 Overlay · 3 Geräteadmin · 4 Akku · 5 Fertig
 */
@Composable
fun OnboardingScreen(
    startPage: Int,
    permissionTick: Int,
    languageLabels: List<String>,
    isOverlayGranted: () -> Boolean,
    isAdminGranted: () -> Boolean,
    isBatteryGranted: () -> Boolean,
    onPickLanguage: (Int) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAdmin: () -> Unit,
    onRequestBattery: () -> Unit,
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

                    2 -> PermissionPage(
                        icon = Icons.Rounded.Layers,
                        title = stringResource(R.string.onb_overlay_title),
                        body = stringResource(R.string.onb_overlay_body),
                        permissionTick = permissionTick,
                        isGranted = isOverlayGranted,
                        onGrant = onRequestOverlay
                    )

                    3 -> PermissionPage(
                        icon = Icons.Rounded.AdminPanelSettings,
                        title = stringResource(R.string.onb_admin_title),
                        body = stringResource(R.string.onb_admin_body),
                        permissionTick = permissionTick,
                        isGranted = isAdminGranted,
                        onGrant = onRequestAdmin
                    )

                    4 -> PermissionPage(
                        icon = Icons.Rounded.BatteryFull,
                        title = stringResource(R.string.onb_battery_title),
                        body = stringResource(R.string.onb_battery_body),
                        permissionTick = permissionTick,
                        isGranted = isBatteryGranted,
                        onGrant = onRequestBattery
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
                    if (page > 1) {
                        TextButton(onClick = { page-- }) {
                            Text(stringResource(R.string.onb_back))
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    if (page >= 5) {
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
