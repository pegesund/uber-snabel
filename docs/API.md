# Uber Snabel API Documentation

Complete REST API documentation for the Uber Snabel meta-programming system.

## Base URL

```
http://localhost:8081
```

## Overview

Uber Snabel provides a REST API for managing Figma-to-code transformation sessions. The API allows you to:
- Create and manage import sessions
- Upload Figma-exported code (zip files)
- Start Claude Code transformations
- Monitor progress via WebSocket
- Validate generated code
- Merge changes to the main branch

---

## Authentication

Currently, no authentication is required (development mode).

---

## Session Management Endpoints

### POST /api/import/session

Create a new import session.

**Request Body:**
```json
{
  "description": "Invoice management UI",
  "instructions": "Create an invoice list with filtering and pagination"
}
```

**Response (200 OK):**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CREATED",
  "createdAt": "2025-11-09T16:00:00"
}
```

**Example:**
```bash
curl -X POST http://localhost:8081/api/import/session \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Invoice management UI",
    "instructions": "Create an invoice list with filtering"
  }'
```

---

### POST /api/import/session/{sessionId}/upload

Upload a Figma-exported code zip file.

**Path Parameters:**
- `sessionId` (string, required): Session ID from create session

**Request:**
- Content-Type: `multipart/form-data`
- Form field: `file` (zip file)

**Response (200 OK):**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ANALYZING",
  "unpackedPath": "/tmp/uber-snabel/unpacked/550e8400-e29b-41d4-a716-446655440000",
  "analysis": {
    "totalFiles": 47,
    "typescriptFiles": 23,
    "javascriptFiles": 5,
    "totalSizeMB": 2.45
  }
}
```

**Example:**
```bash
curl -X POST http://localhost:8081/api/import/session/550e8400-e29b-41d4-a716-446655440000/upload \
  -F "file=@figma-export.zip"
```

---

### POST /api/import/session/{sessionId}/start

Start Claude Code transformation.

**Path Parameters:**
- `sessionId` (string, required): Session ID

**Request Body (optional):**
```json
{
  "additionalInstructions": "Use Material-UI components where possible"
}
```

**Response (200 OK):**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING",
  "branchName": "figma-import/invoice-management-ui-20251109-160000"
}
```

**Example:**
```bash
curl -X POST http://localhost:8081/api/import/session/550e8400-e29b-41d4-a716-446655440000/start \
  -H "Content-Type: application/json" \
  -d '{
    "additionalInstructions": "Use Material-UI components"
  }'
```

---

### POST /api/import/session/{sessionId}/stop

Stop the running Claude Code process.

**Path Parameters:**
- `sessionId` (string, required): Session ID

**Response (200 OK):**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "STOPPED"
}
```

**Example:**
```bash
curl -X POST http://localhost:8081/api/import/session/550e8400-e29b-41d4-a716-446655440000/stop
```

---

### POST /api/import/session/{sessionId}/command

Send a command or query to the running Claude Code process.

**Path Parameters:**
- `sessionId` (string, required): Session ID

**Request Body:**
```json
{
  "command": "What files have been modified so far?"
}
```

**Response (200 OK):**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "command": "What files have been modified so far?",
  "sent": true
}
```

**Response (400 Bad Request):**
```json
{
  "error": "No Claude Code process is running for this session"
}
```

**Example:**
```bash
curl -X POST http://localhost:8081/api/import/session/550e8400-e29b-41d4-a716-446655440000/command \
  -H "Content-Type: application/json" \
  -d '{"command": "What files have been modified so far?"}'
```

**Use Cases:**
- Query progress: "What files have been modified so far?"
- Request information: "Can you explain what you're doing?"
- Give additional instructions: "Please also add error handling"

---

### POST /api/import/session/{sessionId}/validate

Run validation checks on the generated code.

**Path Parameters:**
- `sessionId` (string, required): Session ID

**Response (200 OK):**
```json
{
  "passed": true,
  "typescript": true,
  "apiCompatibility": true,
  "tests": true,
  "build": true,
  "error": null
}
```

**Validation Checks:**
1. **TypeScript Compilation**: `npx tsc --noEmit`
2. **API Compatibility**: Ensures only allowed backend endpoints are used
3. **Tests**: `npm run test` (if tests exist)
4. **Build**: `npm run build`

**Example:**
```bash
curl -X POST http://localhost:8081/api/import/session/550e8400-e29b-41d4-a716-446655440000/validate
```

---

### POST /api/import/session/{sessionId}/merge

Merge the generated code branch to main.

**Path Parameters:**
- `sessionId` (string, required): Session ID

**Response (200 OK):**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "MERGED",
  "mergedAt": "2025-11-09T16:30:00"
}
```

