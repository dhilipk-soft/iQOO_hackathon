# Focus Insights + AI Focus Guard
## Final Hackathon Feature Specification

> **Product vision:** Build an on-device AI study companion that does not simply block distractions. It understands **why a student wants to leave**, distinguishes legitimate study needs, planned breaks, and emergencies, tracks what happens after the interruption, restores the student's learning context when they return, and continuously improves its coaching recommendations.

---

# 1. Executive Summary

The existing StudyLens product already has the foundation for Focus Insights:

- Real Android app-usage signals
- Real notification events
- Study-session calculation
- Room persistence
- Existing on-device Gemma LLM
- Existing Focus screen

The final feature extends this passive analytics capability into an **active AI Focus Guard + Focus Recovery System**.

The core loop is:

> **Study → Understand Why → Respect the Choice → Track Behavior → Recover Context → AI Coaching → Better Next Study Session**

The system should not treat every app switch as a distraction.

When the student intends to switch away from the study session, the system determines the reason and provides an appropriate path:

- **Study Need** → allow a legitimate study-related action.
- **Break** → allow the break and optionally schedule a return reminder.
- **Emergency** → immediately allow the user to leave Focus Mode.
- **Potential Distraction** → use a lightweight contextual focus check before allowing the interruption.

The most important principle is:

> **The system should understand the interruption rather than blindly block it.**

---

# 2. Hackathon Problem Statement

Students frequently lose focus because an intentional short interruption becomes an unplanned long distraction.

Existing focus tools commonly focus on blocking or limiting applications. StudyLens takes a different approach:

> **Understand the user's intent, measure the outcome, and help the student recover.**

The system should answer:

- Why did the student leave the study session?
- Was the app switch actually related to studying?
- Did the student pass the focus check?
- Did the student intentionally take a break?
- Was the interruption an emergency?
- How long was the intended break?
- How long was the actual break?
- Did the student return after the reminder?
- Which apps or notifications are associated with interruptions?
- Does the student repeatedly enter distraction chains?
- Does distraction happen more often after difficult study content?
- Can the AI restore the study context when the student returns?
- What should the student change in the next session?

---

# 3. Core Product Flow

```text
                         STUDY
                           │
                           ▼
                    INTENT TO SWITCH
                           │
                           ▼
                   🧠 UNDERSTAND WHY
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
          STUDY NEED     BREAK       EMERGENCY
              │            │            │
              ▼            ▼            ▼
           ALLOW       REMINDER       ALLOW
              │            │
              │       ┌────┴────┐
              │       ▼         ▼
              │    RETURN      MISS
              │       │         │
              └───────┼─────────┘
                      ▼
                LEARN BEHAVIOR
                      │
                      ▼
               RECOVER CONTEXT
                      │
                      ▼
                 AI COACHING
                      │
                      ▼
              BETTER NEXT
              STUDY SESSION
```

For potential distraction, the **AI Focus Guard** adds an additional verification step:

```text
User studies
    ↓
Intent to switch
    ↓
Understand why
    ↓
Potential distraction?
    ↓
Quick Focus Check
    ↓
Correct / Incorrect
    ↓
Allow / Stay & Review
    ↓
Offer break reminder
    ↓
Track return or missed return
    ↓
Analyze behavior
    ↓
Recover study context
    ↓
AI coaching
```

---

# 4. Product Principles

## 4.1 Do not blindly block

The application should not assume:

> App switch = distraction.

A student may be switching to:

- Search for a study-related concept
- Open a reference
- Check an important message
- Answer an emergency call
- Take a planned break

The system should understand context before labeling an interruption.

---

## 4.2 User remains in control

The user must always have a legitimate way to:

- Take a break
- Override the Focus Guard
- Turn off Focus Mode
- Handle an emergency

The product should support focus without trapping the user.

---

## 4.3 Track intention and outcome separately

This is one of the most important concepts in the feature.

Example:

```text
User intention:
"I'll take a 1-minute break."

Actual behavior:
Returned after 4 minutes 12 seconds.
```

The system should record both.

This allows the AI to understand:

> **Intention → Actual Outcome**

rather than simply counting app switches.

---

## 4.4 Emergency is not distraction

An emergency exit must be classified separately.

For example:

```text
5 interruptions

2  Focus checks passed
1  Planned break
1  Normal override
1  Emergency call
```

