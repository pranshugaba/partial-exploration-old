#!/usr/bin/env bash

set -euo pipefail
IFS=$'\t\n'

[[ $# -eq 7 ]] || exit 1

skip_succeeded=1
skip_all=1
stability_steps="5,10,100"

if [[ -e $(dirname "$0")/timeout ]]; then
  timeout=$(cat "$(dirname "$0")/timeout")
else
  timeout=$((10 * 60))
fi

pet_path=$1
model_path=$2
constants=$3
uniformization=$4
output_base=$5
type=$6
cpu_affinity=$(( ($7 - 1) * 2 ))

function run() {
  local output_base=$1
  local task_type=$2
  local description=$3
  shift 3

  local output="${output_base}${task_type}"

  echo "$description" > "$output.setup"
  if [[ -e "${output}.status" ]]; then
    if [[ $skip_succeeded -eq 1 ]] && [[ "$(cat "${output}.status")" -eq 0 ]] || [[ $skip_all -eq 1 ]]; then
      return 0
    fi
  fi

  export JAVA_OPTS="-Xmx8G -Xss128M"
  # %e realtime
  # %S kerneltime
  # %U usertime
  # %P cpu %
  # %M max resident memory kb
  # %K avg total memory
  # %x exit code
  # 1: 1,%e,%S,%U,%P,%M,%K,%x
  timeout -k 10 $timeout /usr/bin/time -q -f "1,%e,%S,%U,%P,%M,%K,%x" -o "$output.time" taskset -c $cpu_affinity \
    "$@" --output "$output.json" > "$output.out" 2>&1 || ret_val=$?
  if [[ "$ret_val" -ne 130 ]]; then
    echo "$ret_val" > "$output.status"
  fi
}

if [[ "$type" == "complete" ]]; then
  ! run "$output_base" "complete" "complete" \
    "$pet_path" core -m "$model_path" -c "$constants" --complete --component-analysis
elif [[ "$type" == "unbounded"* ]]; then
  IFS=, read -r _ heuristic <<< "$type"
  ! run "$output_base" "${heuristic}_unbounded" "unbounded,${heuristic}" \
    "$pet_path" core -m "$model_path" -c "$constants" --uniformization "$uniformization" --component-analysis  \
    --heuristic "$heuristic" --unbounded
elif [[ "$type" == "bounded"* ]]; then
  IFS=, read -r _ heuristic step_bound <<< "$type"
  ! run "$output_base" "${heuristic}_bounded_${step_bound}" "bounded,${heuristic},${step_bound}" \
      "$pet_path" core -m "$model_path" -c "$constants" --uniformization "$uniformization" --component-analysis  \
        --heuristic "$heuristic" --bounded "$step_bound" --stability-steps "$stability_steps"
fi