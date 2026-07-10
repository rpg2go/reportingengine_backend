package com.reporting.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.jpa.AvailableHints;
import org.springframework.stereotype.Repository;

import java.util.stream.Stream;

/**
 * Low-level data access component for cursor-backed streaming of analytical
 * query results from PostgreSQL.
 *
 * <p>Unlike the standard JPA repository interfaces in this package, this class
 * uses the {@link EntityManager} directly to execute dynamic native SQL queries
 * generated at runtime by {@code SqlGeneratorService}. The query is configured
 * with Hibernate hints that force a true server-side cursor:</p>
 * <ul>
 *   <li>{@code HINT_FETCH_SIZE = 100} — fetches rows in batches of 100 instead
 *       of materializing the full result set into client memory</li>
 *   <li>{@code HINT_READ_ONLY = true} — disables dirty-checking overhead on
 *       result objects within the persistence context</li>
 *   <li>{@code HINT_CACHEABLE = false} — bypasses the Hibernate second-level
 *       cache entirely for streaming queries</li>
 * </ul>
 *
 * <h3>PostgreSQL Cursor Contract</h3>
 * <p>PostgreSQL only opens a true server-side cursor when <strong>all</strong> of the
 * following conditions hold:</p>
 * <ol>
 *   <li>{@code autoCommit = false} — guaranteed by the caller's
 *       {@code @Transactional} scope</li>
 *   <li>{@code fetchSize > 0} — set via {@link AvailableHints#HINT_FETCH_SIZE}</li>
 *   <li>Forward-only, read-only result set — the default for Hibernate native
 *       queries</li>
 * </ol>
 *
 * <p><strong>Important:</strong> The returned {@code Stream<Object[]>} must be consumed
 * within an active {@code @Transactional} scope. Closing the transaction before the
 * stream is fully consumed will abort the cursor and may throw an exception. The caller
 * must also close the stream when done (preferably via try-with-resources) to release
 * the underlying JDBC cursor and statement resources.</p>
 *
 * @since 1.2.0
 * @see com.reporting.service.PostgresExcelStreamService
 */
@Repository
public class ReportDataRepository {

    /**
     * Number of rows fetched per database round-trip. PostgreSQL opens a true
     * server-side cursor when {@code fetchSize > 0} and {@code autoCommit = false}.
     * A value of 100 balances memory usage against network round-trip overhead.
     */
    static final int STREAMING_FETCH_SIZE = 100;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Opens a forward-only, read-only cursor over the supplied native SQL and
     * returns the results as a lazy {@code Stream<Object[]>}.
     *
     * <p>Each element in the stream is an {@code Object[]} where array positions
     * correspond to the columns in the SQL {@code SELECT} clause. For single-column
     * queries, the scalar value is defensively wrapped in a single-element array to
     * provide a uniform interface.</p>
     *
     * <p>The caller <strong>must</strong>:</p>
     * <ol>
     *   <li>Invoke this method within an active {@code @Transactional(readOnly = true)}
     *       scope to satisfy the PostgreSQL cursor precondition</li>
     *   <li>Close the returned stream when done (via try-with-resources) to release
     *       the underlying JDBC cursor and statement resources</li>
     * </ol>
     *
     * @param sql the native SQL query to execute; must not be {@code null} or blank
     * @return a lazily-evaluated stream of result rows, each as an {@code Object[]}
     * @throws IllegalArgumentException          if {@code sql} is null or blank
     * @throws jakarta.persistence.PersistenceException if the query execution fails
     */
    @SuppressWarnings("unchecked")
    public Stream<Object[]> streamNativeQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL query must not be null or blank");
        }

        Stream<?> rawStream = entityManager.createNativeQuery(sql)
                .setHint(AvailableHints.HINT_FETCH_SIZE, STREAMING_FETCH_SIZE)
                .setHint(AvailableHints.HINT_READ_ONLY, true)
                .setHint(AvailableHints.HINT_CACHEABLE, false)
                .getResultStream();

        // Normalize: single-column queries return scalars; wrap into Object[] for
        // uniform downstream handling in PostgresExcelStreamService
        return rawStream.map(row -> row instanceof Object[] arr ? arr : new Object[]{row});
    }
}
