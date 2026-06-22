# Veröffentlichungs-Anleitung

Ein und derselbe Quellcode für alle drei Kanäle. Unterschiede liegen nur in
Build-Format, Signierung und Papierkram.

> ⚠️ **Wichtig zur Signierung:** Play (Google-Key), F-Droid (F-Droid-Key) und
> GitHub (dein Key) verwenden unterschiedliche Signaturschlüssel. Eine aus einem
> Kanal installierte App kann nicht über einen anderen Kanal aktualisiert
> werden. Das ist normal — nur gut zu wissen.

## Terminal & Verzeichnis

Alle folgenden Befehle werden in einem **Terminal** ausgeführt — unter Windows 11
am einfachsten **PowerShell** (oder `cmd`). Öffne es **im Projekt-Stammverzeichnis**:

```powershell
cd "C:\Claude Code\SleepGuard"
```

Dort liegen `settings.gradle`, `gradlew.bat` und (nach Schritt 0) der Keystore.
Voraussetzungen: **Git** installiert und im PATH; für `keytool` ein **JDK** im PATH
(z.B. das von Android Studio mitgelieferte). Die Gradle-Befehle nutzen den
mitgelieferten Wrapper `gradlew.bat` — eine separate Gradle-Installation ist nicht
nötig.

---

## 0. Einmalig: Keystore erzeugen (für Play + GitHub)

In PowerShell/cmd im Projekt-Stammverzeichnis (ein einzelner Befehl, eine Zeile):

```powershell
keytool -genkey -v -keystore sleepguard-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias sleepguard
```

Das erzeugt `sleepguard-release.jks` im Stammverzeichnis (genau dort erwartet die
Konfiguration den Keystore). `keytool` fragt anschließend interaktiv nach
Passwörtern und ein paar Angaben.

Dann `keystore.properties.example` zu `keystore.properties` kopieren und die
echten Werte eintragen:

```powershell
Copy-Item keystore.properties.example keystore.properties
```

**Keystore + Passwörter sicher aufbewahren und niemals einchecken** (sind bereits
in `.gitignore`).

---

## 1. GitHub (am einfachsten — Basis für alles andere)

1. Neues öffentliches Repo anlegen, Code pushen.
2. Tag setzen und Release erstellen:
   ```powershell
   git tag v1.0
   git push origin v1.0
   .\gradlew.bat assembleRelease
   ```
3. Die signierte APK aus `app/build/outputs/apk/release/` an den GitHub-Release
   anhängen.
4. GitHub Pages aktivieren (Settings → Pages → Branch `main`, Ordner `/docs`),
   damit die Datenschutzerklärung unter
   `https://OlafDrechsler.github.io/SleepGuard/privacy-policy` erreichbar ist.

---

## 2. F-Droid

Voraussetzungen (alle bereits erfüllt):
- Quellcode öffentlich auf GitHub ✓
- GPLv3-`LICENSE`-Datei ✓
- Nur freie Abhängigkeiten (nur AndroidX/Apache-2.0) ✓
- Kein Tracking, kein Google Play Services ✓

Schritte:
1. Sicherstellen, dass ein Git-Tag `v1.0` existiert (siehe oben).
2. Das fertige Rezept `fdroid/com.sleepguard.yml` verwenden.
3. Es als `metadata/com.sleepguard.yml` per Merge Request bei
   <https://gitlab.com/fdroid/fdroiddata> einreichen.
4. F-Droid baut und signiert die App selbst aus dem Quellcode. Store-Texte und
   Screenshots zieht F-Droid automatisch aus `fastlane/metadata/android/`.

---

## 3. Google Play Store

1. **Entwicklerkonto** anlegen (einmalig 25 USD).
2. AAB bauen:
   ```powershell
   .\gradlew.bat bundleRelease   # app/build/outputs/bundle/release/app-release.aab
   ```
3. In der Play Console eine App anlegen und das AAB hochladen
   (Play App Signing übernimmt die endgültige Signierung).
4. **Store-Eintrag**: Titel, Kurz- und Vollbeschreibung aus
   `fastlane/metadata/android/en-US/` übernehmen (oder via `fastlane supply`
   automatisch hochladen). Icon + mind. 2 Screenshots hochladen.
5. **Pflichtangaben ausfüllen:**
   - **Datenschutzerklärung-URL** → die GitHub-Pages-Adresse aus Schritt 1.
   - **Data Safety**: „Es werden keine Daten erfasst oder geteilt."
   - **Inhaltsfreigabe** (Fragebogen) + Zielgruppe.
6. **Sensible Berechtigungen begründen** (diese App wird dafür geprüft):
   - `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` → App-Funktion ist ein
     zeitgesteuerter Wecker/Alarm → Kategorie „Alarm/Wecker" angeben.
   - `foregroundServiceType=specialUse` → im Formular erklären: dauerhafter
     Timer, der das Kontroll-Overlay zur richtigen Zeit einblendet.
   - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` → Begründung: zuverlässige
     Auslösung im Hintergrund während eine andere App läuft.
   - **Device Admin / `lockNow`** → erklären: ausschließlich Bildschirmsperre,
     keine weiteren Admin-Rechte (siehe `res/xml/device_admin.xml` → nur
     `force-lock`).

> Hinweis: Falls Play `specialUse` oder die Akku-Ausnahme beanstandet, ist das
> der wahrscheinlichste Ablehnungsgrund. Beide sind für eine Wecker-/Sleep-App
> begründbar; notfalls Support kontaktieren oder die Akku-Ausnahme weglassen
> (die App läuft dank `AlarmManager` auch ohne sie, nur etwas weniger robust).

---

## Versions-Updates (später)

In `app/build.gradle` `versionCode` (+1) und `versionName` erhöhen, neuen
Changelog `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
anlegen, neuen Git-Tag setzen — dann je Kanal neu ausliefern.
