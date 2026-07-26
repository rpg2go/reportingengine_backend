package com.reporting.service;

import com.reporting.dto.Enums.ColType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TimeWindowResolver Unit Tests")
class TimeWindowResolverTest {

    private final LocalDate refDate = LocalDate.of(2026, 7, 15); // Wednesday

    @Nested
    @DisplayName("Validation and Exception Handling")
    class ValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when colType is null")
        void shouldThrowWhenColTypeIsNull() {
            assertThatThrownBy(() -> TimeWindowResolver.resolveBoundaries(refDate, null, 0, 1, "WEEK"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("colType cannot be null");
        }
    }

    @Nested
    @DisplayName("WTD (Week to Date) Boundary Tests")
    class WtdTests {

        @Test
        @DisplayName("WTD with offset 0 should start on Monday and end on refDate")
        void wtdOffsetZero() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.WTD, 0, 1, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 7, 13)); // Monday
            assertThat(boundaries[1]).isEqualTo(refDate); // Wednesday July 15
        }

        @Test
        @DisplayName("WTD with offset -1 (prior week) should cover full Monday to Sunday of previous week")
        void wtdOffsetMinusOne() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.WTD, -1, 1, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 7, 6));  // Monday July 6
            assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 7, 12)); // Sunday July 12
        }

        @Test
        @DisplayName("WTD with rollingN=2 and offset 0 should span 2 weeks starting 1 week before Monday")
        void wtdMultiPeriod() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.WTD, 0, 2, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 7, 6)); // Monday of prior week
            assertThat(boundaries[1]).isEqualTo(refDate);
        }
    }

    @Nested
    @DisplayName("MTD (Month to Date) Boundary Tests")
    class MtdTests {

        @Test
        @DisplayName("MTD with offset 0 should start on 1st of month and end on refDate")
        void mtdOffsetZero() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.MTD, 0, 1, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(boundaries[1]).isEqualTo(refDate);
        }

        @Test
        @DisplayName("MTD with offset -1 should cover entire previous month")
        void mtdOffsetMinusOne() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.MTD, -1, 1, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 6, 30));
        }
    }

    @Nested
    @DisplayName("QTD (Quarter to Date) Boundary Tests")
    class QtdTests {

        @Test
        @DisplayName("QTD with offset 0 (July 15 = Q3) should start on July 1 and end on refDate")
        void qtdOffsetZero() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.QTD, 0, 1, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(boundaries[1]).isEqualTo(refDate);
        }

        @Test
        @DisplayName("QTD with offset -1 (Q2) should cover April 1 to June 30")
        void qtdOffsetMinusOne() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.QTD, -1, 1, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 6, 30));
        }
    }

    @Nested
    @DisplayName("YTD (Year to Date) Boundary Tests")
    class YtdTests {

        @Test
        @DisplayName("YTD with offset 0 should start on Jan 1 and end on refDate")
        void ytdOffsetZero() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.YTD, 0, 1, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(boundaries[1]).isEqualTo(refDate);
        }

        @Test
        @DisplayName("YTD with offset -1 should cover entire previous year (Jan 1 to Dec 31)")
        void ytdOffsetMinusOne() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.YTD, -1, 1, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(boundaries[1]).isEqualTo(LocalDate.of(2025, 12, 31));
        }
    }

    @Nested
    @DisplayName("ROLLING Grain Boundary Tests")
    class RollingTests {

        @Test
        @DisplayName("ROLLING DAY with rollingN=7 should look back 7 days")
        void rollingDay() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.ROLLING, 0, 7, "DAY");
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 7, 9));
            assertThat(boundaries[1]).isEqualTo(refDate);
        }

        @Test
        @DisplayName("ROLLING WEEK with rollingN=4 should look back 4 weeks")
        void rollingWeek() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.ROLLING, 0, 4, "WEEK");
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 6, 18));
            assertThat(boundaries[1]).isEqualTo(refDate);
        }

        @Test
        @DisplayName("ROLLING MONTH with rollingN=3 should look back 3 months")
        void rollingMonth() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.ROLLING, 0, 3, "MONTH");
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 4, 16));
            assertThat(boundaries[1]).isEqualTo(refDate);
        }

        @Test
        @DisplayName("ROLLING QUARTER with rollingN=1 should look back 3 months")
        void rollingQuarter() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.ROLLING, 0, 1, "QUARTER");
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 4, 16));
            assertThat(boundaries[1]).isEqualTo(refDate);
        }

        @Test
        @DisplayName("ROLLING YEAR with rollingN=1 should look back 1 year")
        void rollingYear() {
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(refDate, ColType.ROLLING, 0, 1, "YEAR");
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2025, 7, 16));
            assertThat(boundaries[1]).isEqualTo(refDate);
        }
    }

    @Nested
    @DisplayName("Leap Year and Edge Case Tests")
    class LeapYearTests {

        @Test
        @DisplayName("Leap year Feb 29 resolution under MTD")
        void leapYearFeb29() {
            LocalDate leapDate = LocalDate.of(2024, 2, 29);
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(leapDate, ColType.MTD, 0, 1, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2024, 2, 1));
            assertThat(boundaries[1]).isEqualTo(leapDate);
        }

        @Test
        @DisplayName("Leap year Feb 29 MTD with offset -1 (Jan 2024)")
        void leapYearPriorMonth() {
            LocalDate leapDate = LocalDate.of(2024, 2, 29);
            LocalDate[] boundaries = TimeWindowResolver.resolveBoundaries(leapDate, ColType.MTD, -1, 1, null);
            assertThat(boundaries[0]).isEqualTo(LocalDate.of(2024, 1, 1));
            assertThat(boundaries[1]).isEqualTo(LocalDate.of(2024, 1, 31));
        }

        @ParameterizedTest
        @CsvSource({
                "DAY, 5",
                "WEEK, 2",
                "MONTH, 1",
                "QUARTER, 1",
                "YEAR, 1"
        })
        @DisplayName("Shift reference date by native grains")
        void testShiftRefDateByGrain(String grain, int offset) {
            LocalDate shifted = TimeWindowResolver.shiftRefDateByGrain(refDate, offset, grain);
            assertThat(shifted).isAfter(refDate);
        }
    }
}
