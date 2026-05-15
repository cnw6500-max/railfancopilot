#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────────
# Railfan Copilot — iOS setup script
# Run once on your Mac after cloning the repo.
# ──────────────────────────────────────────────────────────────────────────────
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🚂 Railfan Copilot iOS Setup"
echo "========================================"

# 1. Check for Homebrew
if ! command -v brew &>/dev/null; then
    echo "Installing Homebrew..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
fi

# 2. Install XcodeGen if needed
if ! command -v xcodegen &>/dev/null; then
    echo "Installing XcodeGen..."
    brew install xcodegen
fi

# 3. Copy Secrets file if not present
SECRETS="$SCRIPT_DIR/iosApp/Secrets.swift"
EXAMPLE="$SCRIPT_DIR/iosApp/Secrets.example.swift"
if [ ! -f "$SECRETS" ]; then
    echo "Creating Secrets.swift from example — fill in your API keys!"
    cp "$EXAMPLE" "$SECRETS"
fi

# 4. Build the KMP XCFramework
echo ""
echo "Building KMP shared XCFramework (requires Kotlin/Native — takes ~5 min first time)..."
cd "$ROOT_DIR"
./gradlew :shared:assembleXCFramework

FRAMEWORK="$ROOT_DIR/shared/build/XCFrameworks/release/shared.xcframework"
if [ ! -d "$FRAMEWORK" ]; then
    echo "ERROR: XCFramework not found at $FRAMEWORK"
    exit 1
fi
echo "XCFramework built ✓"

# 5. Generate Xcode project
echo ""
echo "Generating Xcode project..."
cd "$SCRIPT_DIR"
xcodegen generate

echo ""
echo "========================================"
echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "  1. Open iosApp/iosApp.xcodeproj in Xcode"
echo "  2. Edit iosApp/Secrets.swift with your API keys"
echo "  3. Set your Apple Developer Team in Signing & Capabilities"
echo "  4. Build and run on your device or simulator"
echo ""
