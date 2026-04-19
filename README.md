### ARES: Agentic Requirements Evaluation System

[![ARES CI](https://github.com/ruddha2001/project-ares/actions/workflows/ci.yml/badge.svg)](https://github.com/ruddha2001/project-ares/actions/workflows/ci.yml)

**ARES** is an experimental, high-rigor agentic framework designed to bridge the gap between fragmented requirement
sources and actionable engineering intelligence. It automates the ingestion, sanitization, and verification of software
requirements using state-of-the-art orchestration patterns.

---

## 🏗️ Architecture

ARES leverages a modular, "distributed-first" architecture to ensure scalability and isolation of concerns:

1. **Sidecar Connectivity (MCP):** Utilizing the **Model Context Protocol (MCP)** via a Node.js sidecar to handle
   third-party integrations (e.g., Notion) through a standardized STDIO bridge.
2. **Stateful Orchestration:** Built on **LangGraph4j**, ARES manages the evolution of requirements as an immutable
   state graph. Each node operates on "State Deltas," ensuring a robust audit trail and fault-tolerant execution.
3. **Sanitization Layer:** A dedicated processing stage that performs automated PII/PCI masking and data normalization
   before requirements are exposed to large language models.

---

## 🛠️ Tech Stack

* **Runtime:** Java 21+
* **Framework:** Spring Boot 3.x
* **Agentic Orchestration:** LangGraph4j
* **Sidecar Runtime:** Node.js (for MCP servers)
* **Build System:** Gradle

---

## 🚀 Roadmap & Progress

- [x] **Phase 1: Ingestion Bridge** - Implemented Notion MCP client and block-to-text extraction logic.
- [x] **Phase 2: Core Orchestration** - Developed the `AresWorkflow` using asynchronous state nodes and delta-based
  merging.
- [ ] **Phase 3: Cognitive Analysis** - Integration of LLM-based verification nodes for ambiguity and testability
  analysis.
- [ ] **Phase 4: Persistence** - Implementation of Postgres-backed checkpointers for long-running graph state.

---

## 🧪 Development & Testing

ARES utilizes environment-aware integration testing to verify the full handshake between the Java core and the MCP
sidecars.

### Prerequisites

* Java 21 JDK
* Node.js (for MCP sidecar execution)
* `NOTION_TOKEN` and `NOTION_TEST_PAGE_ID` set in the environment.

### Run Tests

```bash
./gradlew test