# StudyLens — Implementation Status

Offline-first AI study companion: camera → read → explain → voice → quiz, for students without reliable internet. This document reflects what is **actually built and verified working on-device**, not the original plan.

## Core architecture

- **UI**: Jetpack Compose, single-activity, `NavGraph.kt` routing between Home/Chat, Quiz, Focus, Settings, Model Picker.
- **Local storage**: hand-rolled SQLite (`SQLiteOpenHelper`) in `AppDatabase.kt` — no Room. Room has no release compatible with the Kotlin 2.4 metadata `litertlm-android` requires, so persistence was rewritten around plain SQLite while keeping the same DAO class/method names.
- **On-device AI runtime**: `com.google.ai.edge.litertlm:litertlm-android:0.17.0` (LiteRT-LM), not the older MediaPipe `tasks-genai`. Required for multimodal (image) support and Kotlin 2.4 compatibility.
- **Build**: AGP 8.5.2, Kotlin 2.4.20, no KSP/kapt (nothing needs annotation processing anymore).

## On-device model engine (`LlmEngine.kt`)

- Wraps LiteRT-LM's `Engine` → `Conversation` (rich multi-turn/tool-calling API) with an automatic fallback to the lower-level `Session` API for models the `Conversation` API rejects (see Known limitations).
- **Backend**: CPU/XNNPACK only, for both text decode and vision encoding. GPU was tried and is deliberately disabled — see rationale below.
- Every model load and generation call is wrapped with real timeouts (via `async`/`await` racing, not naive `withTimeoutOrNull` around blocking calls) so a stuck native call can never hang the UI indefinitely.
- Camera/gallery images are downscaled (max 1024px edge) before being sent to the vision encoder.

### Why CPU, not GPU (confirmed via direct on-device testing, not assumption)

- **Text decode**: GPU (WebGPU) sampling requires `libLiteRtTopKWebGpuSampler.so`, which is not shipped in the `litertlm-android:0.17.0` AAR (confirmed by unzipping and inspecting it directly) — nor is its OpenCL fallback, and the final static fallback under that is itself broken on OpenCL-less devices. This is a deterministic failure on every generation call when the decode graph runs on GPU on this Adreno/WebGPU-only device.
- **Vision (image) encoding**: GPU vision execution intermittently hung/failed with `Failed to lock the tensor buffer... Timed out waiting for future: 10s` — a real GPU synchronization bug on this device/driver combination, not an image-size issue (downscaling did not fix it).
- Net result: everything runs on CPU/XNNPACK. Slower than GPU would be, but the only backend proven reliable for both text and vision on this hardware/library combination.

## Working, verified multimodal models

All three below were downloaded, switched to, and used to generate real (non-canned) text and image-based responses on a physical device this session:

| Model | Size | Notes |
|---|---|---|
| **Qwen2-VL 2B** | ~1.7 GB | Alibaba vision-language model, ungated |
| **Gemma 4 E2B** | ~2.5 GB | Google's newest on-device model (text+vision+audio), ungated |
| **FastVLM 0.5B** | ~1.1 GB | Apple's compact vision-language model, smallest/fastest, ungated |

All three are genuine `.litertlm`-format files from `litert-community`/Google on Hugging Face — no login required.

## In-app model picker (`ModelPickerScreen.kt`, `ModelDownloadManager.kt`)

- Browse a bundled catalog (`assets/models.json`), download over plain HTTPS.
- **Downloads run as a real background job** (`ModelDownloadWorker` via WorkManager with a foreground notification) — survives navigating away, backgrounding the app, or the screen turning off. This replaced an earlier version that reset to 0% on navigation because it ran on a screen-scoped coroutine.
- Cancel-in-progress support.
- Delete downloaded models to reclaim storage (these are multi-GB files).
- **Switching models actually verifies the model loads** before committing to it (`verifyActiveModelLoads()`), with automatic rollback to the previously-working model and a clear error message if it fails — instead of silently leaving the user on a broken model that fails on every future chat message. This survives navigating away mid-verification (`NonCancellable`).
- `knownIncompatible` flag on catalog entries: models proven unable to load are permanently disabled in the UI (not just hidden after one failed attempt), even if the file happens to already be on disk from earlier testing.
- `isMultimodal` flag drives whether the image-attach entry point is shown for the active model.

