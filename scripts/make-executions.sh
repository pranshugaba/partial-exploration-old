#!/usr/bin/env bash

set -euo pipefail
IFS=$'\t\n'

declare -a heuristics=("WEIGHTED" "PROB" "DIFFERENCE" "GRAPH_WEIGHTED" "GRAPH_DIFFERENCE")
declare -a step_bounds=(10 100 200 500)
result_dir="results"
separator=" :: "

if [[ $# -ne 1 ]]; then
  echo "Usage: $(basename "$0") <benchmarks.csv>"
  exit 0
fi

csv_file=$1
if [[ ! -e $csv_file ]]; then
  echo "No file found at $csv_file"
  exit 0
fi

mkdir -p $result_dir

while IFS=\; read -r model_path constants uniformization; do
  model_name=$(basename "$model_path")

  base_string="$model_path$separator"

  if [[ -z "$constants" ]]; then
    output_base=$result_dir/$model_name/
    base_string="$base_string$separator"
  else
    constants_suffix=${constants//[^A-Za-z0-9._-]/_}
    output_base=$result_dir/$model_name/$constants_suffix/
    base_string="$base_string$constants$separator"
  fi
  mkdir -p "$output_base"
  echo "$constants" >"${output_base}constants"

  base_string="$base_string$uniformization$separator$output_base$separator"

  echo "${base_string}complete"
  for heuristic in "${heuristics[@]}"; do
    echo "${base_string}unbounded,${heuristic}"
    for step_bound in "${step_bounds[@]}"; do
      echo "${base_string}bounded,${heuristic},${step_bound}"
    done
  done
done <"$csv_file"
