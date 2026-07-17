#!/usr/bin/env python3
"""Normalisiert die Store-Beschreibungen (fastlane/metadata/android/<locale>/).

Nach jeder (Neu-)Uebersetzung von full_description.txt vor Commit/Upload
ausfuehren:  python tools/normalize_store_descriptions.py

Idempotent. Je Locale (ausser en-US / de-DE, die sind handgepflegt):
  * Ersetzt den englischen Standard-App-Namen, den die Uebersetzer am Ende
    stehen lassen ("SleepGuard - Dead Man's Switch"), durch den Wert aus
    title.txt. Bewusst NUR dieses feste englische Muster -> es wird nie zu
    viel gematcht (keine verschluckten Anfuehrungszeichen/Punkte).
  * Franzoesische Locales (fr*): Leerzeichen vor '?'/':' entfernen und
    Guillemets auf « text » normalisieren.

Warnungen (nichts wird automatisch geaendert):
  * Locale, dessen full_description den title.txt-Namen NICHT enthaelt
    -> der Uebersetzer hat den Namen evtl. uebersetzt, bitte manuell pruefen.
  * full_description ueber Googles 4000-Zeichen-Limit (ga/mt sind fuer Play
    ausgeklammert -> nur Hinweis).
"""
import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
METADATA_DIR = os.path.join(REPO_ROOT, "fastlane", "metadata", "android")

PLAY_LIMIT = 4000
PLAY_UNSUPPORTED = {"ga", "mt"}          # nur F-Droid, kein Play-Listing
SKIP = {"en-US", "de-DE"}                # handgepflegte Quellsprachen

# Das feste englische DeepL-Standardergebnis (dash + gerader/typografischer Apostroph).
ENGLISH_NAME = re.compile(r"SleepGuard\s*[-–—]\s*Dead\s*Man[’']s\s*Switch", re.IGNORECASE)
SPACE_CHARS = "      "


def fix_french(text):
    text = re.sub("[" + SPACE_CHARS + "]+(?=[?:])", "", text)   # kein Leerzeichen vor ? :
    text = re.sub("«[" + SPACE_CHARS + "]*", "« ", text)    # « <space>
    text = re.sub("[" + SPACE_CHARS + "]*»", " »", text)    # <space> »
    return text


def main():
    if not os.path.isdir(METADATA_DIR):
        sys.exit(f"Nicht gefunden: {METADATA_DIR}")
    changed, warn_name, over = [], [], []
    for locale in sorted(os.listdir(METADATA_DIR)):
        d = os.path.join(METADATA_DIR, locale)
        fd = os.path.join(d, "full_description.txt")
        tf = os.path.join(d, "title.txt")
        if not (os.path.isfile(fd) and os.path.isfile(tf)):
            continue
        title = open(tf, encoding="utf-8").read().strip()
        text = open(fd, encoding="utf-8").read().replace("\r\n", "\n")
        baseline = text.rstrip("\n") + "\n"

        if locale not in SKIP:
            text = ENGLISH_NAME.sub(title, text)
            if locale.startswith("fr"):
                text = fix_french(text)
            text = text.rstrip("\n") + "\n"
            if text != baseline:
                with open(fd, "w", encoding="utf-8", newline="\n") as f:
                    f.write(text)
                changed.append(locale)
            if title not in text:
                warn_name.append(locale)

        length = len(text.rstrip("\n"))
        if length > PLAY_LIMIT:
            over.append((locale, length))

    print("Geaendert:", ", ".join(changed) if changed else "(nichts)")
    for locale in warn_name:
        print(f"WARN: {locale} enthaelt den title.txt-Namen nicht -> App-Name am Ende manuell pruefen")
    for locale, length in over:
        note = " (nur F-Droid, fuer Play ausgeklammert)" if locale in PLAY_UNSUPPORTED \
            else "  <<< UEBER PLAY-LIMIT 4000 -> kuerzen!"
        print(f"WARN: {locale} full_description = {length} Zeichen{note}")


if __name__ == "__main__":
    main()