The emergency event should not negatively distort the student's distraction statistics.

---

## 4.5 AI recommendations must be evidence-based

The AI should only use information supplied by the collected signals.

It must:

- Not invent statistics
- Not invent study behavior
- Not shame the student
- Distinguish emergency events
- Distinguish planned breaks
- Clearly separate observations from recommendations

---

# 5. Existing Components to Reuse

The original feature already contains most of the required infrastructure.

## 5.1 UsageCollector

`input/focus/UsageCollector.kt`

Already handles:

- Usage Access permission
- Android `UsageStatsManager` events
- App event persistence
- Study-session construction
- Duration calculation
- Switch-count calculation
- Room persistence

Reuse the existing implementation.

Do not rebuild it.

---

## 5.2 NotificationCollector

`input/focus/NotificationCollector.kt`

Already handles:

- Notification Access permission
- Notification event retrieval
- Time-range notification queries

Reuse it for Focus Guard and final analytics.

---

## 5.3 StudyLensNotificationListenerService

`input/focus/StudyLensNotificationListenerService.kt`

Already listens for notifications and persists them to Room while excluding StudyLens's own notifications.

Reuse it.

---

## 5.4 AppDatabase / focusSignalsDao

`input/data/AppDatabase.kt`

Existing operations include:

```text
insertAppEvent
getAppEvents
insertNotificationEvent
getNotificationEvents
insertStudySession
getRecentStudySessions(limit)
```

Extend only where necessary for Focus Guard-specific events.

Do not create a second Room database.

---

## 5.5 Existing Gemma LLM

`ai/LlmEngine.kt`

Reuse the existing on-device Gemma model.

```kotlin
llmEngine.generateResponse(prompt)
```

Do not create a second LLM engine.

Potential uses:

- Contextual focus questions
- Focus explanations
- Break analysis
- Study-context recovery
- Personalized recommendations

---

## 5.6 Existing FocusScreen

`ui/focus/FocusScreen.kt`

The current UI already contains:

- Focus streak card
- Notification card
- Privacy card

Extend it to show:

- Focus Guard results
- Interruption timeline
- Break/return summary
- Focus metrics
- AI insight
- Study-context recovery
- Personalized recommendation

---

# 6. Permission Experience

The feature needs:

### Usage Access

Used for:

- App-switch detection
- App usage analysis
- Study-session behavior

### Notification Access

Used for:

- Notification interruptions
- Notification frequency
- Notification-related focus patterns

If permissions are missing, show:

```text
🔒 Enable Focus Insights

StudyLens uses phone-usage and notification
signals to understand interruptions during
your study session.

Your focus analysis stays on-device.

[Enable Usage Access]

[Enable Notification Access]
```

Never show fake focus statistics when permissions are unavailable.

---

# 7. AI Focus Guard

## 7.1 Trigger

During an active study session:

```text
StudyLens active
      ↓
User attempts to switch app
      ↓
Focus Guard flow
```

The guard should be lightweight.

---

# 8. Understand Why the User Is Switching

The system should first determine the user's intent.

Example:

```text
┌──────────────────────────────────────┐
│       🧠 Why are you switching?      │
│                                      │
│ ○ I need this for my study           │
│ ○ I need a short break               │
│ ○ I received something important     │
│ ○ I need to make an urgent call     │
│ ○ I'm getting distracted             │
└──────────────────────────────────────┘
```

This can be implemented as a quick interaction or inferred from supported signals where appropriate.

The goal is to classify the interruption before applying an intervention.

---

# 9. Study-Related App Switch

Not every app is a distraction.

Example:

```text
StudyLens
   ↓
Chrome
   ↓
"Newton's Second Law"
   ↓
StudyLens
```

This can be considered a potential **productive switch**.

The system should allow legitimate study-related actions rather than unnecessarily blocking them.

Where technically feasible, the system can use:

- Current study topic
- User-selected intent
- App sequence
- Return behavior
- Session context

to improve classification.

The system should avoid claiming that an app is productive solely because of its package name.

---

# 10. Quick Focus Check

For a potential distraction, present a short question.

Example:

```text
┌────────────────────────────────────────┐
│          🧠 QUICK FOCUS CHECK           │
│                                        │
│ You were studying Newton's Laws.       │
│                                        │
│ What is Newton's Second Law?            │
│                                        │
│ ○ F = ma                               │
│ ○ E = mc²                              │
│ ○ V = IR                               │
│                                        │
│ [Answer]                               │
│                                        │
│ Need a break?                          │
│ [Take a Break]                         │
└────────────────────────────────────────┘
```

