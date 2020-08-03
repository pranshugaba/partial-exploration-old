#!/usr/bin/env python3

import csv
import os
import subprocess
import sys

types = {"DTMC", "MDP", "CTMC"}
uniform_types = {"CTMC"}

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: create-benchmark.py <path to models.csv> <path to uniformization provider>", file=sys.stderr)
        sys.exit(1)

    path = sys.argv[1]
    if not os.path.isfile(path):
        print(f"No file at {path}", file=sys.stderr)
        sys.exit(1)

    uniform_script = sys.argv[2]
    if not os.path.isfile(uniform_script):
        print(f"No file at {uniform_script}", file=sys.stderr)
        sys.exit(1)

    models = list()
    filenames = set()
    model_paths = dict()
    with open(path, mode="rt", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=',', quotechar='"',
                                fieldnames=("model_file", "model_consts", "model_type", "states", "time_constr"))
        for row in reader:
            filenames.add(row["model_file"])
            models.append({"type": row["model_type"], "file": row["model_file"], "constants": row["model_consts"]})

    directory = os.path.dirname(path)
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file in filenames:
                model_path = os.path.join(root, file)
                if file in model_paths:
                    print(f"Duplicate path for file {file}: {model_path} and {model_paths[file]}", file=sys.stderr)
                    sys.exit(1)
                model_paths[file] = model_path

    for model in models:
        if model["type"] not in types:
            continue
        if model["file"] not in model_paths:
            print(f"No path for model {model}", file=sys.stderr)
            continue
        model_path = model_paths[model["file"]]
        constants = model["constants"]
        if model["type"] in uniform_types:
            process = subprocess.run([uniform_script, "uniform", model_path, constants],
                                     encoding="utf-8", capture_output=True)
            if process.returncode:
                print(f"Failed to determine uniform constant for {model_path}/{constants}", file=sys.stderr)
                if process.stderr:
                    print(process.stderr, file=sys.stderr)
                continue
            uniform_constant = float(process.stdout.rstrip())
        else:
            uniform_constant = ""

        print(f"{model_path};{constants};{uniform_constant}")
