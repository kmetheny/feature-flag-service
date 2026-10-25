# Feature Flag Service — Spring Boot Practice Project

**Stack:** Java 25 (Temurin or Corretto) · Spring Boot 4.x · Maven

**Why:** Get current on modern Spring (last used in 2022, Boot 2 era) and be ready for any job that requires it. The
finished repo doubles as portfolio and interview material.

**The idea:** A REST API that stores feature flags (name, description, enabled, rollout percentage, target environments)
and answers "is flag X on for user Y in environment Z?"

---

## Milestones

### 1. Bootstrap and CRUD

- [x] Project from start.spring.io with Web, Data JPA, Validation, H2, Actuator
- [x] `Flag` entity, `FlagRepository` (Spring Data JPA), service layer
- [x] `@RestController` with GET / POST / PUT / DELETE
- [x] Constructor injection everywhere (no field `@Autowired`)

> **Notes:** Put `@RequestMapping("/flags")` on the class, since `@RestController("/flags")` sets the bean name, not a
> path. Use plural resource names, with `GET /flags` for the collection (no `/list`). Leave method-level paths empty
> instead of `"/"`, because trailing-slash matching is off since Boot 3. Path variables need `@PathVariable`.

### 2. Validation and error handling

- [x] Request DTOs as records (no `id`, keep entities out of the API contract)
- [x] `@Valid @RequestBody` on controller parameters
- [x] `@RestControllerAdvice` returning consistent errors (consider `ProblemDetail`)
- [x] Not found → 404 via `Optional` from `findById` or a custom exception
- [x] POST returns 201 with a `Location` header and the created resource

> **Notes:** Use wrapper types (`Boolean`, `Integer`) for required fields so `@NotNull` can catch missing values.
> Without the validation starter, `@Valid` silently does nothing.

### 3. Evaluation endpoint

- [x] `GET /flags/{name}/evaluate?userId=...&env=...` returns true/false
- [x] Deterministic rollout: hash `flagName + userId`, mod 100, compare to rollout percentage
- [x] Unit tests for the evaluation logic

### 4. Profiles and config

- [x] `application.yml` with `dev` and `prod` profiles
- [x] Dev on H2, prod on a real database (Oracle or Postgres via Docker)
- [x] Custom config block bound with `@ConfigurationProperties`

### 5. Database migrations

- [ ] Add Flyway
- [ ] Turn off `ddl-auto`, manage the schema with versioned SQL scripts

### 6. Caching

- [ ] `@Cacheable` on flag lookups
- [ ] `@CacheEvict` on updates and deletes
- [ ] Verify eviction actually works

### 7. Testing

- [x] Service: JUnit 5 + Mockito, no Spring context
- [x] Controller: `@WebMvcTest` + MockMvc
- [x] Repository: `@DataJpaTest`
- [x] One `@SpringBootTest` integration test
- [ ] Optional: Testcontainers for a real database

### 8. Ops polish

- [ ] Expose Actuator health and metrics
- [ ] Custom `HealthIndicator`
- [ ] Dockerfile
- [ ] Liveness / readiness probes (`/actuator/health/liveness`, `/actuator/health/readiness`)
- [ ] Optional: deploy to a local cluster with kind or minikube

---

## Stretch goals

- [ ] Spring Security on write endpoints (`SecurityFilterChain` bean, API key filter or basic auth)
- [ ] Audit log with JPA entity listeners
- [ ] React + Material UI page for toggling flags

**Core target:** Milestones 1–4 and 7 cover most of what day-to-day Spring Boot jobs use.

---

## Interview prep: be able to explain

- [ ] How dependency injection works, bean scopes, why constructor injection is preferred
- [ ] How auto-configuration decides what to wire (`@ConditionalOnClass`, `@ConditionalOnMissingBean`)
- [ ] `@Transactional` pitfalls: self-invocation bypasses the proxy, rollback rules for checked vs. unchecked exceptions
- [ ] JPA traps: N+1 queries, lazy loading exceptions, fetch joins / entity graphs
- [ ] Which test slice to use when, and why `@SpringBootTest` everywhere is slow
- [ ] Boot 2 → 3 changes: `javax` → `jakarta`, Java 17 baseline, `SecurityFilterChain` replacing
  `WebSecurityConfigurerAdapter`, `requestMatchers`, `ProblemDetail`, `RestClient`
- [ ] Boot 3 → 4 changes: Spring Framework 7, JSpecify null-safety, API versioning, `@HttpExchange` clients, modular
  auto-config, Jackson 3

> **Tip:** Trigger the `@Transactional` self-invocation problem on purpose in this project and watch it fail. Seeing it
> break is what makes it stick.

## Portfolio checklist

- [ ] Public GitHub repo
- [ ] README explaining the design and how to run it
- [x] Real tests that pass
- [ ] Dockerfile