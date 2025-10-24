#!/bin/bash

echo "🧪 DEMO: Smart Rebuild System"
echo "================================"
echo ""

echo "1️⃣  First run (should rebuild):"
echo "   ./start-smart-dev.sh"
echo "   → Rebuilds frontend (takes ~4 minutes)"
echo "   → Starts all services"
echo ""

echo "2️⃣  Second run (no changes):"
echo "   ./start-smart-dev.sh"
echo "   → Uses existing image (starts in ~30 seconds)"
echo "   → No rebuild needed!"
echo ""

echo "3️⃣  After making code changes:"
echo "   # Edit any .kt file in src/"
echo "   ./start-smart-dev.sh"
echo "   → Detects changes and rebuilds automatically"
echo "   → Only rebuilds when necessary!"
echo ""

echo "💡 Benefits:"
echo "   ✅ No more waiting 4-5 minutes every time"
echo "   ✅ Only rebuilds when code actually changes"
echo "   ✅ Smart detection of file modifications"
echo "   ✅ Production-ready containers"
echo ""

echo "🛑 To stop: docker-compose down"
echo "🔄 To force rebuild: docker-compose build --no-cache frontend"
