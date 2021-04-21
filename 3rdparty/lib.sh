function patch_if_needed() {
    # if can't reverse, patch
    local -r patch="$1"
    echo "Applying patch $patch"
    if patch -p1 -R --dry-run --force --fuzz=0 --input="../patches/$patch" > /dev/null; then
        echo 'Already applied'
    else
        local -r output="$(patch -p1 --fuzz=0 --input="../patches/$patch")" code="$?"
        if [[ "$code" -ne 0 ]]; then
            printf '%s\n' "$output"
            echo 'Failed to apply patch, exiting'
            exit 1
        else
            echo 'Applied'
        fi
    fi
    echo
}

function apply_patches() {
    local -r patches=("$@")
    for patch in "${patches[@]}"; do
        patch_if_needed "$patch" || return 1
    done
}
