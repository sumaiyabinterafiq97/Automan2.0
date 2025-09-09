#!/bin/bash

echo "📦 Packaging Automan Application for Client Distribution..."

# Create client package directory
PACKAGE_DIR="automan-client-package-$(date +%Y%m%d)"
mkdir -p "$PACKAGE_DIR"

echo "📁 Creating package directory: $PACKAGE_DIR"

# Copy essential files
echo "📋 Copying application files..."
cp -r backend "$PACKAGE_DIR/"
cp -r src "$PACKAGE_DIR/"
cp -r gradle "$PACKAGE_DIR/"
cp gradlew "$PACKAGE_DIR/"
cp build.gradle.kts "$PACKAGE_DIR/"
cp settings.gradle.kts "$PACKAGE_DIR/"
cp docker-compose.yml "$PACKAGE_DIR/"
cp Dockerfile "$PACKAGE_DIR/"
cp .dockerignore "$PACKAGE_DIR/"
cp backend/.dockerignore "$PACKAGE_DIR/"
cp backend/Dockerfile "$PACKAGE_DIR/"
cp build-docker.sh "$PACKAGE_DIR/"
cp CLIENT_DEPLOYMENT.md "$PACKAGE_DIR/"

# Copy database initialization
if [ -d "database" ]; then
    cp -r database "$PACKAGE_DIR/"
fi

# Create a simple README for the package
cat > "$PACKAGE_DIR/README.txt" << EOF
🚀 AUTOMAN APPLICATION - CLIENT PACKAGE

This package contains everything needed to run the Automan application using Docker.

QUICK START:
1. Install Docker Desktop from https://www.docker.com/products/docker-desktop/
2. Extract this package to a folder
3. Open terminal/command prompt in that folder
4. Run: ./build-docker.sh (Mac/Linux) or docker-compose up --build -d (Windows)
5. Open browser to: http://localhost:8080

For detailed instructions, see CLIENT_DEPLOYMENT.md

Package created: $(date)
EOF

# Make scripts executable
chmod +x "$PACKAGE_DIR/build-docker.sh"
chmod +x "$PACKAGE_DIR/gradlew"

# Create ZIP archive
echo "🗜️ Creating ZIP archive..."
zip -r "${PACKAGE_DIR}.zip" "$PACKAGE_DIR"

# Clean up temporary directory
rm -rf "$PACKAGE_DIR"

echo ""
echo "✅ Package created successfully!"
echo "📦 Client package: ${PACKAGE_DIR}.zip"
echo ""
echo "📤 Send this ZIP file to your client along with:"
echo "   - Docker Desktop installation link"
echo "   - CLIENT_DEPLOYMENT.md (included in package)"
echo ""
echo "🎯 Your client just needs to:"
echo "   1. Install Docker Desktop"
echo "   2. Extract the ZIP"
echo "   3. Run one command"
echo "   4. Open browser to http://localhost:8080"
