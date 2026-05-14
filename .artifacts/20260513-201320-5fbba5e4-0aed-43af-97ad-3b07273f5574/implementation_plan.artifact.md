# Implementation Plan - Data Synchronization & Dynamic Prompts

This plan covers the integration of the Android app with the Streamlit/FastAPI backend to synchronize medical data and fetch updated system prompts.

## Proposed Changes

### 1. Networking Infrastructure

#### [libs.versions.toml](file:///C:/repository/gemma4good/gradle/libs.versions.toml)
- Add OkHttp dependency for REST API communication.

#### [build.gradle.kts](file:///C:/repository/gemma4good/app/build.gradle.kts)
- Include OkHttp in dependencies.

### 2. Synchronization Logic

#### [NEW] [SyncManager.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/data/SyncManager.kt)
- Responsible for:
    - Sending all documents with status `READY` to the `/sync` endpoint.
    - Updating local status to `SYNCED` upon success.
    - Handling retry logic and offline errors.

#### [ChatViewModel.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/ui/ChatViewModel.kt)
- Add a `syncData()` function.
- Integrate with `SyncManager`.

### 3. Dynamic Prompts

#### [NEW] [PromptManager.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/data/PromptManager.kt)
- Fetches updated `system_prompts.json` from the backend `/prompts` endpoint.
- Provides default fallbacks if the server is unreachable.

#### [ChatViewModel.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/ui/ChatViewModel.kt)
- Fetch prompts at startup and store them in memory.
- Use fetched prompts in `sendMessage` and `onDocumentScanned`.

### 4. UI Integration

#### [MainActivity.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/MainActivity.kt)
- Add a "Sync Now" button in the `FilesScreen` top bar or as a floating action button.
- Show a loading indicator/toast during synchronization.

## Verification Plan

### Automated Tests
- `./gradlew :app:compileDebugKotlin`
- Unit tests for `SyncManager` using a mock web server if feasible.

### Manual Verification
- **Prompt Update:** Change a prompt in the Streamlit UI -> Restart App -> Verify AI behavior changes.
- **Sync Flow:** Set 3 documents to `READY` -> Click Sync -> Verify they appear in the Streamlit dashboard and disappear/change status in the app.