The question should preferably be generated from the current study context.

If sufficient study context is unavailable, use a simpler supported fallback rather than inventing subject-specific content.

---

# 11. Correct Answer → Allow + Ask for Reminder

This is a critical product rule.

> **A correct answer does not end the interruption lifecycle.**

A correct answer means the student demonstrated recall and can be allowed to switch.

However, the system should immediately offer a break reminder.

Example:

```text
✓ Correct!

You can take a short break.

When should I remind you to return?

[10 seconds]
[1 minute]
[5 minutes]
[Until I return]
[Skip reminder]
```

The flow becomes:

```text
Quiz
 ↓
Correct
 ↓
Allow switch
 ↓
Ask break/reminder duration
 ↓
Break starts
 ↓
Reminder
 ↓
Track return
```

This allows the system to measure both:

1. **Cognitive engagement**
2. **Behavioral follow-through**

---

# 12. Incorrect Answer → Stay / Review / Override

If the answer is incorrect:

```text
Not quite.

Would you like to stay for a quick review?

[Stay & Review]
[Take a Break]
[Switch Anyway]
```

The system should encourage staying but must not trap the user.

Track:

```text
QUIZ_FAILED
STAY_AFTER_QUIZ
```

or:

```text
QUIZ_FAILED
QUIZ_OVERRIDE
```

---

# 13. Planned Break

The user can intentionally choose a break.

Example:

```text
Take a break?

[10 seconds]
[1 minute]
[5 minutes]
[Until I return]
```

Record:

```text
BREAK_STARTED
REMINDER_SCHEDULED
```

The selected duration represents the user's **planned break**.

---

# 14. Break Reminder

When the planned break ends:

```text
🔔 Focus reminder

Your break is over.

Ready to get back to your study session?

[Return to Study]
```

Record:

```text
REMINDER_TRIGGERED
```

Then monitor the next relevant app/session event.

---

# 15. Return Tracking

## Successful return

If the user returns:

```text
RETURNED_TO_STUDY
BREAK_COMPLETED
```

Calculate:

```text
Planned break: 1 minute
Actual break: 1 minute 18 seconds
Return status: Returned
```

---

## Missed return

If the user does not return within the defined return window:

```text
REMINDER_MISSED
NO_RETURN_DETECTED
```

Example:

```text
Planned break: 1 minute
Reminder: sent
Actual time away: 6 minutes 42 seconds
Return status: Missed / delayed
```

This must appear in a dedicated section of the final report.

---

# 16. Emergency Exit

Emergency situations must bypass the quiz and normal break flow.

Example:

```text
🚨 Need to leave urgently?

You can turn off Focus Mode immediately.

Your choice will be respected.
The interruption will still be recorded
as an emergency event.

[TURN OFF FOCUS MODE]
```

Requirements:

1. Immediately allow the user to leave.
2. Never force a quiz.
3. Never force a delay.
4. Record the event as `EMERGENCY_EXIT`.
5. Continue usage tracking where technically permitted.
6. Classify it separately from normal distractions.

Example:

```text
Emergency call
     ↓
Focus Mode OFF
     ↓
User handles call
     ↓
Track interruption
     ↓
User returns
     ↓
Continue study
```

---

# 17. Normal Override

The user should also be able to override Focus Guard without claiming an emergency.

Example:

```text
I need to switch apps.

[Override Focus Guard]
```

Then:

```text
How long do you need?

[10 seconds]
[1 minute]
[5 minutes]
[Until I return]
```

This is an intentional interruption.

Track:

```text
NORMAL_OVERRIDE
BREAK_STARTED
REMINDER_SCHEDULED
```

The user remains in control.

---

# 18. Break Promise: Planned vs Actual

One of the strongest analytics concepts is comparing the user's intended break with the actual outcome.

Example:

```text
BREAK PLAN

Planned: 30 seconds
Actual: 4 minutes 37 seconds
Difference: +4 minutes 07 seconds
```

The system should not judge the student.

Instead:

> "You planned a 30-second break and returned after 4 minutes 37 seconds."

After multiple sessions, the AI can identify patterns.

Example:

