# Drift

A private Android sleep-sound app (Android 17+). Every sound is generated live by the app; there are no recordings.

## Sounds and what each one varies

| Sound | Varies | Notes |
|---|---|---|
| White, Pink, Brown | Tone (low-pass filter, Hz) | Pink and brown have the most sleep research behind them |
| Breathing | Breaths per minute | Swells in (40%) and out (60%), slow paced breathing |
| Binaural | Beat frequency (Hz) | 200 Hz carrier, one tone per ear. Needs earbuds |
| Soft drone | Root pitch (Hz) | Open-fifth chord of sine partials |
| Soft rhythm | Tempo (BPM) | Soft low "doum" and quiet "tek" hand-drum pulse |
| Gentle rain | Intensity (drops/s) | Filtered wash plus synthesized drops |
| Deep bass | Root pitch (Hz) | Low partials plus a filtered rumble |

**Change every** (5 s to 30 min) sets how long each glide takes. The sound always glides smoothly.
**Pattern**: Random (new random point each time), Wave (sweeps up and down), Settle (starts high, eases toward low).

## Behavior

- Timer up to 10 hours, optional fade-out of 1, 5, 15 or 30 minutes; 8-second fade-in at start.
- Stops if earbuds disconnect (never falls back to the phone speaker). Pauses for calls.
- Widget: one tap plays the most recent settings or stops; double-tap opens the app.
- Red on black, amber highlights, no blue in any color. Optional brightness lock (off by default) while the app is open.
- No libraries. Block-based synthesis into a large power-saving audio buffer (under 1% of one CPU core).

## Building

Pushing to `main` runs the GitHub Actions workflow, which builds a signed APK and attaches it to the
"Latest build" release. On your phone, open the release, download `drift.apk`, and allow installs from
your browser when asked.

The signing key (`app/drift.keystore`) lives in this repo so every build installs over the previous one.
Keep the repository private.

## Code map

- `Gens.java` – all sound generators (plain Java, testable on a desktop JVM)
- `Glide.java` – smooth movement between values; Random / Wave / Settle patterns
- `Engine.java` – audio thread, volume, crossfades, timer fade
- `SleepService.java` – foreground playback, audio focus, earbud disconnect, notification
- `MainActivity.java` – the single screen
- `SleepWidget.java` – home-screen play/stop widget
