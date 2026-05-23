package com.reporting.dto;

public class Enums {
    public enum ColType {
        WEEK, MTD, YTD, ROLLING, CALC
    }

    public enum RowType {
        section, data, calc, blank
    }

    public enum ReportStatus {
        draft, published
    }
}
