# Feature Flag Service

A REST API for managing feature flags and evaluating them per user and environment, with percentage-based rollouts.
It's built with Java 25 and Spring Boot 4.

```
GET /flags/new-checkout/evaluate?userId=user-42&env=staging
→ { "flag": "new-checkout", "userId": "user-42", "environment": "staging", "enabled": true }
```

## Features

- **CRUD for flags**: name, description, on/off switch, target environments, rollout percentage (0–100)
- **Deterministic rollouts**: a given user always gets the same answer for a given flag, with no stored per-user state
- **Configurable environments**: the set of valid environments comes from configuration, and requests naming an
  unknown environment are rejected
- **Validation and consistent errors**: bean validation on request DTOs, with every error returned as an
  RFC 9457 `ProblemDetail`
- **Pagination and sorting**: `Pageable` binding, an allow-list of sortable fields, a capped page size, and
  RFC 8288 `Link` headers
- **Three database setups**: in-memory H2, file-backed H2, or PostgreSQL started automatically through Docker Compose

## Tech stack

| Area        | Choice                                                         |
|-------------|----------------------------------------------------------------|
| Language    | Java 25                                                        |
| Framework   | Spring Boot 4 (Spring MVC, Spring Data JPA, Bean Validation)   |
| Persistence | Hibernate, PostgreSQL 18, H2                                   |
| Null safety | JSpecify (`@NullMarked` packages)                              |
| Testing     | JUnit 5, Mockito, MockMvc, `@WebMvcTest`, `@DataJpaTest`       |
| Build       | Maven (wrapper included)                                       |

## Running locally

Requires JDK 25. Docker is only needed for the Postgres profile. On Windows, use `mvnw.cmd` instead of `./mvnw`.

| Profile   | Database                          | Command                                                   |
|-----------|-----------------------------------|-----------------------------------------------------------|
| (default) | H2 in-memory, reset on restart    | `./mvnw spring-boot:run`                                  |
| `dev`     | H2 file in `./data`, H2 console on | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`   |
| `local`   | PostgreSQL via Docker Compose     | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` |
| `prod`    | External, from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `./mvnw spring-boot:run -Dspring-boot.run.profiles=prod` |

With the `local` profile, Spring Boot's Docker Compose support starts the Postgres container from `docker-compose.yaml`
and wires up the connection, so there's nothing to configure by hand. With `dev`, the H2 console is at
`http://localhost:8080/h2-console`.

The app listens on `http://localhost:8080`.

## API

| Method   | Path                                          | Description                          | Success |
|----------|-----------------------------------------------|--------------------------------------|---------|
| `GET`    | `/flags?page=0&size=20&sort=name,asc`         | List flags (paged and sorted)        | 200     |
| `GET`    | `/flags/{id}`                                 | Get one flag                         | 200     |
| `POST`   | `/flags`                                      | Create a flag                        | 201 + `Location` |
| `PUT`    | `/flags/{id}`                                 | Replace a flag                       | 200     |
| `DELETE` | `/flags/{id}`                                 | Delete a flag                        | 204     |
| `GET`    | `/flags/{name}/evaluate?userId=...&env=...`   | Is this flag on for this user?       | 200     |

### Create a flag

```bash
curl -i -X POST http://localhost:8080/flags \
  -H 'Content-Type: application/json' \
  -d '{
        "name": "new-checkout",
        "description": "Redesigned checkout flow",
        "enabled": true,
        "environments": ["dev", "staging"],
        "rolloutPercentage": 50
      }'
```

```
HTTP/1.1 201
Location: http://localhost:8080/flags/1

{"id":1,"name":"new-checkout","description":"Redesigned checkout flow","environments":["dev","staging"],"enabled":true,"rolloutPercentage":50}
```

Flag names and environment names must be lowercase letters, digits, `.`, `_` or `-`. Valid environments default to
`dev`, `staging` and `prod`, and you can change them with the `flags.environments` property.

### Evaluate a flag

```bash
curl 'http://localhost:8080/flags/new-checkout/evaluate?userId=user-42&env=staging'
```

```json
{ "flag": "new-checkout", "userId": "user-42", "environment": "staging", "enabled": true }
```

### List flags

```bash
curl -i 'http://localhost:8080/flags?page=0&size=2&sort=rolloutPercentage,desc'
```

