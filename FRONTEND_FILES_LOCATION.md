# Frontend Files Location

## Quick Navigation

The frontend code is in the **`frontend/`** directory, not in the root.

## Frontend File Structure

```
frontend/
├── src/                    ← Main source code here
│   ├── App.js              ← Main React component (215 lines)
│   ├── App.css             ← Component styles
│   ├── index.js            ← React entry point
│   └── index.css           ← Global styles
├── public/
│   └── index.html          ← HTML template
├── package.json            ← Dependencies
└── node_modules/           ← Installed packages (ignore this)

```

## How to Open Frontend Files in Your IDE

### Option 1: Navigate to the folder
1. In your IDE/file explorer, go to: `pesapal/frontend/src/`
2. You'll see:
   - `App.js` - The main React component
   - `App.css` - Styles
   - `index.js` - Entry point
   - `index.css` - Global styles

### Option 2: Open specific files directly
- **Main Component**: `frontend/src/App.js`
- **Styles**: `frontend/src/App.css`
- **Entry Point**: `frontend/src/index.js`
- **HTML**: `frontend/public/index.html`

## File Descriptions

### `frontend/src/App.js` (Main Component)
- Contains the SQL REPL interface
- Table browser functionality
- API calls to backend
- State management
- **215 lines of code**

### `frontend/src/App.css`
- All styling for the App component
- Layout, colors, buttons, tables

### `frontend/src/index.js`
- React entry point
- Renders the App component

### `frontend/src/index.css`
- Global CSS styles

### `frontend/public/index.html`
- Base HTML template
- Contains the `<div id="root">` where React renders

## Quick Access Commands

```bash
# View frontend files
cd frontend/src
ls -la

# Open in your default editor (macOS)
open frontend/src/App.js

# Or use your IDE to open the frontend folder
```

## Note About Root Directory

The root directory (`pesapal/`) contains:
- **21+ markdown documentation files** (`.md` files)
- `backend/` folder (Java/Spring Boot code)
- `frontend/` folder (React code) ← **This is what you're looking for!**

The frontend code is **NOT** in the root - it's in the `frontend/` subdirectory.