**Note:**
- Automatically resolves conflicts (latest version wins)
- Deletes the feature branch after successful merge
- Creates commit with descriptive message

**Example:**
```bash
curl -X POST http://localhost:8081/api/import/session/550e8400-e29b-41d4-a716-446655440000/merge
```

---

### GET /api/import/session/{sessionId}

Get session details and status.

**Path Parameters:**
- `sessionId` (string, required): Session ID

**Response (200 OK):**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "description": "Invoice management UI",
  "status": "COMPLETED",
  "branchName": "figma-import/invoice-management-ui-20251109-160000",
  "createdAt": "2025-11-09T16:00:00",
  "completedAt": "2025-11-09T16:25:00",
  "merged": false,
  "errorMessage": null,
  "filesCreated": 12,
  "filesModified": 3,
  "filesDeleted": 0,
  "isRunning": false
}
```

**Example:**
```bash
curl http://localhost:8081/api/import/session/550e8400-e29b-41d4-a716-446655440000
```

---

### GET /api/import/sessions

List all import sessions.

**Query Parameters:**
- `limit` (integer, optional, default: 50): Maximum number of results

**Response (200 OK):**
```json
[
  {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "description": "Invoice management UI",
    "status": "COMPLETED",
    "createdAt": "2025-11-09T16:00:00",
    "merged": false
  },
  {
    "sessionId": "660e8400-e29b-41d4-a716-446655440001",
    "description": "Expense tracking form",
    "status": "RUNNING",
    "createdAt": "2025-11-09T15:00:00",
    "merged": false
  }
]
```

**Example:**
```bash
# Get last 20 sessions
curl "http://localhost:8081/api/import/sessions?limit=20"
```

---

## Git Operations

### GET /api/import/session/{sessionId}/diff

Get git diff for the session's branch.

**Path Parameters:**
- `sessionId` (string, required): Session ID

**Response (200 OK):**
```diff
diff --git a/apps/invoicing/src/InvoiceList.tsx b/apps/invoicing/src/InvoiceList.tsx
new file mode 100644
index 0000000..abc1234
--- /dev/null
+++ b/apps/invoicing/src/InvoiceList.tsx
@@ -0,0 +1,50 @@
+import React from 'react';
+
+export function InvoiceList() {
+  return <div>Invoice List</div>;
+}
```

**Example:**
```bash
curl http://localhost:8081/api/import/session/550e8400-e29b-41d4-a716-446655440000/diff
```

---

### GET /api/import/session/{sessionId}/changes

Get list of changed files.

**Path Parameters:**
- `sessionId` (string, required): Session ID

**Response (200 OK):**
```json
{
  "changes": [
    "A apps/invoicing/src/InvoiceList.tsx",
    "M apps/invoicing/src/index.tsx",
    "D apps/invoicing/src/OldComponent.tsx"
  ]
}
```

**File Status Codes:**
- `A` - Added (new file)
- `M` - Modified
- `D` - Deleted

**Example:**
```bash
curl http://localhost:8081/api/import/session/550e8400-e29b-41d4-a716-446655440000/changes
```

---

## System Status Endpoints

### GET /api/status

Get overall system status.

**Response (200 OK):**
```json
{
  "uber-snabel": {
    "version": "1.0.0",
    "status": "running"
  },
  "config": {
    "frontendPath": "/home/petter/dev/snabel/snabel_frontend",
    "backendPath": "/home/petter/dev/snabel/snabel_backend",
    "tempDirectory": "/tmp/uber-snabel",
    "claudeExecutable": "claude",
    "configFile": "/home/petter/.uber-snabel/config.ini"
  }
}
```

**Example:**
```bash
curl http://localhost:8081/api/status
```

---

### GET /api/status/frontend

Check if frontend is running.

**Response (200 OK):**
```json
{
  "configured": true,
  "path": "/home/petter/dev/snabel/snabel_frontend",
  "exists": true,
  "running": true,
  "url": "http://localhost:4200"
}
```

**Example:**
```bash
curl http://localhost:8081/api/status/frontend
```

---

### GET /api/status/backend

Check if backend is running.

**Response (200 OK):**
```json
{
  "configured": true,
  "path": "/home/petter/dev/snabel/snabel_backend",
  "exists": true,
  "running": true,
  "url": "http://localhost:8080"
}
```

**Example:**
```bash
curl http://localhost:8081/api/status/backend
```

---

### GET /api/status/config

Get current configuration.

**Response (200 OK):**
```json
{
  "frontend.path": "/home/petter/dev/snabel/snabel_frontend",
  "backend.path": "/home/petter/dev/snabel/snabel_backend",
  "temp.directory": "/tmp/uber-snabel",
  "claude.executable": "claude",
  "claude.unsafe.mode": "true",
  "branch.prefix": "figma-import",
  "upload.max.size.mb": "100",
  "session.timeout.hours": "24"
}
```

**Example:**
```bash
curl http://localhost:8081/api/status/config
```

---

### PUT /api/status/config

Update configuration.

**Request Body:**
```json
{
  "frontend.path": "/new/path/to/frontend",
  "branch.prefix": "feature-import"
}
```

**Response (200 OK):**
```json
{
  "message": "Configuration updated successfully"
}
```

**Note:** Changes are saved to `~/.uber-snabel/config.ini`

**Example:**
```bash
curl -X PUT http://localhost:8081/api/status/config \
  -H "Content-Type: application/json" \
  -d '{
    "branch.prefix": "feature-import"
  }'
