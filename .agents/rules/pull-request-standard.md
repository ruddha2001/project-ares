---
trigger: always_on
---

# Pull Request & Commit Standards

Whenever you are asked to create a Pull Request, you MUST strictly adhere to the following workflow to commit, push, and create a Pull Request on GitHub:

1. **Logical Commit Grouping**:
   - Do NOT commit all files in a single generic commit.
   - Group files logically by their functional layer (e.g., commit DTOs separately, adapters/logic separately, controllers separately, and tests separately).
   - Use descriptive commit messages following the Conventional Commits specification (e.g., `feat(backend): ...`, `test(backend): ...`, `chore(github): ...`).

2. **Branch Management**:
   - Create a feature branch named according to the ticket or task (e.g., `feature/ares-<ticket_number>-<short-description>`).
   - Push all commits to the remote origin.

3. **Pull Request Creation**:
   - Raise a Pull Request targeting the default branch (usually `main`).
   - **PR Title**: Ensure the title is concise, descriptive, and references the task identifier (e.g., `feat: Implement Polymorphic Model Routing Engine with Adapter Strategy Pattern (ARES-26)`).
   - **PR Description Template**: Read and copy the structure of `.github/pull_request_template.md` at the root of the workspace. Always construct a detailed and structured description following the template sections:
     - **Context & Summary**: Explain the goal and background context of the PR.
     - **Changes**: Group and list the modified or new files logically by layer.
     - **Verification**: List the specific automated test commands (e.g., `./gradlew test`) and manual verification steps along with successful run results.