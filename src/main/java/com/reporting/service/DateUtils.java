package com.reporting.service;

import com.reporting.dto.Enums.ColType;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

public class DateUtils {

    public static LocalDate[] getPeriodBoundaries(LocalDate refDate, ColType colType, int offset, Integer rollingN) {
        LocalDate start = null;
        LocalDate end = null;

        switch (colType) {
            case WEEK:
                // ISO Week: Monday to Sunday
                LocalDate monday = refDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate mondayOffset = monday.plusWeeks(offset);
                start = mondayOffset;
                if (offset == 0) {
                    end = refDate;
                } else {
                    end = mondayOffset.plusDays(6);
                }
                break;

            case MTD:
                // Shift by months
                LocalDate targetMonthDate = refDate.plusMonths(offset);
                start = targetMonthDate.withDayOfMonth(1);
                
                int endDayM = Math.min(refDate.getDayOfMonth(), targetMonthDate.lengthOfMonth());
                end = targetMonthDate.withDayOfMonth(endDayM);
                break;

            case YTD:
                // Offset is in years
                int yearShift = offset;
                if (Math.abs(offset) >= 12) {
                    yearShift = offset / 12;
                }
                LocalDate targetYearDate = refDate.plusYears(yearShift);
                start = LocalDate.of(targetYearDate.getYear(), 1, 1);
                
                LocalDate refMonthInTargetYear = LocalDate.of(targetYearDate.getYear(), refDate.getMonthValue(), 1);
                int endDayY = Math.min(refDate.getDayOfMonth(), refMonthInTargetYear.lengthOfMonth());
                end = LocalDate.of(targetYearDate.getYear(), refDate.getMonthValue(), endDayY);
                break;

            case ROLLING:
                int n = rollingN != null ? rollingN : 1;
                end = refDate;
                start = refDate.minusWeeks(n).plusDays(1);
                break;

            default:
                throw new IllegalArgumentException("Unsupported period type: " + colType);
        }

        return new LocalDate[]{start, end};
    }
}
