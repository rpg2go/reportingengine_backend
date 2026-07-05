package com.reporting.dto;

import java.io.Serializable;

/**
 * Data Transfer Object representing a hierarchical column configuration.
 * Maps L1 parent headers, L2 child sub-headers, layout styling, timeframes,
 * and period configurations from the frontend Reactive Form.
 *
 * @since 1.1.0
 */
public class HierarchicalColumnDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String colId;
    private String label;
    private String colType;
    private String headerLayout;
    private String tierLevel; // "L1" or "L2"
    private String parentId;  // references parent's colId if tierLevel is "L2"
    private String formulaExpr;
    private Integer periodOffset;
    private Integer rollingN;
    private String rollingGrain;

    public HierarchicalColumnDto() {
    }

    public HierarchicalColumnDto(String colId, String label, String colType, String headerLayout,
                                 String tierLevel, String parentId, String formulaExpr,
                                 Integer periodOffset, Integer rollingN, String rollingGrain) {
        this.colId = colId;
        this.label = label;
        this.colType = colType;
        this.headerLayout = headerLayout;
        this.tierLevel = tierLevel;
        this.parentId = parentId;
        this.formulaExpr = formulaExpr;
        this.periodOffset = periodOffset;
        this.rollingN = rollingN;
        this.rollingGrain = rollingGrain;
    }

    // Getters and Setters

    public String getColId() {
        return colId;
    }

    public void setColId(String colId) {
        this.colId = colId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getColType() {
        return colType;
    }

    public void setColType(String colType) {
        this.colType = colType;
    }

    public String getHeaderLayout() {
        return headerLayout;
    }

    public void setHeaderLayout(String headerLayout) {
        this.headerLayout = headerLayout;
    }

    public String getTierLevel() {
        return tierLevel;
    }

    public void setTierLevel(String tierLevel) {
        this.tierLevel = tierLevel;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getFormulaExpr() {
        return formulaExpr;
    }

    public void setFormulaExpr(String formulaExpr) {
        this.formulaExpr = formulaExpr;
    }

    public Integer getPeriodOffset() {
        return periodOffset;
    }

    public void setPeriodOffset(Integer periodOffset) {
        this.periodOffset = periodOffset;
    }

    public Integer getRollingN() {
        return rollingN;
    }

    public void setRollingN(Integer rollingN) {
        this.rollingN = rollingN;
    }

    public String getRollingGrain() {
        return rollingGrain;
    }

    public void setRollingGrain(String rollingGrain) {
        this.rollingGrain = rollingGrain;
    }

    @Override
    public String toString() {
        return "HierarchicalColumnDto{" +
                "colId='" + colId + '\'' +
                ", label='" + label + '\'' +
                ", colType='" + colType + '\'' +
                ", headerLayout='" + headerLayout + '\'' +
                ", tierLevel='" + tierLevel + '\'' +
                ", parentId='" + parentId + '\'' +
                ", formulaExpr='" + formulaExpr + '\'' +
                ", periodOffset=" + periodOffset +
                ", rollingN=" + rollingN +
                ", rollingGrain='" + rollingGrain + '\'' +
                '}';
    }
}
