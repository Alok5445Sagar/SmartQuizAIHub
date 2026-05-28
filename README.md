# Smart Quiz AI Hub

A full-stack MCQ management platform built with Spring Boot, React, and MySQL.

## Architecture

```
smart-quiz-hub/
├── backend/          # Spring Boot 3.2.5 + Java 17
└── frontend/         # React 18 + React Router
```

## Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.0+ (running on port 3306)
- Node.js 18+

## Quick Start

### 1. Database Setup

MySQL database `smart_quiz_hub` is auto-created on first run.

Update credentials in `backend/src/main/resources/application.properties`:
```properties
spring.datasource.username=root
spring.datasource.password=root
```

### 2. Run Backend

```bash
cd backend
mvn spring-boot:run
```

Backend starts on **http://localhost:8080**

Master data is seeded automatically on first run:
- Technology stacks & topics (from the slides master list)
- Demo user accounts (see below)

### 3. Run Frontend

```bash
cd frontend
npm install
npm start
```

Frontend opens at **http://localhost:3000**

## Demo Accounts

| Role  | Enterprise ID          | Password     |
|-------|------------------------|--------------|
| Admin | `admin`                | `admin123`   |
| SME   | `gaurav.a.bhola`       | `password123`|
| SME   | `birendra.kumar.singh` | `password123`|
| SME   | `divya.madhanasekar`   | `password123`|
| SME   | `swati.avinash.nikam`  | `password123`|
| SME   | `indugu.hari.prasad`   | `password123`|

## Features by Role

### SME
- **My Questions** — View all created MCQs with status tracking
- **Add Question** — Single UI form OR Bulk Excel upload
- **Edit Question** — Edit DRAFT or REJECTED questions; re-submit for review
- **My Pending Reviews** — Review MCQs assigned by Admin; Approve or Reject with comments

### Admin
- All SME features
- **Question Bank** — View all MCQs with search & status filter
- **Assign Reviewer** — Assign SMEs (same stack) or Admins as reviewers
- Stats dashboard (total, pending, under review, approved)

## MCQ Lifecycle

```
DRAFT → READY_FOR_REVIEW → UNDER_REVIEW → APPROVED
                                        ↘ REJECTED → DRAFT (re-edit)
```

## API Endpoints

| Method | Endpoint                          | Access    | Description              |
|--------|-----------------------------------|-----------|--------------------------|
| POST   | /api/auth/login                   | Public    | Login                    |
| GET    | /api/mcq/my-questions             | Auth      | Get creator's MCQs       |
| POST   | /api/mcq                          | Auth      | Create MCQ               |
| PUT    | /api/mcq/{id}                     | Auth      | Update MCQ               |
| GET    | /api/mcq/my-pending-reviews       | Auth      | Get assigned reviews     |
| POST   | /api/mcq/{id}/review              | Auth      | Submit review decision   |
| POST   | /api/mcq/bulk-upload              | Auth      | Upload Excel file        |
| GET    | /api/mcq/template                 | Auth      | Download Excel template  |
| GET    | /api/admin/question-bank          | Admin     | All MCQs paginated       |
| POST   | /api/admin/assign-reviewer/{id}   | Admin     | Assign reviewer          |
| GET    | /api/reference/stacks             | Auth      | All technology stacks    |
| GET    | /api/reference/topics/{stackId}   | Auth      | Topics for a stack       |

## Tech Stack

- **Backend**: Spring Boot 3.2.5, Spring Security (JWT), Spring Data JPA, Apache POI
- **Database**: MySQL 8.0
- **Frontend**: React 18, React Router v6, Axios, react-hot-toast
- **Auth**: JWT Bearer tokens
