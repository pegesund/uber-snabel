# Uber Snabel - Setup Guide

## Pre-configured Settings

The application is pre-configured for your environment:

### Directory Structure
```
/home/petter/dev/snabel/
├── snabel_frontend/          ← Frontend project (React Nx)
├── snabel_backend/           ← Backend project (Quarkus API)
└── uber_snabel/
    └── uber-snabel/          ← This application
```

### Configuration

**Default paths (already set):**
- Frontend: `/home/petter/dev/snabel/snabel_frontend`
- Backend: `/home/petter/dev/snabel/snabel_backend`
- Temp directory: `/tmp/uber-snabel`
- Claude executable: `claude` (from PATH)

These can be changed in `~/.uber-snabel/config.ini` after first run.

## Quick Start

### 1. Database Setup

```bash
# Check PostgreSQL is running
pg_isready

# Create database (using postgres user with password postgres)
PGPASSWORD=postgres psql -h localhost -U postgres -c "CREATE DATABASE uber_snabel;"

# Verify connection
PGPASSWORD=postgres psql -h localhost -U postgres -d uber_snabel -c "SELECT current_database();"
```

### 2. Verify Claude Code CLI

```bash
# Check if Claude Code is installed
which claude

# If not found, install or configure path in config.ini
```

### 3. Start Application

```bash
cd /home/petter/dev/snabel/uber_snabel/uber-snabel
./start.sh
```

Or manually:

```bash
./mvnw quarkus:dev
```

### 4. Access Application

Open browser: http://localhost:8081

## First Use Workflow

1. **Create Session**
   - Click "New Import" tab
   - Enter description: "Invoice list UI"
   - Add instructions: "Create an invoice list with filtering and pagination"
   - Click "Create Session"

2. **Upload Figma Code**
   - Select zip file with Figma-exported TypeScript
   - Click "Upload & Analyze"
   - Review file analysis

3. **Start Transformation**
   - Click "Start Claude Code"
   - Watch real-time logs in the console
   - Monitor file changes

4. **Review & Merge**
   - Click "View Changes" to see git diff
   - Optionally run validation
   - Click "Merge to Main" when satisfied

## Configuration File

After first run, edit `~/.uber-snabel/config.ini`:

```ini
# Project paths
frontend.path=/home/petter/dev/snabel/snabel_frontend
backend.path=/home/petter/dev/snabel/snabel_backend

# Temporary storage
temp.directory=/tmp/uber-snabel

# Claude Code settings
claude.executable=claude
claude.unsafe.mode=true

# Git settings
branch.prefix=figma-import

# Limits
upload.max.size.mb=100
session.timeout.hours=24
```

## Troubleshooting

### Database Connection Error

Check PostgreSQL is running:
```bash
sudo systemctl status postgresql
# or
pg_ctl status
```

Update credentials in `application.properties` if needed.

### Claude Code Not Found

```bash
# Check installation
claude --version

# If not installed, install Claude Code CLI
# Or set full path in config.ini:
claude.executable=/usr/local/bin/claude
```

### Frontend/Backend Not Detected

Verify paths exist:
```bash
ls -la /home/petter/dev/snabel/snabel_frontend
ls -la /home/petter/dev/snabel/snabel_backend
```

Update paths in Configuration tab or `~/.uber-snabel/config.ini`.

### Port Already in Use

Change port in `application.properties`:
```properties
quarkus.http.port=8082
```

Then access at http://localhost:8082

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                     Web Browser                         │
│                  http://localhost:8081                  │
└────────┬───────────────────────────────────┬────────────┘
         │                                   │
         │ HTTP/REST                         │ WebSocket
         │                                   │ (logs)
┌────────▼───────────────────────────────────▼────────────┐
│              Uber Snabel (Quarkus)                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │ REST API │  │WebSocket │  │  Config  │             │
│  └────┬─────┘  └────┬─────┘  └──────────┘             │
│       │             │                                   │
│  ┌────▼─────────────▼─────────────────────────┐        │
│  │         Service Layer                      │        │
│  │  • FileService (upload/unpack)            │        │
│  │  • ClaudeCodeService (subprocess)         │        │
│  │  • GitService (branch/merge)              │        │
│  │  • ValidationService (checks)             │        │
│  └───┬──────────────┬──────────────┬─────────┘        │
│      │              │              │                   │
└──────┼──────────────┼──────────────┼───────────────────┘
       │              │              │
       │              │              │
   ┌───▼───┐      ┌───▼────┐    ┌───▼────────┐
   │ File  │      │ Claude │    │    Git     │
   │System │      │  Code  │    │ (frontend) │
   └───────┘      │  CLI   │    └────────────┘
                  └────────┘
```

## Development

### Hot Reload

Quarkus dev mode supports hot reload for Java code:

```bash
./mvnw quarkus:dev
```

Edit Java files and changes apply automatically.

### Database Schema

Schema is auto-generated on startup via:
```properties
quarkus.hibernate-orm.database.generation=update
```

To reset database:
```bash
dropdb uber_snabel
createdb uber_snabel
```

### Logs

Logs are stored in:
- Console output (dev mode)
- Database (`import_sessions.output_log` column)
- WebSocket real-time stream

## Production Deployment

### Build

```bash
./mvnw package -Dquarkus.package.type=uber-jar
```

### Run

```bash
java -jar target/uber-snabel-1.0.0-SNAPSHOT-runner.jar
```

### Environment Variables

Override config via environment:

```bash
export QUARKUS_DATASOURCE_USERNAME=prod_user
export QUARKUS_DATASOURCE_PASSWORD=prod_password
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://prod-db:5432/uber_snabel
java -jar target/uber-snabel-1.0.0-SNAPSHOT-runner.jar
```

## Security Notes

⚠️ **Important**: This application runs Claude Code in **unsafe mode** (`--dangerously-skip-permissions`).

- Use only in trusted environments
- Do not expose to public internet
- Review all Figma code before uploading
- Monitor git commits created by Claude
- Keep database credentials secure

## Support

For issues or questions:
- Check logs in the web UI
- Review database session records
- Check git status in frontend directory
- Verify Claude Code CLI is working: `claude --version`
