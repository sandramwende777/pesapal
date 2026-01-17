package com.pesapal.rdbms.controller;

import com.pesapal.rdbms.dto.*;
import com.pesapal.rdbms.service.FileBasedRdbmsService;
import com.pesapal.rdbms.service.FileBasedSqlParserService;
import com.pesapal.rdbms.storage.Row;
import com.pesapal.rdbms.storage.TableSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for the file-based RDBMS.
 * 
 * All operations go through our custom file-based storage layer.
 */
@RestController
@RequestMapping("/api/rdbms")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FileBasedRdbmsController {
    
    private final FileBasedRdbmsService rdbmsService;
    private final FileBasedSqlParserService sqlParserService;
    
    // ==================== Table Operations ====================
    
    @PostMapping("/tables")
    public ResponseEntity<TableSchema> createTable(@RequestBody CreateTableRequest request) {
        return ResponseEntity.ok(rdbmsService.createTable(request));
    }
    
    @GetMapping("/tables")
    public ResponseEntity<List<TableSchema>> listTables() {
        return ResponseEntity.ok(rdbmsService.listTables());
    }
    
    @GetMapping("/tables/{tableName}")
    public ResponseEntity<TableSchema> getTable(@PathVariable String tableName) {
        return ResponseEntity.ok(rdbmsService.getTable(tableName));
    }
    
    @DeleteMapping("/tables/{tableName}")
    public ResponseEntity<Map<String, Object>> dropTable(@PathVariable String tableName) {
        rdbmsService.dropTable(tableName);
        return ResponseEntity.ok(Map.of("message", "Table '" + tableName + "' dropped successfully"));
    }
    
    // ==================== CRUD Operations ====================
    
    @PostMapping("/insert")
    public ResponseEntity<Row> insert(@RequestBody InsertRequest request) {
        return ResponseEntity.ok(rdbmsService.insert(request));
    }
    
    @PostMapping("/select")
    public ResponseEntity<List<Map<String, Object>>> select(@RequestBody SelectRequest request) {
        return ResponseEntity.ok(rdbmsService.select(request));
    }
    
    @PutMapping("/update")
    public ResponseEntity<Map<String, Object>> update(@RequestBody UpdateRequest request) {
        int updated = rdbmsService.update(request);
        return ResponseEntity.ok(Map.of("updated", updated));
    }
    
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestBody DeleteRequest request) {
        int deleted = rdbmsService.delete(request);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
    
    // ==================== JOIN Operations ====================
    
    @PostMapping("/join")
    public ResponseEntity<List<Map<String, Object>>> join(@RequestBody JoinRequest request) {
        return ResponseEntity.ok(rdbmsService.join(request));
    }
    
    // ==================== SQL Interface ====================
    
    @PostMapping("/sql")
    public ResponseEntity<Object> executeSql(@RequestBody SqlRequest request) {
        try {
            Object result = sqlParserService.executeSql(request.getSql());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    // ==================== Info Endpoint ====================
    
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        return ResponseEntity.ok(Map.of(
                "name", "File-Based RDBMS",
                "version", "2.0",
                "storage", "Custom page-based file storage",
                "indexing", "In-memory hash indexes",
                "features", List.of(
                        "CREATE TABLE with PRIMARY KEY, UNIQUE",
                        "INSERT, SELECT, UPDATE, DELETE",
                        "WHERE with =, !=, <, >, <=, >=, LIKE, IS NULL",
                        "JOIN (INNER, LEFT, RIGHT)",
                        "Indexes for fast lookups",
                        "SQL REPL interface"
                )
        ));
    }
}
