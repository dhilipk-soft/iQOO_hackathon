# Member 3 — Experience & Delivery (Output, Demo, Pitch, Compliance)

You own everything a judge actually sees and hears: the voice output, the UI for the whole study flow, the Focus Insights tab, the demo, the pitch, and keeping the team inside the hackathon's rules. You're also the QA safety net across both other members' work. Read `implementation-plan.md` first for full context and the judging-weight breakdown the pitch is built around.

## What you're building

1. **Text-to-speech playback** of explanations (Android `TextToSpeech`, on-device).
2. **Compose UI** for the primary study flow: capture → explanation (with speak button) → ask a follow-up (typed, voice if Member 1 ships it) → quiz → revision list of wrong topics.
3. **Focus Insights tab** — clearly secondary in the navigation and visual hierarchy, showing the evidence + narrative from Member 2.
4. Demo video + pitch deck.
5. Ongoing QA across the pipeline, and rules-compliance tracking.

## Screens (Jetpack Compose)

- **Capture screen:** camera viewfinder + shutter button (Member 1 provides the capture use case).
- **Explanation screen:** shows the simple explanation text, a "speak" button (or auto-plays via TTS), and a text field for a follow-up question with an "ask" button. If voice input ships, add a mic button next to the text field — never replace the text field.
- **Online/Offline badge (NEW — today's #1 priority, see `implementation-plan.md` §3a):** Member 2 gives you **one** final explanation (`ExplanationResult.finalExplanation`), plus a `usedOnlineContext: Boolean` flag — there is only one answer to display, not two blocks. Show a small badge above/below it: **"📴 Offline answer"** if `usedOnlineContext == false`, or **"📡 Enhanced with live info"** if `true`. This is your best live-demo moment: toggle airplane mode on stage, ask the same question, and show the badge (and the answer's content) change in real time.
- **Quiz screen:** shows 1–3 generated questions (MCQ or short-answer), captures the answer, shows correct/incorrect, and adds wrong topics to a simple **Revision list** screen.
- **Focus Insights tab (secondary):** a small, separate tab/section — 1–2 cards showing the evidence + on-device narrative from Member 2 (e.g., "You stayed on this problem for 6 minutes before switching apps — sessions without a switch finish about 2x faster"). Keep this visually secondary to the main study flow — a smaller section or a second tab, not the home screen.
- **Device Vitals strip (NEW, see `implementation-plan.md` §3b):** a small, always-visible bar (e.g. on the explanation screen) showing live numbers from Member 1's `DeviceVitalsMonitor` and Member 2's `InferenceStats` — RAM in use, thermal status, tokens/sec of the last answer. Doesn't need to be pretty, needs to be **clearly visible and readable during the demo** — this is your concrete proof that the AI is really running on the phone's own chip, which directly backs up the Technical Depth and Creative Phone Use scoring criteria.
- **Privacy note:** somewhere visible — "No account. No cloud. Every explanation, answer, and quiz is generated on this phone." This is a real trust/accessibility point (ties to the offline-first pitch) worth surfacing, not burying in settings.

Keep visual design minimal and consistent — one accent color, clean spacing, readable type. No time for a design system.

### Text-to-speech
```kotlin
val tts = TextToSpeech(context) { status ->
    if (status == TextToSpeech.SUCCESS) tts.language = Locale.getDefault()
}
tts.speak(explanationText, TextToSpeech.QUEUE_FLUSH, null, null)
```
Fully on-device once voice data is present (usually pre-installed on Android) — low risk, verify once on the actual iQOO 15 and move on.

## Timeline

| Window | Time | Task |
|---|---|---|
| 1 | 11:00–14:00 🟢 | Compose project scaffold, navigation (Capture → Explanation → Quiz → Revision, plus a separate Focus tab), placeholder UI with fake data so the other two can integrate against real UI early |
| 2 | 14:00–15:30 🔴 | No laptop: sketch the demo script and pitch deck outline (Problem → Solution → Live demo → Focus Insights bonus → Tech depth → Ask); write the privacy-note copy |
| 3 | 15:30–16:30 🟢 | Wire the explanation screen to Member 2's real `ExplanationResult` (one final answer + `usedOnlineContext` flag); build the online/offline badge; TTS speak button working |
| 4 | 16:30–19:00 🔴 | On-device: use the app as a real student would — photograph real pages, ask real follow-ups, note every rough edge/crash/confusing screen; log bugs for the other two rather than fixing code without a laptop |
| 5 | 19:00–22:00 🟢 | Wire quiz screen + revision list to real data; start the Focus Insights tab against real `FocusInsight` data |
| 6 | 22:00–01:00 🔴 | Full on-device QA pass: fresh install → capture → explain → ask → quiz → focus tab, in that order, no crashes; start demo video shot list |
| 7 | 01:00–06:30 🟢 | Polish UI, fix bugs found in QA, record the demo video on the iQOO 15 itself, finish the pitch deck (a mobile slides app is fine — avoid heavy laptop editing) |
| 8 | 06:30–09:00 🔴 | Final rehearsal of the live pitch on the actual device; confirm repo is pushed and Reskilll submission is complete before the cutoff |

## Rules-compliance checklist (your explicit responsibility)

- [ ] Nobody touches or works around HackTracker; genuine lockouts get reported to an organizer immediately, not debugged solo.
- [ ] Office Kit usage during Red Light stays purposeful and minimal (10% of the score; the format favors "highest on-device builds").
- [ ] No shipped code path depends on internet access (model downloads are a one-time setup step, not a runtime dependency of the demo).
- [ ] README credits MediaPipe/Gemma/ML Kit and any other OSS used.
- [ ] Repo pushed at the end of every Green Light window so you're never one bad Red Light away from losing everything.
- [ ] Confirm the exact submission hard-cutoff time with organizers as soon as it's announced.
- [ ] App doesn't crash on: permission denial, empty state (fresh install, no captures yet), LLM inference failure (Member 2's fallback should handle this — verify it in the actual UI, not just in isolation), camera permission denial.

## Pitch structure (map directly to judging weights — see plan §13)

1. **Hook (10s):** "Every AI tutoring app assumes you have internet. Most students in tier-2 and tier-3 towns don't. StudyLens works either way."
2. **Live demo — the hybrid mode, this is the centerpiece (40–50s):** put the phone in airplane mode, photograph a real textbook page, show it explained aloud with the "📴 Offline" badge — proving it works with zero connectivity. Then turn network back on and ask the same/a follow-up question — show the "📡 Enhanced with live info" badge with a noticeably richer answer. This one toggle *is* the demo: same pipeline, same local model, one answer either way.
3. **Novelty (15s):** name the gap directly — this is offline-first by design, not offline as an afterthought; and it also explains *why* a study session broke down, which generic screen-time tools don't do.
4. **Live demo — Focus Insights, "one more thing" (10-15s):** show one evidence-backed focus card, briefly.
5. **Tech depth (10s):** one glance at the pipeline diagram — capture → on-device read → on-device LLM (explain/answer/quiz/focus) → on-device speech — "zero network calls, the whole time." Point at the Device Vitals strip: "here's the RAM and thermal load right now, live, on this phone's own chip."
6. **Close (10s):** the ask/next step.

Rehearse on the actual iQOO 15, not a laptop screen. Check battery and, if you plan to demo in airplane mode, verify every feature you're about to show actually works with no connection *before* you're on stage.
