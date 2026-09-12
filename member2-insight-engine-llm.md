# Member 2 — On-Device Intelligence (the "Brain")

You own the on-device LLM that powers every AI feature in the app: explaining captured content, answering follow-up questions, generating and grading quizzes, and — as a secondary feature — narrating Focus Insights. You also own the single most important early decision in the whole build: **Plan A vs Plan B** (see `implementation-plan.md` §4). Read the full plan first.

## What you're building

0. **Hybrid offline/online RAG pipeline (NEW — today's #1 priority, see `implementation-plan.md` §3a).** This is a *retrieve-then-generate* pattern, not two separate answers:
   - **Retrieve (only if online):** ask OpenRouter's web-search model for a few short factual snippets about the topic — not a full explanation, just facts.
   - **Combine:** merge those snippets (if any) with the locally-extracted text into one prompt.
   - **Generate (always runs, this is the guarantee):** feed that combined prompt to the on-device Gemma model. It always produces the final answer — with or without online context, in one consistent voice. This is what makes it safe: even if retrieval fails or is slow, generation still happens locally and instantly.
   Build this before the quiz feature. **Note on scope:** don't build a custom web-browsing/scraping agent (e.g. LangGraph) for this — that would need its own server running throughout your demo, which is a real live-dependency risk and isn't needed. OpenRouter's `:online` models already do the "search the web for current info" step as a single HTTP call.
1. **The Plan A/B checkpoint call** — attempt a true on-device VLM (Gemma 3n, multimodal) first; if it's not reliably producing correct explanations from a real photographed page by the end of Window 1 (14:00), switch the team to Plan B (ML Kit OCR text → text-only Gemma 3 1B) without further debate. This is a scoped, time-boxed decision, not an open-ended research task.
2. **Explain prompt:** turn extracted content (image or OCR text) into a simple explanation.
3. **Follow-up answer prompt:** answer a student's typed (or voice, if Member 1 ships it) question about the material.
4. **Quiz generation + grading:** generate 1–3 short questions from the material; grade the student's answer.
5. **Focus Insights heuristics + narration (secondary feature):** simple rules over Member 1's `StudySession`/`NotificationEvent` data, narrated by the same on-device LLM.
6. **Resilience:** every LLM call needs a non-LLM fallback so the app never hangs or crashes.

## Data contract (see `implementation-plan.md` §7)

```kotlin
data class Explanation(val captureId: Long, val simpleExplanation: String, val topic: String)
data class QuizQuestion(val id: Long, val captureId: Long, val topic: String, val question: String, val options: List<String>?, val correctAnswer: String)
data class QuizAttempt(val questionId: Long, val userAnswer: String, val isCorrect: Boolean, val timestamp: Long)
data class FocusInsight(val type: String, val evidence: String, var narrative: String? = null)
```

## MediaPipe LLM Inference setup

```kotlin
// build.gradle
implementation("com.google.mediapipe:tasks-genai:<latest>")
```

```kotlin
val options = LlmInference.LlmInferenceOptions.builder()
    .setModelPath(modelFilePath)   // Gemma 3n (Plan A) or Gemma 3 1B-IT int4 (Plan B), pushed to app files dir
    .setMaxTokens(512)
    .setPreferredBackend(LlmInference.Backend.GPU)  // explicit hardware acceleration — see note below
    .build()
val llmInference = LlmInference.createFromOptions(context, options)
// Plan A: pass image + text prompt if the multimodal session API supports it
// Plan B: pass extracted text + instruction prompt
val response = llmInference.generateResponse(prompt)  // run off the main thread
```

**Set the backend explicitly (see `implementation-plan.md` §3b).** Don't leave this on whatever the default is — set it to GPU (or check your exact MediaPipe version's API for the equivalent flag) so inference actually runs on the Snapdragon's accelerated path. This is a genuine technical-depth point *and* the honest way to make sure the phone's chip is doing real work rather than falling back to a slow CPU path. Verify it's actually taking effect by watching the tokens/sec number below — a GPU/NPU path should be noticeably faster than plain CPU.

