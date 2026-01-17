package com.pesapal.rdbms.dto;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateRequest {
    private String tableName;
    private Map<String, Object> set; // column -> value pairs to update
    private Map<String, Object> where; // conditions
}
