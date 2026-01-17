package com.pesapal.rdbms.controller;

import com.pesapal.rdbms.dto.*;
import com.pesapal.rdbms.entity.DatabaseTable;
import com.pesapal.rdbms.entity.TableRow;
import com.pesapal.rdbms.service.RdbmsService;
import com.pesapal.rdbms.service.SqlParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rdbms")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
public class RdbmsController {

    private final RdbmsService rdbmsService;
    private final SqlParserService sqlParserService;

    @PostMapping("/tables")
    public ResponseEntity<DatabaseTable> createTable(@RequestBody CreateTableRequest request) {
        return ResponseEntity.ok(rdbmsService.createTable(request));
    }

    @GetMapping("/tables")
    public ResponseEntity<List<DatabaseTable>> listTables() {
        return ResponseEntity.ok(rdbmsService.listTables());
    }

    @GetMapping("/tables/{tableName}")
    public ResponseEntity<DatabaseTable> getTable(@PathVariable String tableName) {
        return ResponseEntity.ok(rdbmsService.getTable(tableName));
    }

    @DeleteMapping("/tables/{tableName}")
    public ResponseEntity<Map<String, Object>> dropTable(@PathVariable String tableName) {
        rdbmsService.dropTable(tableName);
        return ResponseEntity.ok(Map.of("message", "Table '" + tableName + "' dropped successfully"));
    }

    @PostMapping("/insert")
    public ResponseEntity<TableRow> insert(@RequestBody InsertRequest request) {
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

    @PostMapping("/join")
    public ResponseEntity<List<Map<String, Object>>> join(@RequestBody JoinRequest request) {
        return ResponseEntity.ok(rdbmsService.join(request));
    }

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
}
