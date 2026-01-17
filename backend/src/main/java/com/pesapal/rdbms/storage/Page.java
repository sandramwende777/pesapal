package com.pesapal.rdbms.storage;

import com.pesapal.rdbms.config.RdbmsConstants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a fixed-size page of data using slotted page architecture.
 * 
 * <p>This implementation mirrors how production databases like PostgreSQL
 * and MySQL organize data in fixed-size blocks for efficient I/O.</p>
 * 
 * <h2>Page Layout ({@value RdbmsConstants#PAGE_SIZE} bytes)</h2>
 * <pre>
 * +------------------+
 * | Page Header (32B)|  - Page ID, row count, free space offset, flags
 * +------------------+
 * | Row Directory    |  - Array of (offset, length) pairs for each row
 * | (grows down)     |    Each slot is 8 bytes: 4B offset + 4B length
 * +------------------+
 * |   Free Space     |  - Available space for new rows
 * +------------------+
 * | Row Data         |  - Actual serialized row data
 * | (grows up)       |    Grows from bottom of page upward
 * +------------------+
 * </pre>
 * 
 * <h2>Header Layout (32 bytes)</h2>
 * <ul>
 *   <li>Offset 0-3: Page ID (4 bytes)</li>
 *   <li>Offset 4-7: Row count (4 bytes)</li>
 *   <li>Offset 8-11: Free space start - where row directory ends (4 bytes)</li>
 *   <li>Offset 12-15: Free space end - where row data starts (4 bytes)</li>
 *   <li>Offset 16-19: Flags (dirty, leaf, etc.) (4 bytes)</li>
 *   <li>Offset 20-31: Reserved for future use (12 bytes)</li>
 * </ul>
 * 
 * @author Pesapal RDBMS Team
 * @version 2.1
 * @see RdbmsConstants#PAGE_SIZE
 */
@Slf4j
public class Page {
    
    /** Page size in bytes (4KB) - same as PostgreSQL default */
    public static final int PAGE_SIZE = RdbmsConstants.PAGE_SIZE;
    
    /** Page header size in bytes */
    public static final int HEADER_SIZE = RdbmsConstants.PAGE_HEADER_SIZE;
    
    /** Size of each slot entry (offset + length) */
    public static final int SLOT_SIZE = RdbmsConstants.SLOT_SIZE;
    
    @Getter
    private final int pageId;
    private final byte[] data;
    private final ByteBuffer buffer;
    
    // Header fields (stored at beginning of page)
    // Offset 0-3:   Page ID
    // Offset 4-7:   Row count
    // Offset 8-11:  Free space start (where row directory ends)
    // Offset 12-15: Free space end (where row data starts)
    // Offset 16-19: Flags (dirty, leaf, etc.)
    // Offset 20-31: Reserved
    
    private int rowCount;
    private int freeSpaceStart;  // End of row directory
    private int freeSpaceEnd;    // Start of row data
    private boolean dirty;
    
    /**
     * Creates a new empty page.
     */
    public Page(int pageId) {
        this.pageId = pageId;
        this.data = new byte[PAGE_SIZE];
        this.buffer = ByteBuffer.wrap(data);
        this.rowCount = 0;
        this.freeSpaceStart = HEADER_SIZE;
        this.freeSpaceEnd = PAGE_SIZE;
        this.dirty = true;
        writeHeader();
    }
    
    /**
     * Loads a page from existing data.
     */
    public Page(int pageId, byte[] existingData) {
        this.pageId = pageId;
        this.data = new byte[PAGE_SIZE];
        System.arraycopy(existingData, 0, this.data, 0, Math.min(existingData.length, PAGE_SIZE));
        this.buffer = ByteBuffer.wrap(data);
        readHeader();
        this.dirty = false;
    }
    
    /**
     * Attempts to insert a row into this page.
     * Returns the slot number if successful, -1 if page is full.
     */
    public int insertRow(byte[] rowData) {
        int requiredSpace = SLOT_SIZE + rowData.length;
        int availableSpace = freeSpaceEnd - freeSpaceStart;
        
        if (requiredSpace > availableSpace) {
            return -1;  // Page is full
        }
        
        // Allocate space for row data (grows up from bottom)
        freeSpaceEnd -= rowData.length;
        System.arraycopy(rowData, 0, data, freeSpaceEnd, rowData.length);
        
        // Add slot entry (grows down from top)
        int slotOffset = HEADER_SIZE + (rowCount * SLOT_SIZE);
        buffer.putInt(slotOffset, freeSpaceEnd);      // Row offset
        buffer.putInt(slotOffset + 4, rowData.length); // Row length
        
        int slotNumber = rowCount;
        rowCount++;
        freeSpaceStart += SLOT_SIZE;
        dirty = true;
        
        writeHeader();
        return slotNumber;
    }
    
    /**
     * Reads a row from the given slot.
     */
    public byte[] readRow(int slotNumber) {
        if (slotNumber < 0 || slotNumber >= rowCount) {
            return null;
        }
        
        int slotOffset = HEADER_SIZE + (slotNumber * SLOT_SIZE);
        int rowOffset = buffer.getInt(slotOffset);
        int rowLength = buffer.getInt(slotOffset + 4);
        
        if (rowOffset == 0 && rowLength == 0) {
            return null;  // Deleted row
        }
        
        byte[] rowData = new byte[rowLength];
        System.arraycopy(data, rowOffset, rowData, 0, rowLength);
        return rowData;
    }
    
    /**
     * Marks a row as deleted (sets slot to 0,0).
     * Does not reclaim space (would need page compaction).
     */
    public boolean deleteRow(int slotNumber) {
        if (slotNumber < 0 || slotNumber >= rowCount) {
            return false;
        }
        
        int slotOffset = HEADER_SIZE + (slotNumber * SLOT_SIZE);
        buffer.putInt(slotOffset, 0);     // Mark as deleted
        buffer.putInt(slotOffset + 4, 0);
        dirty = true;
        
        return true;
    }
    
    /**
     * Updates a row. If new data is larger, may need to relocate.
     */
    public boolean updateRow(int slotNumber, byte[] newRowData) {
        if (slotNumber < 0 || slotNumber >= rowCount) {
            return false;
        }
        
        int slotOffset = HEADER_SIZE + (slotNumber * SLOT_SIZE);
        int oldOffset = buffer.getInt(slotOffset);
        int oldLength = buffer.getInt(slotOffset + 4);
        
        if (newRowData.length <= oldLength) {
            // Fits in existing space
            System.arraycopy(newRowData, 0, data, oldOffset, newRowData.length);
            buffer.putInt(slotOffset + 4, newRowData.length);
            dirty = true;
            return true;
        } else {
            // Need more space - delete old and insert new
            deleteRow(slotNumber);
            
            // Check if there's space
            int availableSpace = freeSpaceEnd - freeSpaceStart - SLOT_SIZE;
            if (newRowData.length > availableSpace) {
                return false;  // No space
            }
            
            // Insert at end
            freeSpaceEnd -= newRowData.length;
            System.arraycopy(newRowData, 0, data, freeSpaceEnd, newRowData.length);
            
            // Update slot
            buffer.putInt(slotOffset, freeSpaceEnd);
            buffer.putInt(slotOffset + 4, newRowData.length);
            dirty = true;
            
            writeHeader();
            return true;
        }
    }
    
    /**
     * Returns all non-deleted rows.
     */
    public List<byte[]> getAllRows() {
        List<byte[]> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            byte[] row = readRow(i);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }
    
    /**
     * Returns the raw page data for writing to disk.
     */
    public byte[] getData() {
        writeHeader();
        return data.clone();
    }
    
    public int getRowCount() {
        return rowCount;
    }
    
    public int getFreeSpace() {
        return freeSpaceEnd - freeSpaceStart;
    }
    
    public boolean isDirty() {
        return dirty;
    }
    
    public void markClean() {
        this.dirty = false;
    }
    
    private void writeHeader() {
        buffer.putInt(0, pageId);
        buffer.putInt(4, rowCount);
        buffer.putInt(8, freeSpaceStart);
        buffer.putInt(12, freeSpaceEnd);
        buffer.putInt(16, dirty ? 1 : 0);
    }
    
    private void readHeader() {
        // pageId is already set
        rowCount = buffer.getInt(4);
        freeSpaceStart = buffer.getInt(8);
        freeSpaceEnd = buffer.getInt(12);
        // flags at offset 16
    }
    
    /**
     * Serializes a Row object to bytes for storage.
     */
    public static byte[] serializeRow(Row row) {
        try {
            // Simple format: JSON-like but more compact
            // Format: rowId(8) | deleted(1) | numFields(4) | [fieldName(len+str) | fieldValue(type+data)]*
            
            ByteBuffer buf = ByteBuffer.allocate(4096);  // Max row size
            buf.putLong(row.getRowId());
            buf.put((byte) (row.isDeleted() ? 1 : 0));
            buf.putInt(row.getValues().size());
            
            for (var entry : row.getValues().entrySet()) {
                // Write field name
                byte[] nameBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                buf.putShort((short) nameBytes.length);
                buf.put(nameBytes);
                
                // Write field value
                Object value = entry.getValue();
                if (value == null) {
                    buf.put((byte) 0);  // NULL type
                } else if (value instanceof Integer) {
                    buf.put((byte) 1);
                    buf.putInt((Integer) value);
                } else if (value instanceof Long) {
                    buf.put((byte) 2);
                    buf.putLong((Long) value);
                } else if (value instanceof Double) {
                    buf.put((byte) 3);
                    buf.putDouble((Double) value);
                } else if (value instanceof Boolean) {
                    buf.put((byte) 4);
                    buf.put((byte) ((Boolean) value ? 1 : 0));
                } else {
                    // String or other - store as string
                    buf.put((byte) 5);
                    byte[] strBytes = value.toString().getBytes(StandardCharsets.UTF_8);
                    buf.putInt(strBytes.length);
                    buf.put(strBytes);
                }
            }
            
            byte[] result = new byte[buf.position()];
            buf.flip();
            buf.get(result);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to serialize row", e);
            throw new RuntimeException("Failed to serialize row", e);
        }
    }
    
    /**
     * Deserializes bytes back to a Row object.
     */
    public static Row deserializeRow(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            
            long rowId = buf.getLong();
            boolean deleted = buf.get() == 1;
            int numFields = buf.getInt();
            
            Row row = new Row();
            row.setRowId(rowId);
            row.setDeleted(deleted);
            
            for (int i = 0; i < numFields; i++) {
                // Read field name
                short nameLen = buf.getShort();
                byte[] nameBytes = new byte[nameLen];
                buf.get(nameBytes);
                String name = new String(nameBytes, StandardCharsets.UTF_8);
                
                // Read field value
                byte type = buf.get();
                Object value = switch (type) {
                    case 0 -> null;
                    case 1 -> buf.getInt();
                    case 2 -> buf.getLong();
                    case 3 -> buf.getDouble();
                    case 4 -> buf.get() == 1;
                    case 5 -> {
                        int strLen = buf.getInt();
                        byte[] strBytes = new byte[strLen];
                        buf.get(strBytes);
                        yield new String(strBytes, StandardCharsets.UTF_8);
                    }
                    default -> null;
                };
                
                row.getValues().put(name, value);
            }
            
            return row;
            
        } catch (Exception e) {
            log.error("Failed to deserialize row", e);
            throw new RuntimeException("Failed to deserialize row", e);
        }
    }
}
