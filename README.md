# Distributed Banking Microservices Platform

A production-grade distributed banking system demonstrating microservices architecture, OAuth2/OIDC authentication, double-entry ledger accounting, and modern DevOps practices.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      CLIENT LAYER                            │
│  Web Browser / Postman / Mobile App                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTPS
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                         │
│  ┌──────────────────────┐      ┌──────────────────────┐    │
│  │   AUTH SERVICE       │      │   BANKING SERVICE    │    │
│  │   Port: 8080         │◄────►│   Port: 8081         │    │
│  │ • User Registration  │ JWT  │ • Account Management │    │
│  │ • OAuth2/JWT Auth    │      │ • Transactions       │    │
│  │ • MFA (TOTP)         │      │ • Double-Entry Ledger│    │
│  │ • Rate Limiting      │      │ • Idempotency        │    │
│  └──────────────────────┘      └──────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        DATA LAYER                            │
│  ┌─────────────────────────┐    ┌─────────────────────┐    │
│  │   PostgreSQL 16         │    │   Redis 7.2         │    │
│  │   Schema: auth, banking │    │   Sessions, Cache   │    │
│  └─────────────────────────┘    └─────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Maven 3.9+

### Local Development

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/distributed-banking-system
   cd distributed-banking-system
   ```

2. **Start infrastructure (PostgreSQL & Redis)**
   ```bash
   docker-compose up -d postgres redis
   ```

3. **Run Auth Service**
   ```bash
   cd auth-service
   ./mvnw spring-boot:run
   ```

4. **Run Banking Service** (in another terminal)
   ```bash
   cd banking-service
   ./mvnw spring-boot:run
   ```

### Docker Compose (Full Stack)

```bash
docker-compose up -d
```

### Verify Services

```bash
# Auth Service health
curl http://localhost:8080/actuator/health

# Banking Service health
curl http://localhost:8081/actuator/health
```

## 📚 API Documentation

- **Auth Service Swagger UI**: http://localhost:8080/swagger-ui.html
- **Banking Service Swagger UI**: http://localhost:8081/swagger-ui.html

## 🛠️ Technology Stack

| Component | Technology |
|-----------|------------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.4.2 |
| **Security** | Spring Security, JWT (RS256) |
| **Database** | PostgreSQL 16 |
| **Cache** | Redis 7.2 |
| **Migrations** | Flyway |
| **Testing** | JUnit 5, Testcontainers |
| **Containerization** | Docker |
| **Documentation** | SpringDoc OpenAPI |

## 📁 Project Structure

```
distributed-banking-platform/
├── auth-service/           # Authentication & Authorization Service
│   ├── src/main/java/
│   │   └── com/banking/auth/
│   │       ├── config/     # Security, JWT configuration
│   │       ├── controller/ # REST endpoints
│   │       ├── dto/        # Request/Response objects
│   │       ├── entity/     # JPA entities
│   │       ├── repository/ # Data access layer
│   │       └── service/    # Business logic
│   └── src/main/resources/
│       ├── db/migration/   # Flyway migrations
│       └── application.yml
├── banking-service/        # Banking Operations Service
│   ├── src/main/java/
│   │   └── com/banking/banking/
│   │       ├── config/
│   │       ├── controller/
│   │       ├── dto/
│   │       ├── entity/
│   │       ├── repository/
│   │       └── service/
│   └── src/main/resources/
│       ├── db/migration/
│       └── application.yml
├── docker-compose.yml
└── README.md
```

## 🔐 Security Features

- **OAuth2/OIDC** compliant authentication
- **JWT tokens** with RS256 signing
- **BCrypt** password hashing (cost factor 12)
- **MFA** with TOTP (Google Authenticator compatible)
- **Rate limiting** on authentication endpoints
- **Audit logging** for all security events

## 💰 Banking Features

- **Double-entry ledger** accounting
- **Pessimistic locking** for concurrent transactions
- **Idempotency** for safe retries
- **ACID transactions** with PostgreSQL

## 📊 Observability

- **Health checks**: `/actuator/health`
- **Metrics**: `/actuator/prometheus`
- **Structured JSON logging**

## 🧪 Testing

```bash
# Run all tests
./mvnw test

# Run with coverage report
./mvnw test jacoco:report
```

## 📄 License

MIT License
