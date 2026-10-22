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
- **Atomic File-Level Commits:**
  - Commit changes in small, bite-sized units per file (or tightly coupled logical pairs).
  - Each commit message must be a concise summary following Conventional Commits (e.g., `feat(livetv): ...`, `fix(settings): ...`, `docs(agents): ...`).
- **Detailed PR Descriptions:**
  - The Pull Request description must provide comprehensive change notes detailing all component updates, parity verifications, and an indexed list of the atomic commits.
  - Merge only via PR (`gh pr merge`).
