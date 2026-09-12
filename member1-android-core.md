# Member 1 — Input (Camera, OCR/VLM feed, Voice-in, Focus Signals)

You own everything that turns a real-world signal (a photo, a spoken question, phone-usage events) into clean data that Member 2's on-device LLM can consume. Read `implementation-plan.md` first for full context, especially §4 (the VLM-vs-OCR decision) and §7 (the shared data contract).

## What you're building

1. **Camera capture** (CameraX) — photograph a textbook page or handwritten problem.
2. **Content extraction feed** — depends on the Plan A/B call (§4 of the plan, owned by Member 2, but you implement whichever path is chosen):
   - Plan A (VLM): hand the captured image bitmap directly to Member 2's MediaPipe pipeline.
   - Plan B (OCR): run ML Kit Text Recognition on the image yourself and hand Member 2 the extracted text.
3. **Voice question input (stretch):** `SpeechRecognizer` with `RecognizerIntent.EXTRA_PREFER_OFFLINE = true`. Always keep a typed-question text field as the default, non-stretch path — don't let voice-in block the core flow.
4. **`NetworkHealthChecker` (NEW — #1 priority right now, see `implementation-plan.md` §3a):** a real "are we actually online" check, not just "is Wi-Fi/data toggled on." This is what decides whether Member 2 attempts the web-enrichment call.
4b. **`DeviceVitalsMonitor` (NEW, see `implementation-plan.md` §3b):** RAM usage and thermal status, exposed live for Member 3's "Device Vitals" UI strip. This is proof that the app is genuinely working the phone's hardware — a real judging/HackTracker signal, not cosmetic.
5. **Focus Insights raw signals (secondary feature, build after 19:00):** `UsageStatsManager` events, `NotificationListenerService`, screen on/off receiver — same techniques as general phone-usage tracking, but you only need enough to detect "did the student switch away from StudyLens, and were there notifications, during a study session."
5. Room schema for all of the above.

## Data contract (see `implementation-plan.md` §7 — do not change without telling Members 2 & 3)

```kotlin
data class StudyCapture(val id: Long, val extractedText: String, val timestamp: Long)
data class AppEvent(val id: Long, val packageName: String, val eventType: Int, val timestamp: Long)
data class NotificationEvent(val id: Long, val packageName: String, val timestamp: Long)
data class StudySession(
    val id: Long, val startTime: Long, val endTime: Long,
    val durationMs: Long, val switchCount: Int, val notificationCount: Int
)
```

## Key implementation notes

### Camera capture (CameraX)
Standard `ImageCapture` use case, save to a `Bitmap`/temp file. Keep the capture screen simple — a viewfinder and a shutter button, nothing fancier. You don't need a gallery/multi-photo flow for the MVP.

### Plan A — feeding a VLM (Gemma 3n multimodal via MediaPipe)
Hand the bitmap to Member 2's `LlmInference` session configured for image input (MediaPipe's vision-modality API for Gemma 3n). Your job is just: capture → correctly-oriented, reasonably-sized bitmap → pass to Member 2's function. Don't over-invest here before the Window 1 checkpoint — if Member 2 calls it as not working, drop straight to Plan B.

### Plan B — ML Kit Text Recognition (fallback, and the safer default)
```kotlin
val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
val image = InputImage.fromBitmap(bitmap, rotationDegrees)
recognizer.process(image)
    .addOnSuccessListener { visionText -> /* visionText.text -> StudyCapture.extractedText */ }
```
Fully on-device, no network call, fast, mature. Works well on printed textbook pages; handwriting recognition is weaker — don't over-promise on handwritten input in the demo.

### `NetworkHealthChecker` — build this first, today's #1 priority
"Bars showing" does not mean the internet actually works, especially in the low-coverage scenario this feature exists for. Do a real check:
```kotlin
suspend fun isReallyOnline(): Boolean = withContext(Dispatchers.IO) {
    // Step 1: cheap check — is there an active network at all?
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@withContext false
    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@withContext false

    // Step 2: real probe — a fast, tiny, reliable request with a short timeout
    try {
        val url = URL("https://www.google.com/generate_204") // returns 204, tiny, fast
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        conn.responseCode == 204
    } catch (e: Exception) {
        false
    }
}
```
Expose this as a simple `StateFlow<Boolean>` (`isOnline`) that Member 2 reads before deciding whether to attempt the enrichment call. Re-check it each time a new capture is explained — don't just check once at app start, since a student may walk in and out of coverage.

