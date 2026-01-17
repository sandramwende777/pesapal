package com.pesapal.rdbms.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DeleteRequest {
    private String tableName;
    private Map<String, Object> where; // conditions
}
