# Preplee Lite

Preplee Lite is a deliberately simple full-stack interview-practice application. It keeps the main Preplee features while using a smaller codebase and basic cream-and-green interface.

## Technology stack

- **React + JavaScript** — frontend screens, timer, microphone, and API calls
- **Java 21 + Spring Boot** — REST API, application logic, AI calls, and scoring
- **PostgreSQL** — coding attempts, résumé interviews, answers, scores, timing, and topic progress
- **Docker Compose** — reproducible PostgreSQL setup
- **Docker** — isolated JavaScript submission execution with CPU, memory, network, process, filesystem, and timeout restrictions

## Features

- 15-minute JavaScript coding interviews
- Automated syntax checking and hidden test cases
- Separate hidden-test, AI, and overall scores
- Typed or browser microphone-to-text explanations
- Personalized questions generated from pasted résumé text
- Typed or spoken résumé-interview answers with feedback
- Persistent PostgreSQL interview history and topic mastery
- Adaptive coding questions selected from the weakest recorded topic
- Demo feedback when no AI key is configured

## Architecture

```text
React + JavaScript frontend
            |
            | REST/JSON
            v
Java + Spring Boot backend -----> AI API
       |                 |
       v                 v
PostgreSQL          Docker code runner
```

The AI key stays in the Spring Boot process and is never exposed to React.

## Requirements

- Node.js 20+
- Java 21
- Maven
- Docker Desktop

## Run the project

### 1. Start PostgreSQL

From the `simple-preplee` folder:

```powershell
docker compose up -d
docker pull node:22-alpine
```

### 2. Start Spring Boot

Open another PowerShell window:

```powershell
cd "C:\Users\Admin\Documents\Codex\2026-07-29\6-ai-mock-interviewer-with-memory\simple-preplee\backend"
mvn spring-boot:run
```

Spring Boot starts at `http://localhost:8080`. Flyway automatically creates the PostgreSQL tables.

### 3. Start React

Open another PowerShell window:

```powershell
cd "C:\Users\Admin\Documents\Codex\2026-07-29\6-ai-mock-interviewer-with-memory\simple-preplee"
npm install
npm run dev
```

Open `http://localhost:3000`.

## Enable real AI feedback

Before starting Spring Boot, set the private backend variables:

```powershell
$env:AI_API_KEY="your_api_key"
$env:AI_API_URL="https://api.openai.com/v1/chat/completions"
$env:AI_MODEL="gpt-4o-mini"
mvn spring-boot:run
```

Without an API key, the full workflow still runs using clearly labelled demo questions and feedback.

## Main files

- `src/App.jsx` — React pages, timer, speech recognition, and REST requests
- `src/styles.css` — basic cream-and-green CSS
- `backend/.../InterviewController.java` — REST endpoints
- `backend/.../InterviewService.java` — scoring, PostgreSQL, history, and adaptive selection
- `backend/.../AiService.java` — backend-only AI API integration
- `backend/.../DockerCodeRunner.java` — restricted code execution
- `backend/.../QuestionBank.java` — coding questions and hidden tests
- `backend/src/main/resources/db/migration` — PostgreSQL table definitions
- `docker-compose.yml` — PostgreSQL container configuration

## Important limitation

This is a local educational project. Docker substantially limits submitted code, but a public production code-execution service should receive an additional security review and stronger infrastructure isolation.
