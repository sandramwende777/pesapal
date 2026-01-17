package com.pesapal.rdbms.repository;

import com.pesapal.rdbms.entity.DatabaseTable;
import com.pesapal.rdbms.entity.TableRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TableRowRepository extends JpaRepository<TableRow, Long> {
    List<TableRow> findByTable(DatabaseTable table);
    List<TableRow> findByTable_TableName(String tableName);
}
