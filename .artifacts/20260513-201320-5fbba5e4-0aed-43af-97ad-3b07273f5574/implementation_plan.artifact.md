# Implementation Plan - Advanced Features and Sync Strategy

This plan covers UI enhancements (selectable text, metadata viewer, document editor) and the strategy for synchronization with a future local backend.

## Proposed Changes

### 1. UI Enhancements (Selection and Metadata)

#### [MainActivity.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/MainActivity.kt)
- Wrap chat text in `SelectionContainer` to allow user selection and copying.
- Update `ChatBubble` to show a "Metadata" summary icon or badge if `documentId` is present.
- Create `DocumentDetailScreen` for the "See It" functionality.
- Update `AppNavigation` to handle the new `Detail` screen.

#### [ChatViewModel.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/ui/ChatViewModel.kt)
- Add functions to update specific fields in a `DocumentState`.
- Add state to track the currently being edited document.

### 2. Document Editing ("See It")

#### [NEW] [DocumentDetailScreen.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/ui/DocumentDetailScreen.kt)
- A new screen that parses the LLM-structured text (using a simple regex or JSON parser if structured) into editable `OutlinedTextField`s.
- Features a "Save" button to commit changes back to `DocumentStateManager`.

### 3. Sync Strategy

#### [NEW] [backend_blueprint.artifact.md](file:///C:/repository/gemma4good/.artifacts/20260513-201320-5fbba5e4-0aed-43af-97ad-3b07273f5574/backend_blueprint.artifact.md)
- Define a simple REST API structure (POST `/sync`, GET `/prompts`).
- Provide a Python/Streamlit starter script that receives JSON and stores it locally.

## Verification Plan

### Automated Tests
- `./gradlew :app:compileDebugKotlin` to ensure UI changes don't break the build.

### Manual Verification
- **Text Selection:** Long-press text in chat to see selection handles.
- **"See It" Flow:** Go to Files -> Click "See It" -> Edit a field -> Save -> Verify change in Files list.
- **Sync Dry Run:** Verify log output for the proposed JSON structure that will be sent to the backend.
