# Report Template Engine — Testing Specification & LLM Guideline

This document defines the architecture, patterns, naming conventions, and best practices for writing automated tests in the Report Template Engine backend. Any future LLM or developer adding tests to this project **MUST** follow these guidelines.

---

## 🛠️ Testing Stack & Libraries

* **Testing Framework**: JUnit 5 (Jupiter)
* **Mocking Framework**: Mockito
* **Assertion Library**: AssertJ (`assertThat(...)`)
* **Spring Boot Context Testing**: Spring Boot Test & MockMvc
* **Database Integration**: Testcontainers (PostgreSQL) with a fallback to the active local PostgreSQL database container.
* **Code Coverage**: Jacoco (target: 90%+ for service layer, 85%+ for controllers, 95%+ for utilities).

---

## 📂 Testing Directory Structure

All test sources must reside in `src/test/java`, paralleling the package structure of `src/main/java`:

```
src/test/java
├── com.reporting
│   ├── BaseIT.java                           <-- Base Integration Test Class
│   ├── controller
│   │   ├── AuthControllerTest.java           <-- Unit/Security Controller Test
│   │   ├── ReportControllerTest.java         <-- Unit/API Controller Test
│   │   └── ReportControllerIT.java           <-- Integration Controller/Security Test
│   └── service
│       ├── DateUtilsTest.java                <-- Utility Unit Test
│       ├── ExcelParserServiceTest.java       <-- Service Parser Unit Test
│       ├── LayoutRendererServiceTest.java    <-- Service Excel Generation Unit Test
│       ├── PostProcessorServiceTest.java     <-- Service Formula Processor Unit Test
│       ├── SemanticResolverServiceTest.java  <-- Service Metadata Resolver Unit Test
│       ├── SqlGeneratorServiceTest.java      <-- Service SQL Generator Unit Test
│       ├── ReportConfigServiceTest.java      <-- Service Config Unit Test
│       ├── ReportConfigServiceIT.java        <-- Service Config Integration Test
│       └── ReportRunnerServiceIT.java        <-- E2E Runner Pipeline Integration Test
src/test/resources
└── application-test.properties               <-- Test environment properties
```

---

## 📝 Test Class Naming Conventions

* **Unit Tests**: Suffixed with `Test`, e.g., [DateUtilsTest](file:///G:/workspace/ReportTemplate_BackEnd/src/test/java/com/reporting/service/DateUtilsTest.java) for [DateUtils](file:///G:/workspace/ReportTemplate_BackEnd/src/main/java/com/reporting/service/DateUtils.java).
* **Integration Tests**: Suffixed with `IT`, e.g., [ReportConfigServiceIT](file:///G:/workspace/ReportTemplate_BackEnd/src/test/java/com/reporting/service/ReportConfigServiceIT.java) for [ReportConfigService](file:///G:/workspace/ReportTemplate_BackEnd/src/main/java/com/reporting/service/ReportConfigService.java).
* **Test Methods**: Follow the descriptive convention:
  `methodName_should_expectedBehavior_when_scenario`
  *Example*: `getPeriodBoundaries_weekOffsetMinusOne_shouldReturnPreviousFullWeek()`

---

## ⚙️ Step-by-Step Guidelines for LLMs

### 1. Writing a New Unit Test (Isolated Class under Test)

When creating unit tests, keep dependencies isolated with Mockito and avoid bootstrapping Spring context (`@SpringBootTest` is for integration tests only).

1. **Annotate the Test Class**:
   Use `@ExtendWith(MockitoExtension.class)` and `@DisplayName("ClassUnderTest Unit Tests")`.
2. **Inject Mocks**:
   Use `@Mock` for collaborator dependencies and `@InjectMocks` on the class under test.
3. **Use Arrange-Act-Assert (AAA)**:
   * **Arrange**: Setup mock stubbing behavior (e.g. `when(dependency.method()).thenReturn(...)`) and prepare input parameters.
   * **Act**: Call the method under test.
   * **Assert**: Verify outputs using AssertJ `assertThat(...)` and verify collaborator interactions only when meaningful (avoid over-mocking).
4. **Cover Positive and Negative Paths**:
   * Test successful outcomes.
   * Test null handling, empty parameters, boundary checks, and expected exceptions (`assertThatThrownBy(() -> classUnderTest.method()).isInstanceOf(...)`).

#### Example Unit Test Skeleton:
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("MyService Unit Tests")
public class MyServiceTest {

    @Mock
    private MyCollaborator collaborator;

    @InjectMocks
    private MyService service;

    @Test
    @DisplayName("doSomething should return processed string when input is valid")
    public void doSomething_shouldReturnProcessedString_whenInputIsValid() {
        // Arrange
        when(collaborator.fetchData("input")).thenReturn("mock-data");

        // Act
        String result = service.doSomething("input");

        // Assert
        assertThat(result).isEqualTo("processed-mock-data");
        verify(collaborator).fetchData("input");
    }
}
```

---

### 2. Writing a New Integration Test (Database or Controller)

Integration tests require a running database schema and application context.

1. **Extend BaseIT**:
   Always extend [BaseIT](file:///G:/workspace/ReportTemplate_BackEnd/src/test/java/com/reporting/BaseIT.java). This handles starting the Testcontainers PostgreSQL DB or falling back to the local database, applying migrations, and loading properties.
2. **Handle Database State (Transactional Tests)**:
   Annotate test classes or methods with `@Transactional` so database modifications roll back automatically after each test runs, keeping the tests isolated and independent.
3. **API Validation**:
   Use `MockMvc` (autowired from `BaseIT`) to perform endpoint calls and assert JSON structures, headers, and HTTP status codes.
4. **Security Testing**:
   * To test protected endpoints (`/api/**`), authenticate requests in MockMvc using:
     * `.with(httpBasic("admin", "password"))`
     * Or annotate the test with `@WithMockUser(username = "admin", roles = {"USER"})`.

#### Example Integration Test Skeleton:
```java
@DisplayName("MyController Integration Tests")
public class MyControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin", roles = {"USER"})
    @DisplayName("GET /api/resource should return ok status and data list")
    public void getResource_shouldReturnOkAndData() throws Exception {
        mockMvc.perform(get("/api/resource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
```

---

## ⚠️ Common Gotchas & Avoidance Guidelines

1. **Empty Context Short-Circuit in Formula Evaluation**:
   In [PostProcessorService](file:///G:/workspace/ReportTemplate_BackEnd/src/main/java/com/reporting/service/PostProcessorService.java), `evaluateFormula(formula, context)` returns `0.0` immediately if the context map is empty. When testing simple math expressions that do not require variables, **always pass a dummy key-value pair in the context** (e.g. `Map.of("dummy", 0.0)`) to avoid this guard check.
2. **Case Sensitivity in Exp4J Variables**:
   Variable lookups inside `ExpressionBuilder` are case-sensitive. If your context keys are uppercase (e.g. `R1`), the formula variables **must** match that casing (e.g. `R1` instead of `r1`). Ensure test contexts align with formula cases.
3. **Avoid Hardcoded Thread.sleep()**:
   If testing asynchronous events, utilize `Awaitility` to await condition matches dynamically instead of using sleeps, preserving test execution speed.
4. **SQL Injection Checks**:
   Ensure whitelists and parameter safety checks are preserved. If adding input parameters to controllers, write tests verifying that malformed strings or SQL keywords (e.g. `;`, `--`, `UNION`) return `400 Bad Request` or throw `IllegalArgumentException`.
