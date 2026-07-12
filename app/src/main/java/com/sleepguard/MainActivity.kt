// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Olaf Drechsler
//
// This file is part of SleepGuard, free software licensed under the GNU General
// Public License v3.0 or later. See the LICENSE file in the project root.

package com.sleepguard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.sleepguard.ui.theme.SleepGuardTheme

/**
 * Hauptbildschirm der SleepGuard-App (Jetpack Compose, Material 3).
 *
 * Bleibt eine AppCompatActivity, weil die Pro-App-Sprachumschaltung
 * (AppCompatDelegate.setApplicationLocales) den AppCompat-Unterbau braucht.
 * Die gesamte Logik (Einstellungen, Dienststart, Berechtigungen) entspricht
 * der frueheren View-Version; nur die Oberflaeche ist jetzt Compose.
 */
class MainActivity : AppCompatActivity() {

    private var intervalMinutes by mutableIntStateOf(5)
    private var timeoutSeconds by mutableIntStateOf(15)
    private var positionIndex by mutableIntStateOf(0)
    private var isRunning by mutableStateOf(false)
    private var showOnboarding by mutableStateOf(false)
    private var permissionTick by mutableIntStateOf(0)
    // false = Video (Button + Sperre), true = Audio (Einschlaf-Timer, stoppt Wiedergabe)
    private var audioMode by mutableStateOf(false)

    private var batteryOptRequested = false
    private var pendingStart = false

    // Die 8 moeglichen Positionen fuer den roten Kreis
    private val positionKeys = arrayOf(
        "right_center", "left_center", "top_center", "bottom_center",
        "top_right", "top_left", "bottom_right", "bottom_left"
    )

    // BCP-47-Tags parallel zu den Eintraegen im Sprach-Dropdown ("" = System)
    private val languageTags = arrayOf(
        "", "de", "en", "es", "fr", "it", "nl", "pl", "tr",
        "bg", "cs", "da", "el", "et", "fi", "ga", "hr", "hu", "lt", "lv",
        "mt", "ro", "ru", "sk", "sl", "sv", "ja", "zh-CN", "zh-TW"
    )

    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var deviceAdminLauncher: ActivityResultLauncher<Intent>
    private lateinit var batteryOptLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    // Wie batteryOptRequested: pro Sitzung hoechstens einmal fragen.
    private var notificationRequested = false

    companion object {
        private const val PREFS_NAME = "sleepguard_prefs"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_TIMEOUT = "timeout_seconds"
        private const val KEY_POSITION = "position_index"
        private const val KEY_MODE = "audio_mode"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_LANG_CHOSEN = "onboarding_lang_chosen"
        private const val KEY_LANG_INDEX = "language_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { onPermissionResult() }
        deviceAdminLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { onPermissionResult() }
        batteryOptLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { onPermissionResult() }
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // Egal ob gewaehrt oder abgelehnt — der Dienst funktioniert auch
            // ohne sichtbare Benachrichtigung; danach normal weitermachen.
            onPermissionResult()
        }

        loadSettings()
        isRunning = OverlayService.isRunning
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        showOnboarding = !prefs.getBoolean(KEY_ONBOARDING_DONE, false)

