package com.pesapal.rdbms.storage.index;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * B-Tree-like index implementation using Java's TreeMap.
 * 
 * This provides O(log n) lookups and supports range queries,
 * unlike a pure hash index which only supports equality.
 * 
 * Features:
 * - insert(key, rowId): Add a row to the index
 * - delete(key, rowId): Remove a row from the index
 * - find(key): Find all rows with exact key match - O(log n)
 * - findRange(min, max): Find all rows in key range - O(log n + k)
 * - findGreaterThan(key): Find all rows > key
 * - findLessThan(key): Find all rows < key
 * 
 * Uses ConcurrentSkipListMap for thread-safe sorted access.
 * Each key maps to a Set of row IDs (for non-unique indexes).
 */
@Slf4j
public class BTreeIndex {
    
    @Getter
    private final String indexName;
    
    @Getter
    private final String tableName;
    
    @Getter
    private final String columnName;
    
    @Getter
    private final boolean unique;
    
    // TreeMap provides O(log n) operations and sorted order for range queries
    // Using ConcurrentSkipListMap for thread safety with ComparableWrapper
    private final ConcurrentSkipListMap<ComparableWrapper, Set<Long>> tree = new ConcurrentSkipListMap<>();
    
    // Statistics
    private long insertCount = 0;
    private long lookupCount = 0;
    private long rangeQueryCount = 0;
    
    public BTreeIndex(String tableName, String columnName, boolean unique) {
        this.indexName = "idx_" + tableName + "_" + columnName;
        this.tableName = tableName;
        this.columnName = columnName;
        this.unique = unique;
    }
    
    public BTreeIndex(String indexName, String tableName, String columnName, boolean unique) {
        this.indexName = indexName;
        this.tableName = tableName;
        this.columnName = columnName;
        this.unique = unique;
    }
    
    /**
     * Insert a key-rowId pair into the index.
     */
    public void insert(Object key, Long rowId) {
        if (key == null) return;
        
        ComparableWrapper wrappedKey = new ComparableWrapper(key);
        
        if (unique) {
            Set<Long> existing = tree.get(wrappedKey);
            if (existing != null && !existing.isEmpty()) {
                throw new IllegalArgumentException(
                    "Duplicate key '" + key + "' in unique index " + indexName);
            }
        }
        
        tree.computeIfAbsent(wrappedKey, k -> Collections.synchronizedSet(new HashSet<>()))
            .add(rowId);
        insertCount++;
        
        log.trace("Index {}: inserted key={}, rowId={}", indexName, key, rowId);
    }
    
    /**
     * Delete a key-rowId pair from the index.
     */
    public void delete(Object key, Long rowId) {
        if (key == null) return;
        
        ComparableWrapper wrappedKey = new ComparableWrapper(key);
        Set<Long> rowIds = tree.get(wrappedKey);
        if (rowIds != null) {
            rowIds.remove(rowId);
            if (rowIds.isEmpty()) {
                tree.remove(wrappedKey);
            }
        }
        
        log.trace("Index {}: deleted key={}, rowId={}", indexName, key, rowId);
    }
    
    /**
     * Update a key for a row (delete old, insert new).
     */
    public void update(Object oldKey, Object newKey, Long rowId) {
        delete(oldKey, rowId);
        insert(newKey, rowId);
    }
    
    /**
     * Find all row IDs with exact key match.
     * Time complexity: O(log n)
     */
    public Set<Long> find(Object key) {
        lookupCount++;
        if (key == null) return Collections.emptySet();
        
        ComparableWrapper wrappedKey = new ComparableWrapper(key);
        Set<Long> result = tree.get(wrappedKey);
        log.debug("Index {}: find({}) -> {} rows", indexName, key, 
                  result != null ? result.size() : 0);
        
        return result != null ? new HashSet<>(result) : Collections.emptySet();
    }
    
    /**
     * Check if a key exists in the index.
     */
    public boolean containsKey(Object key) {
        if (key == null) return false;
        ComparableWrapper wrappedKey = new ComparableWrapper(key);
        Set<Long> rowIds = tree.get(wrappedKey);
        return rowIds != null && !rowIds.isEmpty();
    }
    