## Multimodal chat flow (`StudyChatScreen.kt`)

- Camera or gallery photo is **staged** (shown as a thumbnail chip above the input bar) rather than sent immediately — the user can type a question about it first, or just hit send.
- No OCR step: the photo goes directly to the model's vision encoder.
- Real ChatGPT-style session persistence (SQLite-backed), not seeded/demo data.
- Back button from an active chat returns to the empty home canvas rather than exiting the screen.

## Online enrichment / hybrid RAG (`RetrievalClient.kt`, `ExplainPipeline.kt`)

- When online, retrieval runs **before** the on-device model generates its answer, and the result is woven into the prompt so the final explanation is longer and more detailed (not just facts appended as a list).
- **Hybrid provider strategy**: tries Groq (`groq/compound-mini`, free-tier agentic web search) first; on any failure (timeout, error, empty result — Groq's compound models are independently confirmed unreliable on this account, including a reproducible 413 on trivial requests) it falls back to OpenRouter/Perplexity Sonar (paid, reliable, returns real citations).
- Citations from OpenRouter are extracted from `message.annotations[].url_citation` and appended as a source list; inline numbered citation markers (`[1][3]`) that Perplexity embeds in the text are stripped, since they don't correspond to anything the user can see once the local model has incorporated that context into its own answer.
- Structured diagnostic logging (`Log.i/w`, tag `RetrievalClient`) on every attempt, so retrieval failures are diagnosable from logcat instead of silently swallowed.

## Known limitations

- **Text-only models cannot be used at all.** `EngineConfig.visionBackend` is a mandatory, non-nullable field in this `litertlm-android` version (confirmed via bytecode inspection — no "none" option exists), and both the `Conversation` and `Session` APIs require the model to actually have a vision encoder section once any `visionBackend` is configured. Tested and confirmed failing on two independent text-only models via two independent API paths. Only genuinely multimodal `.litertlm` models work.
- **NPU acceleration is not available.** No Qualcomm QNN/QAIRT dispatch library or compiler plugin is bundled (would require the vendor SDK, a separate license, and an NPU-compiled model) — the app deliberately skips attempting NPU rather than paying a guaranteed-to-fail cold-start cost on every model load.
- **`.task`-format model files do not work with this engine at all**, regardless of whether the model is multimodal — confirmed via two independent real errors (`Unable to open zip archive` for one model, `TF_LITE_VISION_ENCODER not found` for another). Only `.litertlm`-format exports work. Gemma 3n E2B/E4B `.litertlm` exports do exist (`google/gemma-3n-E2B-it-litert-lm`, `-E4B-`) but are gated behind an HF login/license acceptance — would need a public re-host to use.
- Groq's `compound`/`compound-mini` web-search models are independently flaky on the project's account (confirmed via direct `curl` testing) — this is why the hybrid always has a paid fallback rather than relying on Groq alone.

## Recently fixed (this session)

- Retrofit body-type bug: Kotlin's `Map<K, V>` erases to a wildcard type at the JVM level (`Map` declares `out V`), which Retrofit's `@Body` validator rejects outright — every single OpenRouter/Groq call was failing instantly before this was found and fixed (switched to `HashMap`).
- A `coroutineScope`/`async` pitfall where a failing child coroutine crashed the app before `.await()` was ever reached, bypassing the surrounding `try/catch` — fixed by switching to `supervisorScope`.
- WorkManager's foreground service crashing on Android with `foregroundServiceType` mismatch — fixed via a manifest `<service>` override.
- Resolved a large merge conflict against a teammate's parallel branch (which had reverted to a hardcoded fake-response engine and the incompatible `.task` models) without losing any of the tested, working functionality above; also caught and fixed a dependency the raw merge had silently dropped (`litertlm-android`), which would have broken compilation.
