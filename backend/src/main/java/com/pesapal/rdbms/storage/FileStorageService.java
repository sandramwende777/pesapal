package com.pesapal.rdbms.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-based storage service for the RDBMS.
 * 
 * This replaces JPA/H2 with our own file-based storage:
 * - Schemas stored as JSON files in data/schemas/
 * - Row data stored in binary page files in data/tables/
 * - In-memory indexes for query optimization
 * 
 * File structure:
 *   data/
 *   ├── schemas/
 *   │   ├── products.schema.json
 *   │   └── orders.schema.json
 *   └── tables/
 *       ├── products.dat
 *       └── orders.dat
 */
@Service
@Slf4j
public class FileStorageService {
    
    @Value("${rdbms.data.directory:data}")
    private String dataDirectory;
    
    private Path schemasDir;
    private Path tablesDir;
    
    private final ObjectMapper objectMapper;
    
    // In-memory cache of schemas
    private final Map<String, TableSchema> schemaCache = new ConcurrentHashMap<>();
    
    // In-memory cache of pages per table
    private final Map<String, List<Page>> pageCache = new ConcurrentHashMap<>();
    
    // Locks per table for thread safety
    private final Map<String, ReadWriteLock> tableLocks = new ConcurrentHashMap<>();
    
    public FileStorageService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @PostConstruct
    public void initialize() throws IOException {
        // Create directory structure
        schemasDir = Paths.get(dataDirectory, "schemas");
        tablesDir = Paths.get(dataDirectory, "tables");
        
        Files.createDirectories(schemasDir);
        Files.createDirectories(tablesDir);
        
        log.info("Initialized file storage at: {}", dataDirectory);
        
        // Load existing schemas into cache
        loadAllSchemas();
    }
    
    @PreDestroy
    public void shutdown() throws IOException {
        // Flush all dirty pages to disk
        for (String tableName : pageCache.keySet()) {
            flushTable(tableName);
        }
        log.info("File storage shutdown complete");
    }
    
    // ==================== Schema Operations ====================
    
