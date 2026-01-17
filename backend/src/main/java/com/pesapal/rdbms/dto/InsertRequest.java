package com.pesapal.rdbms.dto;

import lombok.Data;

import java.util.Map;

@Data
public class InsertRequest {
    private String tableName;
    private Map<String, Object> values;
}