**Measure and expose `InferenceStats` around every call** (see plan §7) — this feeds Member 3's "Device Vitals" strip and is a strong, concrete demo proof-point ("this is really running on the phone, here are the numbers"):
```kotlin
val start = System.currentTimeMillis()
val response = llmInference.generateResponse(prompt)
val latencyMs = System.currentTimeMillis() - start
val tokensPerSecond = estimatedTokenCount(response) / (latencyMs / 1000.0)
val stats = InferenceStats(tokensPerSecond, latencyMs, deviceVitals.ramUsedMb, deviceVitals.thermalStatus)
```

**Get both model files staged in Window 1** (Gemma 3n for Plan A, Gemma 3 1B for Plan B/focus narration) — see `implementation-plan.md` §8. Don't wait to see which plan wins before downloading — download both in parallel, decide, then only rely on one.

### Prompt templates (keep them short — small models, don't overload the context)

**Explain:**
```
You are a patient tutor explaining to a student who has limited internet
access and needs the concept explained simply, in plain language, in 3-4
sentences. Do not assume the student has any external resources.

Content: {extractedText or image}
```

**Follow-up answer:**
```
The student is studying this content: {extractedText}
You already explained it as: {simpleExplanation}
Their follow-up question: {question}
Answer in 2-3 simple sentences, staying strictly within this material.
```

**Quiz generation:**
```
Based on this content, write {N} short quiz questions (mix of multiple-choice
and short-answer) that test understanding of the key idea. Return them as
plain numbered items with the answer marked clearly.

Content: {extractedText}
```
Parse the model's output into `QuizQuestion` rows — keep the output format simple and constrained (e.g. ask for a fixed delimiter) so parsing doesn't become its own project.

**Focus Insight narration:**
```
You are a calm, evidence-based study coach. Given this observation about a
student's study session, write exactly 2 sentences explaining what it means
for their focus, then 1 short actionable suggestion. Do not invent numbers
beyond what's given.

Observation: {evidence}
```

### The RAG pipeline (retrieve → combine → generate) — today's #1 priority

**Step 1 — Retrieve (only when `isOnline == true`).** Use OpenRouter's web-search-enabled models (append `:online` to a model slug, e.g. a Sonar/Perplexity-style model). Ask it explicitly for short **facts**, not a finished explanation — the local model does the actual explaining in step 3, so you want raw context here, not a competing answer:

```kotlin
suspend fun retrieveContext(topic: String): String? {
    if (!isOnline) return null
    return try {
        withTimeout(6000) {
            val response = openRouterClient.chatCompletion(
                model = "perplexity/sonar:online",   // or any :online-suffixed model available
                messages = listOf(
                    Message("system", "Return 2-3 short, current factual bullet points about this topic. No explanation, no greeting, just facts."),
                    Message("user", "Topic: $topic")
                )
            )
            response.text
        }
    } catch (e: Exception) {
        null   // retrieval failed or too slow — fine, generation still runs without it
    }
}
```

**Step 2 — Combine.** Build one prompt for the local model:
```kotlin
val prompt = buildString {
    append("You are a patient tutor explaining to a student with limited internet access. ")
    append("Explain simply, in plain language, in 3-4 sentences.\n\n")
    append("Content: $localText\n")
    if (retrievedContext != null) {
        append("\nAdditional current context you may use if relevant:\n$retrievedContext\n")
    }
}
```

**Step 3 — Generate (always runs, on-device, this is the guarantee).**
```kotlin
val finalExplanation = llmInference.generateResponse(prompt)   // Gemma, on-device, via MediaPipe
val result = ExplanationResult(captureId, finalExplanation, usedOnlineContext = retrievedContext != null)
```

This gives you `ExplanationResult(finalExplanation, usedOnlineContext)` (see plan §7) — **one** answer either way; `usedOnlineContext` just drives the small badge Member 3 shows.

### Optional Python workflow for designing these prompts

