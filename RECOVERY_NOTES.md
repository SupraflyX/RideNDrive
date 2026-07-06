# RouteShare — Corruption Recovery Notes (2026-07-06)

## What happened
18 tracked files were corrupted by what looks like an unclean shutdown / power loss
(trailing zero-byte fill that preserved file size, plus mid-line truncation, hitting the
most recently modified Sprint 9 files across both `src/` and `target/`). The project would
not compile in this state. Git history was intact (`git fsck` clean), and `target/` held a
complete compiled build from just before the corruption — which made full recovery possible.

## How each file was recovered
- **NUL-padded, content intact** (stripped trailing zeros): `README.md`*, `AuthController.java`,
  `UserService.java`.
  *README tail was also lost; spliced back from a clean git blob, keeping the newer Sprint 9 intro.
- **pom.xml** — truncated in `<build>`; spliced the standard build section from git HEAD.
  Kept the new Sprint 9 deps (actuator, springdoc). Validated as well-formed XML.
- **index.html** — truncated at the modals section; restored from the complete copy in
  `target/classes/static/` (identical Sprint 9 content, plus the missing tail + closing tags).
- **12 truncated `.java` files** — recovered by decompiling their intact `.class` files from
  `target/classes` (CFR decompiler): BookingController, TripOfferController, TripPlanningController,
  RideRequest, RatingRepository, RideRequestRepository, TripOfferRepository, UserRepository,
  BookingLifecycleService, GoogleMapsMappingService, PricingEngine.
- **IntegrationCoverageTest.java** — restored exactly from a dangling git blob.
- **BookingLifecycleIntegrationTest.java** — no class/blob existed; the ~3 lost lines (final
  assertion of the `@Order(21)` test + closing braces) were reconstructed by mirroring the
  complete `@Order(20)` test above it.

## Caveats
- The 12 decompiled files are **logically faithful** (from the exact bytecode that compiled) but
  **lost their original comments and formatting**, and method parameter names on repository
  interfaces show as `var1/var2` (cosmetic; does not affect behavior). Spring MVC parameter names
  (`@PathVariable`, `@RequestParam`) were preserved.
- Explicit `(Object)`/`(HttpStatusCode)` casts added by the decompiler are legal and compile fine.

## Verify on your machine
```
mvn clean test
```
Expected: **Tests run: 118, Failures: 0, Errors: 0** (first run downloads the new dependencies).

## Backup
A full pre-recovery backup of the folder (excluding node_modules) was saved to the outputs area
before any changes were made.
