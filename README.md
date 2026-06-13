# 🚗 RideNDrive — Commuter Carpooling Platform

A modular commuter carpooling application built as a 3rd-year **Software Engineering** course project.

RideNDrive connects drivers who are already making a journey with passengers looking for a ride along a compatible route. The system dynamically matches users, calculates optimal pickup/drop-off sequences, and fairly splits the cost of the journey.

---

## ✨ Key Features

- **Intelligent Route Matching** — DFS algorithm with constraint-based pruning to sequence multi-passenger stops within time and detour budgets.
- **Dynamic Pricing Engine** — Policy-chaining pricing system handling rush hour, late-night, and same-zone conditions.
- **Reputation System** — Time-decaying reputation tiers that incentivise reliable behaviour.
- **BCrypt Password Security** — Salted BCrypt hashing for secure credential storage.
- **CI/CD Pipeline** — GitHub Actions workflow for automated testing and JaCoCo coverage reporting.

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Spring Boot 3.2.5, Java 17 |
| **Database** | MySQL 8.0 (Spring Data JPA / Hibernate) |
| **Frontend** | Vanilla HTML / CSS / JavaScript (SPA) |
| **Testing** | JUnit 5, Spring Boot Test, JaCoCo (84% coverage) |
| **CI/CD** | GitHub Actions |

---

## 📐 Architecture

RideNDrive uses a three-tier **Layered MVC** architecture with dedicated packages for Controller, Service, and Repository layers. The project implements **8 design patterns** including:

1. **Strategy** — `PricingPolicy` implementations
2. **Chain of Responsibility** — Sequential pricing modifiers
3. **Repository** — Spring Data JPA abstraction
4. **MVC** — Presentation, routing, and data separation
5. **Dependency Injection** — Spring IoC container
6. **DTO** — Decoupling entities from API responses
7. **Factory** — `UserFactory` for user creation
8. **Singleton** — `GoogleMapsMappingService`

---

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- XAMPP (for MySQL) or any MySQL 8.0 server

### Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/<your-username>/RideNDrive.git
   cd RideNDrive
   ```

2. **Start MySQL** via XAMPP Control Panel (or your preferred method).

3. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

4. **Open in browser:**
   ```
   http://localhost:8080
   ```

### Running Tests

```bash
mvn test
```

All **76 automated tests** should pass with **84% code coverage**.

---

## 📊 UML Diagrams

The project includes 15 comprehensive UML diagrams covering:

- Use Case Diagram
- Class Diagram
- Component Diagram
- Entity Relationship Diagram
- Activity Diagrams (DFS Stop Planning, Dynamic Pricing)
- Sequence Diagrams (Registration, Login, Search & Book, Rating)
- Flow Diagrams (Account Deletion, UI Flow)
- Service Layer Diagram
- Layered Architecture Diagram

Diagram source files are located in `diagram_svgs/` and `diagram_pdfs/`.

---

## 📁 Project Structure

```
RideNDrive/
├── .github/workflows/     # CI/CD pipeline (GitHub Actions)
├── src/
│   ├── main/
│   │   ├── java/com/routeshare/
│   │   │   ├── controller/    # REST API endpoints
│   │   │   ├── model/         # JPA entities
│   │   │   ├── repository/    # Spring Data repositories
│   │   │   ├── service/       # Business logic
│   │   │   └── integration/   # External API adapters
│   │   └── resources/
│   │       └── static/        # Frontend (HTML/CSS/JS)
│   └── test/                  # JUnit test suite
├── diagram_svgs/              # UML diagrams (SVG)
├── diagram_pdfs/              # UML diagrams (PDF)
├── data/                      # Sample data
├── REPORT.md                  # Full project report (Markdown)
├── pom.xml                    # Maven dependencies
└── README.md
```

---

## 👤 Author

**Mohammad Haroon** (560824)  
3rd Year — Software Engineering  
Professor: Salvatore Distefano

---

## 📄 License

This project was developed for academic purposes as part of the Software Engineering course.
