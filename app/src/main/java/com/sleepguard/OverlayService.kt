// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Olaf Drechsler
//
// This file is part of SleepGuard, free software licensed under the GNU General
// Public License v3.0 or later. See the LICENSE file in the project root.

package com.sleepguard

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat

/**
 * Kern-Dienst der SleepGuard-App.
 *
 * Ablauf:
 * 1. Dienst startet und wartet [intervalMinutes] Minuten.
 * 2. Nach Ablauf wird ein roter Kreis (Overlay) auf dem Bildschirm angezeigt.
 * 3. Wenn der Benutzer innerhalb von [timeoutSeconds] Sekunden auf den Kreis tippt:
 *    -> Kreis verschwindet, Timer startet neu ab Schritt 1.
 * 4. Wenn der Benutzer NICHT tippt (eingeschlafen):
 *    -> Bildschirm wird gesperrt (wie Power-Taste).
 *    -> Dienst beendet sich selbst.
 *
 * SICHERHEITSHINWEIS:
 * Dieser Dienst verwendet keine Internet-APIs.
 * Er liest keine persoenlichen Daten.
 * Er tut ausschliesslich zwei Dinge: Kreis anzeigen und Bildschirm sperren.
 */
class OverlayService : Service() {

    private var intervalMinutes = 5
    private var timeoutSeconds = 15
    private var position = "right_center"
    // false = Video (Button + Sperre), true = Audio (Einschlaf-Timer: pausiert
    // nach dem Intervall die Wiedergabe, auch bei ausgeschaltetem Bildschirm).
    private var audioMode = false

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var countdownTimer: CountDownTimer? = null

    companion object {
        // Damit die MainActivity pruefen kann, ob der Dienst laeuft
        var isRunning = false
            private set

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "sleepguard_channel"

        // Action, mit der der AlarmManager den Dienst weckt, um den Kreis anzuzeigen
        const val ACTION_SHOW_OVERLAY = "com.sleepguard.action.SHOW_OVERLAY"
        private const val ALARM_REQUEST_CODE = 100
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Parameter aus dem Intent lesen. Die Defaults sind die aktuellen
        // Feldwerte, damit ein erneuter Start ohne Extras (z.B. durch den
        // AlarmManager nach einem Prozess-Neustart) die Einstellungen behaelt.
        intent?.let {
            intervalMinutes = it.getIntExtra("interval_minutes", intervalMinutes)
            timeoutSeconds = it.getIntExtra("timeout_seconds", timeoutSeconds)
            position = it.getStringExtra("position") ?: position
            audioMode = it.getBooleanExtra("audio_mode", audioMode)
        }

        // Foreground-Notification erstellen (Android verlangt das fuer
        // Hintergrunddienste). Muss auch beim Alarm-Start sofort passieren,
        // falls der Prozess zwischenzeitlich beendet wurde.
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        isRunning = true

        if (intent?.action == ACTION_SHOW_OVERLAY) {
            // Der AlarmManager hat den Dienst geweckt.
            if (audioMode) {
                // Audio-Modus (Einschlaf-Timer): Wiedergabe pausieren; ist der
                // Bildschirm noch an, zusaetzlich sperren. Kein Button.
                pauseMediaAndFinish()
            } else if (isScreenUsable()) {
                showOverlay()
            } else {
                // Video-Modus, Bildschirm aus/gesperrt: Der Kreis waere
                // unsichtbar, ein Countdown wuerde "aus dem Nichts" sperren.
                // Stattdessen still das naechste Intervall planen.
                scheduleOverlay()
            }
        } else {
            // Normaler Start -> naechste Anzeige planen.
            scheduleOverlay()
        }

        return START_NOT_STICKY
    }

    /**
     * Nur wenn der Bildschirm an und entsperrt ist, kann der Benutzer den
     * roten Kreis ueberhaupt sehen und antippen.
     */
    private fun isScreenUsable(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return powerManager.isInteractive && !keyguardManager.isKeyguardLocked
    }

