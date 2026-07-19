package com.reporting.service;

import com.reporting.dto.Enums.ColType;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

/**
 * Pure calendar-grain date window resolver engine.
 * Computes date boundaries for WTD, MTD, QTD, YTD, and ROLLING column configurations
 * by treating the period offset as a direct integer multiplier of the native calendar grain.
 */
public class TimeWindowResolver {

    /**
     * Resolves the inclusive [start, end] date window boundaries for a column config.
     * Treats periodOffset as a multiplier of the native calendar grain.
     *
     * @param refDate       the base reference date (reporting date)
     * @param colType       WTD, MTD, QTD, YTD, or ROLLING
     * @param offset        period offset (multiplier of native grain)
     * @param rollingN      timeframe length (number of periods to look back)
     * @param rollingGrain  grain for ROLLING columns (DAY, WEEK, MONTH, QUARTER, YEAR)
     * @return LocalDate[2] representing [startDate, endDate] (both inclusive)
     */
    public static LocalDate[] resolveBoundaries(
            LocalDate refDate,
            ColType colType,
            int offset,
            Integer rollingN,
            String rollingGrain) {

        if (colType == null) {
            throw new IllegalArgumentException("colType cannot be null");
        }

        int n = (rollingN != null && rollingN > 0) ? rollingN : 1;

        // Stage 1: Anchor Shifting
        // Shift base date by the offset using the native grain's time unit.
        LocalDate shiftedRef = shiftRefDateByGrain(refDate, offset, getNativeGrain(colType, rollingGrain));

        LocalDate start;
        LocalDate end;

        // Stage 2: Boundary Expansion
        switch (colType) {
            case WTD: {
                LocalDate monday = shiftedRef.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                start = monday.minusWeeks(n - 1);
                end = (offset == 0) ? refDate : monday.plusDays(6);
                break;
            }

            case MTD: {
                start = shiftedRef.withDayOfMonth(1).minusMonths(n - 1);
                end = (offset == 0) ? refDate : shiftedRef.with(TemporalAdjusters.lastDayOfMonth());
                break;
            }

            case QTD: {
                int startMonthValue = ((shiftedRef.getMonthValue() - 1) / 3) * 3 + 1;
                start = LocalDate.of(shiftedRef.getYear(), startMonthValue, 1).minusMonths((n - 1) * 3);
                if (offset == 0) {
                    end = refDate;
                } else {
                    LocalDate qEndMonthDate = LocalDate.of(shiftedRef.getYear(), startMonthValue + 2, 1);
                    end = qEndMonthDate.with(TemporalAdjusters.lastDayOfMonth());
                }
                break;
            }

            case YTD: {
                start = LocalDate.of(shiftedRef.getYear(), 1, 1).minusYears(n - 1);
                end = (offset == 0) ? refDate : LocalDate.of(shiftedRef.getYear(), 12, 31);
                break;
            }

            case ROLLING: {
                String grain = (rollingGrain != null && !rollingGrain.isBlank())
                        ? rollingGrain.trim().toUpperCase()
                        : "WEEK";
                end = shiftedRef;
                switch (grain) {
                    case "DAY":
                        start = shiftedRef.minusDays(n).plusDays(1);
                        break;
                    case "MONTH":
                        start = shiftedRef.minusMonths(n).plusDays(1);
                        break;
                    case "QUARTER":
                        start = shiftedRef.minusMonths(n * 3L).plusDays(1);
                        break;
                    case "YEAR":
                        start = shiftedRef.minusYears(n).plusDays(1);
                        break;
                    case "WEEK":
                    default:
                        start = shiftedRef.minusWeeks(n).plusDays(1);
                        break;
                }
                break;
            }

            default:
                throw new IllegalArgumentException("Unsupported period type: " + colType);
        }

        return new LocalDate[]{start, end};
    }

    /**
     * Shifts the reference date based on the native grain and offset.
     *
     * @param date   the base date to shift
     * @param offset the amount to shift by
     * @param grain  the grain unit (DAY, WEEK, MONTH, QUARTER, YEAR)
     * @return the shifted LocalDate
     */
    public static LocalDate shiftRefDateByGrain(LocalDate date, int offset, String grain) {
        if (offset == 0) {
            return date;
        }
        if (grain == null) {
            grain = "WEEK";
        }
        switch (grain.toUpperCase()) {
            case "DAY":
                return date.plusDays(offset);
            case "WEEK":
                return date.plusWeeks(offset);
            case "MONTH":
                return date.plusMonths(offset);
            case "QUARTER":
                return date.plusMonths(offset * 3L);
            case "YEAR":
                return date.plusYears(offset);
            default:
                return date.plusWeeks(offset); // fallback
        }
    }

    private static String getNativeGrain(ColType colType, String rollingGrain) {
        switch (colType) {
            case WTD:
                return "WEEK";
            case MTD:
                return "MONTH";
            case QTD:
                return "QUARTER";
            case YTD:
                return "YEAR";
            case ROLLING:
                return (rollingGrain != null && !rollingGrain.isBlank()) ? rollingGrain : "WEEK";
            default:
                return "WEEK";
        }
    }
}
