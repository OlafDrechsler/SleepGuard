// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Olaf Drechsler
//
// This file is part of SleepGuard, free software licensed under the GNU General
// Public License v3.0 or later. See the LICENSE file in the project root.

package com.sleepguard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin Receiver fuer SleepGuard.
 *
 * Diese Klasse tut fast nichts — sie ist nur noetig, damit Android
 * der App erlaubt, den Bildschirm zu sperren (DevicePolicyManager.lockNow()).
 *
 * Die einzige Berechtigung, die in device_admin.xml angefordert wird,
 * ist "force-lock" (Bildschirm sperren). Keine anderen Geraete-Rechte.
 */
class SleepGuardDeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        // Berechtigung wurde erteilt — nichts weiter zu tun
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Berechtigung wurde entzogen — nichts weiter zu tun
    }
}
