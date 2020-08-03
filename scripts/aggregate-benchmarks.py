#!/usr/bin/env python3

import json
import os
import sys

heuristics = {
    "WEIGHTED", "PROB", "DIFFERENCE", "GRAPH_WEIGHTED", "GRAPH_DIFFERENCE"
}
experiment_expected_files = {
    ".out", ".status", ".time", ".setup"
}

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <path_to_results> <path_to_aggregate.json>")
        sys.exit(1)

    results_dir = sys.argv[1]
    if not os.path.isdir(results_dir):
        print(f"{results_dir} is not a directory")
        sys.exit(1)

    models = set(model.name for model in os.scandir(results_dir) if model.is_dir())
    instances = dict()

    for model in models:
        model_parameters = []
        instances[model] = model_parameters
        model_path = results_dir + os.sep + model

        if os.path.isfile(model_path + os.sep + "constants"):
            experiment_folders = [model_path]
        else:
            experiment_folders = []
            for parameter_folder in os.scandir(model_path):
                if not parameter_folder.is_dir():
                    continue
                if not os.path.isfile(parameter_folder.path + os.sep + "constants"):
                    continue
                experiment_folders.append(parameter_folder.path)

        for parameter_folder in experiment_folders:
            with open(parameter_folder + os.sep + "constants", mode="r", encoding="utf-8") as f:
                constants = f.read().rstrip()

            experiment_names = set()
            for output_file in os.scandir(parameter_folder):
                if output_file.name.endswith(".setup"):
                    experiment_names.add(os.path.splitext(output_file.name)[0])

            valid = set()
            for experiment_name in experiment_names:
                is_valid = True
                for expected in experiment_expected_files:
                    if not os.path.isfile(parameter_folder + os.sep + experiment_name + expected):
                        print(f"Experiment {model}/{constants}/{experiment_name} is missing {expected}")
                        is_valid = False
                        break
                if is_valid:
                    valid.add(experiment_name)

            parameter_experiments = []
            for experiment in valid:
                with open(parameter_folder + os.sep + experiment + ".setup", mode="r", encoding="utf-8") as f:
                    setup = f.read().rstrip().split(",")
                experiment_data = {"base_path": parameter_folder + os.sep + experiment}
                if setup[0] == "complete":
                    experiment_data["type"] = "complete"
                elif setup[0] in {"bounded", "unbounded"}:
                    if setup[1] not in heuristics:
                        print(f"Invalid heuristic {setup[1]} in {experiment} of {model}/{constants}")
                        continue
                    experiment_data["heuristic"] = setup[1]
                    if setup[0] == "unbounded":
                        experiment_data["type"] = "unbounded"
                    elif setup[0] == "bounded":
                        experiment_data["type"] = "bounded"
                        experiment_data["steps"] = int(setup[2])
                parameter_experiments.append(experiment_data)
            model_parameters.append({"constants": constants, "experiments": parameter_experiments})

    results = dict()

    for model, model_parameters in instances.items():
        if model not in results:
            results[model] = list()

        for parameter_instance in model_parameters:
            parameter_results = dict()
            results[model].append({"constants": parameter_instance["constants"],
                                   "results": parameter_results})


            def check_errors(path):
                if os.path.isfile(path + ".json"):
                    return None
                if not os.path.isfile(path + ".out"):
                    return "generic"
                with open(path + ".out", mode="rt", encoding="utf-8") as f:
                    if "OutOfMemoryError" in f.read():
                        return "memout"
                return "timeout"


            for experiment_data in parameter_instance["experiments"]:
                path = experiment_data["base_path"]
                parsed_data = dict()
                error = check_errors(path)

                if experiment_data["type"] == "complete":
                    if error:
                        data = error
                    else:
                        with open(path + ".json", mode="rt", encoding="utf-8") as f:
                            json_data = json.load(f)
                        data = json_data["model"]
                    parameter_results["complete"] = data
                elif experiment_data["type"] == "unbounded":
                    if "unbounded" not in parameter_results:
                        parameter_results["unbounded"] = dict()
                    if error:
                        data = error
                    else:
                        with open(path + ".json", mode="rt", encoding="utf-8") as f:
                            json_data = json.load(f)
                        data = json_data["unbounded"][experiment_data["heuristic"]]
                    parameter_results["unbounded"][experiment_data["heuristic"]] = data
                elif experiment_data["type"] == "bounded":
                    if "bounded" not in parameter_results:
                        parameter_results["bounded"] = dict()
                    steps = experiment_data["steps"]
                    if steps not in parameter_results["bounded"]:
                        parameter_results["bounded"][steps] = dict()

                    if error:
                        data = error
                    else:
                        with open(path + ".json", mode="rt", encoding="utf-8") as f:
                            json_data = json.load(f)
                        data = json_data["bounded"][str(steps)][experiment_data["heuristic"]]
                    parameter_results["bounded"][steps][experiment_data["heuristic"]] = data

    with open(sys.argv[2], mode="wt", encoding="utf-8") as f:
        json.dump(results, f)