> "Your planned short breaks often extend beyond their intended duration. A slightly longer planned break with a clear return reminder may work better for you."

Only make this recommendation when historical evidence supports it.

---

# 19. Distraction Chain Detection

A single app switch may not be a problem.

A chain may be.

Example:

```text
StudyLens
   ↓
WhatsApp
   ↓
Instagram
   ↓
YouTube
   ↓
Chrome
   ↓
Instagram
```

Detect repeated switching within a short period.

Example:

```text
⚠️ Distraction chain detected

You opened 4 different apps during the
last 2 minutes.

[Return to Study]
[Continue Break]
```

Track:

```text
DISTRACTION_CHAIN_STARTED
DISTRACTION_CHAIN_APP
DISTRACTION_CHAIN_ENDED
```

This can become an important behavioral signal.

---

# 20. Focus Recovery

When the user returns to StudyLens, don't simply say:

> "Welcome back."

Restore the learning context.

Example:

```text
👋 Welcome back.

You were studying Newton's Second Law.

You were away for 3 minutes 42 seconds.

Want a 10-second recap before continuing?

[Quick Recap]
[Continue]
```

Gemma can generate the recap from the existing study context.

Example:

> "We were discussing F = ma: force equals mass multiplied by acceleration."

This feature connects **focus management directly to learning continuity**.

---

# 21. Usage Pattern Analysis

Analyze:

### Study

- Total study duration
- Focused duration
- Longest uninterrupted period
- Number of sessions

### App switching

- Total switches
- Interruptions per session
- Repeated switches
- Apps involved
- Time away from StudyLens

### Notifications

- Notification count
- Notification-heavy periods
- Notification followed by app switch

### Focus Guard

- Questions presented
- Questions passed
- Questions failed
- Stay/review choices
- Normal overrides
- Emergency exits

### Breaks

- Planned breaks
- Planned duration
- Actual duration
- Reminders scheduled
- Reminders triggered
- Returned after reminder
- Missed reminders
- Longest delayed return

### Recovery

- Return rate
- Time to return
- Context recap usage
- Repeated interruption patterns

---

# 22. Interruption Event Model

Recommended event types:

```text
APP_SWITCH

QUIZ_PRESENTED
QUIZ_PASSED
QUIZ_FAILED
STAY_AFTER_QUIZ
QUIZ_OVERRIDE

STUDY_RELATED_SWITCH

NORMAL_OVERRIDE

EMERGENCY_EXIT

BREAK_STARTED
REMINDER_SCHEDULED
REMINDER_TRIGGERED
REMINDER_MISSED

RETURNED_TO_STUDY
BREAK_COMPLETED
BREAK_EXPIRED
NO_RETURN_DETECTED

DISTRACTION_CHAIN_STARTED
DISTRACTION_CHAIN_APP
DISTRACTION_CHAIN_ENDED

NOTIFICATION_INTERRUPTION

CONTEXT_RECAP_SHOWN
CONTEXT_RECAP_USED
```

Use only events that are practical to implement and persist.

---

# 23. Focus Report

The final report should tell the story of the session rather than only display numbers.

## Overview

```text
━━━━━━━━━━━━━━━━━━━━━━━━━━
       🎯 FOCUS REPORT
━━━━━━━━━━━━━━━━━━━━━━━━━━

Study Time              52 min
Focused Time            41 min
Longest Focus           14 min

App Switches             6
Notifications            4
━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Focus Guard

```text
🧠 FOCUS GUARD

Checks presented          3
✓ Passed                  2
✕ Failed                  1

Normal overrides          2
Emergency exits           1
```

## Break & Return

```text
⏰ BREAK & RETURN

Planned breaks            3
Reminders sent            3
Returned after reminder   2
Missed reminders          1

Average planned break    1m 10s
Average actual break     3m 42s

Longest delayed return   6m 42s
```

## Interruption Timeline

```text
10:00  Study started
10:14  App switch → Focus check
10:14  Quiz passed
10:15  30-sec break started
10:16  Returned
10:28  Notification
10:29  Normal override
10:35  Reminder
10:38  Returned
10:45  Emergency call
10:48  Returned
```

---

# 24. AI Focus Insight

The evidence layer should be created first.

Example:

```text
Study duration: 52 minutes
Focused duration: 41 minutes
App switches: 6
Notifications: 4