Since you know Python: prototype the retrieve/combine/generate wording in a quick Python script (calling OpenRouter's API directly with `requests`, or testing against any local model you have handy) before porting the final prompt strings into the Kotlin code above. Iterating on prompt wording in a Python REPL is faster than rebuilding the Android app each time — just remember the hand-off is **the prompt text itself**, not any Python code. If you're not yet comfortable writing the Kotlin for the MediaPipe/OpenRouter calls, pair with Member 1 or 3 to wire it up once the prompts are validated — the actual Kotlin here is short (the three snippets above are close to the whole thing).

**Security note — do not commit the OpenRouter API key to the repo.** Put it in `local.properties` (already gitignored by default in Android projects) and read it into `BuildConfig` via Gradle, e.g.:
```gradle
// app/build.gradle
buildConfigField "String", "OPENROUTER_API_KEY", "\"${project.findProperty("OPENROUTER_API_KEY") ?: ""}\""
```
Double-check `git status` before any push that the key isn't sitting in a committed file — a leaked key in a public hackathon repo is a real, avoidable problem.

### Focus Insights heuristics (secondary feature — keep this small, build after 19:00)
Compute 1–2 rules from Member 1's `StudySession`/`NotificationEvent` data, relative to the student's own sessions where possible:
- **Switch impact:** compare average session completion/duration for sessions with a mid-session app switch vs. those without. Evidence: "Sessions without a switch away finished about 2x faster."
- **Notification impact:** sessions with a notification arriving mid-session vs. without. Evidence: "3 of your last 5 study sessions were interrupted by a notification within the first 5 minutes."

That's enough for a "bonus feature" — do not build the full multi-rule pattern-mining system from the original research doc; this is not the main product anymore.

### Resilience — required, not optional
```kotlin
val text = try {
    withTimeout(4000) { llmInference.generateResponse(prompt) }
} catch (e: Exception) {
    templatedFallback(...)   // plain string, no LLM, for explain/answer/quiz/focus alike
}
```
Every one of the four LLM-backed features (explain, answer, quiz, focus narration) needs this. A frozen screen on stage is worse than a slightly plainer fallback sentence.

## Stretch, after the 7 PM checkpoint: a bigger model for a stronger technical-depth story

Once the core is stable, consider stepping up from Gemma 3 1B to a larger quantized variant (e.g. Gemma 3n E4B or Gemma 3 4B) — the iQOO 15's 16GB RAM can likely handle it, and a genuinely heavier on-device model is a stronger "creative phone use"/"technical depth" signal (see plan §3b). Use the exact same checkpoint discipline as the Plan A/B decision: try it, watch `InferenceStats` (latency, thermal status), and revert to the 1B model immediately if it lags, crashes, or the thermal status climbs toward SEVERE. A reliable smaller model beats an impressive-sounding one that breaks mid-demo.

## Timeline

| Window | Time | Task |
|---|---|---|
| 1 | 11:00–14:00 🟢 | **Start both model downloads immediately** (Gemma 3n + Gemma 3 1B); get the OpenRouter API key wired safely (see security note above); stand up the multimodal (Plan A) call against a real photographed page; **make the Plan A/B call by 14:00** and tell the team |
| 2 | 14:00–15:30 🔴 | No laptop: refine explain/answer/quiz/focus prompt wording on paper/phone; think through the quiz-output parsing format |
| 3 | 15:30–16:30 🟢 | Explain prompt working end-to-end on the chosen plan; wire the `isOnline` branch and the OpenRouter enrichment call; verify it degrades silently when offline or slow |
| 4 | 16:30–19:00 🔴 | On-device: test explain quality against real textbook pages the team photographs; note where explanations are wrong/too complex and adjust prompt wording mentally for the next Green window |
| 5 | 19:00–22:00 🟢 | Follow-up answer prompt + quiz generation/grading working; start Focus Insights heuristics + narration |
| 6 | 22:00–01:00 🔴 | On-device: full run-throughs of explain → ask → quiz; prompt tuning via short Office Kit bursts; watch for thermal throttling on sustained inference |
| 7 | 01:00–06:30 🟢 | Finish Focus Insights narration; add resilience/fallback everywhere if not already done; help Member 3 wire real data into the UI |
| 8 | 06:30–09:00 🔴 | Final on-device run-through for the pitch demo; freeze prompts |

## Acceptance criteria
- A real photographed textbook page produces a correct, simply-worded explanation, end-to-end, on-device.
- A follow-up question about that material gets a relevant, on-device-generated answer.
- At least one quiz question is generated and correctly graded against the student's answer.
- At least one Focus Insight produces a plausible, evidence-backed narrative from real session data.
- If any LLM call fails or times out, the app shows a fallback string instead of crashing or hanging — verified, not assumed.
- End-to-end latency per LLM call is demo-able (a few seconds) — measured early, not the night before the pitch.