```

---

## WebSocket Endpoints

### WS /ws/logs/{sessionId}

Real-time log streaming for a session.

**Path Parameters:**
- `sessionId` (string, required): Session ID

**Message Format:**
```json
{
  "timestamp": "2025-11-09T16:00:00",
  "level": "INFO",
  "message": "Starting Claude Code transformation..."
}
```

**Log Levels:**
- `INFO` - General information
- `ERROR` - Errors
- `GIT` - Git operations
- `FILE` - File operations
- `VALIDATE` - Validation messages

**Example (JavaScript):**
```javascript
const ws = new WebSocket('ws://localhost:8081/ws/logs/550e8400-e29b-41d4-a716-446655440000');

ws.onmessage = (event) => {
  const log = JSON.parse(event.data);
  console.log(`[${log.level}] ${log.message}`);
};

ws.onopen = () => {
  console.log('Connected to log stream');
};

ws.onclose = () => {
  console.log('Disconnected from log stream');
};
```

---

## Session Status Values

Sessions progress through these states:

| Status | Description |
|--------|-------------|
| `CREATED` | Session created, ready for file upload |
| `UNPACKING` | Unpacking uploaded zip file |
| `ANALYZING` | Analyzing unpacked files |
| `TRANSFORMING` | Claude Code is analyzing the structure |
| `RUNNING` | Claude Code is actively transforming code |
| `PAUSED` | Paused by user |
| `VALIDATING` | Running validation checks |
| `COMPLETED` | Successfully completed |
| `FAILED` | Failed with errors |
| `MERGED` | Merged into main branch |

---

## Error Responses

### 404 Not Found
```json
{
  "error": "Session not found"
}
```

### 400 Bad Request
```json
{
  "error": "No branch to merge"
}
```

### 500 Internal Server Error
```json
{
  "error": "Failed to start: <error details>"
}
```

---

## Complete Workflow Example

```bash
# 1. Create session
SESSION_ID=$(curl -s -X POST http://localhost:8081/api/import/session \
  -H "Content-Type: application/json" \
  -d '{"description":"Invoice UI","instructions":"Create invoice list"}' \
  | jq -r '.sessionId')

echo "Session created: $SESSION_ID"

# 2. Upload Figma code
curl -X POST http://localhost:8081/api/import/session/$SESSION_ID/upload \
  -F "file=@figma-export.zip"

# 3. Start transformation
curl -X POST http://localhost:8081/api/import/session/$SESSION_ID/start

# 4. Monitor via WebSocket (in browser or separate script)
# ws://localhost:8081/ws/logs/$SESSION_ID

# 5. Check status
curl http://localhost:8081/api/import/session/$SESSION_ID

# 6. View changes
curl http://localhost:8081/api/import/session/$SESSION_ID/changes

# 7. Validate code
curl -X POST http://localhost:8081/api/import/session/$SESSION_ID/validate

# 8. Merge to main
curl -X POST http://localhost:8081/api/import/session/$SESSION_ID/merge
```

---

## Rate Limiting

Currently no rate limiting is implemented.

---

## CORS

CORS is enabled for:
- `http://localhost:4200` (frontend)
- `http://localhost:8081` (self)

Methods: GET, POST, PUT, DELETE, OPTIONS

---

## Notes

1. **Session IDs**: UUIDs generated automatically
2. **File Size Limit**: 100MB (configurable in application.properties)
3. **Session Timeout**: 24 hours (configurable)
4. **Git Branches**: Automatically named `{prefix}/{description}-{timestamp}`
5. **Conflict Resolution**: Automatic (latest version wins)
6. **Database**: PostgreSQL with Hibernate ORM auto-schema generation
7. **Backend API Integration**: Automatically loads and includes backend API documentation from `{backend-path}/docs/API.md` in Claude Code instructions
8. **Health Checks**: Frontend/backend status checks verify both port availability and HTTP response
9. **Interactive Commands**: Send real-time commands to running Claude Code processes via WebSocket

---

## Support

For issues or questions:
- Check application logs: `tail -f uber-snabel.log`
- Review session details via API
- Check WebSocket logs for real-time debugging
