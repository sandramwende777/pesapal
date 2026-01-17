package com.pesapal.rdbms.config;

/**
 * Constants used throughout the RDBMS implementation.
 * 
 * <p>Centralizes magic numbers and configuration values for maintainability.</p>
 */
public final class RdbmsConstants {
    
    private RdbmsConstants() {
        // Utility class - prevent instantiation
    }
    
    // ==================== Storage Constants ====================
    
    /** Default page size in bytes (4KB like PostgreSQL) */
    public static final int PAGE_SIZE = 4096;
    
    /** Page header size in bytes */
    public static final int PAGE_HEADER_SIZE = 32;
    
    /** Size of each slot entry (offset + length) */
    public static final int SLOT_SIZE = 8;
    
    /** Maximum row size (must fit in one page minus overhead) */
    public static final int MAX_ROW_SIZE = PAGE_SIZE - PAGE_HEADER_SIZE - SLOT_SIZE - 100;
    
    /** Default VARCHAR length if not specified */
    public static final int DEFAULT_VARCHAR_LENGTH = 255;
    
    /** Maximum VARCHAR length */
    public static final int MAX_VARCHAR_LENGTH = 65535;
    
    // ==================== Index Constants ====================
    
    /** Prefix for primary key index names */
    public static final String PRIMARY_KEY_INDEX_PREFIX = "pk_";
    
    /** Prefix for unique key index names */
    public static final String UNIQUE_KEY_INDEX_PREFIX = "uk_";
    
    /** Prefix for regular index names */
    public static final String INDEX_PREFIX = "idx_";
    
    /** Index file extension */
    public static final String INDEX_FILE_EXTENSION = ".idx";
    
    // ==================== File Extensions ====================
    
    /** Schema file extension */
    public static final String SCHEMA_FILE_EXTENSION = ".schema.json";
    
    /** Data file extension */
    public static final String DATA_FILE_EXTENSION = ".dat";
    
    // ==================== Directory Names ====================
    
    /** Schemas directory name */
    public static final String SCHEMAS_DIR = "schemas";
    
    /** Tables directory name */
    public static final String TABLES_DIR = "tables";
    
    /** Indexes directory name */
    public static final String INDEXES_DIR = "indexes";
    
    // ==================== Query Limits ====================
    
    /** Default query result limit */
    public static final int DEFAULT_QUERY_LIMIT = 1000;
    
    /** Maximum query result limit */
    public static final int MAX_QUERY_LIMIT = 100000;
    
    /** Default page offset */
    public static final int DEFAULT_OFFSET = 0;
    
    // ==================== SQL Keywords ====================
    
    public static final String SQL_SELECT = "SELECT";
    public static final String SQL_INSERT = "INSERT";
    public static final String SQL_UPDATE = "UPDATE";
    public static final String SQL_DELETE = "DELETE";
    public static final String SQL_CREATE_TABLE = "CREATE TABLE";
    public static final String SQL_DROP_TABLE = "DROP TABLE";
    public static final String SQL_WHERE = "WHERE";
    public static final String SQL_ORDER_BY = "ORDER BY";
    public static final String SQL_LIMIT = "LIMIT";
    public static final String SQL_OFFSET = "OFFSET";
    public static final String SQL_JOIN = "JOIN";
    public static final String SQL_INNER_JOIN = "INNER JOIN";
    public static final String SQL_LEFT_JOIN = "LEFT JOIN";
    public static final String SQL_RIGHT_JOIN = "RIGHT JOIN";
    
    // ==================== Version Info ====================
    
    /** RDBMS version */
    public static final String VERSION = "2.1";
    
    /** RDBMS name */
    public static final String NAME = "File-Based RDBMS";
}
