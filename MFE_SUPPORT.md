# Micro-Frontend (MFE) Support

## Overview

Uber Snabel now fully supports the Nx micro-frontend architecture used in the Snabel frontend project. Users can select which MFE to target when transforming Figma code, and Claude Code will work within the specific MFE directory.

## Architecture

The frontend project uses Module Federation with these MFEs:

- **shell** (port 4200) - Host app with layout, routing, auth UI
- **dashboard** (port 4201) - Overview dashboard
- **invoicing** (port 4202) - Invoice management 
- **expenses** (port 4203) - Expense tracking
- **reports** (port 4204) - Financial reports
- **clients** (port 4205) - Client directory

## Features Implemented

### 1. MFE Discovery ✅

**Service**: `MfeDiscoveryService.java`

- Automatically scans `{frontend-path}/apps/` directory
- Discovers all available MFEs
- Falls back to predefined list if directory not found
- Infers routing, ports, and descriptions from MFE names

**API Endpoint**: `GET /api/import/mfes`

**Response**:
```json
[
  {
    "name": "invoicing",
    "path": "/home/petter/dev/snabel/snabel_frontend/apps/invoicing",
    "route": "/invoices/*",
    "port": 4202,
    "description": "Invoice management and templates"
  },
  ...
]
```

### 2. MFE Selection in GUI ✅

**HTML Changes**: `index.html`
- Added dropdown to select target MFE
- Shows MFE name and description
- Loads MFEs dynamically on page load

**JavaScript Changes**: `app.js`
- `loadMfes()` - Fetches and populates MFE dropdown
- `createSession()` - Includes selected MFE in session creation

### 3. Session Model Update ✅

**Model**: `ImportSession.java`
- Added `targetMfe` field (nullable String)
- Stores which MFE the session targets

### 4. Working Directory Management ✅

**Service**: `MfeDiscoveryService`
- `getMfeWorkingDirectory(String mfeName)` - Returns correct path
- Returns `{frontend-path}/apps/{mfe-name}` for specific MFE
- Returns `{frontend-path}` if no MFE selected

**Resource**: `ImportResource.java` (startTransformation)
```java
String workingDirectory = session.targetMfe != null && !session.targetMfe.isEmpty()
    ? mfeDiscoveryService.getMfeWorkingDirectory(session.targetMfe)
    : appConfig.getFrontendPath();
```

### 5. Claude Instructions Enhancement ✅

**Service**: `FileService.buildClaudeInstructions()`

When MFE is selected, adds:
```
PROJECT CONTEXT:
- Target MFE: invoicing
- You are working in the 'invoicing' micro-frontend app

DELIVERABLES:
- Place all components in the 'invoicing' MFE directory (apps/invoicing/src/)
- Follow the routing conventions for the 'invoicing' MFE
- Use the shared design-system package from packages/design-system

IMPORTANT: You are working in apps/invoicing/ - make all changes within this MFE directory.
```

## Usage Workflow

### Step 1: Create Session
1. Enter description (e.g., "Invoice list with filtering")
2. **Select target MFE** from dropdown (e.g., "invoicing")
3. Optionally add instructions
4. Click "Create Session"

### Step 2: Upload & Transform
1. Upload Figma-exported zip
2. Start transformation
3. **Claude Code runs in the MFE directory** (`apps/invoicing/`)

### Step 3: Verification
- All changes are made within the selected MFE
- Git operations work correctly within MFE directory
- TypeScript compilation runs in MFE context

## Git Integration

Git operations work seamlessly with MFEs:
- Branch creation: Uses frontend root directory
- Claude Code execution: Uses specific MFE directory
- Git commits: Captures changes in MFE subdirectory

## API Changes

### New Endpoint
**GET /api/import/mfes**
- Returns list of available MFEs
- No parameters required

### Updated Endpoint
**POST /api/import/session**

Request body now includes:
```json
{
  "description": "Invoice management UI",
  "instructions": "Additional instructions",
  "targetMfe": "invoicing"
}
```

Response includes:
```json
{
  "sessionId": "...",
  "status": "CREATED",
  "targetMfe": "invoicing",
  "createdAt": "..."
}
```

## Files Modified

1. **Model Layer**
   - `src/main/java/com/snabel/model/ImportSession.java` - Added targetMfe field

2. **Service Layer**
   - `src/main/java/com/snabel/service/MfeDiscoveryService.java` - NEW: MFE discovery
   - `src/main/java/com/snabel/service/FileService.java` - Updated Claude instructions

3. **Resource Layer**
   - `src/main/java/com/snabel/resource/ImportResource.java` - Added /mfes endpoint, updated session creation

4. **Frontend**
   - `src/main/resources/templates/index.html` - Added MFE dropdown
   - `src/main/resources/META-INF/resources/js/app.js` - Added loadMfes() function

## Testing

**MFE Discovery**: ✅
```bash
curl http://localhost:8081/api/import/mfes | jq
# Returns all 6 MFEs correctly
```

**Session Creation with MFE**: ✅
```bash
curl -X POST http://localhost:8081/api/import/session \
  -H "Content-Type: application/json" \
  -d '{"description":"Test","targetMfe":"invoicing"}'
# Creates session with targetMfe set
```

**Working Directory**: ✅
- invoicing → `/home/petter/dev/snabel/snabel_frontend/apps/invoicing`
- none → `/home/petter/dev/snabel/snabel_frontend`

## Benefits

1. **Precision**: Code changes go to the correct MFE directory
2. **Clarity**: Claude knows exactly which MFE it's working on
3. **Safety**: Won't accidentally modify wrong MFE
4. **Flexibility**: Can still work at frontend root if needed
5. **Automation**: MFE list updates automatically when new MFEs added

## Future Enhancements

Potential improvements:
1. Validate MFE selection before starting transformation
2. Show MFE structure in GUI
3. Multi-MFE support (work on multiple MFEs in one session)
4. MFE-specific validation rules
5. Cross-MFE dependency detection

## Configuration

No configuration changes required. The system automatically:
- Scans for MFEs in `{frontend.path}/apps/`
- Uses expected Nx workspace structure
- Falls back to predefined list if scanning fails

## Summary

MFE support is fully integrated and tested. Users can now:
- ✅ See all available MFEs in a dropdown
- ✅ Select which MFE to modify
- ✅ Have Claude Code work in the correct MFE directory
- ✅ Get MFE-specific instructions for Claude
- ✅ Maintain proper module federation architecture

The implementation follows the Nx micro-frontend conventions and ensures that Figma transformations respect the modular architecture.
