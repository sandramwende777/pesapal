package com.pesapal.rdbms.repository;

import com.pesapal.rdbms.entity.DatabaseTable;
import com.pesapal.rdbms.entity.TableIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TableIndexRepository extends JpaRepository<TableIndex, Long> {
    List<TableIndex> findByTable(DatabaseTable table);
    List<TableIndex> findByTable_TableName(String tableName);
}
