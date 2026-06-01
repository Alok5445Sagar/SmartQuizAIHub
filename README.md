# 🧠 Smart Quiz AI Hub

<div align="center">

![Smart Quiz AI Hub](https://img.shields.io/badge/Smart%20Quiz-AI%20Hub-6c63ff?style=for-the-badge&logo=brain)
![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?style=for-the-badge&logo=springboot)
![React](https://img.shields.io/badge/React-18-blue?style=for-the-badge&logo=react)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=for-the-badge&logo=mysql)

**A centralized, AI-powered question management platform for Subject Matter Experts and Admins**

[Features](#-features) • [Tech Stack](#-tech-stack) • [Getting Started](#-getting-started) • [API Docs](#-api-endpoints) • [Screenshots](#-screenshots)

</div>

---

## 📋 Overview

Smart Quiz AI Hub is a full-stack web application built for the **L&TT (Learning and Talent Transformation)** team. It streamlines the creation, review, and management of quiz questions (MCQ & MSQ) through a structured lifecycle workflow with role-based access for SMEs and Admins.

---

## ✨ Features

### 👤 Role-Based Access
| Feature | SME | Admin |
|---|---|---|
| Create questions (MCQ & MSQ) | ✅ | ✅ |
| Bulk upload via Excel | ✅ | ✅ |
| Edit own DRAFT / REJECTED questions | ✅ | ✅ |
| Edit any non-DRAFT question | ❌ | ✅ |
| Review assigned questions | ✅ | ✅ |
| Assign reviewers | ❌ | ✅ |
| View Question Bank (all questions) | ❌ | ✅ |

---

### 📝 Question Types
- **MCQ** — Single correct answer (Options A–D)
- **MSQ** — Multiple correct answers (Options A–F, minimum 2 correct)

---

### 🔄 Question Lifecycle

DRAFT → READY_FOR_REVIEW → UNDER_REVIEW → APPROVED 
                                        ↘ REJECTED → DRAFT (re-edit & resubmit)


| Status | Description |
|---|---|
| `DRAFT` | Created but not yet submitted. Only creator can view/edit. |
| `READY_FOR_REVIEW` | Submitted for review. Admin assigns a reviewer. |
| `UNDER_REVIEW` | Reviewer is evaluating the question. |
| `APPROVED` | Meets quality criteria. Added to question bank. |
| `REJECTED` | Needs revision. Creator can edit and resubmit. |

---

### 🛡️ Duplicate Detection
- Duplicate question stems are rejected at both single create and bulk upload
- Case-insensitive and whitespace-normalized comparison
- Intra-file duplicates detected during bulk upload
- Scoped per stack + topic combination

---

### 📊 Bulk Upload
- Upload multiple questions via Excel file (`.xlsx`)
- Supports both MCQ and MSQ in the same file
- Row-level error reporting — valid rows imported, invalid rows skipped with reasons
- Download template directly from the app

---

### 🔍 Advanced Filtering
- Filter by Stack, Topic, Difficulty, Status
- Server-side filtering across all pages (not just current page)
- Search by question text, stack name, or creator
- Page retention after editing — returns to same page

---

## 🛠 Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Core language |
| Spring Boot | 3.2.5 | Application framework |
| Spring Security | 6.x | Authentication & Authorization |
| Spring Data JPA | 3.2.5 | Database ORM |
| JWT (jjwt) | 0.11.5 | Stateless authentication |
| Apache POI | 5.2.5 | Excel file processing |
| MySQL | 8.0 | Primary database |
| H2 | In-memory | Test database |
| JUnit 5 | 5.x | Unit & integration testing |
| Maven | 3.8+ | Build tool |

### Frontend
| Technology | Version | Purpose |
|---|---|---|
| React | 18.2 | UI framework |
| React Router | 6.x | Client-side routing |
| Axios | 1.6 | HTTP client |
| react-hot-toast | 2.4 | Notifications |
| Testing Library | 14.x | Component testing |
| Jest | 27.x | Test runner |

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version | Check Command |
|---|---|---|
| Java | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| MySQL | 8.0+ | `mysql --version` |
| Node.js | 18+ | `node -v` |

---

### 1. Clone the Repository

```bash
git clone https://github.com/Alok5445Sagar/SmartQuizAIHub.git
cd SmartQuizAIHub
```

---

### 2. Database Setup

```bash
mysql -u root -p
```

```sql
source backend/src/main/resources/schema-and-data.sql
```

---

### 3. Configure Database Credentials

Edit `backend/src/main/resources/application.properties`:

```properties
spring.datasource.username=root
spring.datasource.password=your_password
```

---

### 4. Run Backend

```bash
cd backend
mvn spring-boot:run
```

Backend starts at **http://localhost:8080**

---

### 5. Run Frontend

```bash
cd frontend
npm install
npm start
```

Frontend opens at **http://localhost:3000**

---

## 🔑 Demo Accounts

| Role | Enterprise ID | Password |
|---|---|---|
| 👑 Admin | `admin` | `admin123` |
| 👤 SME | `gaurav.a.bhola` | `password123` |
| 👤 SME | `birendra.kumar.singh` | `password123` |
| 👤 SME | `divya.madhanasekar` | `password123` |
| 👤 SME | `swati.avinash.nikam` | `password123` |
| 👤 SME | `indugu.hari.prasad` | `password123` |

---

## 🗄️ Database Schema

users
├── enterprise_id (PK)
├── full_name
├── password
├── role (SME / ADMIN)
└── active
technology_stacks
├── stack_id (PK)
└── stack_name
topics
├── topic_id (PK)
├── topic_name
└── stack_id (FK)
user_stack_mapping
├── id (PK)
├── enterprise_id (FK)
└── stack_id (FK)
mcqs
├── question_id (PK)
├── question_stem
├── question_type (MCQ / MSQ)
├── option_a, option_b, option_c, option_d
├── option_e, option_f (MSQ optional)
├── correct_answer (single: "A" | multiple: "A,C,E")
├── difficulty (Easy / Medium / Hard)
├── stack_id (FK)
├── topic_id (FK)
├── creator_enterprise_id (FK)
├── reviewer_enterprise_id (FK)
├── status (DRAFT / READY_FOR_REVIEW / UNDER_REVIEW / APPROVED / REJECTED)
├── reviewer_comments
├── created_at
└── updated_at

---

## 📡 API Endpoints

### Authentication
| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Login and get JWT token |
| GET | `/api/auth/me` | Auth | Get current user info |

### Questions (SME + Admin)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/mcq` | Auth | Create single question |
| PUT | `/api/mcq/{id}` | Auth | Update question |
| GET | `/api/mcq/{id}` | Auth | Get question by ID |
| GET | `/api/mcq/my-questions` | Auth | Get creator's questions (paginated + filtered) |
| GET | `/api/mcq/my-pending-reviews` | Auth | Get assigned reviews |
| POST | `/api/mcq/{id}/review` | Auth | Approve or Reject a question |
| POST | `/api/mcq/bulk-upload` | Auth | Upload Excel file |
| GET | `/api/mcq/template` | Auth | Download Excel template |

### Admin Only
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/api/admin/question-bank` | Admin | All questions (paginated + filtered) |
| POST | `/api/admin/assign-reviewer/{id}` | Admin | Assign reviewer to question |
| PUT | `/api/admin/edit-question/{id}` | Admin | Edit any non-DRAFT question |
| GET | `/api/admin/reviewers/{stackId}` | Admin | Get eligible reviewers for stack |

### Reference Data
| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/api/reference/stacks` | Auth | All technology stacks |
| GET | `/api/reference/topics/{stackId}` | Auth | Topics for a stack |
| GET | `/api/reference/users` | Auth | All users |

---

## 🧪 Running Tests

### Backend Tests

```bash
cd backend

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=MCQServiceTest
mvn test -Dtest=MCQControllerTest
mvn test -Dtest=MCQRepositoryTest
mvn test -Dtest=JwtUtilsTest
mvn test -Dtest=MCQLifecycleIntegrationTest

# Generate HTML report
mvn test surefire-report:report
```

### Frontend Tests

```bash
cd frontend

# Run all tests
npm test -- --watchAll=false

# Run with coverage
npm test -- --coverage --watchAll=false

# Run specific test
npm test -- StatusBadge.test.js
```

### Test Coverage

| Layer | Test File | Tests |
|---|---|---|
| Service | `MCQServiceTest` | 28 tests |
| Controller | `MCQControllerTest` | 22 tests |
| Repository | `MCQRepositoryTest` | 18 tests |
| Security | `JwtUtilsTest` | 6 tests |
| Integration | `MCQLifecycleIntegrationTest` | 8 tests |
| Frontend Components | `StatusBadge.test.js`, `Pagination.test.js` | 8 tests |
| Frontend Pages | `LoginPage.test.js` | 6 tests |
| Frontend Services | `api.test.js` | 11 tests |

---

## 📁 Project Structure

SmartQuizAIHub/
├── backend/
│   ├── src/
│   │   ├── main/java/com/smartquiz/
│   │   │   ├── config/          # Security, CORS, DataInitializer, GlobalExceptionHandler
│   │   │   ├── controller/      # Auth, MCQ, Admin, Reference controllers
│   │   │   ├── dto/             # Request/Response DTOs
│   │   │   ├── entity/          # JPA Entities (User, MCQ, Stack, Topic)
│   │   │   ├── repository/      # Spring Data JPA Repositories
│   │   │   ├── security/        # JWT utils, filters, UserDetailsService
│   │   │   └── service/         # Business logic
│   │   ├── test/                # Unit, integration, repository tests
│   │   └── resources/
│   │       ├── application.properties
│   │       └── schema-and-data.sql
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── components/          # Layout, MCQForm, StatusBadge, Pagination, ViewMCQModal
│   │   ├── context/             # AuthContext
│   │   ├── pages/               # Login, MyQuestions, AddQuestion, Edit, Reviews, QuestionBank
│   │   ├── services/            # Axios API service
│   │   └── tests/           # Component, page, service tests
│   └── package.json
│
└── README.md


---

## 🏗️ Architecture

┌─────────────────┐         ┌──────────────────────┐        ┌─────────────┐
│   React Frontend │ ──JWT──▶│  Spring Boot Backend  │──JPA──▶│    MySQL    │
│   (Port 3000)   │◀────────│     (Port 8080)        │        │  Database   │
└─────────────────┘  JSON   └──────────────────────┘        └─────────────┘
│                            │
│                    Spring Security
│                    JWT Auth Filter
│                    Role-based Access
│
React Router
Axios (HTTP)
react-hot-toast

---

## 🔐 Security

- **JWT Bearer Token** authentication — stateless, no sessions
- **Role-based authorization** — SME and ADMIN roles
- **NoOpPasswordEncoder** for demo (replace with BCrypt for production)
- **CORS** configured for frontend origin
- **Global exception handler** returns proper HTTP status codes

---

## 📦 Bulk Upload Format

Download the template from the app or use this column structure:

| Column | Required | Values |
|---|---|---|
| Stack_Name | ✅ | Must match exactly e.g. `Spring Boot` |
| Topic_Name | ✅ | Must match exactly e.g. `Spring Boot Starters` |
| Difficulty | ✅ | `Easy`, `Medium`, `Hard` |
| Question_Type | ✅ | `MCQ` or `MSQ` |
| Question_Stem | ✅ | The question text |
| Option_A | ✅ | Option A text |
| Option_B | ✅ | Option B text |
| Option_C | ✅ | Option C text |
| Option_D | ✅ | Option D text |
| Option_E | ❌ | Optional (MSQ only) |
| Option_F | ❌ | Optional (MSQ only) |
| Correct_Answer | ✅ | MCQ: `A` · MSQ: `A,C` or `A,C,E` |

---

## 🌐 Supported Technology Stacks

| Stack | Topics |
|---|---|
| Spring Cloud | Eureka, Feign, LoadBalancer, Resilience4J, Actuator |
| Spring Boot | Auto-Config, Starters, Embedded Server, Profiles, Testing |
| Spring Core | IoC, Dependency Injection, Bean Lifecycle, AOP |
| Spring MVC & REST | Controllers, Mappings, Exception Handling, DTOs |
| Spring ORM & Data JPA | Entities, Relationships, JPQL, Transactions |
| Core Java | OOP, Collections, Java 8+, Streams & Lambdas |

---

## 👥 SME Stack Assignments

| SME | Assigned Stacks |
|---|---|
| gaurav.a.bhola | Spring Cloud, Spring Boot, Core Java |
| birendra.kumar.singh | Spring Boot, Spring Core, Spring Cloud |
| divya.madhanasekar | Spring Core, Spring MVC & REST |
| swati.avinash.nikam | Spring MVC & REST, Spring ORM & Data JPA |
| indugu.hari.prasad | Spring ORM & Data JPA, Core Java |

---

## 📄 License

This project was built as part of the **Hack-N-Stack: Code the Future — Java Full Stack with AI: Learning Hackathon 2026**.

---

<div align="center">
Built with ❤️ for Smart Quiz AI Hub
</div>
