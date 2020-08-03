#!/usr/bin/env python3

import json
import math
import os
import statistics
import sys
from copy import deepcopy as copy

import tabulate

heuristics = [
    "WEIGHTED", "PROB", "DIFFERENCE", "GRAPH_WEIGHTED", "GRAPH_DIFFERENCE"
]
heuristic_names = {
    "WEIGHTED": "\\texttt{W}",
    "PROB": "\\texttt{P}",
    "DIFFERENCE": "\\texttt{D}",
    "GRAPH_WEIGHTED": "\\texttt{GW}",
    "GRAPH_DIFFERENCE": "\\texttt{GD}"
}

model_groups = {
    "csma": {"csma2_2.nm", "csma2_4.nm", "csma2_6.nm", "csma3_2.nm", "csma3_4.nm", "csma3_6.nm",
             "csma4_2.nm", "csma4_4.nm", "csma4_6.nm"},
    "brp": {"brp.pm"},
    "cluster": {"cluster.sm"},
    "coin": {"coin2.nm", "coin4.nm", },
    "crowds": {"crowds.pm"},
    "egl": {"egl.pm"},
    "embedded": {"embedded.sm"},
    "firewire": {"firewire.nm", "firewire_abst.nm", "firewire_dl.nm", "firewire_impl_dl.nm"},
    "fms": {"fms.sm"},
    "herman": {"herman11.pm", "herman13.pm", "herman15.pm", "herman3.pm", "herman5.pm",
               "herman7.pm", "herman9.pm"},
    "kanban": {"kanban.sm"},
    "leader\\_sync": {"leader_sync3_2.pm", "leader_sync3_3.pm", "leader_sync3_4.pm",
                      "leader_sync4_2.pm", "leader_sync4_3.pm", "leader_sync4_4.pm",
                      "leader_sync5_2.pm", "leader_sync5_3.pm", "leader_sync5_4.pm", },
    "mapk\\_cascade": {"mapk_cascade.sm"},
    "nand": {"nand.pm"},
    "poll": {"poll10.sm", "poll11.sm", "poll12.sm", "poll13.sm", "poll14.sm", "poll15.sm",
             "poll16.sm", "poll17.sm", "poll18.sm", "poll19.sm", "poll20.sm", "poll3.sm",
             "poll4.sm", "poll5.sm", "poll6.sm", "poll7.sm", "poll8.sm", "poll9.sm"},
    "tandem": {"tandem.sm"},
    "wlan": {"wlan0.nm", "wlan1.nm", "wlan2.nm", "wlan3.nm", "wlan4.nm", "wlan5.nm", "wlan6.nm"},
    "wlan\\_dl": {"wlan_dl0.nm", "wlan_dl1.nm", "wlan_dl2.nm", "wlan_dl3.nm", "wlan_dl4.nm",
                  "wlan_dl5.nm", "wlan_dl6.nm", },
    "zeroconf": {"zeroconf.nm"},
    "zeroconf\\_dl": {"zeroconf_dl.nm"}
}

step_bounds = [10, 100, 200, 500]


group_mapping = dict()
for key, value_set in model_groups.items():
    for value in value_set:
        group_mapping[value] = key

def is_error(data):
    return type(data) is str


def error_string(data):
    if data == "memout":
        return "M/O & --- "
    elif data == "timeout":
        return "T/O & --- "
    elif data == "generic":
        return "--- & --- "
    return None


