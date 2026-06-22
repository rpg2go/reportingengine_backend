# Spec: Edge Case Validation

## Objective
Ensure the reporting engine handles mathematical edge cases, circular dependencies, and missing metrics gracefully. This spec covers the implementation details, test coverage, and validation rules to guarantee robustness.

## Tech Stack
* Java 17 / Spring Boot v3.2.4
* exp4j (Expression Evaluation Library)
* JUnit 5 / Mockito (Testing)

## Commands
* Run Backend Tests: `./maven/apache-maven-3.9.6/bin/mvn clean test`

## Project Structure
* `src/main/java/com/reporting/service/PostProcessorService.java` (Formula execution)
* `src/main/java/com/reporting/service/ReportValidationService.java` (Configuration schema and syntax checks)
* `src/test/java/com/reporting/service/PostProcessorServiceTest.java` (Unit tests)
* `src/test/java/com/reporting/service/ReportValidationServiceTest.java` (Unit tests)

## Code Style
Standard JUnit 5 unit tests with AssertJ assertions:
```java
@Test
@DisplayName("evaluateFormula handles division by zero safely")
public void evaluateFormula_divisionByZero_returnsZero() {
    double val = service.evaluateFormula("R1 / R2", Map.of("R1", 10.0, "R2", 0.0));
    assertThat(val).isEqualTo(0.0);
}
```

## Testing Strategy
We will implement unit tests covering:
1. **Mathematical Edge Cases (PostProcessorService)**:
   * Division by zero (`R1 / R2` with `R2 = 0.0`).
   * Division by zero using constant denominator (`10 / 0`).
   * Division resulting in NaN or Infinity (handled gracefully as `0.0`).
   * Missing metrics (row or column in formula context is missing / null).
2. **Circular Reference Validation (ReportValidationService)**:
   * Self-circular row reference (e.g., `R1` formula references `R1`).
   * Multi-hop circular row reference (e.g., `R1` -> `R2` -> `R3` -> `R1`).
   * Self-circular column reference (e.g., `C1` formula references `C1`).
   * Multi-hop circular column reference (e.g., `C1` -> `C2` -> `C1`).
3. **Empty/Missing Metric Validation (ReportValidationService)**:
   * Data rows with missing or blank `measure_definition`.
   * Calc rows with blank `formula_expr`.
   * Calc columns with blank `formula_expr`.

## Boundaries
* **Always**: Write tests before/during execution, verify that all maven checks pass.
* **Ask first**: Making changes to the `exp4j` library or parsing mechanisms.
* **Never**: Remove existing tests, suppress circular reference exceptions or arithmetic crashes silently without returning correct values/validation errors.

## Success Criteria
- [ ] All new tests for divide-by-zero, cyclic references, and missing metrics pass.
- [ ] Validation errors are reported as `CRITICAL` for cycles and missing configurations.
- [ ] Division by zero returns `0.0` and does not crash execution.
- [ ] No regression in existing tests (total test count increases from 83 to 90+).