    /**
     * Creates a new table schema and data file.
     */
    public void createTable(TableSchema schema) throws IOException {
        String tableName = schema.getTableName();
        
        if (schemaCache.containsKey(tableName)) {
            throw new IllegalArgumentException("Table already exists: " + tableName);
        }
        
        ReadWriteLock lock = getTableLock(tableName);
        lock.writeLock().lock();
        try {
            // Write schema file
            Path schemaFile = schemasDir.resolve(tableName + ".schema.json");
            objectMapper.writeValue(schemaFile.toFile(), schema);
            
            // Create empty data file with header page
            Path dataFile = tablesDir.resolve(tableName + ".dat");
            try (RandomAccessFile raf = new RandomAccessFile(dataFile.toFile(), "rw")) {
                // Write file header (first page is metadata)
                Page headerPage = new Page(0);
                raf.write(headerPage.getData());
            }
            
            // Add to cache
            schemaCache.put(tableName, schema);
            pageCache.put(tableName, new ArrayList<>());
            
            log.info("Created table: {}", tableName);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets a table schema.
     */
    public TableSchema getSchema(String tableName) {
        TableSchema schema = schemaCache.get(tableName);
        if (schema == null) {
            throw new IllegalArgumentException("Table not found: " + tableName);
        }
        return schema;
    }
    
    /**
     * Checks if a table exists.
     */
    public boolean tableExists(String tableName) {
        return schemaCache.containsKey(tableName);
    }
    
    /**
     * Lists all tables.
     */
    public List<TableSchema> listTables() {
        return new ArrayList<>(schemaCache.values());
    }
    
    /**
     * Drops a table.
     */
    public void dropTable(String tableName) throws IOException {
        if (!schemaCache.containsKey(tableName)) {
            throw new IllegalArgumentException("Table not found: " + tableName);
        }
        
        ReadWriteLock lock = getTableLock(tableName);
        lock.writeLock().lock();
        try {
            // Delete schema file
            Path schemaFile = schemasDir.resolve(tableName + ".schema.json");
            Files.deleteIfExists(schemaFile);
            
            // Delete data file
            Path dataFile = tablesDir.resolve(tableName + ".dat");
            Files.deleteIfExists(dataFile);
            
            // Remove from caches
            schemaCache.remove(tableName);
            pageCache.remove(tableName);
            tableLocks.remove(tableName);
            
            log.info("Dropped table: {}", tableName);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Updates schema metadata (e.g., row count).
     */
    public void updateSchema(TableSchema schema) throws IOException {
        String tableName = schema.getTableName();
        
        ReadWriteLock lock = getTableLock(tableName);
        lock.writeLock().lock();
        try {
            Path schemaFile = schemasDir.resolve(tableName + ".schema.json");
            objectMapper.writeValue(schemaFile.toFile(), schema);
            schemaCache.put(tableName, schema);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ==================== Row Operations ====================
    
    /**
     * Inserts a row into a table.
     * Returns the assigned row ID.
     */
    public long insertRow(String tableName, Row row) throws IOException {
        TableSchema schema = getSchema(tableName);
        
        ReadWriteLock lock = getTableLock(tableName);
        lock.writeLock().lock();
        try {
            // Assign row ID
            long rowId = schema.getNextRowId();
            row.setRowId(rowId);
            
            // Serialize row
            byte[] rowData = Page.serializeRow(row);
            
            // Find a page with space, or create new one
            List<Page> pages = getPages(tableName);
            Page targetPage = null;
            
            for (Page page : pages) {
                if (page.getFreeSpace() >= rowData.length + Page.SLOT_SIZE) {
                    targetPage = page;
                    break;
                }
            }
            
            if (targetPage == null) {
                // Create new page
                targetPage = new Page(pages.size());
                pages.add(targetPage);
            }
            
            // Insert into page
            int slot = targetPage.insertRow(rowData);
            if (slot < 0) {
                throw new RuntimeException("Failed to insert row - page full");
            }
            
            // Update schema stats
            schema.incrementRowCount();
            updateSchema(schema);
            
            // Mark page dirty (will be flushed later)
            flushPage(tableName, targetPage);
            
            return rowId;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Reads all rows from a table.
     */
    public List<Row> readAllRows(String tableName) throws IOException {
        if (!schemaCache.containsKey(tableName)) {
            throw new IllegalArgumentException("Table not found: " + tableName);
        }
        
        ReadWriteLock lock = getTableLock(tableName);
        lock.readLock().lock();
        try {
            List<Row> rows = new ArrayList<>();
            List<Page> pages = getPages(tableName);
            
            for (Page page : pages) {
                for (byte[] rowData : page.getAllRows()) {
                    Row row = Page.deserializeRow(rowData);
                    if (row.isActive()) {
                        rows.add(row);
                    }
                }
            }
            
            return rows;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Updates rows matching the predicate.
     * Returns the number of rows updated.
     */
    public int updateRows(String tableName, Map<String, Object> updates, 
                          java.util.function.Predicate<Row> predicate) throws IOException {
        TableSchema schema = getSchema(tableName);
        
        ReadWriteLock lock = getTableLock(tableName);
        lock.writeLock().lock();
        try {
            int updated = 0;
            List<Page> pages = getPages(tableName);
            
            for (Page page : pages) {
                for (int slot = 0; slot < page.getRowCount(); slot++) {
                    byte[] rowData = page.readRow(slot);
                    if (rowData == null) continue;
                    
                    Row row = Page.deserializeRow(rowData);
                    if (!row.isActive()) continue;
                    
                    if (predicate.test(row)) {
                        // Apply updates
                        for (var entry : updates.entrySet()) {
                            row.setValue(entry.getKey(), entry.getValue());
                        }
                        
                        // Write back
                        byte[] newData = Page.serializeRow(row);
                        if (!page.updateRow(slot, newData)) {
                            // Row grew too large - need to handle this
                            log.warn("Row update failed - row too large for page");
                        }
                        updated++;
                    }
                }
                
                if (page.isDirty()) {
                    flushPage(tableName, page);
                }
            }
            
            return updated;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Deletes rows matching the predicate.
     * Returns the number of rows deleted.
     */
    public int deleteRows(String tableName, java.util.function.Predicate<Row> predicate) 
            throws IOException {
        TableSchema schema = getSchema(tableName);
        
        ReadWriteLock lock = getTableLock(tableName);
        lock.writeLock().lock();
        try {
            int deleted = 0;
            List<Page> pages = getPages(tableName);
            
            for (Page page : pages) {
                for (int slot = 0; slot < page.getRowCount(); slot++) {
                    byte[] rowData = page.readRow(slot);
                    if (rowData == null) continue;
                    
                    Row row = Page.deserializeRow(rowData);
                    if (!row.isActive()) continue;
                    
                    if (predicate.test(row)) {
                        // Mark as deleted
                        row.markDeleted();
                        byte[] newData = Page.serializeRow(row);
                        page.updateRow(slot, newData);
                        deleted++;
                    }
                }
                
                if (page.isDirty()) {
                    flushPage(tableName, page);
                }
            }
            
            // Update schema stats
            for (int i = 0; i < deleted; i++) {
                schema.decrementRowCount();
            }
            updateSchema(schema);
            
            return deleted;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ==================== Page Management ====================
    
    /**
     * Gets all pages for a table, loading from disk if necessary.
     */
    private List<Page> getPages(String tableName) throws IOException {
        List<Page> pages = pageCache.get(tableName);
        if (pages == null) {
            pages = loadPages(tableName);
            pageCache.put(tableName, pages);
        }
        return pages;
    }
    
    /**
     * Loads all pages from a table's data file.
     */
    private List<Page> loadPages(String tableName) throws IOException {
        List<Page> pages = new ArrayList<>();
        Path dataFile = tablesDir.resolve(tableName + ".dat");
        
        if (!Files.exists(dataFile)) {
            return pages;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(dataFile.toFile(), "r")) {
            long fileSize = raf.length();
            int numPages = (int) (fileSize / Page.PAGE_SIZE);
            
            for (int i = 0; i < numPages; i++) {
                byte[] pageData = new byte[Page.PAGE_SIZE];
                raf.seek((long) i * Page.PAGE_SIZE);
                raf.readFully(pageData);
                pages.add(new Page(i, pageData));
            }
        }
        
        return pages;
    }
    
    /**
     * Flushes a page to disk.
     */
    private void flushPage(String tableName, Page page) throws IOException {
        Path dataFile = tablesDir.resolve(tableName + ".dat");
        
        try (RandomAccessFile raf = new RandomAccessFile(dataFile.toFile(), "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock()) {
            
            raf.seek((long) page.getPageId() * Page.PAGE_SIZE);
            raf.write(page.getData());
            page.markClean();
        }
    }
    
    /**
     * Flushes all dirty pages for a table.
     */
    public void flushTable(String tableName) throws IOException {
        List<Page> pages = pageCache.get(tableName);
        if (pages == null) return;
        
        ReadWriteLock lock = getTableLock(tableName);
        lock.writeLock().lock();
        try {
            for (Page page : pages) {
                if (page.isDirty()) {
                    flushPage(tableName, page);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ==================== Helper Methods ====================
    
    private void loadAllSchemas() {
        try {
            if (!Files.exists(schemasDir)) return;
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(schemasDir, "*.schema.json")) {
                for (Path file : stream) {
                    try {
                        TableSchema schema = objectMapper.readValue(file.toFile(), TableSchema.class);
                        schemaCache.put(schema.getTableName(), schema);
                        log.debug("Loaded schema: {}", schema.getTableName());
                    } catch (Exception e) {
                        log.error("Failed to load schema from: {}", file, e);
                    }
                }
            }
            
            log.info("Loaded {} table schemas", schemaCache.size());
            
        } catch (IOException e) {
            log.error("Failed to load schemas", e);
        }
    }
    
    private ReadWriteLock getTableLock(String tableName) {
        return tableLocks.computeIfAbsent(tableName, k -> new ReentrantReadWriteLock());
    }
}
