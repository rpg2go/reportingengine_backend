package com.reporting.dto;

public class Enums {
    public enum ColType {
        WTD, MTD, YTD, ROLLING, CALC, HEADER
    }

    public enum RowType {
        section, data, calc, blank
    }

    public enum ReportStatus {
        draft, in_review, published
    }
}
