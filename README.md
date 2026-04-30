# Project ARES: Architectural Retrieval & Evaluation System

[![ARES CI](https://github.com/ruddha2001/project-ares/actions/workflows/ci.yml/badge.svg)](https://github.com/ruddha2001/project-ares/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Ares** is a sovereign agentic governance engine designed for **Architectural Archaeology**. It navigates fragmented,
legacy, and remote codebases to extract, refine, and evaluate architectural "truth" against evolving requirements. By
utilizing the Model Context Protocol (MCP) and a dedicated refinery layer, Ares aims to reduce the "Token Tax" of
agentic workflows by over 90% while maintaining absolute governance.

---

## 🏗️ Architecture

Ares is built for high-throughput, non-blocking ingestion and deep structural analysis.

### Sovereign Connectivity (MCP)

Ares utilizes the **Model Context Protocol (MCP)** to interact with data silos (Notion, GitHub) without baking
API-specific logic into the core.

* **GitHub Provider**: Orchestrates remote tool calls like `get_file_contents` to navigate repository structures without
  full local clones.
* **Notion Provider**: Ingests structured requirements and design documentation to establish the governance baseline.
* **Standardized Transport**: Supports `StdioMcpTransport` for local "Dark Site" security and `StreamableHttpTransport`
  for managed remote endpoints.

### The Refinery & AresBlocks

Raw data is processed by the **Refinery Service**, which distills raw manifests and diffs into **AresBlocks**. These are
atomic units of architectural signal stored in **pgvector** for semantic retrieval and conflict analysis.

### Concurrency Model (Java 21)

The engine is built on **Java 21 Virtual Threads**. All ingestion and refinery tasks are executed via a custom
`aresTaskExecutor`, allowing for high-concurrency "Archaeological Digs" with minimal memory overhead.

---

## ✨ Features

| Capability                                                                              | Status         |
|:----------------------------------------------------------------------------------------|:---------------|
| **Non-Recursive Archaeology** — High-speed directory manifest extraction via GitHub MCP | ✅ Complete     |
| **Virtual Thread Ingestion** — Asynchronous, non-blocking provider orchestration        | ✅ Complete     |
| **McpConfig Nervous System** — Standardized transport layer for all providers           | ✅ Complete     |
| **AresBlock Refinery** — Automated noise reduction and semantic distilling              | 🚧 In Progress |
| **Token Tax Mitigation** — Cost-governance layer for usage-based API optimization       | 🚧 In Progress |
| **Graphify Integration** — Deep AST structural parsing and relationship mapping         | 📅 May 2026    |
| **Sovereign Guardian** — Continuous architectural drift monitoring and alerting         | 📅 August 2026 |

---

## 🛠️ Tech Stack

| Layer             | Technology                                       |
|:------------------|:-------------------------------------------------|
| **Runtime**       | Java 21 (Virtual Threads)                        |
| **Framework**     | Spring Boot 3.4.x                                |
| **Orchestration** | LangGraph4j (Stateful Agentic Workflows)         |
| **Connectivity**  | Model Context Protocol (MCP)                     |
| **Intelligence**  | Claude 3.5/Opus & DeepSeek V3 (via Continue/API) |
| **Persistence**   | PostgreSQL 16 + pgvector (Ares Vault)            |

---

## 🚀 Getting Started

### Local Development

Ares utilizes a Debian-based container environment to ensure `npx` and Node-based MCP servers run with 100%
reliability.

```bash
# Clone the Sovereign Engine
git clone [https://github.com/ruddha2001/project-ares.git](https://github.com/ruddha2001/project-ares.git)
cd project-ares

# Initialize the Vault and Engine
cp infra/.sample.env .env
docker compose -f infra/docker-compose.yml up --build