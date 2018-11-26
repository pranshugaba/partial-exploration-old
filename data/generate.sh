#!/usr/bin/env bash

if [[ -z "$1" ]]; then
  echo "Need to pass a configuration file";
else
  grep -v '^#' $1 | while IFS= read -r line; do
    echo ${line} | tr -d '( )' | awk -F';' '{printf "-m data/models/%s -p data/properties/%s -n %s",
     $1, $2, $3; if (length($4) != 0) printf " --const " $4; if (length($5) != 0) printf " --expected " $5; print "" }'
  done
fi
