# Rebuttal email — draft

**To:** sdistefano@unime.it
**Subject:** RideNDrive — revised project and report (v2), point-by-point response to your remarks

---

Dear Professor Distefano,

Thank you for your remarks and for the RESILIENT example report. I have first revised and extended the project, then rewritten the report from scratch following the structure and artifacts of the example you provided. Below is a point-by-point response; section and figure numbers refer to the attached report (v2).

**1. "The report is a mess, badly organized and written."**
The previous report was organized by topic rather than by process — you were right. The new report is restructured around the software development process itself, following the three Scrum phases as taught in the course: outline planning and architectural design (Section 3), nine sprint cycles (Section 7), and project closure (Section 12). The old report has been discarded entirely; this is a new document of ~110 pages with 42 figures and 41 tables.

**2. "Organized according to the SDP you choose … a section for each increment … user stories, backlog, burndown chart."**
I adopted Scrum complemented with DevOps practices (choice justified against plan-driven and other agile methods in Section 2, using the course materials). The report now contains: a MoSCoW-prioritized Product Backlog with story points and requirement traceability (Section 5, PB-1…PB-22), a Definition of Ready and Definition of Done, and one section per sprint (Sprints 1–9), each with the five-part structure Planning / Implementation / Testing / Review / Retrospective and closed by its burndown **and** burnup chart (Figures throughout Section 7), plus a velocity chart at closure (Section 12.1). The sprint record is honest rather than idealized: velocity varies with real capacity, and Sprint 4 — which collided with the examination period — missed its goal by 2 story points that were carried into Sprint 5 and delivered there.

**3. "Complex functionalities need to be described algorithmically by proper activity/sequence diagrams."**
Each complex functionality is now specified algorithmically in the sprint that built it: the DFS stop-sequencing algorithm with its five pruning constraints (activity diagram, Fig. 9), the dynamic pricing policy chain (activity diagram, Fig. 13), the rating→reputation workflow (Fig. 16), registration/login sequences (Figs. 19–20), the booking lifecycle finite state machine (UML **state diagram**, Fig. 28 — newly added), the booking-confirmation/notification flow (activity diagram, Fig. 29) and the CI/CD delivery pipeline (Fig. 30).

**4. "Requirements must be uniquely identified, properly described by user stories … and referred throughout the report."**
Section 4 now defines requirements at two levels — user requirements UR-1…UR-10 (problem statements) refined into system requirements FR-1…FR-16 and NFR-1…NFR-8. Every functional requirement is expressed as a user story with a system-level description, verifiable acceptance criteria and MoSCoW priority; NFRs carry measurable metrics. These identifiers are referenced consistently in the backlog, in every sprint's user stories, in the architecture section, and in the verification section, which maps requirements to the automated tests that check them (Section 11); Appendix A carries the full UR→FR→backlog→sprint→test traceability matrix, and a dedicated chapter (Section 9) specifies every complex algorithm with pseudo-code, correctness arguments and complexity analysis.

**5. "Address these comments and revise first your project, properly extending it."**
The project itself was extended in three dedicated revision sprints (Sprints 7–9, 15 June – 12 July), treating your feedback as a Product Owner re-prioritization:
- **Booking lifecycle management (FR-12):** a formally guarded state machine (PENDING → CONFIRMED/REJECTED/CANCELLED → COMPLETED) with driver confirm/decline, passenger withdrawal, trip completion, HTTP 409 on illegal transitions, and status badges/actions in the UI (State pattern; the system now implements 11 documented design patterns).
- **In-app notification centre (FR-13):** Observer-pattern notifications on booking and rating events, with inbox, unread badge and mark-as-read endpoints plus the corresponding UI.
- **DevOps & security hardening:** multi-stage Dockerfile and docker-compose one-command deployment, secrets externalized to environment variables (the previously committed API key was revoked), a credential-hash disclosure fixed, and the CI pipeline extended with coverage/jar artifacts and a Docker build stage.
- **Driver-owned policy engine (Sprint 9):** your proposal-review remark — *"push more on customizability, but not parametric ones but rule/policy customizability, letting the owner define her pricing and traveling policy"* — is now implemented literally. Drivers author their own **travel rules** (minimum passenger reputation, same-destination-only, no large luggage) and their own **pricing rules** (own base rate, surcharges, and a loyalty discount for high-tier riders). Candidates violating a rule are vetoed with an audited violation list; the remaining ones are **ranked best-first** with reputation-driven priority — completing the reputation + incentive mechanism you suggested (FR-15/FR-16 in §4.1b, Sprint 9 in §7, Figure 36). Section 4.4 additionally self-assesses every functionality against your three-category rubric (CRUD / third-party / algorithmically complex) with explicit motivations.
- **User interface redesign (Sprint 8):** the interface was rebuilt around a task-centred information architecture (search-first for passengers, trip management for drivers), with rating eligibility restricted to completed trips, styled non-blocking dialogs, and a mobile-responsive layout — still framework-free vanilla HTML/CSS/JS.
- The automated test suite grew from 76 to 121 tests (all green, 84% JaCoCo coverage), including exhaustive verification of the new state machine.

**6. On the example report.**
I followed its structure and artifacts as instructed, adapted to a DevOps-oriented project, and omitted the OOP part as you indicated. One honest adaptation is documented in Section 2.3: since my colleagues were engaged in other examinations, the project was carried out individually, so Scrum roles were adapted for a single developer (ceremonies self-managed and documented; the CI pipeline acting as the always-on integration reviewer). I hope this adaptation is acceptable — I am of course available to discuss it.

The revised report is attached (Word and PDF). The extended project is available at https://github.com/SupraflyX/RideNDrive.

Thank you again for the detailed feedback — restructuring the work around the process genuinely improved the project.

Best regards,
Mohammad Haroon (560824)
