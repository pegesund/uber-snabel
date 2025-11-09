#!/bin/bash

echo "================================================"
echo "  Uber Snabel - Figma to Code Transformer"
echo "================================================"
echo ""

# Check if PostgreSQL is running
if ! pg_isready -q; then
    echo "❌ PostgreSQL is not running"
    echo "   Please start PostgreSQL and try again"
    exit 1
fi
echo "✓ PostgreSQL is running"

# Check if database exists
if ! PGPASSWORD=postgres psql -h localhost -U postgres -lqt | cut -d \| -f 1 | grep -qw uber_snabel; then
    echo "⚠  Database 'uber_snabel' not found"
    read -p "   Create database? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        PGPASSWORD=postgres psql -h localhost -U postgres -c "CREATE DATABASE uber_snabel;"
        echo "✓ Database created"
    else
        echo "❌ Database required to run application"
        exit 1
    fi
else
    echo "✓ Database 'uber_snabel' exists"
fi

# Check if Claude Code is available
if ! command -v claude &> /dev/null; then
    echo "⚠  Claude Code CLI not found in PATH"
    echo "   Application will start but transformations will fail"
    echo "   Install Claude Code or configure path in ~/.uber-snabel/config.ini"
else
    echo "✓ Claude Code CLI found"
fi

echo ""

# Check if uber-snabel is already running
if [ -f .uber-snabel.pid ]; then
    PID=$(cat .uber-snabel.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo "⚠  Uber Snabel is already running (PID: $PID)"
        echo "   Stop it first with ./stop.sh"
        exit 1
    else
        # Stale PID file, remove it
        rm -f .uber-snabel.pid
    fi
fi

echo "Starting Uber Snabel..."
echo "Web UI will be available at: http://localhost:8081"
echo "Logs will be written to: uber-snabel.log"
echo ""
echo "Press Ctrl+C to stop, or run ./stop.sh from another terminal"
echo ""

# Start in background and save PID
nohup ./mvnw quarkus:dev > uber-snabel.log 2>&1 &
echo $! > .uber-snabel.pid

echo "✓ Uber Snabel started (PID: $(cat .uber-snabel.pid))"
echo ""
echo "To view logs: tail -f uber-snabel.log"
echo "To stop: ./stop.sh"
