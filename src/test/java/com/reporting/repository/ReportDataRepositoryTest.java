package com.reporting.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReportDataRepository}.
 *
 * <p>The {@link EntityManager} is mocked to verify that correct Hibernate
 * query hints are applied and that the result stream is properly normalized.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportDataRepository Unit Tests")
public class ReportDataRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private ReportDataRepository repository;

    @BeforeEach
    void setUp() {
        // EntityManager.createNativeQuery() returns a Query that supports chaining
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setHint(anyString(), any())).thenReturn(query);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Input Validation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("rejects null SQL with IllegalArgumentException")
        void nullSql_shouldThrowIllegalArgument() {
            assertThatThrownBy(() -> repository.streamNativeQuery(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null or blank");
        }

        @Test
        @DisplayName("rejects blank SQL with IllegalArgumentException")
        void blankSql_shouldThrowIllegalArgument() {
            assertThatThrownBy(() -> repository.streamNativeQuery("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null or blank");
        }

        @Test
        @DisplayName("rejects empty string SQL with IllegalArgumentException")
        void emptySql_shouldThrowIllegalArgument() {
            assertThatThrownBy(() -> repository.streamNativeQuery(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null or blank");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Hibernate Query Hints
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Hibernate query hints")
    class HibernateQueryHints {

        @Test
        @DisplayName("sets fetch size hint to 100 for server-side cursor")
        void fetchSize_shouldBeSetTo100() {
            // Arrange
            when(query.getResultStream()).thenReturn(Stream.empty());

            // Act
            repository.streamNativeQuery("SELECT 1");

            // Assert
            verify(query).setHint(
                    eq("org.hibernate.fetchSize"),
                    eq(ReportDataRepository.STREAMING_FETCH_SIZE));
        }

        @Test
        @DisplayName("sets read-only hint to true to disable dirty checking")
        void readOnly_shouldBeSetToTrue() {
            // Arrange
            when(query.getResultStream()).thenReturn(Stream.empty());

            // Act
            repository.streamNativeQuery("SELECT 1");

            // Assert
            verify(query).setHint(eq("org.hibernate.readOnly"), eq(true));
        }

        @Test
        @DisplayName("sets cacheable hint to false to bypass L2 cache")
        void cacheable_shouldBeSetToFalse() {
            // Arrange
            when(query.getResultStream()).thenReturn(Stream.empty());

            // Act
            repository.streamNativeQuery("SELECT 1");

            // Assert
            verify(query).setHint(eq("org.hibernate.cacheable"), eq(false));
        }

        @Test
        @DisplayName("applies all three hints on the same query")
        void allHints_shouldBeAppliedTogether() {
            // Arrange
            when(query.getResultStream()).thenReturn(Stream.empty());

            // Act
            repository.streamNativeQuery("SELECT 1");

            // Assert: exactly 3 hint calls
            verify(query, times(3)).setHint(anyString(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Stream Normalization
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stream normalization")
    class StreamNormalization {

        @Test
        @DisplayName("passes through multi-column Object[] rows unchanged")
        void multiColumnResult_shouldPassThrough() {
            // Arrange
            Object[] row = new Object[]{"R1", 100.0, "North"};
            when(query.getResultStream()).thenReturn(Stream.of((Object) row));

            // Act
            Object[] result = repository.streamNativeQuery("SELECT 1").findFirst().orElseThrow();

            // Assert
            assertThat(result).containsExactly("R1", 100.0, "North");
        }

        @Test
        @DisplayName("wraps single-column scalar results into Object[] for uniform handling")
        void singleColumnResult_shouldBeWrappedInArray() {
            // Arrange: Hibernate returns a raw scalar for single-column queries
            when(query.getResultStream()).thenReturn(Stream.of("scalar_value"));

            // Act
            Object[] result = repository.streamNativeQuery("SELECT 1").findFirst().orElseThrow();

            // Assert: scalar is wrapped into Object[1]
            assertThat(result).containsExactly("scalar_value");
        }

        @Test
        @DisplayName("wraps numeric scalar results into Object[] correctly")
        void numericScalar_shouldBeWrappedInArray() {
            // Arrange
            when(query.getResultStream()).thenReturn(Stream.of(42));

            // Act
            Object[] result = repository.streamNativeQuery("SELECT COUNT(*)").findFirst().orElseThrow();

            // Assert
            assertThat(result).containsExactly(42);
        }

        @Test
        @DisplayName("returns empty stream when query produces no results")
        void emptyResult_shouldReturnEmptyStream() {
            // Arrange
            when(query.getResultStream()).thenReturn(Stream.empty());

            // Act
            long count = repository.streamNativeQuery("SELECT 1 WHERE 1=0").count();

            // Assert
            assertThat(count).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SQL Pass-Through
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SQL pass-through")
    class SqlPassThrough {

        @Test
        @DisplayName("passes the exact SQL string to EntityManager.createNativeQuery()")
        void sqlString_shouldBePassedExactly() {
            // Arrange
            String sql = "SELECT row_id, SUM(amount) FROM analytics.fact_sales GROUP BY row_id";
            when(query.getResultStream()).thenReturn(Stream.empty());

            // Act
            repository.streamNativeQuery(sql);

            // Assert
            verify(entityManager).createNativeQuery(sql);
        }
    }
}
