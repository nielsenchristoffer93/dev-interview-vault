#!/usr/bin/env bash
# ==============================================================================
# Dev Interview Vault - Export READMEs for NotebookLM / Gemini / AI
# ==============================================================================
# Usage:
#   ./export-readmes.sh [target_directory]
# Example:
#   ./export-readmes.sh ./notebook-export
#   ./export-readmes.sh /mnt/c/temp/vault-notes
# ==============================================================================

set -euo pipefail

TARGET_DIR="${1:-./notebook-export}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESOLVED_TARGET="$(mkdir -p "$TARGET_DIR" && cd "$TARGET_DIR" && pwd)"

echo "=========================================================="
echo " Dev Interview Vault - Export READMEs for NotebookLM/AI"
echo "=========================================================="
echo "Target directory: $RESOLVED_TARGET"

CATEGORIES=("backend" "cloud-and-k8s" "database" "frontend" "security" "system-design")
CONSOLIDATED_FILE="$RESOLVED_TARGET/_ALL_MODULES_CONSOLIDATED.md"

cat << 'EOF' > "$CONSOLIDATED_FILE"
# Dev Interview Vault - Complete Study Curriculum

> Formatted for Gemini Notebook / NotebookLM audio podcasts, study guides, and flashcards.

---

EOF

COUNT=0

# Export root README as syllabus overview
if [[ -f "$REPO_ROOT/README.md" ]]; then
    cp "$REPO_ROOT/README.md" "$RESOLVED_TARGET/00-vault-syllabus-overview.md"
    echo "  -> Exported: 00-vault-syllabus-overview.md"
    COUNT=$((COUNT + 1))

    echo "<!-- START FILE: 00-vault-syllabus-overview.md -->" >> "$CONSOLIDATED_FILE"
    cat "$REPO_ROOT/README.md" >> "$CONSOLIDATED_FILE"
    echo -e "\n<!-- END FILE: 00-vault-syllabus-overview.md -->\n\n---\n" >> "$CONSOLIDATED_FILE"
fi

# Export all category modules
for cat in "${CATEGORIES[@]}"; do
    CAT_PATH="$REPO_ROOT/$cat"
    if [[ -d "$CAT_PATH" ]]; then
        for mod_dir in "$CAT_PATH"/*; do
            if [[ -d "$mod_dir" && -f "$mod_dir/README.md" ]]; then
                MOD_NAME="$(basename "$mod_dir")"
                DEST_FILE="$cat-$MOD_NAME.md"
                cp "$mod_dir/README.md" "$RESOLVED_TARGET/$DEST_FILE"
                echo "  -> Exported: $DEST_FILE"
                COUNT=$((COUNT + 1))

                echo "<!-- START FILE: $DEST_FILE -->" >> "$CONSOLIDATED_FILE"
                echo -e "# Category: $cat | Module: $MOD_NAME\n" >> "$CONSOLIDATED_FILE"
                cat "$mod_dir/README.md" >> "$CONSOLIDATED_FILE"
                echo -e "\n<!-- END FILE: $DEST_FILE -->\n\n---\n" >> "$CONSOLIDATED_FILE"
            fi
        done
    fi
done

echo "  -> Generated master file: _ALL_MODULES_CONSOLIDATED.md"
echo "=========================================================="
echo " Done! Successfully exported $COUNT files to:"
echo " $RESOLVED_TARGET"
echo " Tip: You can drag all individual files or just '_ALL_MODULES_CONSOLIDATED.md' into NotebookLM."
echo "=========================================================="
