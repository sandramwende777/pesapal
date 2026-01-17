package com.pesapal.rdbms.repository;

import com.pesapal.rdbms.entity.DatabaseTable;
import com.pesapal.rdbms.entity.TableColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TableColumnRepository extends JpaRepository<TableColumn, Long> {
    List<TableColumn> findByTable(DatabaseTable table);
    List<TableColumn> findByTable_TableName(String tableName);
}