def table3(data):
    data_template = {"count": 0, "complete": [], "fail": 0, "components": []}
    for heuristic in heuristics:
        data_template[heuristic] = {"states": [], "fail": 0, "succeed": 0}

    group_data = dict((group, copy(data_template)) for group in model_groups.keys())

    for model, model_results in data.items():
        if model not in group_mapping:
            print(f"{model} not in mapping")
            sys.exit(1)
        group = group_mapping[model]
        group_data[group]["count"] += len(model_results)
        for instance in model_results:
            instance_results = instance["results"]

            if "complete" in instance_results:
                complete = instance_results["complete"]
            else:
                complete = "generic"

            complete_error = is_error(complete)
            if complete_error:
                complete_states = None
                group_data[group]["fail"] += 1
            else:
                complete_states = int(complete["states"])
                group_data[group]["complete"] += [complete_states]
                group_data[group]["components"] += [int(complete["components"]["count"])]

            for heuristic in heuristics:
                if heuristic in instance_results.get("unbounded", {}):
                    unbounded = instance_results["unbounded"][heuristic]
                else:
                    unbounded = "generic"

                if is_error(unbounded):
                    group_data[group][heuristic]["fail"] += 1
                    group_data[group][heuristic]["states"] += [1.0]
                else:
                    if is_error(complete):
                        group_data[group][heuristic]["succeed"] += 1
                    else:
                        ratio = int(unbounded["explored-states"]) / complete_states
                        group_data[group][heuristic]["states"] += [ratio]

    table_headers = ["Model", "Count", "C States", "C Fail", "Comp"]
    for heuristic in heuristics:
        name = heuristic_names[heuristic]
        table_headers += [name, ""]
    table_data = []

    def sort_key(group):
        group_aggregate = group_data[group]

        ratios = []
        for heuristic in heuristics:
            if "states" in group_aggregate[heuristic]:
                ratios += [statistics.mean(group_aggregate[heuristic]["states"])]
            else:
                ratios += [1.0]

        mean = statistics.mean(ratios)

        if mean < 0.999:
            return 0, mean, group
        if group_data[group]["components"]:
            if max(group_data[group]["components"]) == 1:
                return 1, mean, group
        return 0, mean, group

    groups = [group for group, group_aggregate in group_data.items() if group_aggregate["count"]]
    groups = sorted(groups, key=sort_key)
    for group in groups:
        group_aggregate = group_data[group]
        table_list = [group, group_aggregate["count"]]
        if group_aggregate["complete"]:
            complete_states_avg = statistics.mean(group_aggregate["complete"])
            components_avg = statistics.mean(group_aggregate["components"])
            table_list += [f"{complete_states_avg:.0f}", group_aggregate["fail"],
                           f"{components_avg:.0f}"]
        for heuristic in heuristics:
            heuristic_success = group_aggregate[heuristic]["succeed"]
            # heuristic_failures = group_aggregate[heuristic]["fail"]

            if group_aggregate[heuristic]["states"]:
                table_list += [f'{statistics.mean(group_aggregate[heuristic]["states"]) * 100:.0f}']
            else:
                table_list += ['---']
            # table_list += [f'{heuristic_failures:d}', f'{heuristic_success:d}']
            table_list += [f'{heuristic_success:d}']
        table_data.append(table_list)

    print(tabulate.tabulate(table_data, headers=table_headers, tablefmt="latex_raw"))


