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
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Hauptbildschirm der SleepGuard-App.
 *
 * Hier kann der Benutzer folgende Einstellungen vornehmen:
 * - Intervall in Minuten (wie oft der rote Kreis erscheint)
 * - Timeout in Sekunden (wie lange der Kreis sichtbar bleibt, bevor der Bildschirm gesperrt wird)
 * - Position des roten Kreises auf dem Bildschirm
 *
 * Die Einstellungen werden in SharedPreferences gespeichert.
 */
class MainActivity : AppCompatActivity() {

    private var intervalMinutes = 5
    private var timeoutSeconds = 15
    private var isRunning = false

    // Verhindert eine Endlosschleife, falls der Nutzer die Akku-Ausnahme
    // ablehnt: pro App-Sitzung wird hoechstens einmal danach gefragt.
    private var batteryOptRequested = false

    private lateinit var intervalText: TextView
    private lateinit var timeoutText: TextView
    private lateinit var statusText: TextView
    private lateinit var btnStartStop: Button
    private lateinit var positionSpinner: Spinner
    private lateinit var languageSpinner: Spinner

    // BCP-47-Sprach-Tags parallel zu den Eintraegen im languageSpinner.
    // "" = System-Standard (folgt der Geraetesprache).
    private val languageTags = arrayOf(
        "", "de", "en", "es", "fr", "it", "nl", "pl", "pt", "tr"
    )

    // Die 8 moeglichen Positionen fuer den roten Kreis
    private val positionKeys = arrayOf(
        "right_center",    // Rechter Rand, Mitte (Default)
        "left_center",     // Linker Rand, Mitte
        "top_center",      // Oberer Rand, Mitte
        "bottom_center",   // Unterer Rand, Mitte
        "top_right",       // Oben rechts
        "top_left",        // Oben links
        "bottom_right",    // Unten rechts
        "bottom_left"      // Unten links
    )

