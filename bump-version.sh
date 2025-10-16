#!/bin/bash

# A script to automate the process of creating a Homebrew version bump PR.

# Exit immediately if a command exits with a non-zero status.
set -e

# Check if a version number was provided as an argument.
if [ -z "$1" ]; then
  echo "Usage: $0 <version>"
  echo "Example: $0 1.4.1"
  exit 1
fi

VERSION="$1"
FILENAME="gradle-completion-${VERSION}.tar.gz"
URL="https://github.com/gradle/gradle-completion/releases/download/v${VERSION}/${FILENAME}"

echo "==> Preparing to bump formula to version ${VERSION}"

# Download the release tarball using curl (more common on macOS than wget).
echo "==> Downloading from ${URL}..."
curl -sSL -o "$FILENAME" "$URL"
echo "    Download complete."

# Calculate the SHA256 checksum.
echo "==> Calculating SHA256 checksum for ${FILENAME}..."
SHA256=$(shasum -a 256 "$FILENAME" | awk '{print $1}')
echo "    SHA256: ${SHA256}"

# Run the brew command with the dynamically gathered URL and SHA256.
echo "==> Executing brew bump-formula-pr..."
brew bump-formula-pr --url="$URL" --sha256="$SHA256" gradle-completion

# Clean up the downloaded tarball.
echo "==> Cleaning up ${FILENAME}..."
rm "$FILENAME"

echo ""
echo "==> All done. Follow the prompts from brew to finalize the pull request."
