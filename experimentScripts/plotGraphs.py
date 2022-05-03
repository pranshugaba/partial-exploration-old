import os
import numpy as np
import matplotlib.pyplot as plt
import modelNames
import trueValues
import benchmarksUtil
import maxRewards
import argparse

modelResults = {model: [None, None] for model in modelNames.model_names}

parser = argparse.ArgumentParser()
parser.add_argument("--blackboxResultDir", required=True)
parser.add_argument("--greyboxResultDir", required=True)
parser.add_argument("--resultDir", required=True)
arguments = parser.parse_args()

blackbox_result_dir = arguments.blackboxResultDir
greybox_result_dir = arguments.greyboxResultDir
resultDir = arguments.resultDir
plotsDir = os.path.join(resultDir, "plots")

print(blackbox_result_dir)
print(greybox_result_dir)
print(resultDir)
print(plotsDir)

# Create results dir if not present
os.makedirs(plotsDir, exist_ok=True)


def store_model_result(file_path, index):
    model_result = benchmarksUtil.parse_output_file(file_path)
    modelResults[model_result.model_name][index] = model_result


def parse_results_in_dir(result_dir, index):
    for file in os.listdir(result_dir):
        if not file.split(".")[0].isnumeric():
            continue
        store_model_result(os.path.join(result_dir, file), index)


def parse_blackbox_results():
    parse_results_in_dir(blackbox_result_dir, 0)


def parse_greybox_results():
    parse_results_in_dir(greybox_result_dir, 1)


parse_blackbox_results()
parse_greybox_results()

for model in modelResults:
    blackboxResult = modelResults[model][0]
    if blackboxResult is None:
        continue

    true_model_value = trueValues.get_true_value(model) / maxRewards.get_max_reward(model)
    times = (np.array(blackboxResult.times))
    times -= times.min()
    lowerBounds = blackboxResult.lower_bounds
    upperBounds = blackboxResult.upper_bounds

    plt.plot(times/60000.0, lowerBounds, label="Lower Bounds (B): "+str(np.around(lowerBounds[-1], 8)))
    plt.plot(times/60000.0, upperBounds, label="Upper Bounds (B): "+str(np.around(upperBounds[-1], 8)))
    plt.plot(times/60000.0, [true_model_value]*len(times), label="True Value: "+str(true_model_value), linestyle="dotted")
    lasttime = times[-1]

    greyResult = modelResults[model][1]
    times = (np.array(greyResult.times))
    times -= times.min()
    lowerBounds = greyResult.lower_bounds
    upperBounds = greyResult.upper_bounds

    plt.plot(times/60000.0, lowerBounds, label="Lower Bounds (G): "+str(np.around(lowerBounds[-1], 8)))
    plt.plot(times/60000.0, upperBounds, label="Upper Bounds (G): "+str(np.around(upperBounds[-1], 8)))

    plt.legend()

    plt.xlabel("times (minutes)")
    plt.ylabel("mean payoff")
    model = model.replace(".", "-")
    plt.title(model)
    print(os.path.join(plotsDir, model))
    plt.savefig(os.path.join(plotsDir, model))
    plt.close()
