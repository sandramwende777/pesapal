package com.pesapal.rdbms.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SelectRequest {
    private String tableName;
    private List<String> columns; // null or empty means SELECT *
    private Map<String, Object> where; // simple equality conditions
    private Integer limit;
    private Integer offset;
}
