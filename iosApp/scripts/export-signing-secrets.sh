#!/bin/bash
# Prepares the iOS CI signing secrets for GitHub Actions (.github/workflows/ios-appstore.yml).
# Usage: ./iosApp/scripts/export-signing-secrets.sh <distribution.p12> <AuthKey_XXXX.p8>
#
# Export the .p12 first: Keychain Access → My Certificates → "Apple Distribution: … (7K7CYUNXTT)"
# → right-click → Export, with a password. The .p8 is the App Store Connect API key file.
# Each value is copied to the clipboard in turn as single-line base64; paste it into
# GitHub → Settings → Secrets and variables → Actions under the name shown.
set -euo pipefail

P12="${1:-}"
P8="${2:-}"
if [ -z "$P12" ] || [ ! -f "$P12" ]; then
    echo "Usage: $0 <distribution.p12> <AuthKey_XXXX.p8>" >&2
    exit 1
fi

# Fail early if the .p12 is not a readable PKCS#12 bundle with a key (the CI error was
# "Unable to decode the provided data").
read -r -s -p "Password you set when exporting the .p12: " P12_PASS; echo
if ! openssl pkcs12 -in "$P12" -passin "pass:$P12_PASS" -nokeys -noout 2>/dev/null &&
   ! openssl pkcs12 -legacy -in "$P12" -passin "pass:$P12_PASS" -nokeys -noout 2>/dev/null; then
    echo "Could not open $P12 with that password. Re-export it from Keychain Access." >&2
    exit 1
fi

base64 -i "$P12" | tr -d '\n' | pbcopy
echo "Copied APPLE_CERTIFICATE_BASE64 to the clipboard. Paste it into GitHub, then press Return."
read -r
echo "Set APPLE_CERTIFICATE_PASSWORD to the export password you just typed (not copied here)."
echo "Set KEYCHAIN_PASSWORD to any random string."

if [ -n "$P8" ] && [ -f "$P8" ]; then
    echo "Press Return to copy APP_STORE_CONNECT_KEY_BASE64."
    read -r
    base64 -i "$P8" | tr -d '\n' | pbcopy
    KEY_ID="$(basename "$P8" .p8)"; KEY_ID="${KEY_ID#AuthKey_}"
    echo "Copied APP_STORE_CONNECT_KEY_BASE64. APP_STORE_CONNECT_KEY_ID is probably: $KEY_ID"
    echo "APP_STORE_CONNECT_ISSUER_ID is on App Store Connect → Users and Access → Integrations."
fi

echo "Done. Re-run Actions → iOS App Store Release → Run workflow."
