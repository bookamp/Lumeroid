# Agent Guidelines & Project Rules

These guidelines are mandatory for all AI agents working within the Lume-Android repository.

---

## 1. Reference Sources, Apple TV Verification & Hardware Testing
- **Apple TV App is the Canonical Ground Truth:**
  - The Apple TV Lume application is the primary reference for user experience, aesthetics, typography scaling, screen density, and functional behavior.
  - When designing, building, or refining features in Android TV, always compare against the Apple TV app and the original Lume source code.
  - Target 1:1 visual and behavioral parity with Apple TV (e.g. top bar dimensions, EPG timeline ruler, channel cards, hero layout, focus animations).
- **Runtime Verification & Screenshot Clues on Apple TV:**
  - Source code alone does not always reveal the full picture (such as animation curves, dynamic focus state transitions, subtle gradients, and layout shifts under real data).
  - Whenever visual or functional nuances are unclear, launch the app on Apple TV and capture screenshots or observe live behavior. Use these visual clues as ground-truth evidence to guide implementation.
- **Shield TV Deployment & Testing:**
  - The NVIDIA Shield TV is the target Android TV hardware for runtime deployment, remote control D-pad navigation validation, and video playback verification.
  - Test and verify on the Shield TV where necessary before concluding tasks.

---

## 2. Reverse Engineering with REA
- When system behavior, streaming URLs, catchup mechanisms, EPG schemas, or API nuances are ambiguous or undocumented, utilize **REA (Reverse Engineer Anything)** capabilities.
- Inspect and decompile artifacts, trace runtime method calls, or analyze network signatures to determine ground-truth behavior rather than guessing.

---

## 3. Git & Release Workflow
- **Branch & PR Requirement:**
  - Never push directly to `main`.
  - Always create a dedicated feature branch (`feat/<topic>`, `fix/<topic>`, `docs/<topic>`), push to `origin`, and open a Pull Request via GitHub CLI (`gh pr create`).
- **Never Auto-Commit During Development:**
  - Never automatically stage or commit code changes on your own while iterating on tasks.
  - Await explicit user instruction (e.g., "commit", "check in") before initiating any commit process.
- **Automated Check-in Pipeline (Triggered by User):**
  - When the user instructs you to commit or check in code, automatically execute the full release workflow without requiring separate step-by-step prompts:
    1. **Atomic File-Level Commits:** Stage and commit changes in small, bite-sized units per file (or tightly coupled logical pairs) with concise summary messages following Conventional Commits (e.g., `feat(livetv): ...`, `fix(settings): ...`, `docs(agents): ...`).
    2. **Push to Remote:** Push the dedicated feature branch to `origin`.
    3. **Open Pull Request:** Create a PR via `gh pr create` with comprehensive change notes detailing all component updates, parity verifications, and an indexed list of the atomic commits.
    4. **Merge & Cleanup:** When requested or approved, merge the PR via `gh pr merge` and delete the feature branch.