### `DeviceVitalsMonitor` — RAM + thermal, exposed live
```kotlin
fun thermalStatus(pm: PowerManager): String = when (pm.currentThermalStatus) {
    PowerManager.THERMAL_STATUS_NONE -> "NONE"
    PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
    PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
    PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
    PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
    else -> "UNKNOWN"
}

fun ramUsedMb(am: ActivityManager): Long {
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return (info.totalMem - info.availMem) / (1024 * 1024)
}
```
Poll these every second or two while the app is active (a simple coroutine loop is enough — no need for anything fancy) and expose as a `StateFlow` for Member 3 to display, and for Member 2 to read into `InferenceStats` alongside tokens/sec and latency. `PowerManager.currentThermalStatus` needs no special permission and is available from Android 10+ — the iQOO 15 will be well above that.

### Voice input (stretch only)
```kotlin
val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
}
```
Requires the offline language pack pre-downloaded on-device (Settings → System → Languages → Voice input → offline recognition) — do this in Window 1 if you're attempting this at all. If it's flaky at test time, cut it and ship the typed-question box; it's explicitly not required.

### Focus Insights signals (same techniques as general usage tracking, scoped down)
- **Permissions:** `Settings.ACTION_USAGE_ACCESS_SETTINGS` and `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` — manual per-device toggle, do this in Window 1.
- **UsageStatsManager:** use `queryEvents` (not `queryUsageStats`) to catch `ACTIVITY_RESUMED`/`ACTIVITY_PAUSED` transitions — this is how you detect "the student switched away from StudyLens."
- **NotificationListenerService:** log `onNotificationPosted` events with package + timestamp.
- **Session definition, scoped to study:** a `StudySession` starts when StudyLens comes to the foreground and ends when it goes to the background (or after a long idle gap). `switchCount` = how many times the user left and came back (or left entirely) during that window. `notificationCount` = notifications posted during that window.

This is a much smaller job than a general-purpose usage tracker — you don't need all-day, all-app analytics, just "what happened during this study session."

## Timeline

| Window | Time | Task |
|---|---|---|
| 1 | 11:00–14:00 🟢 | **`NetworkHealthChecker` first** (see above), repo scaffold, CameraX capture screen, ML Kit OCR wired as the safety-net path regardless of Plan A/B, Room entities + DAOs. Usage/Notification permissions (for Focus Insights) can wait until after 19:00 |
| 2 | 14:00–15:30 🔴 | On-device: photograph real textbook pages, sanity-check OCR output quality by eye; manually trigger app-switch/notification events to confirm Focus signals log correctly |
| 3 | 15:30–16:30 🟢 | Finalize whichever path (A/B) was decided at the Window 1 checkpoint; hand Member 2 a clean `StudyCapture` |
| 4 | 16:30–19:00 🔴 | On-device: real usage testing of capture + focus-signal collection running together; log bugs, don't force fixes without a laptop |
| 5 | 19:00–22:00 🟢 | Polish capture reliability (blur/lighting edge cases); implement `StudySession` builder from raw events |
| 6 | 22:00–01:00 🔴 | Support Member 2/3 on-device testing; minor fixes via Office Kit in short batches |
| 7 | 01:00–06:30 🟢 | Stretch: voice input, if time and reliability allow; otherwise polish the typed-question fallback and Focus signal edge cases |
| 8 | 06:30–09:00 🔴 | Final on-device verification of the full input pipeline from a cold app install; freeze |

## Acceptance criteria
- Photographing a clear, printed textbook page reliably produces usable extracted text (or a clean image handoff for Plan A).
- Usage/Notification permissions, once granted, reliably produce logged events during a real study session — verified with a real test, not assumed.
- The app never blocks on voice input — a typed question always works as a fallback.
- No crash if a permission is denied (Focus Insights degrades gracefully; the core tutoring flow doesn't depend on it at all).
