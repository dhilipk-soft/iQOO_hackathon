# Focus Insights — Add-on Feature Brief

This is the secondary feature from the original plan: while the core product (offline AI study companion) is done and working, this adds a "why did your focus break" layer using real phone-usage signals — reusing the research from the very first project docs.

**Read this whole file before writing code.** Unlike a from-scratch feature, most of the plumbing already exists — your job is mostly *wiring real data through*, not building collectors from zero. Below is the actual current state of the codebase, verified by reading the real files, not a guess.

---

## What already exists and works (don't rebuild these)

### 1. Data collection — already real, already running
- **`input/focus/UsageCollector.kt`** — checks/requests Usage Access permission, queries real `UsageStatsManager` events, persists them to Room, and has a working `buildStudySession(startTime, endTime, notifications)` function that computes a real `StudySession` (duration, a real `switchCount` — counts how many times the user switched away from StudyLens — and notification count) and saves it to the database.
- **`input/focus/NotificationCollector.kt`** — checks/requests Notification Access permission, and reads back persisted notification events for a time range.
- **`input/focus/StudyLensNotificationListenerService.kt`** — a real, manifest-registered `NotificationListenerService`. It's already listening live and persisting every notification (except StudyLens's own) to Room. This runs independently of the rest of the app once the user grants access.
- **`input/data/AppDatabase.kt`** — `focusSignalsDao()` already has everything: `insertAppEvent`, `getAppEvents`, `insertNotificationEvent`, `getNotificationEvents`, `insertStudySession`, `getRecentStudySessions(limit)`.
- **`StudyLensApp.kt`** — the `Application` singleton already creates `usageCollector` and `notificationCollector` instances at app startup, accessible anywhere via `StudyLensApp.instance.usageCollector` / `.notificationCollector`.

### 2. The AI narration engine — already real, already working
- **`ai/LlmEngine.kt`** — the same on-device Gemma model used for the main tutoring feature. Just call `llmEngine.generateResponse(prompt): String`. It's resilient (never throws, returns a fallback string on failure) — reuse it as-is, don't build a second one.

### 3. The UI — already built, just needs real data plugged in
- **`ui/focus/FocusScreen.kt`** — already has a polished 3-card layout (a "focus streak" card, a "notifications" card, an on-device privacy disclaimer card). **Bug to fix:** it currently ignores the `focusInsight` parameter entirely and just interpolates raw `session.durationMs`/`session.notificationCount` into hardcoded English sentences. Your job includes fixing this to actually use the AI-generated narrative.
- **`ui/navigation/NavGraph.kt`** — already routes to `FocusScreen(session = studySession, focusInsight = focusInsight)`, sourced from `StudyViewModel`.

### 4. Shared data shapes (already defined in `shared/Models.kt` — don't change these without checking with the team)
```kotlin
data class AppEvent(val id: Long, val packageName: String, val eventType: Int, val timestamp: Long)
data class NotificationEvent(val id: Long, val packageName: String, val timestamp: Long)
data class StudySession(
    val id: Long, val startTime: Long, val endTime: Long,
    val durationMs: Long, val switchCount: Int, val notificationCount: Int
)
data class FocusInsight(val type: String, val evidence: String, var narrative: String? = null)
```

---

## What's actually missing (this is your real work)

### 1. Permission UI — nothing currently asks the user to grant access
`UsageCollector.isUsageAccessGranted()` / `.getUsageAccessSettingsIntent()` and `NotificationCollector.isNotificationAccessGranted()` / `.getNotificationAccessSettingsIntent()` all exist and work, but **no screen calls them yet**. Add a permission-check banner to the Focus tab (or Settings screen) — if either permission isn't granted, show a card explaining why ("we use this to understand your study session, entirely on-device") with a button that launches the settings intent.

### 2. Wire real data into `StudyViewModel` (currently hardcoded — same bug pattern we already fixed for chat)
Current state in `StudyViewModel.kt`:
```kotlin
private val _focusInsight = MutableStateFlow(
    FocusInsight(
        type = "DEEP_WORK_STREAK",
        evidence = "You stayed on this problem for 6 minutes before switching apps.",
        narrative = "Sessions without a switch tend to finish about 2x faster."
    )
)
// ...
private val _studySession = MutableStateFlow(
    StudySession(id = 101L, startTime = ..., endTime = ..., durationMs = 42*60*1000L, switchCount = 1, notificationCount = 3)
)
```
This is entirely fake, hardcoded seed data — exactly like the old chat history bug. Replace it with real values:
```kotlin
private val usageCollector = StudyLensApp.instance.usageCollector
private val notificationCollector = StudyLensApp.instance.notificationCollector
```
Then, on Focus tab load (or periodically), compute a real session for a recent window (e.g., the last hour, or since the app was opened):
```kotlin
val notifications = notificationCollector.getNotificationEvents(startTime, endTime)
val session = usageCollector.buildStudySession(startTime, endTime, notifications)
_studySession.value = session
```

### 3. Build the heuristic + narration logic (new code — this is the core of your feature)
Suggested simplest approach, given the real data available:
1. Compute the evidence directly from the real `StudySession`: e.g. `"Your last study session lasted ${durationMins} minutes with ${session.switchCount} app switches and ${session.notificationCount} notifications."`
2. If you have time, compare against a rolling history using `focusSignalsDao().getRecentStudySessions(limit = 20)` for a more research-backed insight (e.g., "sessions with fewer than 2 switches finish X% faster than average" — this is the original heuristic from the research doc, worth doing if time allows, but the single-session version above is a legitimate MVP).
3. Feed the evidence into a short prompt and call the existing LLM engine:
```kotlin
val prompt = """
    You are a calm, evidence-based study coach. Given this observation about a
    student's study session, write exactly 2 sentences explaining what it means
    for their focus, then 1 short actionable suggestion. Do not invent numbers
    beyond what's given.

    Observation: $evidence
""".trimIndent()
val narrative = llmEngine.generateResponse(prompt)
_focusInsight.value = FocusInsight(type = "SESSION_SUMMARY", evidence = evidence, narrative = narrative)
```
This is the exact same retrieve/generate discipline already used in `ExplainPipeline.kt` — reuse the pattern, don't reinvent it.

### 4. Fix `FocusScreen.kt` to actually display the narrative
Right now the cards show hardcoded sentences built from raw numbers. Change them to show `focusInsight?.evidence` and `focusInsight?.narrative` instead (falling back to a neutral "not enough data yet" state if `focusInsight` is null or the user hasn't granted permissions).

---

## Suggested order of work

1. Confirm you can manually grant Usage Access + Notification Access on your test device and that real events are actually landing in Room (quickest check: add a temporary log, or query `focusSignalsDao().getRecentStudySessions(5)` after using the phone for a few minutes).
2. Wire `StudyViewModel` to real `usageCollector`/`notificationCollector` data (§2 above) — get a real `StudySession` flowing into the UI before touching the AI part.
3. Add the permission-request UI (§1) — needed for a clean demo (a fresh install with no permissions shouldn't crash or show fake data).
4. Add the heuristic + LLM narration (§3).
5. Fix `FocusScreen.kt` to render the real narrative (§4).

## What NOT to do
- Don't touch `app/build.gradle.kts` or `build.gradle.kts` — the dependency/toolchain setup is fragile and already working; this feature needs zero new dependencies (Room, and the existing LLM engine, are already in the project).
- Don't create a second `LlmEngine` instance or a second Room database — reuse the singletons that already exist.
- Don't rebuild `UsageCollector`/`NotificationCollector`/the notification listener service — they're real and working, just unused so far.
