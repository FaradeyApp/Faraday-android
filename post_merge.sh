#!/bin/sh

set -e

mydir="$(dirname "$(realpath "$0")")"
pushd "$mydir" > /dev/null
mydir=.
source "$mydir/merge_helpers.sh"

# Require clean git state
require_clean_git

# Color corrections | TODO more?
sed -i 's|"@color/riotx_accent"|"?colorAccent"|g' vector/src/*/res/layout/* library/ui-styles/src/main/res/layout/*
sed -i 's|"@style/VectorButtonStyle"|"?materialButtonStyle"|g' vector/src/*/res/layout/* library/ui-styles/src/main/res/layout/*
sed -i 's|"@color/element_background_light"|"?backgroundColorLight"|g' vector/src/*/res/layout/* library/ui-styles/src/main/res/layout/*
sed -i 's|#FF4B55|#E53935|g' vector/src/*/res/drawable/* vector-app/src/*/res/drawable/*
sed -i 's|#ff4b55|#e53935|g' vector/src/*/res/drawable/* vector-app/src/*/res/drawable/*
sed -i 's|#737D8C|#808080|g' vector/src/*/res/drawable/* vector-app/src/*/res/drawable/*
uncommitted=`git status --porcelain`
if [ -z "$uncommitted" ]; then
    echo "Seems like colors are still fine :)"
else
    git add -A
    git commit -m 'Automatic color correction'
fi

# Keep in sync with pre_merge.sh!
restore_sc README.md
restore_sc fastlane
restore_sc .github

git add -A
git commit -m "Automatic upstream merge postprocessing"

"$mydir"/correct_strings.sh

popd > /dev/null