        setContent {
            SleepGuardTheme {
                if (showOnboarding) {
                    OnboardingScreen(
                        startPage = if (prefs.getBoolean(KEY_LANG_CHOSEN, false)) 1 else 0,
                        permissionTick = permissionTick,
                        languageLabels = languageLabelsNative,
                        audioMode = audioMode,
                        onModeChange = { audioMode = it; saveSettings() },
                        isOverlayGranted = { Settings.canDrawOverlays(this) },
                        isAdminGranted = { isDeviceAdminActive() },
                        isBatteryGranted = { isIgnoringBatteryOptimizations() },
                        isNotifGranted = { isNotificationGranted() },
                        onPickLanguage = { onPickLanguage(it) },
                        onRequestOverlay = { requestOverlayPermission() },
                        onRequestAdmin = { requestDeviceAdmin() },
                        onRequestBattery = { requestBatteryExemption() },
                        onRequestNotif = { requestNotificationPermission() },
                        onFinish = { finishOnboarding() }
                    )
                } else {
                    MainScreen(
                        intervalMinutes = intervalMinutes,
                        timeoutSeconds = timeoutSeconds,
                        positionIndex = positionIndex,
                        positionLabels = positionLabels(),
                        isRunning = isRunning,
                        languageIndex = currentLanguageIndex(),
                        onIntervalChange = { intervalMinutes = it.coerceIn(1, 60) },
                        onTimeoutChange = { timeoutSeconds = it.coerceIn(5, 120) },
                        onPositionChange = { positionIndex = it; saveSettings() },
                        onToggle = { if (isRunning) stopService() else startService() },
                        onLanguageChange = { changeLanguage(it) },
                        onSupport = { openSupport() },
                        audioMode = audioMode,
                        onModeChange = { audioMode = it; saveSettings() }
                    )
                }
            }
        }
    }

    /**
     * Gleicht die Anzeige bei jedem Sichtbarwerden mit dem echten Dienst-Status
     * ab (der Dienst beendet sich nach dem Sperren selbst).
     */
    override fun onResume() {
        super.onResume()
        isRunning = OverlayService.isRunning
        permissionTick++ // Berechtigungsstatus im Onboarding aktualisieren
    }

    private fun startServiceDelayed() {
        window.decorView.postDelayed({ startService() }, 500)
    }

    /** Nach Rueckkehr aus einer Berechtigungs-Einstellung. */
    private fun onPermissionResult() {
        permissionTick++
        if (pendingStart) {
            pendingStart = false
            startServiceDelayed()
        }
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(this, SleepGuardDeviceAdmin::class.java))
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestOverlayPermission() {
        overlayPermissionLauncher.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun requestDeviceAdmin() {
        val adminComponent = ComponentName(this, SleepGuardDeviceAdmin::class.java)
        deviceAdminLauncher.launch(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_description))
            }
        )
    }

    private fun requestBatteryExemption() {
        @Suppress("BatteryLife")
        batteryOptLauncher.launch(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        )
    }

    /** Vor Android 13 gibt es die Laufzeit-Berechtigung nicht — gilt als erteilt. */
    private fun isNotificationGranted(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionTick++
        }
    }

    private fun onPickLanguage(index: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LANG_CHOSEN, true).apply()
        changeLanguage(index)
    }

    private fun finishOnboarding() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ONBOARDING_DONE, true).apply()
        showOnboarding = false
    }

    private fun positionLabels(): List<String> = listOf(
        getString(R.string.pos_right_center),
        getString(R.string.pos_left_center),
        getString(R.string.pos_top_center),
        getString(R.string.pos_bottom_center),
        getString(R.string.pos_top_right),
        getString(R.string.pos_top_left),
        getString(R.string.pos_bottom_right),
        getString(R.string.pos_bottom_left)
    )

    private fun currentLanguageIndex(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_LANG_INDEX)) {
            return prefs.getInt(KEY_LANG_INDEX, 0).coerceIn(0, languageTags.size - 1)
        }
        // Fallback fuer Upgrades ohne gespeicherten Index: aus aktiver Locale ableiten.
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return 0
        val active = locales[0] ?: return 0
        return languageTags.indexOfFirst { tag ->
            tag.isNotEmpty() && java.util.Locale.forLanguageTag(tag).let { l ->
                l.language == active.language &&
                    (l.country.isEmpty() || l.country.equals(active.country, ignoreCase = true))
            }
        }.takeIf { it >= 0 } ?: 0
    }

    private fun changeLanguage(index: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LANG_INDEX, 0) == index) return
        prefs.edit().putInt(KEY_LANG_INDEX, index).apply()
        saveSettings()
        val newTag = languageTags[index]
        val locales = if (newTag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(newTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun openSupport() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/OLEobjekt"))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.support_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Startet den Overlay-Dienst. Prueft vorher Overlay-, Geraeteadmin- und
     * (einmalig) Akku-Berechtigung.
     */
    private fun startService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.permission_overlay_needed, Toast.LENGTH_LONG).show()
            pendingStart = true
            requestOverlayPermission()
            return
        }

        if (!isDeviceAdminActive()) {
            Toast.makeText(this, R.string.permission_admin_needed, Toast.LENGTH_LONG).show()
            pendingStart = true
            requestDeviceAdmin()
            return
        }

        if (!batteryOptRequested && !isIgnoringBatteryOptimizations()) {
            batteryOptRequested = true
            Toast.makeText(this, R.string.permission_battery_needed, Toast.LENGTH_LONG).show()
            pendingStart = true
            requestBatteryExemption()
            return
        }

        // Ab Android 13 ist die Foreground-Notification nur mit dieser
        // Laufzeit-Berechtigung sichtbar. Ohne sie merkt der Benutzer nicht,
        // dass SleepGuard noch aktiv ist -> einmal pro Sitzung anfragen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationRequested &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationRequested = true
            pendingStart = true
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        saveSettings()

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("interval_minutes", intervalMinutes)
            putExtra("timeout_seconds", timeoutSeconds)
            putExtra("position", positionKeys[positionIndex])
            putExtra("audio_mode", audioMode)
        }
        startForegroundService(intent)
        isRunning = true
    }

    private fun stopService() {
        stopService(Intent(this, OverlayService::class.java))
        isRunning = false
    }

    private fun saveSettings() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt(KEY_INTERVAL, intervalMinutes)
            putInt(KEY_TIMEOUT, timeoutSeconds)
            putInt(KEY_POSITION, positionIndex)
            putBoolean(KEY_MODE, audioMode)
            apply()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        intervalMinutes = prefs.getInt(KEY_INTERVAL, 5)
        timeoutSeconds = prefs.getInt(KEY_TIMEOUT, 15)
        positionIndex = prefs.getInt(KEY_POSITION, 0)
        audioMode = prefs.getBoolean(KEY_MODE, false)
    }
}

