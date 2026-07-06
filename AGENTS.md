# Project Agent Instructions

## Flutter Architecture

All Flutter app work in `dashboard/` must follow clean architecture with package-level feature boundaries.

- Keep `dashboard/lib/main.dart` as the entry point only.
- Put shared infrastructure in `dashboard/packages/core`.
- Put each feature in its own local package under `dashboard/packages/feature_*`.
- Inside each feature package use:
  - `domain/entities`
  - `domain/repositories`
  - `domain/usecases`
  - `data/models`
  - `data/repositories`
  - `presentation/pages` or `presentation/widgets`
- Domain code must not import Flutter, HTTP clients, or data models.
- Presentation may depend on domain use cases/entities, not on raw HTTP clients.
- Data repositories may depend on `core` and convert DTO/models into domain entities.
- Root app composition may wire dependencies manually until a DI/state-management package is intentionally introduced.
