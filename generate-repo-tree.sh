#!/usr/bin/env bash
# ==============================================================================
# Dev Interview Vault - Generate Copyable Directory Tree
# ==============================================================================
# Generates a clean, copyable text tree of the repository, showing top-level
# pillars and modules (max 1 level down into each pillar), ignoring build/vcs.
# ==============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_FILE="${1:-}"

# Ignored directories (exact name match)
IGNORED='^(\.git|\.agents|node_modules|target|notebook-export|\.idea|\.vscode|dist|build)$'

generate_tree() {
    echo "dev-interview-vault/"
    
    # Get top-level directories
    local top_dirs=()
    while IFS= read -r dir; do
        local base
        base="$(basename "$dir")"
        if [[ ! "$base" =~ $IGNORED ]]; then
            top_dirs+=("$dir")
        fi
    done < <(find "$REPO_ROOT" -mindepth 1 -maxdepth 1 -type d | sort)

    local total_top=${#top_dirs[@]}
    local i=0

    for dir in "${top_dirs[@]}"; do
        i=$((i + 1))
        local dir_name
        dir_name="$(basename "$dir")"
        local is_last_top=false
        local prefix="├── "
        local sub_prefix="│   "

        if [[ $i -eq $total_top ]]; then
            is_last_top=true
            prefix="└── "
            sub_prefix="    "
        fi

        echo "${prefix}${dir_name}/"

        # Get sub-modules (1 level down)
        local sub_dirs=()
        while IFS= read -r sdir; do
            local sbase
            sbase="$(basename "$sdir")"
            if [[ ! "$sbase" =~ $IGNORED ]]; then
                sub_dirs+=("$sdir")
            fi
        done < <(find "$dir" -mindepth 1 -maxdepth 1 -type d | sort)

        local total_sub=${#sub_dirs[@]}
        local j=0

        for sdir in "${sub_dirs[@]}"; do
            j=$((j + 1))
            local sdir_name
            sdir_name="$(basename "$sdir")"
            local sub_branch="├── "

            if [[ $j -eq $total_sub ]]; then
                sub_branch="└── "
            fi

            echo "${sub_prefix}${sub_branch}${sdir_name}/"
        done
    done
}

TREE_OUTPUT="$(generate_tree)"

# Print to stdout
echo "$TREE_OUTPUT"

# If an output file or flag was passed, write to file as well
if [[ -n "$OUTPUT_FILE" ]]; then
    echo "$TREE_OUTPUT" > "$OUTPUT_FILE"
    echo ""
    echo "Saved tree output to: $OUTPUT_FILE"
fi
