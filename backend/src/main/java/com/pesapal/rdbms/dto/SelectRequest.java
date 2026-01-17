package com.pesapal.rdbms.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SelectRequest {
    private String tableName;
    private List<String> columns; // null or empty means SELECT *
    private Map<String, Object> where; // simple equality conditions
    private List<OrderBy> orderBy; // ORDER BY columns
    private Integer limit;
    private Integer offset;
    
    @Data
    public static class OrderBy {
        private String column;
        private boolean descending = false;
        
        public OrderBy() {}
        
        public OrderBy(String column, boolean descending) {
            this.column = column;
            this.descending = descending;
        }
    }
}
