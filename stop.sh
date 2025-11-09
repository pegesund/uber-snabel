#!/bin/bash

echo "================================================"
echo "  Stopping Uber Snabel"
echo "================================================"
echo ""

# Check if PID file exists
if [ ! -f .uber-snabel.pid ]; then
    echo "⚠  No PID file found (.uber-snabel.pid)"
    echo "   Uber Snabel doesn't appear to be running"
    echo ""
    echo "Checking for any uber-snabel processes..."

    # Look for processes in this specific directory
    PROJECT_DIR=$(pwd)
    PIDS=$(pgrep -f "quarkus:dev.*${PROJECT_DIR}")

    if [ -n "$PIDS" ]; then
        echo "Found uber-snabel processes: $PIDS"
        read -p "Kill these processes? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo "$PIDS" | xargs kill
            sleep 2
            echo "✓ Processes killed"
        fi
    else
        echo "No uber-snabel processes found"
    fi
    exit 0
fi

# Read PID from file
PID=$(cat .uber-snabel.pid)

# Check if process is running
if ! ps -p $PID > /dev/null 2>&1; then
    echo "⚠  Process with PID $PID is not running"
    rm -f .uber-snabel.pid
    echo "✓ Cleaned up stale PID file"
    exit 0
fi

echo "Stopping Uber Snabel (PID: $PID)..."

# Try graceful shutdown first
kill $PID

# Wait for process to stop (max 10 seconds)
for i in {1..10}; do
    if ! ps -p $PID > /dev/null 2>&1; then
        echo "✓ Uber Snabel stopped gracefully"
        rm -f .uber-snabel.pid
        exit 0
    fi
    sleep 1
    echo -n "."
done

echo ""
echo "⚠  Process did not stop gracefully, forcing shutdown..."

# Force kill
kill -9 $PID

sleep 1

if ! ps -p $PID > /dev/null 2>&1; then
    echo "✓ Uber Snabel force stopped"
    rm -f .uber-snabel.pid
else
    echo "❌ Failed to stop process $PID"
    echo "   You may need to manually kill it: kill -9 $PID"
    exit 1
fi
