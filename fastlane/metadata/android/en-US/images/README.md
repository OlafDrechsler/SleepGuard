# Grafiken hier ablegen

Lege deine selbst erstellten Bilder genau in diese Struktur (F-Droid und der
Play Store lesen beide dieses Fastlane-Layout):

```
images/
├── icon.png                 App-Icon, 512 x 512 px (PNG)
├── featureGraphic.png       Feature-Banner, 1024 x 500 px (PNG/JPG)
└── phoneScreenshots/
    ├── 1.png                Screenshots vom Telefon
    ├── 2.png                (mind. 2 fuer den Play Store,
    └── 3.png                 Seitenverhaeltnis 16:9 oder 9:16)
```

Hinweise:
* Dateinamen exakt so verwenden (`icon.png`, `featureGraphic.png`).
* Screenshots durchnummerieren (`1.png`, `2.png`, …) — die Reihenfolge
  bestimmt die Anzeige in F-Droid.
* Fuer weitere Sprachen einen Geschwister-Ordner anlegen, z.B.
  `fastlane/metadata/android/de-DE/images/` mit eigenen Screenshots.
* Der Play Store verlangt zusaetzlich ein hochaufgeloestes Icon (512x512)
  und mindestens 2 Telefon-Screenshots — beides deckt diese Struktur ab.
