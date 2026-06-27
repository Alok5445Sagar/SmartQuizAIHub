# 🧠 Smart Quiz AI Hub

<div align="center">

![Smart Quiz - AI Hub](https://img.shields.io/badge/Smart%20Quiz-AI%20Hub-6c63ff?style=for-the-badge&logo=brain)
![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?style=for-the-badge&logo=springboot)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-blue?style=for-the-badge&logo=spring)
![React](https://img.shields.io/badge/React-18-blue?style=for-the-badge&logo=react)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=for-the-badge&logo=mysql)
![Azure OpenAI](https://img.shields.io/badge/Azure%20OpenAI-GPT%204.1%20Mini-0078d4?style=for-the-badge&logo=microsoft)

**A centralized, enterprise-grade AI-powered assessment platform for Subject Matter Experts (SMEs), Admins, and Participants.**

[Overview](#-overview) • [Core Pillars](#-core-pillars) • [System Architecture](#-system-architecture) • [Role-Based Access](#-role-based-access) • [Tech Stack](#-tech-stack) • [Database Schema](#-database-schema) • [API Endpoints](#-api-endpoints) • [Getting Started](#-getting-started) • [Testing](#-running-tests)

</div>

---

## 📋 Overview

**Smart Quiz AI Hub** is a full-stack, enterprise assessment and question-lifecycle management application. Designed for training teams, tech leads, and educational administrators, the platform streamlines the generation, translation, review, and hosting of technical quiz questions (MCQs & MSQs) via a secure role-based workflow.

It leverages **Azure OpenAI (via Spring AI)** to generate unique questions, run smart semantic similarity audits, suggest translations, and act as an interactive technical assistant for content developers.

---

## ✨ Core Pillars & Features

### 1. 🤖 AI-Powered Question Authoring & Screening
* **AI Generation**: Generate batches of Single-Answer (MCQ) or Multiple-Answer (MSQ) questions customized by **Technology Stack**, **Topic**, and **Difficulty** (Easy, Medium, Hard).
* **Smart Similarity Screening**: Every AI-generated question is automatically screened against the database using a **semantic similarity filter** (threshold config default at `30%`). Questions exceeding the threshold are auto-discarded and replaced.
* **SME & Admin Assistants**: An embedded chat assistant widget provides guidance on exam writing, technical corrections, and prompt suggestions based on user roles.

### 2. 🌐 Multi-Lingual Localization & AI Translation
* **Localization**: The application UI is completely localized into **English (en)**, **Spanish (es)**, **French (fr)**, and **Romanian (ro)** via custom contexts.
* **AI Translation Suggestions**: Authors can request instant translations of question stems and options using the Azure OpenAI engine. The system automatically preserves option structures, correct answer definitions, and formatting while translating the text content.

### 3. 💬 Real-Time SSE Collaboration Chat
* **Dual Chat Systems**: Features **Team Chat** (for SMEs & Admins) and **Participant Chat** (for live support and peer messaging).
* **Server-Sent Events (SSE)**: Built-in reactive SSE endpoint (`/api/public/quiz/participant/chat/stream`) enables instant message delivery, live typing alerts, and read receipt notifications without HTTP polling.
* **File & Attachment Sharing**: Users can upload and share attachments (up to 10MB) directly in chat rooms with secure download controllers.

### 4. 🔄 Question Lifecycle & Review Workflows
* **Lifecycle Flow**: `DRAFT` ➡️ `READY_FOR_REVIEW` ➡️ `UNDER_REVIEW` ➡️ `APPROVED` / `REJECTED`.
* **Assign Reviewers**: Admins assign submitted questions to SMEs matched with matching stack expertise (excluding the question creator).
* **Interactive Editing**: SMEs can revise drafts or rejected questions. Admins retain full override permissions to modify any active question.

### 5. 📊 Bulk Upload and Templates
* **Excel Processing**: Upload bulk questions using a customized Excel (`.xlsx`) sheet.
* **Row-Level Validation**: The engine imports valid rows as drafts and returns precise errors for invalid rows (e.g., duplicate stems, missing fields, incorrect correct-answer options).
* **Direct Template Download**: Download standard templates directly from the client.

### 6. 🏆 Live Quizzes & Advanced Analytics
* **Host Live Assessments**: Admins select topics, specify question type mix, auto-pick questions, and publish a live quiz link with a timer.
* **Participant Portals**: Employees/Guests log in, complete the interactive timed exam, save responses, and view instant leaderboard results.
* **SVG Visual Dashboards**: Rich performance dashboards showing average scores, completion rates, topic weakness heatmaps, low-success difficulty alerts, and an active student watchlist.

---

## 🏗 System Architecture

```
                 ┌──────────────────────────────────────────────────────────┐
                 │                   REACT SINGLE PAGE APP                  │
                 │                 (Vanilla CSS + Contexts)                 │
                 └─────────────────────────────┬────────────────────────────┘
                                               │
                                       HTTPS / JWT / SSE
                                               │
                                               ▼
                 ┌──────────────────────────────────────────────────────────┐
                 │                SPRING BOOT SECURITY LAYER                │
                 │           (JWT Filter, Role Auth, CORS Config)           │
                 └─────────────────────────────┬────────────────────────────┘
                                               │
                                               ▼
                 ┌──────────────────────────────────────────────────────────┐
                 │                 SPRING BOOT REST BACKEND                 │
                 │     (Controllers, JPA Repositories, SSE Emitters)        │
                 └──────────────┬──────────────┬──────────────┬─────────────┘
                                │              │              │
                                │              │              │
                                ▼              ▼              ▼
                     ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
                     │    MYSQL     │   │  APACHE POI  │   │  SPRING AI   │
                     │   DATABASE   │   │  EXCEL ENG.  │   │  AZURE GPT-4 │
                     │ (Schema/Seed)│   │  (.xlsx File)│   │ (Translation/│
                     │              │   │              │   │ Similarity)  │
                     └──────────────┘   └──────────────┘   └──────────────┘
```

---

## 👥 Role-Based Access

| Feature | 👑 Admin | 👤 SME | 🎓 Participant |
| :--- | :---: | :---: | :---: |
| **Manage Question Bank (Global View)** | ✅ | ❌ | ❌ |
| **Assign Reviewers to Questions** | ✅ | ❌ | ❌ |
| **Edit any non-DRAFT Question** | ✅ | ❌ | ❌ |
| **Publish Live Quizzes / Manage Links** | ✅ | ❌ | ❌ |
| **View Global & Student Analytics** | ✅ | ❌ | ❌ |
| **Create MCQ/MSQ Questions (Form/Excel)** | ✅ | ✅ | ❌ |
| **Review Assigned Questions** | ✅ | ✅ | ❌ |
| **Use AI Assistant & Translation Widget** | ✅ | ✅ | ❌ |
| **Chat with Team (Team Chat)** | ✅ | ✅ | ❌ |
| **Attempt Timed Quizzes** | ❌ | ❌ | ✅ |
| **Access Participant Portal & Revision Tips** | ❌ | ❌ | ✅ |
| **Chat with Peers & Instructors** | ❌ | ❌ | ✅ |

---

## 🛠 Tech Stack

### Backend
* **Java 17** & **Spring Boot 3.2.5**
* **Spring Security & JWT** (Stateless Bearer Authentication)
* **Spring Data JPA & Hibernate**
* **Spring AI (Azure OpenAI Integration)**
* **Apache POI 5.2.5** (Excel read/write templates)
* **WebFlux / WebClient** (Asynchronous operations)
* **MySQL 8.0** & **H2** (In-Memory Database for testing)

### Frontend
* **React 18.2** (Vite-free Create-React-App configuration)
* **React Router Dom v6**
* **Axios** (HTTP client with response interceptors)
* **React Hot Toast** (Alert notifications)
* **React Markdown & Remark GFM** (AI formatting)
* **Prism Light / Syntax Highlighter** (Technical code block renders)

---

## 🗄 Database Schema

The database relies on a relational architecture designed with MySQL 8.0 constraints. Below are the primary entities:

### Entities & Columns
* **`users`**: `enterprise_id` (PK), `full_name`, `password` (Plain Text for demo/dev), `role` (`SME`, `ADMIN`, `PARTICIPANT`), `active` (boolean), `preferred_language`.
* **`technology_stacks`**: `stack_id` (PK), `stack_name` (Unique).
* **`topics`**: `topic_id` (PK), `topic_name`, `stack_id` (FK ➡️ `technology_stacks`).
* **`user_stack_mapping`**: `id` (PK), `enterprise_id` (FK), `stack_id` (FK).
* **`mcqs`**: `question_id` (PK), `question_stem` (Text), `question_type` (`MCQ`, `MSQ`), `option_a` through `option_f`, `correct_answer` (Comma-separated for MSQ), `is_ai_generated`, `difficulty` (`Easy`, `Medium`, `Hard`), `stack_id` (FK), `topic_id` (FK), `creator_enterprise_id` (FK), `reviewer_enterprise_id` (FK), `status` (`DRAFT`, `READY_FOR_REVIEW`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`), `reviewer_comments`, timestamps.
* **`mcq_translations`**: `translation_id` (PK), `question_id` (FK), `language_code` (`es`, `fr`, `ro`), `question_stem`, options, `translated_by` (FK ➡️ `users`).
* **`chat_messages`**: `chat_message_id` (PK), `enterprise_id` (FK), `session_id`, `role` (`USER`, `ASSISTANT`), `content`, `created_at`.
* **`participant_direct_messages`**: `message_id` (PK), `sender_enterprise_id` (FK), `recipient_enterprise_id` (FK), `message_text`, `attachment_name`, `attachment_content_type`, `attachment_size_bytes`, `attachment_storage_path`.

---

## 📡 API Endpoints

### 🔑 Authentication
* `POST /api/auth/login` — Sign in and retrieve JWT Bearer token.
* `GET /api/auth/me` — Retrieve context details of the signed-in user.

### ❓ Question Management (SME + Admin)
* `POST /api/mcq` — Create a single question.
* `PUT /api/mcq/{id}` — Update an existing question.
* `GET /api/mcq/{id}` — Get question details (localized by language code).
* `GET /api/mcq/my-questions` — Paginated & filtered list of author's questions.
* `GET /api/mcq/my-pending-reviews` — Review queue assigned to user.
* `POST /api/mcq/{id}/review` — Approve/reject a question with comments.
* `POST /api/mcq/bulk-upload` — Upload Excel files.
* `GET /api/mcq/template` — Download the standard Excel template.

### 🌍 Translation Endpoints
* `GET /api/mcq/{id}/translations` — Get all saved translations for a question.
* `POST /api/mcq/{id}/translations/suggest?languageCode={code}` — Generate AI translation suggestions.
* `PUT /api/mcq/{id}/translations` — Save a translation.

### 👑 Admin Utilities
* `GET /api/admin/question-bank` — Filter and view all system questions.
* `POST /api/admin/assign-reviewer/{id}` — Delegate reviewer assignment.
* `PUT /api/admin/edit-question/{id}` — Admin override to modify active questions.
* `GET /api/admin/reviewers/{stackId}` — Find eligible stack reviewers.

### 🏆 Quiz Control & Analytics (Admin Only)
* `GET /api/admin/quizzes/approved-questions` — Get approved questions matching stacks/topics.
* `POST /api/admin/quizzes/auto-pick` — Auto-pick balanced MCQ/MSQ sets.
* `POST /api/admin/quizzes/publish` — Create a live quiz with a code.
* `POST /api/admin/quizzes/{quizId}/close` / `reopen` / `rehost` — Manage quiz access.
* `GET /api/admin/quizzes/{quizId}/leaderboard/export` — Export Excel leaderboard.
* `GET /api/admin/quizzes/participant-analytics` — SVG visual statistics data.

### 🎓 Participant Portal (Public Access)
* `GET /api/public/quiz/{quizCode}` — Fetch live quiz structure.
* `POST /api/public/quiz/{quizCode}/participant-login` — Quiz entry credential check.
* `POST /api/public/quiz/{quizCode}/attempt` — Submit a quiz score.
* `GET /api/public/quiz/participant/dashboard` — History, active quizzes, and tips.
* `GET /api/public/quiz/participant/chat/stream` — Real-time SSE chat connection.
* `POST /api/public/quiz/participant/chat/send` — Send peer direct messages.

---

## 🚀 Getting Started

### Prerequisites
* **Java**: JDK 17 installed and configured (`java -version`).
* **Maven**: Maven 3.8+ installed (`mvn -version`).
* **MySQL**: MySQL 8.0+ server running.
* **Node.js**: Node 18+ installed.

### 1. Database Setup
Log in to your MySQL terminal:
```sql
CREATE DATABASE smart_quiz_hub;
USE smart_quiz_hub;
```
Import the schema and seed data:
```bash
mysql -u root -p smart_quiz_hub < backend/src/main/resources/schema-and-data.sql
```

### 2. Configure Backend Credentials
Open `backend/src/main/resources/application.properties` and edit the database username and password:
```properties
spring.datasource.username=root
spring.datasource.password=your_secure_password
```

### 3. Run Backend Server
Navigate to the backend directory and launch:
```bash
cd backend
mvn spring-boot:run
```
The backend server runs on **http://localhost:8080**.

### 4. Run Frontend Server
Navigate to the frontend directory, install dependencies, and run:
```bash
cd frontend
npm install
npm start
```
The client starts on **http://localhost:3000**.

> [!TIP]
> **Convenience Scripts**: You can run script commands directly from the workspace root directory:
> * Install frontend dependencies: `npm run install:frontend`
> * Run frontend client: `npm start`
> * Run tests: `npm test`

---

## 🔑 Demo Accounts

Use these credentials to log in and explore different portal modes:

| Role | Username (Enterprise ID) | Password | Stacks Assigned |
| :--- | :--- | :--- | :--- |
| **👑 Admin** | `admin` | `admin123` | *All Access* |
| **👤 SME** | `gaurav.a.bhola` | `password123` | Spring Cloud, Spring Boot, Core Java |
| **👤 SME** | `birendra.kumar.singh` | `password123` | Spring Boot, Spring Core, Spring Cloud |
| **👤 SME** | `divya.madhanasekar` | `password123` | Spring Core, Spring MVC & REST |
| **👤 SME** | `swati.avinash.nikam` | `password123` | Spring MVC, Spring ORM & Data JPA |
| **🎓 Participant** | `participant.aarav` | `password123` | *Candidate / Student* |
| **🎓 Participant** | `participant.anaya` | `password123` | *Candidate / Student* |

---

## 🧪 Running Tests

### Backend JUnit Tests
Run all unit and integration tests (uses the in-memory H2 database configuration):
```bash
cd backend
mvn test
```
To run specific layers:
* Service Tests: `mvn test -Dtest=MCQServiceTest`
* Integration Tests: `mvn test -Dtest=MCQLifecycleIntegrationTest`
* JWT Security: `mvn test -Dtest=JwtUtilsTest`

### Frontend Jest Tests
Run the frontend test suite:
```bash
cd frontend
npm test -- --watchAll=false
```

---

## 📄 License
This project was developed for the **Hack-N-Stack: Code the Future — Java Full Stack with AI: Learning Hackathon 2026**.
