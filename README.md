# RideNDrive — Commuter Carpooling Platform

A modular commuter carpooling application built as a 3rd-year **Software Engineering** course project.

RideNDrive connects drivers who are already making a journey with passengers looking for a ride along a compatible route. The system dynamically matches users, calculates optimal pickup/drop-off sequences, and fairly splits the cost of the journey.

---

## Key Features

- **Intelligent Route Matching** — DFS algorithm with constraint-based pruning to sequence multi-passenger stops within time and detour budgets.
- **Dynamic Pricing Engine** — Policy-chaining pricing system handling rush hour, late-night, and same-zone conditions.
- **Driver-Owned Policy Engine** — Drivers author their own *travel rules* (min passenger reputation, same-destination-only, no large luggage) and *pricing rules* (own base rate, surcharges, loyalty discount for GOLD+ riders). Candidates violating rules are vetoed with an audited reason; the rest are **ranked best-first** with reputation-driven priority.
- **Booking Lifecycle State Machine** — Every booking flows through a formally guarded lifecycle (`PENDING → CONFIRMED/REJECTED/CANCELLED → COMPLETED`) with driver confirm/decline, passenger withdrawal, and trip completion.
- **In-App Notification Center** — Observer-pattern notifications for booking events and new ratings, with unread badge, inbox panel, and mark-as-read endpoints.
- **Reputation System** — Time-decaying reputation tiers that incentivise reliable behaviour.
- **BCrypt Password Security** — Salted BCrypt hashing for secure credential storage; secrets externalized via environment variables.
- **Payment Ledger** — every (simulated) fare transfer persists as a referenced transaction (`PAY-2026-…`): passengers see receipts on their rides, drivers see earnings, both can pull their full statement.
- **Driver Route Cockpit** — per-trip status (Scheduled / Action needed / Departed / Completed) and an on-demand route view: a side-effect-free DFS re-plan with metrics, stop timeline, and a live Google map of the multi-stop route.
- **Demo World** — on an empty database (dev profile only) the app seeds Sicilian personas, trips, bookings in every lifecycle state, ratings, driver policies and payment history, so it never demos as a ghost town.
- **CI/CD + Containerized Deployment** — GitHub Actions pipeline (build → test → coverage artifact → Docker image) and one-command `docker compose up` deployment.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Spring Boot 3.2.5, Java 17 |
| **Database** | MySQL 8.0 (Spring Data JPA / Hibernate) |
| **Frontend** | Vanilla HTML / CSS / JavaScript (SPA) |
| **Testing** | JUnit 5, Spring Boot Test, JaCoCo (84% coverage) |
| **CI/CD** | GitHub Actions |

---

## 📐 Architecture

RideNDrive uses a three-tier **Layered MVC** architecture with dedicated packages for Controller, Service, and Repository layers. The project implements **11 design patterns**:

1. **Strategy** — `PricingPolicy` implementations
2. **Chain of Responsibility** — Sequential pricing modifiers
3. **Repository** — Spring Data JPA abstraction
4. **MVC** — Presentation, routing, and data separation
5. **Dependency Injection** — Spring IoC container
6. **DTO** — Decoupling entities from API responses
7. **Factory** — `UserFactory` for user creation
8. **Singleton** — `GoogleMapsMappingService`
9. **State** — `BookingLifecycleService` guarded booking state machine
10. **Observer** — `NotificationService` event publication on booking/rating events
11. **Specification** — `DriverTravelRule` sets interpreted by `TravelPolicyService` (driver-owned composable predicates)

---

##  Getting Started

### Option A — XAMPP / MariaDB (default)

**Prerequisites:** Java 17+, Maven 3.8+, a current XAMPP (MariaDB 10.4+; Hibernate 6 does not support the ancient 5.5-era bundles)

1. **Start MySQL** in the XAMPP Control Panel.

2. **(Optional) Set the Maps key** — without it the app uses its built-in fallback visualization:
   ```powershell
   # Windows PowerShell (persistent)
   setx GOOGLE_MAPS_API_KEY "your-key-here"
   ```

3. **Run:**
   ```bash
   mvn spring-boot:run
   ```

4. Open `http://localhost:8080`. The `routesharedb` database is created automatically. API docs at `/swagger-ui.html`, health at `/actuator/health`.

### Option B — Docker (production-like)

```bash
cp .env.example .env        # set MYSQL_ROOT_PASSWORD and (optionally) GOOGLE_MAPS_API_KEY
docker compose up --build
```

Open `http://localhost:8080`. MySQL 8, schema creation, and app startup are fully automated (Infrastructure as Code).

### Option C — Zero-dependency demo (no database install)

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

Runs on an embedded H2 file database (data persists in `./data/`). Inspect it live at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/routesharedb;AUTO_SERVER=TRUE`, user `sa`, empty password).

### Demo accounts

On first start with an empty database the seeder creates a demo world. All demo accounts use password **`demo123`**:

| Persona | Role | Notable |
|---|---|---|
| `Giulia` | Driver | GOLD, Fiat 500X, travel + pricing rules configured, completed trip history |
| `Marco` | Driver | SILVER, VW Golf, default pricing |
| `Piera` | Driver | PREMIUM, Mini Cooper, same-destination-only rule |
| `Alice` | Passenger | PREMIUM 4.91 — sails through every policy |
| `Bruno` | Passenger | 3.40 reputation — blocked by Giulia's min-reputation rule (demo the veto!) |
| `Chiara` | Passenger | GOLD — pending booking awaiting Giulia's decision |
| `Davide` | Passenger | Fresh 5.00 — open request, rankable |

### Running Tests

```bash
mvn test
```

All **121 automated tests** should pass (JaCoCo coverage report in `target/site/jacoco/`).

---

##  UML Diagrams

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

##  Project Structure

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

##  Author

**Mohammad Haroon** (560824)  
3rd Year — Software Engineering  
Professor: Salvatore Distefano

---

## 📄 License

This project was developed for academic purposes as part of the Software Engineering course.
