package com.pesapal.rdbms.repository;

import com.pesapal.rdbms.entity.DatabaseTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DatabaseTableRepository extends JpaRepository<DatabaseTable, Long> {
    Optional<DatabaseTable> findByTableName(String tableName);
    boolean existsByTableName(String tableName);
}