    /**
     * Find all row IDs in a key range [min, max] (inclusive).
     * Time complexity: O(log n + k) where k is the number of keys in range
     */
    public Set<Long> findRange(Object min, Object max) {
        rangeQueryCount++;
        Set<Long> result = new HashSet<>();
        
        ComparableWrapper minKey = new ComparableWrapper(min);
        ComparableWrapper maxKey = new ComparableWrapper(max);
        
        NavigableMap<ComparableWrapper, Set<Long>> subMap = tree.subMap(minKey, true, maxKey, true);
        
        for (Set<Long> rowIds : subMap.values()) {
            result.addAll(rowIds);
        }
        
        log.debug("Index {}: findRange({}, {}) -> {} rows", 
                  indexName, min, max, result.size());
        
        return result;
    }
    
    /**
     * Find all row IDs with key > value.
     */
    public Set<Long> findGreaterThan(Object value, boolean inclusive) {
        rangeQueryCount++;
        Set<Long> result = new HashSet<>();
        
        ComparableWrapper wrappedKey = new ComparableWrapper(value);
        NavigableMap<ComparableWrapper, Set<Long>> tailMap = tree.tailMap(wrappedKey, inclusive);
        
        for (Set<Long> rowIds : tailMap.values()) {
            result.addAll(rowIds);
        }
        
        log.debug("Index {}: findGreaterThan({}, inclusive={}) -> {} rows", 
                  indexName, value, inclusive, result.size());
        
        return result;
    }
    
    /**
     * Find all row IDs with key < value.
     */
    public Set<Long> findLessThan(Object value, boolean inclusive) {
        rangeQueryCount++;
        Set<Long> result = new HashSet<>();
        
        ComparableWrapper wrappedKey = new ComparableWrapper(value);
        NavigableMap<ComparableWrapper, Set<Long>> headMap = tree.headMap(wrappedKey, inclusive);
        
        for (Set<Long> rowIds : headMap.values()) {
            result.addAll(rowIds);
        }
        
        log.debug("Index {}: findLessThan({}, inclusive={}) -> {} rows", 
                  indexName, value, inclusive, result.size());
        
        return result;
    }
    
    /**
     * Get all row IDs in the index.
     */
    public Set<Long> getAllRowIds() {
        Set<Long> result = new HashSet<>();
        for (Set<Long> rowIds : tree.values()) {
            result.addAll(rowIds);
        }
        return result;
    }
    
    /**
     * Clear the index.
     */
    public void clear() {
        tree.clear();
        insertCount = 0;
        log.debug("Index {} cleared", indexName);
    }
    
    /**
     * Get the number of unique keys in the index.
     */
    public int getKeyCount() {
        return tree.size();
    }
    
    /**
     * Get the total number of entries (key-rowId pairs).
     */
    public int getEntryCount() {
        return tree.values().stream().mapToInt(Set::size).sum();
    }
    
    /**
     * Get index statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("indexName", indexName);
        stats.put("tableName", tableName);
        stats.put("columnName", columnName);
        stats.put("unique", unique);
        stats.put("keyCount", getKeyCount());
        stats.put("entryCount", getEntryCount());
        stats.put("insertCount", insertCount);
        stats.put("lookupCount", lookupCount);
        stats.put("rangeQueryCount", rangeQueryCount);
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("BTreeIndex[%s on %s.%s, keys=%d, unique=%b]",
                indexName, tableName, columnName, getKeyCount(), unique);
    }
    
    // ==================== Persistence Methods ====================
    
    /**
     * Serializes the index to a file.
     * Format: indexName, tableName, columnName, unique, stats, then entries.
     */
    public void saveToFile(File file) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            
            // Write metadata
            dos.writeUTF(indexName);
            dos.writeUTF(tableName);
            dos.writeUTF(columnName);
            dos.writeBoolean(unique);
            
            // Write statistics
            dos.writeLong(insertCount);
            dos.writeLong(lookupCount);
            dos.writeLong(rangeQueryCount);
            
            // Write number of entries
            dos.writeInt(tree.size());
            
            // Write each entry
            for (var entry : tree.entrySet()) {
                // Write key
                writeObject(dos, entry.getKey().value);
                
                // Write row IDs
                Set<Long> rowIds = entry.getValue();
                dos.writeInt(rowIds.size());
                for (Long rowId : rowIds) {
                    dos.writeLong(rowId);
                }
            }
            
