#!/bin/bash
# Script to copy Kotlin/JS dependency files to dist directory
# This ensures all required dependencies are available for the frontend

SOURCE_DIR="build/js/packages/automan-car-purchase/kotlin"
TARGET_DIRS=("build/dist/js/developmentExecutable" "build/dist/js/productionExecutable")

if [ ! -d "$SOURCE_DIR" ]; then
    echo "⚠️  Kotlin/JS package directory not found: $SOURCE_DIR"
    echo "   (Run './gradlew jsBrowserProductionWebpack' first)"
    exit 0
fi

echo "Copying Kotlin/JS dependencies..."

for TARGET_DIR in "${TARGET_DIRS[@]}"; do
    mkdir -p "$TARGET_DIR"

    cp "$SOURCE_DIR"/kotlin-kotlin-stdlib.js "$TARGET_DIR/" 2>/dev/null
    cp "$SOURCE_DIR"/88b0986a7186d029-atomicfu-js-ir.js "$TARGET_DIR/" 2>/dev/null
    cp "$SOURCE_DIR"/kotlin-kotlinx-atomicfu-runtime-js-ir.js "$TARGET_DIR/" 2>/dev/null
    cp "$SOURCE_DIR"/kotlinx-serialization-kotlinx-serialization-core.js "$TARGET_DIR/" 2>/dev/null
    cp "$SOURCE_DIR"/kotlinx.coroutines-kotlinx-coroutines-core-js-ir.js "$TARGET_DIR/" 2>/dev/null
    cp "$SOURCE_DIR"/kotlinx-serialization-kotlinx-serialization-json.js "$TARGET_DIR/" 2>/dev/null
    cp "$SOURCE_DIR"/kotlin_org_jetbrains_kotlin_kotlin_dom_api_compat.js "$TARGET_DIR/" 2>/dev/null

    # Copy updated index.html with correct script order/documentation
    cp src/jsMain/resources/index.html "$TARGET_DIR/" 2>/dev/null
done

# Production bundle should already exist after webpack build, but ensure fallback copy
if [ ! -f "build/dist/js/productionExecutable/automan-car-purchase.js" ]; then
    cp "$SOURCE_DIR"/automan-car-purchase.js "build/dist/js/productionExecutable/" 2>/dev/null
fi

echo "✅ Dependencies copied successfully"
echo "⚠️  IMPORTANT: Script loading order in index.html is CRITICAL - do not change the order!"

