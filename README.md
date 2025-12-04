# Uber Snabel - Claude Code Interface

A meta-programming system that transforms uploaded TypeScript code into production-ready React code for the Snabel accounting system using Claude Code with streaming.

This project uses Quarkus, the Supersonic Subatomic Java Framework.

## Important for AI Assistants (Claude Code)

When working in the accounting-system repo, the Vite dev server runs in the background on port 4200. If you perform git operations that change files (e.g., `git checkout`, `git merge`, `git revert`), Vite's file watcher may crash due to sudden file changes.

**After any git operation that modifies working directory files, verify the server is still running:**
```bash
curl -s http://localhost:4200/frontend/ | head -1
```

**If no response, restart the server:**
```bash
cd /home/petter/snabel/accounting-system && pnpm run dev &
```

**Alternative approach for git operations (avoids crashing Vite):**
Instead of `git checkout main && git merge branch`, push directly to remote:
```bash
git push origin branch-name:main
```

## Overview

Uber Snabel provides a web-based interface for accountants and designers to:

1. Upload uploaded TypeScript/React code (as zip files)
2. Provide context and instructions about what the code implements
3. Let Claude Code transform mockup code into production-ready components
4. Monitor the transformation process in real-time via WebSocket
5. Validate generated code (TypeScript, API compatibility, tests, build)
6. Review changes with git diff
7. Merge changes into main with automatic conflict resolution

## Features

- **Zip Upload & Analysis**: Upload source code, auto-unpack and analyze
- **Claude Code Integration**: Subprocess execution with streaming output
- **Session Management**: Continue existing sessions or start new ones
- **Git Integration**: Auto branch creation, conflict resolution, merging
- **Real-time Monitoring**: WebSocket log streaming to web UI
- **Validation**: TypeScript, API compatibility, tests, and build checks
- **Smart Instructions**: Auto-generates comprehensive Claude prompts
- **Status Monitoring**: Track frontend/backend health from UI

## Prerequisites

- Java 21+
- Maven
- PostgreSQL 12+
- Node.js 18+ (for validation)
- Claude Code CLI installed
- Git

## Quick Start

1. **Database Setup**
   ```bash
   createdb uber_snabel
   ```

2. **Configure** (edit `~/.uber-snabel/config.ini` after first run or use web UI)
   ```ini
   frontend.path=/path/to/snabel_frontend
   backend.path=/path/to/snabel_backend
   ```

3. **Run**
   ```bash
   ./mvnw quarkus:dev
   ```

4. **Access**: http://localhost:8081

## Usage Workflow

1. Create session with description
2. Upload source zip file
3. Start transformation (Claude Code runs automatically)
4. Monitor real-time logs
5. View changes and validate
6. Merge to main branch

## API Endpoints

- `POST /api/import/session` - Create session
- `POST /api/import/session/{id}/upload` - Upload zip
- `POST /api/import/session/{id}/start` - Start transformation
- `POST /api/import/session/{id}/validate` - Validate code
- `POST /api/import/session/{id}/merge` - Merge to main
- `WS /ws/logs/{sessionId}` - Real-time logs

See full API documentation in the project wiki.

## Configuration

Edit `~/.uber-snabel/config.ini`:

```ini
frontend.path=../snabel_frontend
backend.path=../snabel_backend
claude.executable=claude
claude.unsafe.mode=true
branch.prefix=claude-code
```

Or use the Configuration tab in the web UI.

## Running the application

### Quick Start (Recommended)

```bash
# Start the application
./start.sh

# Stop the application
./stop.sh

# View logs
tail -f uber-snabel.log
```

### Manual Start (for development)

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/uber-snabel-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- Hibernate ORM with Panache ([guide](https://quarkus.io/guides/hibernate-orm-panache)): Simplify your persistence code for Hibernate ORM via the active record or the repository pattern
- SmallRye JWT ([guide](https://quarkus.io/guides/security-jwt)): Secure your applications with JSON Web Token
- REST Qute ([guide](https://quarkus.io/guides/qute-reference#rest_integration)): Qute integration for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.
- JDBC Driver - PostgreSQL ([guide](https://quarkus.io/guides/datasource)): Connect to the PostgreSQL database via JDBC
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- WebSockets ([guide](https://quarkus.io/guides/websockets)): WebSocket communication channel support
