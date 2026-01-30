#!/bin/bash
# Runner for Zsh completion tests
# Integrates zsh completion tests into the CI/CD pipeline

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Find the zsh executable
if ! command -v zsh &> /dev/null; then
    echo -e "${YELLOW}⊘${NC} Zsh not found, skipping Zsh completion tests."
    exit 0
fi

echo "Running Zsh Completion Tests..."
echo "==============================="

# Run the zsh test script in interactive mode
# The -i flag loads the interactive shell configuration
if zsh -i "$SCRIPT_DIR/test_completion.zsh"; then
    echo -e "${GREEN}✓${NC} Zsh completion tests passed"
    exit 0
else
    echo -e "${RED}✗${NC} Zsh completion tests failed"
    exit 1
fi