Focus checks: 3
Passed: 2
Failed: 1

Normal overrides: 2
Emergency exits: 1

Planned breaks: 3
Returned after reminder: 2
Missed reminders: 1

Longest uninterrupted period: 14 minutes
Longest delayed return: 6 minutes 42 seconds
```

Then provide the evidence to Gemma.

Example output:

> "Your longest focused periods occurred when interruptions were limited. Most planned breaks ended successfully, while one break continued considerably longer than intended."

Recommendation:

> "Try a shorter, clearly timed break after your next long focus period and return as soon as the reminder appears."

Emergency events should be explicitly excluded from negative distraction interpretation.

---

# 25. Study Difficulty → Distraction Correlation

This is an advanced and highly valuable feature for an AI study product.

If enough study-context data exists, compare:

```text
Easy content
     ↓
Low interruption frequency

Difficult content
     ↓
Repeated app switching
```

Potential insight:

> "Your app switching appears more frequent after difficult study questions."

Then offer a learning-oriented intervention:

> "Instead of taking a phone break, would you like me to explain this concept in a simpler way?"

This transforms Focus Guard from a blocker into a **learning assistant**.

This should be considered an advanced feature and only implemented if the required study-context signals are available.

---

# 26. Adaptive Intervention

The same intervention should not appear every time.

Example:

### First interruption

```text
🧠 Quick focus check?
```

### Second interruption

```text
You've switched apps again.

Want to take a planned 1-minute break?
```

### Repeated interruption

```text
You've had several interruptions recently.

Would you like help with the current topic?
```

The system can gradually adapt intervention intensity based on observed behavior.

The user must always retain override/emergency controls.

---

# 27. Focus Recovery Score

Instead of focusing only on a generic "Focus Score," measure the ability to recover after interruption.

Example:

```text
FOCUS RECOVERY

Interruption 1 → returned in 12 sec
Interruption 2 → returned in 31 sec
Interruption 3 → returned in 2m 14s

Recovery trend:
Slower
```

Potential insight:

> "You're taking longer to return after each interruption."

This is more actionable than simply saying:

> "Focus score: 72%."

---

# 28. Intention vs Outcome Analytics

Track:

```text
USER INTENTION
      ↓
Planned 30-second break
      ↓
ACTUAL OUTCOME
      ↓
Returned after 4m 37s
```

Over several sessions:

```text
Session 1 → planned 30 sec → actual 1m 20s
Session 2 → planned 1 min  → actual 3m 10s
Session 3 → planned 1 min  → actual 4m 05s
```

The AI can then identify:

> "Your short planned breaks frequently become longer than intended."

And suggest:

> "Would you like to default to a 3-minute break with a return reminder?"

This creates a genuinely personalized focus loop.

---

# 29. Privacy Architecture

The feature should preserve the existing on-device architecture.

```text
Phone Usage
     │
     ▼
UsageCollector
     │
     ▼
Room
     │
     ├──────────────┐
     ▼              ▼
StudySession    Focus Events
     │              │
     └──────┬───────┘
            ▼
      Local Analysis
            │
            ▼
       Local Gemma
            │
            ▼
      Focus Insight
```

The core behavioral analysis should remain local where supported by the existing architecture.

The UI should clearly communicate the privacy model.

---

# 30. Recommended Data Flow

```text
Android Usage Signals
        │
        ▼
UsageCollector
        │
        ├───────────────┐
        │               │
        ▼               ▼
   App Events      Study Session
        │               │
        └───────┬───────┘
                │
Notification Signals
        │
        ▼
NotificationCollector
        │
        ▼
Notification Events
        │
        └──────────────┐
                       ▼
                Focus Guard
                       │
       ┌───────────────┼────────────────┐
       ▼               ▼                ▼
   Study Need        Break          Emergency
       │               │                │
       ▼               ▼                ▼
     Allow         Reminder          Allow
                       │
                 ┌─────┴─────┐
                 ▼           ▼
              Return       Miss
                 │           │
                 └─────┬─────┘
                       ▼
                Event Database
                       │
                       ▼
               Pattern Analysis
                       │
          ┌────────────┼─────────────┐
          ▼            ▼             ▼
      Focus Data    Break Data   App Data
          │            │             │
          └────────────┼─────────────┘
                       ▼
                  Evidence
                       │
                       ▼
                  Local Gemma
                       │
                       ▼
                AI Focus Insight
                       │
             ┌─────────┴─────────┐
             ▼                   ▼
       Focus Report        Context Recovery