def table12(data):
    total = 0
    data_template = {"failures": 0, "success": 0, "states": [], "time": [], "fraction": []}
    results_table = dict({
        "complete": copy(data_template),
        "unbounded": dict((heuristic, copy(data_template)) for heuristic in heuristics),
        "bounded": dict()
    })
    for step_bound in step_bounds:
        results_table["bounded"][step_bound] = dict(
            (heuristic, copy(data_template)) for heuristic in heuristics)

    for model, model_results in data.items():
        for instance in model_results:
            instance_results = instance["results"]
            if "complete" in instance_results:
                complete = instance_results["complete"]
            else:
                complete = "timeout"
            complete_states = None if is_error(complete) else int(complete["states"])
            total += 1

            def add_core_result(data, table):
                if is_error(data):
                    table["failures"] += 1
                else:
                    if "explored-states" in data:
                        core_states = int(data["explored-states"])
                    else:
                        core_states = int(data["states"])

                    table["time"] += [float(data["time"])]
                    table["states"] += [core_states]
                    if complete_states is None:
                        table["success"] += 1
                    else:
                        table["fraction"] += [core_states / complete_states]

            add_core_result(complete, results_table["complete"])

            for heuristic in heuristics:
                if heuristic in instance_results["unbounded"]:
                    unbounded = instance_results["unbounded"][heuristic]
                    add_core_result(unbounded, results_table["unbounded"][heuristic])

                for step_bound in step_bounds:
                    if str(step_bound) in instance_results["bounded"] and heuristic in \
                            instance_results["bounded"][str(step_bound)]:
                        step_bounded = instance_results["bounded"][str(step_bound)][heuristic]
                        add_core_result(step_bounded,
                                        results_table["bounded"][step_bound][heuristic])

    def results_to_data(name, data):
        time = statistics.mean(data["time"])
        states = statistics.mean(data["states"])
        fraction = statistics.mean(data["fraction"])
        failures = data["failures"] / total
        success = data["success"] / total
        return [name, f"{time:.0f} s", f"{states:.0f}", f"{fraction * 100:.0f} \\%",
                f"{failures * 100:.0f} \\%", f"{success * 100:.0f} \\%"]

    table_headers = ["", "Time", "States", "Fraction", "Failures", "Success"]
    table_data = []
    table_data.append(results_to_data("Complete", results_table["complete"]))
    table_data.append(["\\multicolumn{6}{l}{Unbounded}", "", "", "", "", ""])
    for heuristic in heuristics:
        table_data.append(results_to_data("\\hspace{1em} " + heuristic_names[heuristic],
                                          results_table["unbounded"][heuristic]))
    for step_bound in step_bounds:
        table_data.append(
            ["\\multicolumn{6}{l}{Bounded " + str(step_bound) + "}", "", "", "", "", ""])
        for heuristic in heuristics:
            table_data.append(results_to_data("\\hspace{1em} " + heuristic_names[heuristic],
                                              results_table["bounded"][step_bound][heuristic]))

    print(tabulate.tabulate(table_data, headers=table_headers, tablefmt="latex_raw"))

    heuristic_compare = dict((heuristic, []) for heuristic in heuristics)
    for model, model_results in data.items():
        for instance in model_results:
            instance_results = instance["results"]
            if any(heuristic not in instance_results["unbounded"] or
                   is_error(instance_results["unbounded"][heuristic])
                   for heuristic in heuristics):
                continue
            for heuristic in heuristics:
                heuristic_compare[heuristic] += [
                    instance_results["unbounded"][heuristic]["explored-states"]]
    for heuristic, data in heuristic_compare.items():
        print(f"{heuristic}, {statistics.mean(data):.0f}, {statistics.stdev(data):.0f}")

    print("Total instance count " + str(total))


def plot(data):
    pairs = []
    groups = ["zeroconf", "zeroconf_dl", "embedded", "wlan", "nand", "brp"]

    for model, model_results in data.items():
        if model not in group_mapping:
            print(f"{model} not in mapping")
            sys.exit(1)
        group = group_mapping[model]
        if group not in groups:
            continue
        index = groups.index(group_mapping[model])

        for instance in model_results:
            instance_results = instance["results"]

            complete = instance_results.get("complete", "generic")
            complete_states = int(complete["states"]) if "states" in complete else math.inf

            smallest = math.inf
            for heuristic in heuristics:
                unbounded = instance_results.get("unbounded", {}).get(heuristic, "generic")
                if not is_error(unbounded):
                    smallest = min(smallest, int(unbounded["explored-states"]))
            pairs.append((complete_states, smallest, index))

    def format(v):
        if v == math.inf:
            return "nan"
        return f'{v:.1f}'

    for l, r, i in pairs:
        print(f'{format(l)},{format(r)},{i}')


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <path_to_results.json> <evaluation>")
        sys.exit(1)

    results_file = sys.argv[1]
    if not os.path.isfile(results_file):
        print(f"{results_file} is not a file")
        sys.exit(1)

    eval_type = int(sys.argv[2])

    with open(results_file, mode="rt", encoding="utf-8") as f:
        results = json.load(f)

    model_list = sorted(list(results.keys()))

    # Structure:
    # model -> [<instance>]
    # instance: "constants": ..., "results": <results>
    # results: "complete" -> result, "unbounded" -> heuristic -> result, "bounded" -> "step" -> heuristic -> result
    # result: "timeout" / "memout" / "generic" / <good result>
    # good result: "time", "states", "transitions", "components": {"count", ...}

    if eval_type == 1:
        table12(results)
    elif eval_type == 2:
        filtered = dict()
        for model, model_results in results.items():
            filtered_data = []
            for model_data in model_results:
                model_result = model_data["results"]
                if "complete" not in model_result or is_error(model_result["complete"]):
                    continue
                complete_data = model_result["complete"]
                if complete_data["components"]["count"] == 1:
                    if complete_data["components"]["average-size"] == complete_data["states"]:
                        continue
                filtered_data.append(model_data)
            if filtered_data:
                filtered[model] = filtered_data
        table12(filtered)
    elif eval_type == 3:
        table3(results)
    elif eval_type == 4:
        plot(results)