#!/usr/bin/env bash

set -euo pipefail
IFS=$'\t\n'

[[ $# -eq 2 ]] || { echo "Usage: run-parallel.sh <pet executable> <executions list>" && exit 1; }

pet_path=$1
if [[ ! -e $pet_path ]]; then
  echo "No executable found at $pet_path"
  exit 1
fi

execution_path=$2
if [[ ! -e $pet_path ]]; then
  echo "No executions found at $execution_path"
  exit 1
fi

parallel --eta --progress --jobs 10 --colsep ' :: ' \
  "$(dirname "$0")/run-single.sh" ::: "$pet_path" :::: "$execution_path" ::: {%}