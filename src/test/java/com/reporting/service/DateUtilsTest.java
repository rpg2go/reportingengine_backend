package com.reporting.service;

import com.reporting.dto.Enums.ColType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DateUtils Unit Tests")
public class DateUtilsTest {

    private final LocalDate refDate = LocalDate.of(2026, 5, 26); // Tuesday

    @Test
    @DisplayName("WTD with offset 0: should return Monday to refDate")
    public void getPeriodBoundaries_wtdOffsetZero_shouldReturnMondayToRefDate() {
        LocalDate[] boundaries = DateUtils.getPeriodBoundaries(refDate, ColType.WTD, 0, null);
        assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 5, 25)); // Monday
        assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 5, 26)); // Tuesday (refDate)
    }

    @Test
    @DisplayName("WTD with offset -1: should return previous week Monday to Sunday")
    public void getPeriodBoundaries_wtdOffsetMinusOne_shouldReturnPreviousFullWeek() {
        LocalDate[] boundaries = DateUtils.getPeriodBoundaries(refDate, ColType.WTD, -1, null);
        assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 5, 18)); // Previous Monday
        assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 5, 24)); // Previous Sunday
    }

    @Test
    @DisplayName("WTD with timeframe length 3: should look back 3 weeks starting from Monday")
    public void getPeriodBoundaries_wtdTimeframeLength_shouldLookbackThreeWeeks() {
        LocalDate[] boundaries = DateUtils.getPeriodBoundaries(refDate, ColType.WTD, 0, 3);
        assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 5, 11)); // Monday 2 weeks ago
        assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 5, 26));
    }

    @Test
    @DisplayName("MTD with offset 0: should return 1st of month to refDate")
    public void getPeriodBoundaries_mtdOffsetZero_shouldReturnFirstOfMonthToRefDate() {
        LocalDate[] boundaries = DateUtils.getPeriodBoundaries(refDate, ColType.MTD, 0, null);
        assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 5, 26));
    }

    @Test
    @DisplayName("MTD with offset -1: should return 1st of previous month to end of previous month")
    public void getPeriodBoundaries_mtdOffsetMinusOne_shouldReturnPreviousMonthBoundaries() {
        LocalDate[] boundaries = DateUtils.getPeriodBoundaries(refDate, ColType.MTD, -1, null);
        assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("MTD with offset -3 (February adjustment): should return last day of February")
    public void getPeriodBoundaries_mtdOffsetMinusThree_shouldCapAtEndOfFebruary() {
        LocalDate leapRefDate = LocalDate.of(2024, 5, 31); // 2024 is leap year, Feb has 29 days
        LocalDate[] boundariesLeap = DateUtils.getPeriodBoundaries(leapRefDate, ColType.MTD, -3, null);
        assertThat(boundariesLeap[1]).isEqualTo(LocalDate.of(2024, 2, 29));

        LocalDate nonLeapRefDate = LocalDate.of(2025, 5, 31); // 2025 non-leap, Feb has 28 days
        LocalDate[] boundariesNonLeap = DateUtils.getPeriodBoundaries(nonLeapRefDate, ColType.MTD, -3, null);
        assertThat(boundariesNonLeap[1]).isEqualTo(LocalDate.of(2025, 2, 28));
    }

    @Test
    @DisplayName("QTD with offset 0: should return start of quarter to refDate")
    public void getPeriodBoundaries_qtdOffsetZero_shouldReturnStartOfQuarterToRefDate() {
        LocalDate[] boundaries = DateUtils.getPeriodBoundaries(refDate, ColType.QTD, 0, null);
        assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 5, 26));
    }

    @Test
    @DisplayName("QTD with offset -1: should return previous quarter start to end")
    public void getPeriodBoundaries_qtdOffsetMinusOne_shouldReturnPreviousQuarterBoundaries() {
        LocalDate[] boundaries = DateUtils.getPeriodBoundaries(refDate, ColType.QTD, -1, null);
        assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("YTD with offset 0: should return Jan 1st of year to refDate")
    public void getPeriodBoundaries_ytdOffsetZero_shouldReturnJanFirstToRefDate() {
        LocalDate[] boundaries = DateUtils.getPeriodBoundaries(refDate, ColType.YTD, 0, null);
        assertThat(boundaries[0]).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(boundaries[1]).isEqualTo(LocalDate.of(2026, 5, 26));
    }

    @Test
    @DisplayName("YTD with offset -1 (or -12): should shift by 1 year and return end of target year")
    public void getPeriodBoundaries_ytdOffsetMinusOne_shouldShiftByOneYear() {
        LocalDate[] boundaries1 = DateUtils.getPeriodBoundaries(refDate, ColType.YTD, -1, null);
        assertThat(boundaries1[0]).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(boundaries1[1]).isEqualTo(LocalDate.of(2025, 12, 31));

        LocalDate[] boundaries12 = DateUtils.getPeriodBoundaries(refDate, ColType.YTD, -12, null);
        assertThat(boundaries12[0]).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(boundaries12[1]).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    @DisplayName("ROLLING: should return rolling weeks boundaries")
    public void getPeriodBoundaries_rolling_shouldReturnRollingBoundaries() {
        LocalDate[] boundariesDefault = DateUtils.getPeriodBoundaries(refDate, ColType.ROLLING, 0, null);
        assertThat(boundariesDefault[0]).isEqualTo(refDate.minusWeeks(1).plusDays(1));
        assertThat(boundariesDefault[1]).isEqualTo(refDate);

        LocalDate[] boundariesN = DateUtils.getPeriodBoundaries(refDate, ColType.ROLLING, 0, 4);
        assertThat(boundariesN[0]).isEqualTo(refDate.minusWeeks(4).plusDays(1));
        assertThat(boundariesN[1]).isEqualTo(refDate);
    }

    @Test
    @DisplayName("ROLLING with Grain: should compute correct boundaries for DAY, WEEK, and MONTH")
    public void getPeriodBoundaries_rollingWithGrain_shouldReturnCorrectBoundaries() {
        // DAY grain
        LocalDate[] dayBoundaries = DateUtils.getPeriodBoundaries(refDate, ColType.ROLLING, 0, 5, "DAY");
        assertThat(dayBoundaries[0]).isEqualTo(refDate.minusDays(5).plusDays(1));
        assertThat(dayBoundaries[1]).isEqualTo(refDate);

        // WEEK grain
        LocalDate[] weekBoundaries = DateUtils.getPeriodBoundaries(refDate, ColType.ROLLING, 0, 3, "WEEK");
        assertThat(weekBoundaries[0]).isEqualTo(refDate.minusWeeks(3).plusDays(1));
        assertThat(weekBoundaries[1]).isEqualTo(refDate);

        // MONTH grain
        LocalDate[] monthBoundaries = DateUtils.getPeriodBoundaries(refDate, ColType.ROLLING, 0, 2, "MONTH");
        assertThat(monthBoundaries[0]).isEqualTo(refDate.minusMonths(2).plusDays(1));
        assertThat(monthBoundaries[1]).isEqualTo(refDate);

        // Default / null fallback to WEEK
        LocalDate[] defaultBoundaries = DateUtils.getPeriodBoundaries(refDate, ColType.ROLLING, 0, 3, null);
        assertThat(defaultBoundaries[0]).isEqualTo(refDate.minusWeeks(3).plusDays(1));
        assertThat(defaultBoundaries[1]).isEqualTo(refDate);
    }

    @Test
    @DisplayName("Unsupported Period Type: CALC should throw IllegalArgumentException")
    public void getPeriodBoundaries_unsupportedType_shouldThrowException() {
        assertThatThrownBy(() -> DateUtils.getPeriodBoundaries(refDate, ColType.CALC, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported period type");
    }
}