    /**
     * Audio-Modus: Wiedergabe pausieren (Pause-Tastenereignis + Audio-Fokus).
     * Ist der Bildschirm noch an, wird er zusaetzlich gesperrt (ausgeschaltet);
     * ist er bereits aus, gibt es nichts zu tun. Danach den Dienst beenden.
     */
    private fun pauseMediaAndFinish() {
        pauseViaMediaButton()
        pauseOtherMedia()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isInteractive) {
            lockScreen()
        }
        stopSelf()
    }

    /**
     * Plant die naechste Anzeige des roten Kreises ueber den AlarmManager.
     *
     * WICHTIG (Xiaomi/MIUI-Fix): Ein Handler.postDelayed wuerde im
     * App-Prozess laufen. Sobald eine andere App im Vordergrund ist, friert
     * MIUI den SleepGuard-Prozess ein und der Timer steht still — der Kreis
     * erscheint erst, wenn man wieder in die App wechselt. Der AlarmManager
     * liegt dagegen im System und weckt den (ggf. eingefrorenen) Prozess
     * zuverlaessig zum richtigen Zeitpunkt auf.
     */
    private fun scheduleOverlay() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = overlayAlarmPendingIntent()
        val triggerAt = SystemClock.elapsedRealtime() + intervalMinutes * 60 * 1000L

        alarmManager.cancel(pendingIntent)

        // setExactAndAllowWhileIdle feuert auch im Doze-/Idle-Zustand.
        // Falls exakte Alarme ausnahmsweise nicht erlaubt sind, weichen wir
        // auf die ungenaue, aber ebenfalls idle-faehige Variante aus.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent
            )
        }
    }

    /**
     * Der PendingIntent, mit dem der AlarmManager diesen Dienst wieder
     * startet (Action [ACTION_SHOW_OVERLAY]). Die aktuellen Einstellungen
     * werden mitgegeben, damit sie auch nach einem Prozess-Neustart erhalten
     * bleiben.
     */
    private fun overlayAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_SHOW_OVERLAY
            putExtra("interval_minutes", intervalMinutes)
            putExtra("timeout_seconds", timeoutSeconds)
            putExtra("position", position)
            putExtra("audio_mode", audioMode)
        }
        return PendingIntent.getService(
            this, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Bricht einen geplanten Overlay-Alarm ab.
     */
    private fun cancelScheduledOverlay() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(overlayAlarmPendingIntent())
    }

    /**
     * Zeigt den roten Kreis als Overlay ueber allen Apps an.
     * Startet gleichzeitig einen Countdown — wenn der Kreis nicht
     * angetippt wird, wird der Bildschirm gesperrt.
     */
    private fun showOverlay() {
        // Schutz vor doppeltem Overlay (z.B. doppelt zugestellter Alarm):
        // sonst liefe der alte Countdown unsichtbar weiter und wuerde sperren,
        // obwohl der Benutzer den neuen Kreis antippt.
        if (overlayView != null) return

        // Overlay-View erstellen: ein runder roter Kreis
        val circleView = ImageView(this).apply {
            setImageResource(R.drawable.red_circle)
            // ~1cm entspricht ungefaehr 40dp
        }

        // Layout-Parameter fuer das Overlay-Fenster
        val size = (40 * resources.displayMetrics.density).toInt() // 40dp in Pixel

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Wichtig: FLAG_NOT_FOCUSABLE damit der Film weiterlaeuft
            // und Touch-Events anderer Apps nicht blockiert werden.
            // Aber der Kreis selbst muss antippbar sein.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Position basierend auf der Einstellung setzen
        setOverlayPosition(params)

        // Klick-Handler: wenn angetippt, Kreis entfernen und naechsten
        // Alarm fuer das uebernaechste Intervall planen.
        circleView.setOnClickListener {
            removeOverlay()
            cancelCountdown()
            scheduleOverlay() // naechste Anzeige planen
        }

        // Overlay anzeigen. Schlaegt das fehl (z.B. Overlay-Berechtigung
        // zwischenzeitlich entzogen), NICHT sperren — der Benutzer koennte
        // den Kreis ja gar nicht sehen. Stattdessen naechstes Intervall planen.
        try {
            windowManager.addView(circleView, params)
        } catch (e: Exception) {
            scheduleOverlay()
            return
        }
        overlayView = circleView

        // Countdown starten: wenn nicht angetippt, Bildschirm sperren
        startCountdown()
    }

    /**
     * Setzt die Position des Overlay-Fensters basierend auf der Einstellung.
     */
    private fun setOverlayPosition(params: WindowManager.LayoutParams) {
        when (position) {
            "right_center" -> {
                params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                params.x = 16 // kleiner Abstand vom Rand
                params.y = 0
            }
            "left_center" -> {
                params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                params.x = 16
                params.y = 0
            }
            "top_center" -> {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.x = 0
                params.y = 16
            }
            "bottom_center" -> {
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.x = 0
                params.y = 16
            }
            "top_right" -> {
                params.gravity = Gravity.TOP or Gravity.END
                params.x = 16
                params.y = 16
            }
            "top_left" -> {
                params.gravity = Gravity.TOP or Gravity.START
                params.x = 16
                params.y = 16
            }
            "bottom_right" -> {
                params.gravity = Gravity.BOTTOM or Gravity.END
                params.x = 16
                params.y = 16
            }
            "bottom_left" -> {
                params.gravity = Gravity.BOTTOM or Gravity.START
                params.x = 16
                params.y = 16
            }
        }
    }

    /**
     * Startet den Countdown. Wenn er ablaeuft, wird der Bildschirm gesperrt.
     */
    private fun startCountdown() {
        countdownTimer = object : CountDownTimer(timeoutSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Nichts zu tun — wir warten einfach
            }

            override fun onFinish() {
                // Benutzer hat nicht reagiert -> eingeschlafen!
                removeOverlay()
                pauseViaMediaButton() // echtes Pause-Kommando an Prime/Netflix/...
                pauseOtherMedia()     // zusaetzlich Audio-Fokus entziehen
                lockScreen()          // dann den Bildschirm sperren
                stopSelf()            // Dienst beenden
            }
        }.start()
    }

    /**
     * Sendet ein simuliertes "Pause"-Tastenereignis an die gerade spielende
     * Media-App.
     *
     * Das ist exakt dasselbe wie die Pausetaste an einem (Bluetooth-)Kopfhoerer:
     * Android leitet das Ereignis an die aktive Media-Session weiter, und Apps
     * wie Prime Video, Netflix oder YouTube pausieren daraufhin. Anders als der
     * blosse Audio-Fokus-Entzug (den manche Apps beim Entsperren ignorieren und
     * dann weiterspielen) bleibt die Wiedergabe so pausiert.
     *
     * Vorteil gegenueber dem MediaSession-Weg: KEINE Sonderberechtigung noetig
     * (kein Benachrichtigungszugriff). Es wird gezielt KEYCODE_MEDIA_PAUSE
     * verwendet (nicht der Toggle PLAY_PAUSE), damit nichts versehentlich
     * gestartet statt gestoppt wird.
     */
    private fun pauseViaMediaButton() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            )
        } catch (e: Exception) {
            // Defensive: Sperre/Fokus greifen weiterhin.
        }
    }

    /**
     * Stoppt die Wiedergabe anderer Apps (Prime Video, Netflix, YouTube, ...).
     *
     * Bildschirmsperre allein beendet die Wiedergabe NICHT zuverlaessig — viele
     * Streaming-Apps spielen im Hintergrund weiter. Deshalb fordert SleepGuard
     * hier den Audio-Fokus an (AUDIOFOCUS_GAIN). Das ist der offizielle
     * Android-Weg, anderen Media-Apps "hoer auf zu spielen" zu signalisieren —
     * sie pausieren daraufhin. Der Fokus wird bewusst NICHT wieder freigegeben,
     * damit die andere App nicht von selbst weiterspielt.
     */
    private fun pauseOtherMedia() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .build()
        try {
            audioManager.requestAudioFocus(request)
        } catch (e: Exception) {
            // Fokus-Anfrage fehlgeschlagen — Sperre erfolgt trotzdem.
        }
    }

    /**
     * Sperrt den Bildschirm (wie Power-Taste druecken).
     */
    private fun lockScreen() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, SleepGuardDeviceAdmin::class.java)

        if (devicePolicyManager.isAdminActive(adminComponent)) {
            devicePolicyManager.lockNow()
        }
    }

    /**
     * Entfernt den roten Kreis vom Bildschirm.
     */
    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View war bereits entfernt — ignorieren
            }
        }
        overlayView = null
    }

    /**
     * Bricht den laufenden Countdown ab.
     */
    private fun cancelCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
    }

    /**
     * Erstellt den Notification-Kanal (ab Android 8 erforderlich).
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW // Leise, kein Ton
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Erstellt die Foreground-Notification.
     * Diese Benachrichtigung ist noetig, damit Android den Dienst
     * nicht im Hintergrund beendet.
     */
    private fun createNotification(): android.app.Notification {
        // Beim Tippen auf die Notification die App oeffnen
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, intervalMinutes))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Kann nicht weggewischt werden
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelScheduledOverlay()
        cancelCountdown()
        removeOverlay()
        isRunning = false
    }
}
