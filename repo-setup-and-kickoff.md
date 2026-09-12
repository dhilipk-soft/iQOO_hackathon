# StudyLens — Repo Setup & Kickoff Guide

Read this before anyone opens an AI coding agent. It answers: who sets up the repo, what folder structure everyone follows (so work doesn't collide), and exactly what to paste into each person's AI agent to start building.

---

## 1. One-time setup (one person does this, ~10-15 minutes, before anyone else starts coding)

Pick whoever is fastest to get Android Studio running. That person:

1. Creates **one** GitHub repo (e.g. `studylens-iqoo2026`). Adds the other two teammates as collaborators.
2. In Android Studio: **New Project → Empty Activity (Compose)**.
   - Package name: `com.studylens`
   - Language: Kotlin
   - Min SDK: 30 (you only need to run on the issued iQOO 15, so don't worry about older devices)
3. Adds the Gradle dependencies everyone will need (see §4 below) to `app/build.gradle` up front, so nobody hits "wrong library version" conflicts later.
4. Creates the empty folder structure (§2 below) and the shared contract file (§3 below).
5. Adds a `.gitignore` (see §5 — this matters, don't skip it).
6. Commits and pushes this as commit #1: **"Initial scaffold + shared contract."**
7. Tells the other two: "repo's up, clone it and pull before you start."

Everyone else now runs `git clone <repo-url>` and opens the project in Android Studio.

---

## 2. Folder structure — everyone follows this exactly

This is what lets three people work "independently" without constantly colliding on the same files.

```
studylens/
├── implementation-plan.md
├── member1-android-core.md
├── member2-insight-engine-llm.md
├── member3-ui-ux-demo.md
├── repo-setup-and-kickoff.md
├── .gitignore
├── local.properties              (NOT committed — holds your OpenRouter API key)
├── build.gradle
├── settings.gradle
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/studylens/
            ├── MainActivity.kt
            ├── StudyLensApp.kt         (Application class — inits Room, etc. — edit together, not solo)
            │
            ├── shared/                  ⚠️ JOINT OWNERSHIP — see §3, don't change solo
            │   ├── Models.kt            (StudyCapture, ExplanationResult, QuizQuestion, etc.)
            │   └── Contracts.kt         (InputProvider, StudyBrain interfaces)
            │
            ├── input/                   👤 MEMBER 1 — camera, OCR, network, vitals, focus signals
            │   ├── capture/CameraCapture.kt
            │   ├── ocr/TextExtractor.kt
            │   ├── network/NetworkHealthChecker.kt
            │   ├── vitals/DeviceVitalsMonitor.kt
            │   ├── voice/VoiceInput.kt          (stretch)
            │   ├── focus/UsageCollector.kt      (build after 7 PM)
            │   ├── focus/NotificationCollector.kt
            │   └── data/                        (Room: AppDatabase.kt, entities, DAOs)
            │
            ├── ai/                       👤 MEMBER 2 — on-device LLM + RAG pipeline
            │   ├── LlmEngine.kt                 (MediaPipe LlmInference wrapper)
            │   ├── RetrievalClient.kt           (OpenRouter :online call)
            │   ├── ExplainPipeline.kt           (combine + generate)
            │   ├── QuizGenerator.kt
            │   └── FocusNarrator.kt             (build after 7 PM)
            │
            └── ui/                       👤 MEMBER 3 — screens, voice output, vitals display
                ├── navigation/NavGraph.kt
                ├── capture/CaptureScreen.kt
                ├── explanation/ExplanationScreen.kt
                ├── quiz/QuizScreen.kt
                ├── revision/RevisionScreen.kt
                ├── focus/FocusScreen.kt         (build after 7 PM)
                ├── vitals/DeviceVitalsStrip.kt
                └── tts/TtsManager.kt
```

**The rule that prevents merge conflicts:** each person only edits files inside their own top-level folder (`input/`, `ai/`, or `ui/`). The only shared files are in `shared/` and the two root files (`MainActivity.kt`, `StudyLensApp.kt`, `NavGraph.kt`) — if you need to change one of those, say so in your team chat first so you don't overwrite each other.

---

## 3. The shared contract file — create this in commit #1, before any feature code

`app/src/main/java/com/studylens/shared/Models.kt`:
```kotlin
package com.studylens.shared

data class StudyCapture(val id: Long, val extractedText: String, val timestamp: Long)

data class ExplanationResult(
    val captureId: Long,
    val finalExplanation: String,
    val usedOnlineContext: Boolean
)

data class QuizQuestion(
    val id: Long, val captureId: Long, val topic: String,
    val question: String, val options: List<String>?, val correctAnswer: String
)
data class QuizAttempt(val questionId: Long, val userAnswer: String, val isCorrect: Boolean, val timestamp: Long)

data class InferenceStats(
    val tokensPerSecond: Double, val latencyMs: Long,
    val ramUsedMb: Long, val thermalStatus: String
)

// Focus Insights (build after 7 PM)
data class AppEvent(val id: Long, val packageName: String, val eventType: Int, val timestamp: Long)
data class NotificationEvent(val id: Long, val packageName: String, val timestamp: Long)
data class StudySession(
    val id: Long, val startTime: Long, val endTime: Long,
    val durationMs: Long, val switchCount: Int, val notificationCount: Int
)
data class FocusInsight(val type: String, val evidence: String, var narrative: String? = null)
```

`app/src/main/java/com/studylens/shared/Contracts.kt`:
```kotlin
package com.studylens.shared

import kotlinx.coroutines.flow.StateFlow

interface InputProvider {
    suspend fun captureAndExtractText(): StudyCapture
    val isOnline: StateFlow<Boolean>
}

interface StudyBrain {
    suspend fun explain(capture: StudyCapture, isOnline: Boolean): ExplanationResult
    suspend fun answerFollowUp(capture: StudyCapture, explanation: String, question: String): String
    suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion>
}
```

Everyone builds against these exact shapes. If a change is genuinely needed mid-event, whoever needs it pings the other two before editing — a silent change here breaks the other two's code.

---

## 4. Gradle dependencies (add all of these up front, in commit #1)

```gradle
dependencies {
    // Compose (Member 3)
    implementation platform('androidx.compose:compose-bom:2024.09.00')
    implementation 'androidx.activity:activity-compose:1.9.0'
    implementation 'androidx.navigation:navigation-compose:2.7.7'
    implementation 'androidx.compose.material3:material3'

    // Camera + OCR (Member 1)
    implementation 'androidx.camera:camera-camera2:1.3.4'
    implementation 'androidx.camera:camera-lifecycle:1.3.4'
    implementation 'androidx.camera:camera-view:1.3.4'
    implementation 'com.google.mlkit:text-recognition:16.0.0'

    // Local storage + background work (Member 1)
    implementation 'androidx.room:room-runtime:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    implementation 'androidx.work:work-runtime-ktx:2.9.0'

    // On-device LLM (Member 2)
    implementation 'com.google.mediapipe:tasks-genai:0.10.24' // check for the latest at setup time

    // Networking, for the OpenRouter retrieval call (Member 2)
    implementation 'com.squareup.retrofit2:retrofit:2.11.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.11.0'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

    // Coroutines (everyone)
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1'
}
```

---

## 5. `.gitignore` — add these, they matter

```
local.properties
*.task
*.tflite
*.gguf
/app/build/
.gradle/
*.iml
.idea/
```

**Why:** `local.properties` holds your OpenRouter API key — never commit it. The on-device model files (`.task`/`.tflite`/`.gguf`) are hundreds of MB to a few GB — committing them will blow up your repo size and make every clone/push painfully slow during Red Light windows when you have limited laptop time. Push model files straight to the device with `adb push` instead; they never need to touch git.

---

## 6. Git workflow while building

- **Pull** before starting each new work window.
- **Push** at the end of every Green Light window (see `implementation-plan.md` §9a) — don't sit on uncommitted work through a Red Light block.
- Commit small and often, with clear messages: `"input: camera capture screen"`, `"ai: RAG retrieval call"`, `"ui: explanation screen wired to real data"`.
- If a conflict happens, it'll almost always be in one of the shared files (`shared/Models.kt`, `MainActivity.kt`, `NavGraph.kt`) — resolve those together in a quick call, don't just force-push over each other.

---

## 7. Kickoff prompts — paste these into each person's AI coding agent

Each of you opens your cloned copy of the repo in your own AI coding tool and pastes your version below to get started.

### Member 1
```
I'm building my part of an Android app called StudyLens, for a hackathon (Kotlin, Jetpack Compose,
single shared repo). Read implementation-plan.md and member1-android-core.md in this repo for full
context on the whole project and my specific responsibilities. Also read
app/src/main/java/com/studylens/shared/Models.kt and Contracts.kt — those are the exact data shapes
and function signatures I need to build to, already agreed with my two teammates building the other
parts in parallel.

I own everything under app/src/main/java/com/studylens/input/ — camera capture, on-device OCR, the
network health checker, the device vitals monitor, and (later) the focus-insight signal collectors.
Don't touch files outside input/ or shared/ without telling me first.

Start with the Window 1 tasks in my file's timeline table. Today's #1 priority is the
NetworkHealthChecker and the DeviceVitalsMonitor — build those first, before the camera/OCR polish.
```

### Member 2
```
I'm building my part of an Android app called StudyLens, for a hackathon (Kotlin, Jetpack Compose,
single shared repo). Read implementation-plan.md and member2-insight-engine-llm.md in this repo for
full context on the whole project and my specific responsibilities. Also read
app/src/main/java/com/studylens/shared/Models.kt and Contracts.kt — those are the exact data shapes
and function signatures I need to build to, already agreed with my two teammates building the other
parts in parallel.

I own everything under app/src/main/java/com/studylens/ai/ — the on-device MediaPipe/Gemma model,
the OpenRouter retrieval call, the retrieve-then-generate (RAG) pipeline, quiz generation, and (later)
focus-insight narration. Don't touch files outside ai/ or shared/ without telling me first.

Start with the Window 1 tasks in my file's timeline table. Today's #1 priority is the hybrid
offline/online RAG pipeline in implementation-plan.md §3a — build that before the quiz feature.
```

### Member 3
```
I'm building my part of an Android app called StudyLens, for a hackathon (Kotlin, Jetpack Compose,
single shared repo). Read implementation-plan.md and member3-ui-ux-demo.md in this repo for full
context on the whole project and my specific responsibilities. Also read
app/src/main/java/com/studylens/shared/Models.kt and Contracts.kt — those are the exact data shapes
and function signatures I need to build to, already agreed with my two teammates building the other
parts in parallel.

I own everything under app/src/main/java/com/studylens/ui/ — all Compose screens, navigation,
text-to-speech, and the device vitals display strip. I also own the demo video and pitch deck.
Don't touch files outside ui/ or shared/ without telling me first.

Start with the Window 1 tasks in my file's timeline table: project navigation skeleton with
placeholder/fake data, so Member 1 and Member 2 can integrate against real screens early.
```

---

## 8. First hour checklist (all three, in parallel, right after cloning)

- [ ] Repo cloned, project opens and builds with no errors
- [ ] `shared/Models.kt` and `Contracts.kt` present and match this file exactly
- [ ] Each person's AI agent has been given their kickoff prompt above
- [ ] Model files (Gemma 3n + Gemma 3 1B) downloading in parallel on all 3 laptops (Member 2 leads this, but everyone can help by downloading in the background)
- [ ] OpenRouter API key added to `local.properties`, confirmed **not** showing up in `git status`