The body holds the page content and metadata (`content`, `page.size`, `page.number`, `page.totalElements`,
`page.totalPages`), and the `Link` header carries `first`, `prev`, `next` and `last` URLs. You can sort by `name`,
`description`, `enabled` or `rolloutPercentage`, and the default sort is `name`. The page size is capped at 200.

### Errors

Every error is a `ProblemDetail`. Validation failures also list the problem with each field:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/flags",
  "errors": { "rolloutPercentage": "must be less than or equal to 100" }
}
```

| Status | When                                                                 |
|--------|----------------------------------------------------------------------|
| 400    | Validation failure, malformed JSON, unknown environment, invalid sort field |
| 404    | No flag with that id or name                                         |
| 409    | A flag with that name already exists                                 |

## Design decisions

**How rollouts are evaluated.** A flag is on for a user when all three of these are true:

1. The flag is enabled.
2. The requested environment is one of the flag's environments.
3. The user's bucket is below the rollout percentage.

The bucket is `floorMod(hash(flagName + ":" + userId), 100)`.

- *It's deterministic without storing per-user state.* `String.hashCode()` is defined by the Java spec, so a user lands
  in the same bucket on every request, on every instance, and across restarts.
- *Including the flag name spreads users across flags.* Without it, the same users would be first in line for every
  rollout.
- *Raising the percentage only adds users.* A user's bucket never changes, so going from 10% to 25% keeps the
  original 10% and adds more.

**Keeping the API separate from the entity.** Requests and responses are Java records (`FlagRequest`,
`FlagResponse`), so the JPA entity never shows up in the API contract. Required fields use wrapper types (`Boolean`,
`Integer`) so a missing value fails `@NotNull` instead of quietly becoming `false` or `0`. A composed constraint
annotation (`@FlagKey`) keeps the name rules in one place for flags, environments and configuration.

**Avoiding N+1 queries.** Single-flag lookups load `environments` with an `@EntityGraph`. Paged lists use
`@BatchSize` instead, because combining a fetch join on a collection with paging makes Hibernate apply the limit in
memory. A `@DataJpaTest` checks the batch fetching.

**Stable paging.** `id` is always appended as the final sort key, so rows with equal sort values can't move between
pages. Nulls sort last.

**Transactions.** The service is `@Transactional`, and reads are marked `readOnly`. Open Session in View is turned
off, so lazy loading can't happen in the web layer by accident.

**Uniqueness enforced twice.** The service checks for a duplicate name up front to return a clear 409. A unique
constraint in the database catches concurrent creates that slip past that check, and those are also mapped to 409.

## Tests

```bash
./mvnw test
```

| Layer       | Class                  | Approach                                                        |
|-------------|------------------------|-----------------------------------------------------------------|
| Evaluation  | `FlagEvaluatorTest`    | Plain unit tests: boundaries, determinism, proportional distribution |
| Service     | `FlagServiceTest`      | JUnit + Mockito, no Spring context                              |
| Web         | `FlagControllerTest`   | `@WebMvcTest` slice: binding, validation, error mapping, `Link` headers |
| Persistence | `FlagRepositoryTest`   | `@DataJpaTest` slice: queries, batch fetching, null ordering, unique constraint |
| End to end  | `FlagIntegrationTest`  | `@SpringBootTest` + MockMvc against the full application        |

## Project structure

```
src/main/java/org/kyle/featureflagservice
├── FeatureFlagServiceApplication.java
├── GlobalExceptionHandler.java       # exceptions → ProblemDetail
└── flag/                             # feature package: entity, repository, service, controller, DTOs
    ├── FlagController.java
    ├── FlagService.java
    ├── FlagEvaluator.java            # rollout bucketing
    ├── FlagRepository.java
    ├── Flag.java
    ├── FlagRequest.java / FlagResponse.java / EvaluationResponse.java
    ├── FlagProperties.java           # @ConfigurationProperties("flags")
    └── FlagKey.java                  # composed validation constraint
```

## Roadmap

- [ ] Flyway migrations instead of `ddl-auto`
- [ ] Caching flag lookups, with eviction on update and delete
- [ ] Filtering the list by `enabled`, environment and partial name (JPA Specifications)
- [ ] Actuator health and metrics, a custom `HealthIndicator`, liveness and readiness probes
- [ ] Dockerfile and Kubernetes manifests
- [ ] Testcontainers for running integration tests against Postgres
- [ ] Spring Security on write endpoints
- [ ] Audit log
- [ ] React + Material UI admin page
