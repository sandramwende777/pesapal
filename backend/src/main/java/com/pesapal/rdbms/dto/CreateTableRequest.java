package com.pesapal.rdbms.dto;

import com.pesapal.rdbms.entity.TableColumn;
import lombok.Data;

import java.util.List;

@Data
public class CreateTableRequest {
    private String tableName;
    private List<ColumnDefinition> columns;
    private List<String> primaryKeys;
    private List<String> uniqueKeys;
    private List<IndexDefinition> indexes;

    @Data
    public static class ColumnDefinition {
        private String name;
        private TableColumn.DataType dataType;
        private Integer maxLength;
        private Boolean nullable = true;
        private Object defaultValue;
    }

    @Data
    public static class IndexDefinition {
        private String indexName;
        private String columnName;
        private Boolean unique = false;
    }
}
