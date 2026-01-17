package com.pesapal.rdbms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
            "message", "RDBMS API is running",
            "status", "OK",
            "endpoints", Map.of(
                "GET /api/rdbms/tables", "List all tables",
                "GET /api/rdbms/tables/{tableName}", "Get table schema",
                "POST /api/rdbms/tables", "Create a new table",
                "POST /api/rdbms/insert", "Insert data into a table",
                "POST /api/rdbms/select", "Select data from a table",
                "PUT /api/rdbms/update", "Update data in a table",
                "DELETE /api/rdbms/delete", "Delete data from a table",
                "POST /api/rdbms/join", "Join two tables",
                "POST /api/rdbms/sql", "Execute SQL query"
            ),
            "frontend", "Access the React frontend at http://localhost:3000",
            "h2Console", "Access H2 console at http://localhost:8080/h2-console"
        ));
    }
}