```

---

# 31. Hackathon MVP

## P0 — Must Have

1. Permission UI
2. Real usage data
3. Real notification data
4. Real study session
5. App-switch detection
6. Focus Guard
7. Quick contextual quiz
8. Correct → allow
9. Correct → offer break reminder
10. Incorrect → Stay/Review
11. Normal Override
12. Emergency Exit
13. Break timer/reminder
14. Return tracking
15. Missed-return tracking
16. Interruption event tracking
17. Break & Return Summary
18. Focus Report
19. Gemma-generated Focus Insight
20. Personalized recommendation

---

# 32. P1 — Strong Additions

1. Study-related switch detection
2. Distraction-chain detection
3. Interruption timeline
4. Focus recovery metrics
5. Actual-vs-planned break analytics
6. Study-context recovery
7. Quick recap after returning
8. Most distracting app analysis
9. Historical comparison

---

# 33. P2 — Future Vision

1. Predictive distraction detection
2. Difficulty → distraction correlation
3. Adaptive intervention intensity
4. Personalized break duration
5. Personalized focus-session duration
6. Weekly focus coaching
7. Long-term behavioral trends
8. Adaptive quiz difficulty
9. Learning-aware distraction prediction
10. Personalized recovery strategy

---

# 34. Recommended Implementation Order

## Phase 1 — Validate existing infrastructure

Confirm:

- Usage Access works.
- Notification Access works.
- App events reach Room.
- Notification events reach Room.
- `buildStudySession()` produces real values.
- Existing Gemma responds reliably.

---

## Phase 2 — Replace fake data

Replace hardcoded `StudySession` and `FocusInsight` values in `StudyViewModel`.

Use the existing:

```kotlin
val notifications =
    notificationCollector.getNotificationEvents(startTime, endTime)

val session =
    usageCollector.buildStudySession(
        startTime,
        endTime,
        notifications
    )
```

Get real data flowing to the UI before building advanced AI behavior.

---

## Phase 3 — Build Focus Guard

Implement:

```text
Active study session
       ↓
Switch detected
       ↓
Understand intent
       ↓
Study need / Break / Emergency / Potential distraction
```

---

## Phase 4 — Build Focus Check

Implement:

```text
Potential distraction
       ↓
Question
       ↓
Correct / Incorrect
       ↓
Allow / Stay & Review
```

For a correct answer:

```text
Correct
 ↓
Allow
 ↓
Ask reminder duration
```

---

## Phase 5 — Build Break & Reminder

Implement:

```text
Break selected
     ↓
Reminder scheduled
     ↓
Reminder triggered
     ↓
Returned / Missed
```

Persist the complete lifecycle.

---

## Phase 6 — Build Emergency Exit

Implement:

```text
Emergency
    ↓
Immediate Focus Mode OFF
    ↓
Track event
    ↓
Continue usage monitoring where supported
```

---

## Phase 7 — Build Behavioral Analytics

Calculate:

- Interruption count
- Break count
- Planned vs actual duration
- Return rate
- Missed reminders
- Emergency exits
- Repeated switching
- Distraction chains

---

## Phase 8 — Build Context Recovery

When the student returns:

```text
Return detected
     ↓
Recover current study context
     ↓
Offer 10-second recap
     ↓
Continue learning
```

---

## Phase 9 — Build Gemma Narration

Create evidence first.

Then send only grounded evidence to the existing LLM.

Generate:

1. What happened.
2. What pattern was observed.
3. One practical recommendation.

---

## Phase 10 — Final Demo UI

Show:

- Focus Overview
- Focus Guard history
- Interruption timeline
- Break & Return Summary
- Emergency events
- AI Focus Insight
- Personalized recommendation
- Privacy explanation

---

# 35. Hackathon Demo Script

The ideal demo should show the complete lifecycle.

### Step 1 — Start studying

```text
📚 Physics
Focus session started
```

### Step 2 — User tries to leave

```text
🧠 Quick Focus Check

What is Newton's Second Law?
```

### Step 3 — User answers correctly

```text
✓ Correct!

You can take a short break.

When should I remind you to return?

