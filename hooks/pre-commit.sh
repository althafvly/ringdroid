#!/usr/bin/env bash

echo '[githooks] formatting files using spotless'
echo

./gradlew spotlessApply

changed_files="$(git diff --name-only)"
echo

# check if there are untracked files
if [[ ! -z "$changed_files" ]] && [[ -n "$changed_files" ]];
then
    echo '[githooks] aborting commit, untracked files found:'
    echo "$changed_files"
    exit 1
else
    echo '[githooks] continuing commit, no untracked files found'
fi
