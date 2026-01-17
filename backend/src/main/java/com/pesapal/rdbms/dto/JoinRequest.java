package com.pesapal.rdbms.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class JoinRequest {
    private String leftTable;
    private String rightTable;
    private String leftColumn;
    private String rightColumn;
    private JoinType joinType = JoinType.INNER;
    private List<String> columns; // null or empty means SELECT *
    private Map<String, Object> where; // conditions
    private Integer limit;
    private Integer offset;

    public enum JoinType {
        INNER, LEFT, RIGHT
    }
}
