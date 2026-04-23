#!/usr/bin/env bash
# Run once to create the standard issue labels on GitHub.
# Requires the GitHub CLI (gh) and that you are authenticated: gh auth login
#
# Usage: bash .github/setup-labels.sh

REPO="codaBlurd/data-preprocessor-plugin"

echo "Setting up labels for $REPO ..."

# Delete GitHub's default labels so the tracker stays clean
for label in "bug" "documentation" "duplicate" "enhancement" \
             "good first issue" "help wanted" "invalid" "question" \
             "wontfix"; do
  gh label delete "$label" --repo "$REPO" --yes 2>/dev/null || true
done

# Create the canonical label set
gh label create "bug"             --repo "$REPO" --color "d73a4a" --description "Something isn't working"
gh label create "enhancement"     --repo "$REPO" --color "a2eeef" --description "New feature or improvement"
gh label create "question"        --repo "$REPO" --color "d876e3" --description "Further information is requested"
gh label create "good-first-issue" --repo "$REPO" --color "7057ff" --description "Good for newcomers"
gh label create "dependencies"    --repo "$REPO" --color "0075ca" --description "Dependency version bump"
gh label create "ci"              --repo "$REPO" --color "e4e669" --description "CI / build pipeline"
gh label create "wontfix"         --repo "$REPO" --color "ffffff" --description "This will not be worked on"
gh label create "duplicate"       --repo "$REPO" --color "cfd3d7" --description "This issue or PR already exists"
gh label create "platform-compat" --repo "$REPO" --color "f9a825" --description "IntelliJ Platform API compatibility"

echo "Done. Visit https://github.com/$REPO/labels to verify."
