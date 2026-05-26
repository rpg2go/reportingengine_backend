# Java + Spring Boot Test Implementation Prompt

You are a Senior Java Engineer, Test Automation Architect, QA Engineer, and Spring Boot Expert.

Your task is to implement a comprehensive automated testing strategy for this Java + Spring Boot backend application.

The goal is to ensure the application is production-ready and that the entire codebase is validated through reliable, maintainable, enterprise-grade automated tests.

You must analyze the existing codebase and IMPLEMENT missing tests, IMPROVE weak tests, and IDENTIFY gaps in coverage.

Use your existing skills and agents functionalities to achieve the goal.

---

# Main Objectives

Implement:

1. Unit Tests
2. Integration Tests
3. Repository Tests
4. Controller/API Tests
5. Service Layer Tests
6. Security Tests
7. Validation Tests
8. Error Handling Tests
9. Database Integration Tests
10. Messaging/Event Tests
11. Contract Tests (if applicable)
12. End-to-End Flow Tests
13. Performance-Sensitive Tests
14. Regression Tests

The implementation should validate:

- correctness
- stability
- resilience
- edge cases
- failure scenarios
- security behavior
- transactional consistency

---

# Mandatory Requirements

## General Testing Standards

Use:

- JUnit 5
- Mockito
- AssertJ
- Spring Boot Test
- Testcontainers
- MockMvc or WebTestClient
- WireMock (for external APIs)
- Awaitility (for async testing)
- Jacoco

Tests must:

- be deterministic
- be isolated
- avoid flaky behavior
- avoid shared mutable state
- avoid unnecessary sleeps
- run independently
- support parallel execution where possible

Prefer:

- constructor injection
- builder patterns for test data
- reusable test fixtures
- clear Arrange / Act / Assert structure

---

# Expected Deliverables

Implement and/or generate:

1. Missing unit tests
2. Missing integration tests
3. Missing edge-case tests
4. Missing failure-path tests
5. Test utilities/helpers
6. Base test classes
7. Test fixtures/builders
8. Mocking strategy improvements
9. Testcontainers setup
10. Coverage report configuration
11. CI test execution strategy
12. Recommended test pyramid

At the end provide:

- Current estimated coverage
- Recommended target coverage
- Untested critical areas
- High-risk modules lacking tests
- Flaky test risks
- Suggested improvements

---

# 1. Unit Test Implementation

Implement unit tests for:

## Services

Validate:

- business logic
- edge cases
- null handling
- invalid inputs
- exception handling
- retries
- fallback logic
- transactional logic
- mapping logic

Check for:

- hidden side effects
- improper mocking
- missing assertions
- weak validations

Requirements:

- isolate dependencies with Mockito
- verify interactions only when meaningful
- avoid overmocking
- prefer behavior verification over implementation details

---

## Utility Classes

Implement tests for:

- parsers
- converters
- validators
- mappers
- helper classes
- date/time logic
- formatting logic

Include:

- boundary testing
- invalid formats
- locale/timezone scenarios

---

## Security Components

Test:

- JWT validation
- token expiration
- authorization rules
- role validation
- custom security filters
- authentication providers

Include:

- invalid token tests
- expired token tests
- privilege escalation attempts

---

# 2. Integration Test Implementation

Implement full Spring Boot integration tests.

Use:

- @SpringBootTest
- Testcontainers
- Real database instances
- Minimal mocking

Validate:

- application context startup
- bean wiring
- database integration
- transaction behavior
- configuration correctness
- serialization/deserialization
- external integration behavior

---

## Database Integration Tests

Use Testcontainers for:

- PostgreSQL
- MySQL
- Oracle
- MongoDB
- Kafka
- Redis
  (as applicable)

Validate:

- migrations
- repositories
- query correctness
- transaction rollback
- cascade behavior
- locking
- indexing assumptions
- pagination
- optimistic/pessimistic locking

Check for:

- N+1 query issues
- lazy loading failures
- entity mapping issues

---

## Repository Tests

Implement:

- @DataJpaTest
- query validation
- custom query testing
- specification testing
- pagination testing

Validate:

- sorting
- filtering
- joins
- complex queries
- projection behavior

---

# 3. API / Controller Tests

Implement:

- MockMvc or WebTestClient tests

Validate:

## Success Scenarios

- valid requests
- correct responses
- status codes
- headers
- serialization

## Failure Scenarios

- validation failures
- malformed payloads
- unauthorized access
- forbidden access
- not found
- conflicts
- rate limiting
- invalid parameters

Validate:

- error response consistency
- global exception handling
- API contracts
- OpenAPI compatibility

---

# 4. Security Testing

Implement tests for:

## Authentication

- login success/failure
- invalid credentials
- expired tokens
- token tampering

## Authorization

- role-based access
- forbidden endpoints
- privilege escalation attempts

## Input Security

- SQL injection attempts
- XSS payloads
- invalid JSON
- oversized payloads

## Configuration Security

- actuator exposure
- public endpoints
- CORS behavior

---

# 5. External API Integration Tests

For all external integrations:

Use:

- WireMock
- MockWebServer

Validate:

- retries
- timeouts
- fallback behavior
- partial failures
- malformed responses
- slow responses
- unavailable services

Check:

- resilience
- circuit breaker behavior
- retry exhaustion

---

# 6. Messaging / Async Tests

If using:

- Kafka
- RabbitMQ
- Pub/Sub
- Async events

Implement tests for:

- message publishing
- message consumption
- retries
- dead-letter queues
- idempotency
- duplicate handling
- ordering assumptions

Use:

- Awaitility
- Testcontainers

Validate:

- eventual consistency
- async processing correctness

---

# 7. Transaction & Concurrency Tests

Implement tests for:

- concurrent updates
- race conditions
- optimistic locking
- deadlocks
- duplicate requests
- idempotency

Validate:

- transactional boundaries
- rollback behavior
- consistency guarantees

---

# 8. Validation Tests

Implement tests for:

- Bean Validation
- DTO validation
- custom validators

Include:

- nulls
- empty values
- max/min limits
- invalid formats
- invalid enums
- nested object validation

---

# 9. Error Handling & Resilience Tests

Implement tests for:

- unexpected exceptions
- dependency failures
- DB outages
- timeout scenarios
- partial failures
- retry exhaustion

Validate:

- graceful degradation
- fallback behavior
- proper logging
- proper error responses

---

# 10. Performance-Sensitive Tests

Implement targeted tests for:

- large payloads
- pagination limits
- memory-heavy operations
- batch processing
- streaming logic

Check for:

- excessive execution time
- memory risks
- blocking calls

---

# 11. Test Quality Rules

Tests MUST NOT:

- rely on execution order
- use real external services
- contain hardcoded sleeps
- use shared databases
- leak state between tests

Tests SHOULD:

- be readable
- use descriptive naming
- follow AAA pattern
- minimize boilerplate
- maximize reliability

Naming convention:

- shouldDoXWhenY
- givenX_whenY_thenZ

---

# 12. Coverage Expectations

Target:

- Service layer:
    - 90%+ meaningful coverage

- Controllers:
    - 85%+

- Utilities:
    - 95%+

- Critical business flows:
    - near 100%

Coverage must prioritize:

- logic quality
- edge cases
- failure paths

NOT artificial coverage.

---

# 13. CI/CD Recommendations

Provide recommendations for:

- Maven/Gradle test stages
- Parallel test execution
- Coverage gates
- Mutation testing
- Test reporting
- Flaky test detection

Include:

- Jacoco setup
- Surefire/Failsafe configuration
- GitHub Actions/GitLab/Jenkins integration

---

# 14. Additional Instructions

- Be extremely thorough.
- Think like a QA engineer validating a mission-critical banking application.
- Prioritize reliability and maintainability.
- Prefer enterprise-grade patterns.
- Refactor tests when needed.
- Identify hidden testing gaps.
- Add missing negative-path scenarios.
- Validate production-like behavior.

If tests cannot be implemented because code is tightly coupled or poorly designed:

- explain why
- identify architectural problems
- suggest refactoring

Do not generate superficial happy-path-only tests.

Implement robust, production-grade automated tests.