[10 seconds]
[1 minute]
[5 minutes]
```

### Step 4 — User selects 10 seconds

```text
BREAK_STARTED
REMINDER_SCHEDULED
```

### Step 5 — User leaves

The user can use another app.

### Step 6 — Reminder fires

```text
🔔 Your break is over.

Ready to return to studying?
```

### Step 7 — Demonstrate return or missed return

If returned:

```text
✓ Returned after reminder
```

If not:

```text
⚠ Reminder missed
Return delayed
```

### Step 8 — Demonstrate emergency

```text
🚨 Emergency call

[Turn Off Focus Mode]
```

Focus Mode turns off immediately.

The event is tracked separately.

### Step 9 — Return to StudyLens

```text
Welcome back.

You were studying Newton's Second Law.

Want a quick recap?
```

### Step 10 — Show final report

```text
52 min study
41 min focused

6 interruptions
3 focus checks
2 passed
1 failed

3 planned breaks
2 successful returns
1 missed reminder

1 emergency exit
4 notifications
```

### Step 11 — AI explanation

```text
Your longest focus periods occurred during
uninterrupted study blocks. Most planned breaks
ended successfully, while one interruption
continued longer than intended.

Recommendation:
Try a clearly timed 1–2 minute break and
return when the reminder appears.
```

---

# 36. Judge-Facing Differentiator

Do not pitch this as:

> "We built an AI app blocker."

Pitch it as:

> **"We built an AI Focus Recovery System."**

Traditional focus approach:

```text
Distraction
    ↓
BLOCK
```

StudyLens:

```text
Distraction
    ↓
UNDERSTAND WHY
    ↓
DISTINGUISH INTENT
    ↓
ALLOW LEGITIMATE ACTION
    ↓
REMIND
    ↓
TRACK RETURN
    ↓
LEARN BEHAVIOR
    ↓
RECOVER STUDY CONTEXT
    ↓
AI COACHING
```

The key differentiator is not any single popup or timer.

It is the **closed-loop understanding of interruption intent and outcome**.

---

# 37. Strong Product Story

The complete story is:

### Before the interruption

Understand what the student is doing.

### At the interruption

Understand why they want to leave.

### During the break

Respect their decision and optionally provide a return reminder.

### After the reminder

Measure whether they actually return.

### After they return

Restore their study context.

### At the end

Explain what happened using evidence.

### Next session

Use the pattern to provide better coaching.

This creates:

> **Intent → Intervention → Outcome → Recovery → Learning**

---

# 38. Final Architecture

```text
                         ┌───────────────┐
                         │    STUDY      │
                         └───────┬───────┘
                                 │
                                 ▼
                       ┌───────────────────┐
                       │ INTENT TO SWITCH  │
                       └─────────┬─────────┘
                                 │
                                 ▼
                       ┌───────────────────┐
                       │ UNDERSTAND WHY    │
                       └─────────┬─────────┘
                                 │
                ┌────────────────┼────────────────┐
                ▼                ▼                ▼
          STUDY NEED          BREAK           EMERGENCY
                │                │                │
                ▼                ▼                ▼
             ALLOW          REMINDER           ALLOW
                                 │
                            ┌────┴────┐
                            ▼         ▼
                         RETURN     MISS
                            │         │
                            └────┬────┘
                                 │
                                 ▼
                        LEARN BEHAVIOR
                                 │
                                 ▼
                       RECOVER CONTEXT
                                 │
                                 ▼
                          AI COACHING
                                 │
                                 ▼
                       BETTER NEXT SESSION
```

Potential distraction path:

```text
Potential distraction
        ↓
Quick Focus Check
        ↓
 ┌──────┴──────┐
 ▼             ▼
Correct      Incorrect
 │             │
 ▼             ▼
Allow       Stay/Review
 │
 ▼
Offer reminder
 │
 ▼
Track return/miss
```

---

# 39. Final One-Line Hackathon Pitch

> **StudyLens is an on-device AI Focus Recovery System that understands why students leave their study session, verifies engagement when appropriate, respects planned breaks and emergencies, tracks whether they return, restores their learning context, and turns real behavioral signals into personalized coaching for the next study session.**

---

# 40. Core Design Principle

> **Detect the interruption.  
> Understand the intent.  
> Respect the user.  
> Offer a return reminder.  
> Track the outcome.  
> Recover the learning context.  
> Learn the behavior.  
> Coach the student.  
> Help them focus better next time.**
