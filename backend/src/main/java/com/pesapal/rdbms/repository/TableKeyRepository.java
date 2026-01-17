package com.pesapal.rdbms.repository;

import com.pesapal.rdbms.entity.DatabaseTable;
import com.pesapal.rdbms.entity.TableKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TableKeyRepository extends JpaRepository<TableKey, Long> {
    List<TableKey> findByTable(DatabaseTable table);
    List<TableKey> findByTable_TableName(String tableName);
    List<TableKey> findByTable_TableNameAndKeyType(String tableName, TableKey.KeyType keyType);
}