    // Activity Result Launcher fuer Berechtigungsanfragen (ersetzt deprecated startActivityForResult)
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var deviceAdminLauncher: ActivityResultLauncher<Intent>
    private lateinit var batteryOptLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val PREFS_NAME = "sleepguard_prefs"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_TIMEOUT = "timeout_seconds"
        private const val KEY_POSITION = "position_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Launcher registrieren (muss vor onStart passieren)
        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            btnStartStop.postDelayed({ startService() }, 500)
        }
        deviceAdminLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            btnStartStop.postDelayed({ startService() }, 500)
        }
        batteryOptLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // Egal ob gewaehrt oder nicht — Dienst trotzdem starten.
            // (Ohne Ausnahme laeuft er dank AlarmManager weiterhin, nur
            //  geringfuegig weniger zuverlaessig.)
            btnStartStop.postDelayed({ startService() }, 500)
        }

        // Views finden
        intervalText = findViewById(R.id.intervalText)
        timeoutText = findViewById(R.id.timeoutText)
        statusText = findViewById(R.id.statusText)
        btnStartStop = findViewById(R.id.btnStartStop)
        positionSpinner = findViewById(R.id.positionSpinner)
        languageSpinner = findViewById(R.id.languageSpinner)

        // Gespeicherte Einstellungen laden
        loadSettings()

        // Positions-Dropdown befuellen
        val positionLabels = arrayOf(
            getString(R.string.pos_right_center),
            getString(R.string.pos_left_center),
            getString(R.string.pos_top_center),
            getString(R.string.pos_bottom_center),
            getString(R.string.pos_top_right),
            getString(R.string.pos_top_left),
            getString(R.string.pos_bottom_right),
            getString(R.string.pos_bottom_left)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, positionLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        positionSpinner.adapter = adapter

        // Gespeicherte Position setzen
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        positionSpinner.setSelection(prefs.getInt(KEY_POSITION, 0))

        // Intervall +/- Buttons
        findViewById<Button>(R.id.btnIntervalMinus).setOnClickListener {
            if (intervalMinutes > 1) {
                intervalMinutes--
                intervalText.text = intervalMinutes.toString()
            }
        }
        findViewById<Button>(R.id.btnIntervalPlus).setOnClickListener {
            if (intervalMinutes < 60) {
                intervalMinutes++
                intervalText.text = intervalMinutes.toString()
            }
        }

        // Timeout +/- Buttons
        findViewById<Button>(R.id.btnTimeoutMinus).setOnClickListener {
            if (timeoutSeconds > 5) {
                timeoutSeconds -= 5
                timeoutText.text = timeoutSeconds.toString()
            }
        }
        findViewById<Button>(R.id.btnTimeoutPlus).setOnClickListener {
            if (timeoutSeconds < 120) {
                timeoutSeconds += 5
                timeoutText.text = timeoutSeconds.toString()
            }
        }

        // Start/Stop Button
        btnStartStop.setOnClickListener {
            if (isRunning) {
                stopService()
            } else {
                startService()
            }
        }

        // Sprach-Dropdown einrichten
        setupLanguageSpinner()

        // Freiwillige Spende: oeffnet den PayPal-Link extern im Browser.
        // Bewusst ohne Gegenleistung, damit es eine Spende bleibt und nicht
        // unter Googles In-App-Kauf-/Play-Billing-Pflicht faellt.
        findViewById<Button>(R.id.btnSupport).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/OLEobjekt"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.support_no_browser, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Gleicht die Anzeige bei jedem Sichtbarwerden mit dem echten
     * Dienst-Status ab. Wichtig, weil sich der Dienst nach dem Sperren
     * selbst beendet, waehrend die Activity im Hintergrund ist — beim
     * Wiederoeffnen laeuft nur onResume(), nicht onCreate().
     */
    override fun onResume() {
        super.onResume()
        isRunning = OverlayService.isRunning
        updateUI()
    }

    /**
     * Richtet das Sprach-Dropdown ein.
     *
     * Die Eintraege sind native Sprachnamen (Autonyme) und damit
     * sprachunabhaengig — sie werden nicht uebersetzt. Bei Auswahl wird die
     * App-Sprache ueber AppCompatDelegate gesetzt; AppCompat erzeugt die
     * Activity neu und speichert die Wahl dauerhaft (autoStoreLocales).
     */
    private fun setupLanguageSpinner() {
        val languageLabels = arrayOf(
            getString(R.string.lang_system), // "" = System-Standard
            "Deutsch",
            "English",
            "Español",
            "Français",
            "Italiano",
            "Nederlands",
            "Polski",
            "Português",
            "Türkçe"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        // Aktuelle App-Sprache ermitteln und im Dropdown vorauswaehlen.
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (currentLocales.isEmpty) {
            ""
        } else {
            // Nur den primaeren Sprachcode vergleichen (z.B. "de" aus "de-DE").
            currentLocales[0]?.language ?: ""
        }
        val selectedIndex = languageTags.indexOfFirst { it == currentTag }
            .takeIf { it >= 0 } ?: 0
        languageSpinner.setSelection(selectedIndex)

        // Listener erst nach dem Setzen der Vorauswahl registrieren, damit der
        // initiale Aufruf die Sprache nicht unnoetig neu setzt.
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val newTag = languageTags[pos]
                val activeTag = AppCompatDelegate.getApplicationLocales()
                    .takeIf { !it.isEmpty }?.get(0)?.language ?: ""
                if (newTag == activeTag) return // keine echte Aenderung

                // Aktuelle Eingaben sichern, da die Activity gleich neu erstellt wird.
                saveSettings()

                val locales = if (newTag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList() // System-Standard
                } else {
                    LocaleListCompat.forLanguageTags(newTag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Startet den Overlay-Dienst.
     * Prueft vorher alle nötigen Berechtigungen.
     */
    private fun startService() {
        // 1. Overlay-Berechtigung pruefen
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.permission_overlay_needed, Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }

        // 2. Device-Admin-Berechtigung pruefen
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, SleepGuardDeviceAdmin::class.java)
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            Toast.makeText(this, R.string.permission_admin_needed, Toast.LENGTH_LONG).show()
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_description)
                )
            }
            deviceAdminLauncher.launch(intent)
            return
        }

        // 3. Akku-Optimierung: ohne Ausnahme friert MIUI/Xiaomi den Dienst
        //    im Hintergrund ein. Einmal pro Sitzung danach fragen.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!batteryOptRequested && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            batteryOptRequested = true
            Toast.makeText(this, R.string.permission_battery_needed, Toast.LENGTH_LONG).show()
            @Suppress("BatteryLife")
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            batteryOptLauncher.launch(intent)
            return
        }

        // Einstellungen speichern
        saveSettings()

        // Dienst starten
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("interval_minutes", intervalMinutes)
            putExtra("timeout_seconds", timeoutSeconds)
            putExtra("position", positionKeys[positionSpinner.selectedItemPosition])
        }
        startForegroundService(intent)

        isRunning = true
        updateUI()
    }

    /**
     * Stoppt den Overlay-Dienst.
     */
    private fun stopService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)

        isRunning = false
        updateUI()
    }

    private fun updateUI() {
        if (isRunning) {
            statusText.text = getString(R.string.status_running)
            statusText.setTextColor(0xFF4CAF50.toInt()) // Gruen
            btnStartStop.text = getString(R.string.btn_stop)
        } else {
            statusText.text = getString(R.string.status_stopped)
            statusText.setTextColor(0xFF888888.toInt()) // Grau
            btnStartStop.text = getString(R.string.btn_start)
        }
    }

    private fun saveSettings() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt(KEY_INTERVAL, intervalMinutes)
            putInt(KEY_TIMEOUT, timeoutSeconds)
            putInt(KEY_POSITION, positionSpinner.selectedItemPosition)
            apply()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        intervalMinutes = prefs.getInt(KEY_INTERVAL, 5)
        timeoutSeconds = prefs.getInt(KEY_TIMEOUT, 15)
        intervalText.text = intervalMinutes.toString()
        timeoutText.text = timeoutSeconds.toString()
    }
}
