# Default Value Fix

## Problem
The backend was failing to start with the following error:
```
Data conversion error converting "JAVA_OBJECT to CHARACTER VARYING"
```

This occurred when trying to save `TableColumn` entities with `defaultValue` set to non-String objects (like Integer `0`).

## Root Cause
- `TableColumn.defaultValue` was defined as `Object` type
- The database column was `TEXT` (which maps to VARCHAR in H2)
- H2 cannot automatically convert serialized Java objects to VARCHAR
- When saving an Integer default value (e.g., `0`), Hibernate tried to serialize it as a Java object, but H2 couldn't convert it

## Solution
Changed `TableColumn.defaultValue` from `Object` to `String` and added conversion logic:

1. **Entity Change** (`TableColumn.java`):
   ```java
   // Before:
   private Object defaultValue;
   
   // After:
   private String defaultValue;
   ```

2. **Service Change** (`RdbmsService.java`):
   ```java
   // Convert defaultValue to String for storage (H2 TEXT column)
   Object defaultValue = colDef.getDefaultValue();
   column.setDefaultValue(defaultValue != null ? String.valueOf(defaultValue) : null);
   ```

## Result
- ✅ Backend starts successfully
- ✅ Default values are stored as strings (e.g., `"0"`, `"pending"`)
- ✅ Tables are created correctly
- ✅ Data can be inserted and retrieved

## Verification
```bash
# Check tables endpoint
curl http://localhost:8080/api/rdbms/tables

# Verify default values are stored as strings
# Example: "defaultValue": "0" for stock column
```

## Notes
- Default values are now stored as strings in the database
- When applying defaults, they can be parsed back to the appropriate type based on the column's `dataType`
- This approach is simpler and more compatible with H2's TEXT column type
