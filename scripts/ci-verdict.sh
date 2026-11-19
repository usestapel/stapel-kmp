#!/usr/bin/env bash
# The ci-ok verdict of one CI run. Prints one of
#   GREEN                           every needed job succeeded (exit 0)
#   SUPERSEDED by <sha>: <jobs>     no job failed, but a job was cancelled before it ever started (a
#                                   newer commit's job took its place in the macOS queue) and the
#                                   branch has moved on; this commit is NOT verified (exit 1)
#   FAILED: <job=result ...>        anything else (exit 1)
# A job cancelled after it started, or cancelled while this commit is still the newest, is FAILED.
# A job skipped because a superseded job did not run counts with the superseded ones.
#
# Inputs (environment):
#   NEEDS       toJSON(needs) of the ci-ok job: {"<job id>": {"result": "...", ...}, ...}
#   JOBS        the run attempt's jobs from the REST API (.jobs): [{name, conclusion, steps, runner_name}]
#   THIS_SHA    the commit this run verifies
#   LATEST_SHA  the branch's (or pull request's) head now
# Rules tested by build-logic CiWorkflowTest.
set -euo pipefail

results="$(jq -r 'to_entries[] | "\(.key)=\(.value.result)"' <<<"${NEEDS:-{\}}")"
if [[ -z "$results" ]]; then echo "FAILED: no jobs"; exit 1; fi
if ! grep -qv '=success$' <<<"$results"; then echo "GREEN"; exit 0; fi

superseded=()
other=()
skipped=()
while IFS='=' read -r job result; do
    case "$result" in
        success) ;;
        skipped) skipped+=("$job") ;;
        cancelled)
            # A job that never got a runner: no steps ran.
            never_started="$(jq -r --arg j "$job" '
                [.[] | select(.name == $j or (.name | startswith($j + " (")))] |
                if length == 0 then "false"
                else (all(.[]; .conclusion == "cancelled" and ((.steps // []) | length) == 0 and ((.runner_name // "") == ""))) | tostring
                end' <<<"${JOBS:-[]}")"
            if [[ "$never_started" == "true" && "${LATEST_SHA:-}" != "${THIS_SHA:-}" && -n "${LATEST_SHA:-}" ]]; then
                superseded+=("$job")
            else
                other+=("$job=$result")
            fi
            ;;
        *) other+=("$job=$result") ;;
    esac
done <<<"$results"

if [[ ${#other[@]} -eq 0 && ${#superseded[@]} -gt 0 ]]; then
    echo "SUPERSEDED by ${LATEST_SHA}: ${superseded[*]}${skipped[*]:+ (skipped after it: ${skipped[*]})} never started; commit ${THIS_SHA} is not verified"
    exit 1
fi
for j in "${skipped[@]+"${skipped[@]}"}"; do other+=("$j=skipped"); done
echo "FAILED: ${other[*]}"
exit 1
