# 🚀 PSC Master: Project Evolution Summary
**Current Date**: March 28, 2026  
**Status**: Stable Phase 2  
**Database Schema Version**: **11**

## 1. Core Architecture & Tech Stack
*   **Framework**: Native Android (Kotlin) with **Jetpack Compose**.
*   **Architecture**: Clean Architecture (MVVM/MVI) using **Dagger Hilt** for Dependency Injection.
*   **Local Database**: **Room (SQLite)**.
*   **Cloud Backend**: **Firebase Auth** (Anonymous/Email) and **Firestore** (Shared question distribution and synchronization).
*   **AI Engine**: Integrates with Gemini/Groq via a custom `AiService` for Question Variation and Insights (manual trigger paradigm to save tokens).
*   **Concurrency**: Coroutines and Kotlin Flow (using `combine` and `WhileSubscribed` to manage background leaks).

## 2. Core Features & Capabilities
*   **Spaced Repetition System (SRS)**: The app intelligently tracks user attempts (`totalAttempts`, `correctAttempts`) continuously through the `UserPerformance` logging and `PerformanceMetrics` aggregation tables.
*   **Adaptive Quiz Engine**: Dynamically shifts between exposing users to their weakest subjects and injecting a ratio of purely new questions to prevent memory decay.
*   **Smart Revision Mode**: Enforces a purely retroactive study path utilizing `getQuestionsWithMistakes()` to target only known failed items.
*   **On-The-Fly Question Editing**: Allows users to actively alter questions, options, or spelling errors seamlessly during an active quiz session without exiting to the dashboard, by utilizing `OnTheFlyEditDialog` and live dataset remapping via `QuizViewModel.updateQuestion()`.
*   **Practice Setup**: A robust configuration panel allowing users to strictly toggle between Adaptive Mode vs. Revision Mode (which are now cleanly mutually exclusive), tag subjects, and shuffle items. Includes a smooth "FINISH SESSION" loop that safely resets the backstack locally to `resetToConfig()`.

## 3. Analytics & Gamification Engine
*   **🔥 Study Streak Engine**: Advanced local epoch timestamp tracking inside `PSCRepositoryImpl.calculateStudyStreak()`. It securely reads from `PerformanceDao.getAllTimestamps()` to construct consecutive study days while properly nullifying daylight savings errors or overlapping midnight epochs.
*   **Widgets**: Features a high-quality Compose "Day Streak!" layout on `AnalyticsScreen.kt`.
*   **Weekly & Subject Reports**: Uses SQLite aggregation to fetch `getWeeklyProgress()` and `getWeakSubjects()`.
*   **Automated Storage Cleanup**: Features a background `CleanupWorker` managed by WorkManager that strictly prunes `user_performance` individual log data trailing off after 30 days while leaving the permanent macro-aggregated metrics untouched.

## 4. Key Architectural Rescues & Optimizations Complete
*   **Database Flow Memory Leaks Patched**: Restructured the base `InputViewModel` (`HomeScreen` provider). It now maps massive `collectLatest` DB calls into a refined `kotlinx.coroutines.flow.combine(flow1, flow2...5)` array. This implements rigorous `stateIn(WhileSubscribed(5000))` scoping—completely silencing background database activity queries when Compose screens are unmounted, significantly multiplying device battery execution time.
*   **Security Context**: Secure API Keys (`AiService`, `Firestore`) are hidden securely within local properties and `BuildConfig` objects, out of version control.
*   **Synchronous State Handling**: Replaced generic error-prone boolean states for Quiz sessions gracefully.

## 5. Next Session Developer Guidelines
*   **Current Goal Pipeline**: Expanding further optimizations, maintaining UI/UX layout cleanly under Material 3 Jetpack Compose guidelines, and observing AI quota token usage restrictions where possible.
*   **Workflow Warning**: When adjusting states globally, avoid calling long-running blocking `.map` combinations directly on UI keystrokes (`MutableStateFlow`). Always decouple DB query flows from text input flows. Include `import kotlinx.coroutines.flow.*` strictly when refactoring flows.
*   All tests passing. `compileDebugKotlin` is verified clean with 0 exit code.
