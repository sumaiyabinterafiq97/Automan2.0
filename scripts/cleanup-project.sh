#!/bin/bash
# Cleanup script to remove unnecessary files for running on another MacBook

set -e

echo "=========================================="
echo "Project Cleanup Script"
echo "=========================================="
echo ""
echo "This script will remove:"
echo "  - Build artifacts (will be regenerated)"
echo "  - macOS system files (.DS_Store)"
echo "  - Test screenshots"
echo "  - Log files"
echo "  - Windows-specific files"
echo "  - Temporary files"
echo ""
read -p "Continue? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 1
fi

echo ""
echo "Cleaning up..."

# Build artifacts (safe to delete - will be regenerated)
echo "  Removing build artifacts..."
rm -rf build/
rm -rf backend/build/
rm -rf kotlin-js-store/
rm -rf .gradle/
rm -rf backend/.gradle/

# macOS system files
echo "  Removing .DS_Store files..."
find . -name ".DS_Store" -delete 2>/dev/null || true

# Log files
echo "  Removing log files..."
find . -name "*.log" -delete 2>/dev/null || true
rm -rf logs/ 2>/dev/null || true

# Test screenshots
echo "  Removing test screenshots..."
rm -f tests/*.png 2>/dev/null || true

# Windows files (optional - comment out if needed)
echo "  Removing Windows-specific files..."
rm -f docker/clean-docker-windows.ps1 2>/dev/null || true
rm -f scripts/**/*.bat 2>/dev/null || true
rm -f scripts/build-and-deploy-frontend.bat 2>/dev/null || true
rm -f gradlew.bat 2>/dev/null || true

# Temporary files
echo "  Removing temporary files..."
rm -f IDLE 2>/dev/null || true
rm -f check_database_schema.sql 2>/dev/null || true

# IDE files
echo "  Removing IDE-specific files..."
rm -rf .cursor/ 2>/dev/null || true

# Redundant docker files (keep only multiplatform)
echo "  Removing redundant docker-compose files..."
rm -f docker/docker-compose.yml 2>/dev/null || true
rm -f docker/docker-compose.client.yml 2>/dev/null || true
rm -f docker/docker-compose.dev.yml 2>/dev/null || true
rm -f docker/docker-compose.hot-reload.yml 2>/dev/null || true

# Empty SQL file
echo "  Removing empty SQL file..."
rm -f scripts/sql/database_schema.sql 2>/dev/null || true

echo ""
echo "=========================================="
echo "Cleanup Complete!"
echo "=========================================="
echo ""
echo "Note: Archived migrations and temporary docs are kept for reference."
echo "You can manually delete them if not needed:"
echo "  - database/archived/"
echo "  - scripts/sql/archived/"
echo "  - DATABASE_MIGRATION_*.md"
echo "  - MIGRATION_VERIFICATION_REPORT.md"
echo ""
