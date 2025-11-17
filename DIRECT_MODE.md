# Direct Mode - Talk to Claude Code Without Upload

## Overview

You can now start Claude Code sessions **without uploading any source code**. This allows you to have direct conversations with Claude Code for general development tasks, bug fixes, refactoring, or any other coding work.

## Two Modes of Operation

### 1. **source code Transformation Mode** (Original)
- Upload uploaded zip file
- Claude transforms mockup code into production-ready components
- Works with uploaded files as context

### 2. **Direct Development Mode** (NEW!)
- No upload required
- Start Claude Code immediately
- General development tasks
- Direct conversation with Claude

## How to Use Direct Mode

### Via GUI

1. **Create Session**
   - Go to "New Import" tab
   - Enter description (e.g., "Add user authentication")
   - Select target MFE (optional)
   - Add instructions describing what you want Claude to do
   - Click "Create Session"

2. **Start Without Upload**
   - Click the green **"Start Without Upload"** button
   - Skip the file upload step entirely

3. **Interact with Claude**
   - Use the command input field to send messages
   - Claude will respond via the log stream
   - Send follow-up questions or instructions

### Via API

```bash
# 1. Create session
SESSION_ID=$(curl -s -X POST http://localhost:8081/api/import/session \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Add user authentication",
    "instructions": "Implement JWT-based authentication in the shell MFE",
    "targetMfe": "shell"
  }' | jq -r '.sessionId')

# 2. Start directly (no upload)
curl -X POST http://localhost:8081/api/import/session/$SESSION_ID/start \
  -H "Content-Type: application/json" \
  -d '{}'

# 3. Send commands while running
curl -X POST http://localhost:8081/api/import/session/$SESSION_ID/command \
  -H "Content-Type: application/json" \
  -d '{"command": "What files have you created?"}'
```

## Use Cases

### Perfect for:
- **Feature Development**: "Add a new invoice status filter"
- **Bug Fixes**: "Fix the date formatting issue in expense form"
- **Refactoring**: "Refactor the invoice list to use the design system components"
- **Code Improvements**: "Add error handling to all API calls"
- **Testing**: "Add unit tests for the invoice service"
- **Documentation**: "Add JSDoc comments to the authentication module"
- **General Questions**: "How does the authentication flow work?"

### Examples

**Example 1: Add a Feature**
```
Description: "Add invoice search functionality"
Instructions: "Add a search bar to the invoice list component that filters by invoice number and client name"
Target MFE: invoicing
```

**Example 2: Fix a Bug**
```
Description: "Fix date picker issue"
Instructions: "The date picker in the expense form is not clearing when the form resets. Please fix this."
Target MFE: expenses
```

**Example 3: Refactoring**
```
Description: "Refactor dashboard widgets"
Instructions: "Extract the stats cards into reusable components in the design-system package"
Target MFE: dashboard
```

## How It Works

### Backend Changes

When you start without upload, the system:

1. **Checks for upload** - Detects `session.unpackedPath` is null/empty
2. **Uses direct instructions** - Calls `fileService.buildDirectInstructions()`
3. **Includes full context**:
   - Project architecture (Nx, MFEs, Tailwind)
   - Complete backend API documentation
   - Target MFE information
   - User task description

### Instruction Template

Claude receives context like:
```
You are working on a development task for the Snabel accounting system.

PROJECT CONTEXT:
- Task: Add user authentication
- Target MFE: shell
- Working directory: apps/shell/

ARCHITECTURE:
- Frontend: Nx monorepo with micro-frontends (Module Federation)
- MFEs: shell, dashboard, invoicing, expenses, reports, clients
- Styling: Tailwind CSS
- Shared components: packages/design-system
- TypeScript with React functional components

=== BACKEND API DOCUMENTATION ===
[Full API docs loaded here]
=== END OF API DOCUMENTATION ===

REQUIREMENTS:
1. Follow the Nx micro-frontend architecture
2. Use TypeScript with proper type safety
3. Use Tailwind CSS for styling
...

USER TASK:
Implement JWT-based authentication in the shell MFE
```

## Interactive Commands

Once Claude Code is running, send commands via:

### GUI Input Field
- Type in the "Send Command to Claude Code" field
- Press Enter or click "Send"
- See responses in the log stream

### API
```bash
curl -X POST http://localhost:8081/api/import/session/{id}/command \
  -H "Content-Type: application/json" \
  -d '{"command": "Can you explain the changes?"}'
```

### Example Commands
- "What files have you modified?"
- "Can you explain the authentication flow?"
- "Please also add loading states"
- "Add error handling for failed API calls"
- "Show me the changes you made"

## Comparison: With Upload vs Without Upload

| Feature | With Upload | Without Upload |
|---------|-------------|----------------|
| Use Case | Transform source code | General development |
| Upload Required | Yes (zip file) | No |
| File Analysis | Yes | No |
| Context | Uploaded files | Project architecture |
| Instructions | Transformation-focused | Task-focused |
| Target MFE | Recommended | Recommended |
| Interactive Commands | Yes | Yes |
| Git Branch | Yes | Yes |

## Benefits

1. **Faster**: No need to create and upload zip files
2. **Flexible**: Use for any development task, not just source code transformations
3. **Interactive**: Full two-way conversation with Claude
4. **Contextual**: Still gets project structure and API docs
5. **Focused**: Work in specific MFE if needed

## Workflow Comparison

### Traditional (With Upload)
```
1. Export code from source code → zip
2. Create session
3. Upload zip
4. Wait for analysis
5. Start transformation
6. Interact if needed
```

### Direct Mode (Without Upload)
```
1. Create session with description
2. Start immediately
3. Interact with Claude
```

## Files Modified

1. **Backend**:
   - `ImportResource.java` - Added upload check in startTransformation()
   - `FileService.java` - Added buildDirectInstructions() method

2. **Frontend**:
   - `index.html` - Added "Start Without Upload" button
   - `app.js` - Added startWithoutUpload() function

## Configuration

No configuration changes needed. Works with existing setup:
- Uses same MFE discovery
- Same backend API integration
- Same git branch workflow
- Same WebSocket logging

## Limitations

None! Direct mode has all the same capabilities as upload mode:
- ✅ MFE targeting
- ✅ Backend API documentation
- ✅ Interactive commands
- ✅ Git integration
- ✅ Real-time logging
- ✅ Validation
- ✅ Merging

## Summary

Direct mode makes Uber Snabel a **general-purpose AI pair programming tool** in addition to a source code transformation system. You can now use it for any development task without needing to upload files first.

**Start talking to Claude Code immediately for any development task!**