// Native Sprachnamen (Autonyme) parallel zu languageTags; sprachunabhaengig.
private val languageLabelsNative = listOf(
    "System", "Deutsch", "English", "Español", "Français",
    "Italiano", "Nederlands", "Polski", "Türkçe",
    "Български", "Čeština", "Dansk", "Ελληνικά", "Eesti", "Suomi",
    "Gaeilge", "Hrvatski", "Magyar", "Lietuvių", "Latviešu",
    "Malti", "Română", "Русский", "Slovenčina", "Slovenščina",
    "Svenska", "日本語", "中文 (简体)", "中文 (繁體)"
)

@Composable
private fun MainScreen(
    intervalMinutes: Int,
    timeoutSeconds: Int,
    positionIndex: Int,
    positionLabels: List<String>,
    isRunning: Boolean,
    languageIndex: Int,
    onIntervalChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onPositionChange: (Int) -> Unit,
    onToggle: () -> Unit,
    onLanguageChange: (Int) -> Unit,
    onSupport: () -> Unit,
    audioMode: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(if (isRunning) R.string.status_running else R.string.status_stopped),
                style = MaterialTheme.typography.titleMedium,
                color = if (isRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            ModeSelector(audioMode = audioMode, onModeChange = onModeChange)

            Spacer(Modifier.height(24.dp))

            StepperCard(
                label = stringResource(R.string.title_interval),
                value = intervalMinutes.toString(),
                onMinus = { onIntervalChange(intervalMinutes - 1) },
                onPlus = { onIntervalChange(intervalMinutes + 1) }
            )

            // Timeout und Position sind nur im Video-Modus relevant
            // (im Audio-Modus gibt es keinen Button und keine Sperre).
            if (!audioMode) {
                Spacer(Modifier.height(16.dp))
                StepperCard(
                    label = stringResource(R.string.title_timeout),
                    value = timeoutSeconds.toString(),
                    onMinus = { onTimeoutChange(timeoutSeconds - 5) },
                    onPlus = { onTimeoutChange(timeoutSeconds + 5) }
                )

                Spacer(Modifier.height(16.dp))
                DropdownField(
                    label = stringResource(R.string.title_position),
                    options = positionLabels,
                    selectedIndex = positionIndex,
                    onSelect = onPositionChange
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(if (isRunning) R.string.btn_stop else R.string.btn_start),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(28.dp))
            DropdownField(
                label = stringResource(R.string.title_language),
                options = languageLabelsNative,
                selectedIndex = languageIndex,
                onSelect = onLanguageChange
            )

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onSupport) {
                Icon(Icons.Rounded.Favorite, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_support))
            }
        }
    }
}

@Composable
private fun ModeSelector(audioMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.title_mode), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeButton("Video", selected = !audioMode, onClick = { onModeChange(false) }, modifier = Modifier.weight(1f))
            ModeButton("Audio", selected = audioMode, onClick = { onModeChange(true) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(if (audioMode) R.string.mode_audio_desc else R.string.mode_video_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
private fun StepperCard(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(onClick = onMinus) {
                    Icon(Icons.Rounded.Remove, contentDescription = "minus")
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                FilledTonalIconButton(onClick = onPlus) {
                    Icon(Icons.Rounded.Add, contentDescription = "plus")
                }
            }
        }
    }
}

@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = options.getOrElse(selectedIndex) { "" },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