            log.info("Saved index {} to disk: {} keys", indexName, tree.size());
        }
    }
    
    /**
     * Loads an index from a file.
     */
    public static BTreeIndex loadFromFile(File file) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            
            // Read metadata
            String indexName = dis.readUTF();
            String tableName = dis.readUTF();
            String columnName = dis.readUTF();
            boolean unique = dis.readBoolean();
            
            BTreeIndex index = new BTreeIndex(indexName, tableName, columnName, unique);
            
            // Read statistics
            index.insertCount = dis.readLong();
            index.lookupCount = dis.readLong();
            index.rangeQueryCount = dis.readLong();
            
            // Read number of entries
            int entryCount = dis.readInt();
            
            // Read each entry
            for (int i = 0; i < entryCount; i++) {
                // Read key
                Object key = readObject(dis);
                
                // Read row IDs
                int rowIdCount = dis.readInt();
                Set<Long> rowIds = Collections.synchronizedSet(new HashSet<>());
                for (int j = 0; j < rowIdCount; j++) {
                    rowIds.add(dis.readLong());
                }
                
                index.tree.put(new ComparableWrapper(key), rowIds);
            }
            
            log.info("Loaded index {} from disk: {} keys", indexName, index.tree.size());
            return index;
        }
    }
    
    /**
     * Writes an object to the stream with type information.
     */
    private static void writeObject(DataOutputStream dos, Object value) throws IOException {
        if (value == null) {
            dos.writeByte(0);
        } else if (value instanceof Integer) {
            dos.writeByte(1);
            dos.writeInt((Integer) value);
        } else if (value instanceof Long) {
            dos.writeByte(2);
            dos.writeLong((Long) value);
        } else if (value instanceof Double) {
            dos.writeByte(3);
            dos.writeDouble((Double) value);
        } else if (value instanceof String) {
            dos.writeByte(4);
            dos.writeUTF((String) value);
        } else if (value instanceof Boolean) {
            dos.writeByte(5);
            dos.writeBoolean((Boolean) value);
        } else {
            // Default: serialize as string
            dos.writeByte(4);
            dos.writeUTF(String.valueOf(value));
        }
    }
    
    /**
     * Reads an object from the stream.
     */
    private static Object readObject(DataInputStream dis) throws IOException {
        byte type = dis.readByte();
        return switch (type) {
            case 0 -> null;
            case 1 -> dis.readInt();
            case 2 -> dis.readLong();
            case 3 -> dis.readDouble();
            case 4 -> dis.readUTF();
            case 5 -> dis.readBoolean();
            default -> null;
        };
    }
    
    /**
     * Wrapper class to make any Object comparable.
     * This allows us to store different types in the same index.
     */
    private static class ComparableWrapper implements Comparable<ComparableWrapper> {
        private final Object value;
        
        ComparableWrapper(Object value) {
            this.value = value;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public int compareTo(ComparableWrapper other) {
            if (this.value == null && other.value == null) return 0;
            if (this.value == null) return -1;
            if (other.value == null) return 1;
            
            // If both are Comparable and same type, use natural ordering
            if (this.value instanceof Comparable && other.value instanceof Comparable) {
                try {
                    if (this.value.getClass().equals(other.value.getClass())) {
                        return ((Comparable<Object>) this.value).compareTo(other.value);
                    }
                } catch (ClassCastException ignored) {
                    // Fall through to number comparison
                }
            }
            
            // Try numeric comparison
            if (this.value instanceof Number && other.value instanceof Number) {
                double d1 = ((Number) this.value).doubleValue();
                double d2 = ((Number) other.value).doubleValue();
                return Double.compare(d1, d2);
            }
            
            // Try to parse as numbers
            try {
                double d1 = Double.parseDouble(String.valueOf(this.value));
                double d2 = Double.parseDouble(String.valueOf(other.value));
                return Double.compare(d1, d2);
            } catch (NumberFormatException ignored) {
                // Fall through to string comparison
            }
            
            // Fall back to string comparison
            return String.valueOf(this.value).compareTo(String.valueOf(other.value));
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ComparableWrapper that = (ComparableWrapper) obj;
            return Objects.equals(value, that.value);
        }
        
        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
        
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
}
