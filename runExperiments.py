import argparse
import os
import inputOptions
from modelConfigurations import runConfigs


def find_curr_max_dir():
    files = os.listdir(resultDir)
    max_file = 0
    for file in files:
        if file.split(".")[0].isnumeric():
            max_file = max(int(file), max_file)

    return max_file


parser = argparse.ArgumentParser()
input_values = inputOptions.parse_user_input()

# Information level
for i in range(len(runConfigs)):
    runConfigs[i] += " --informationLevel " + input_values.information_level


# Update method
if input_values.update_method == inputOptions.update_method_both:
    for i in range(len(runConfigs)):
        runConfigs.append(runConfigs[i] + " --updateMethod " + inputOptions.black_box_value)
elif input_values.update_method:
    for i in range(len(runConfigs)):
        runConfigs[i] += " --updateMethod " + input_values.update_method


# get error probability
if input_values.get_error_probability:
    for i in range(len(runConfigs)):
        runConfigs[i] += " --getErrorProbability"


# Simulate Mec
if input_values.simulate_mec:
    for i in range(len(runConfigs)):
        runConfigs[i] += " --simulateMec " + input_values.simulate_mec


resultDir = "results/"
gradle_exec = "./gradlew run"
baseVal = find_curr_max_dir()

for i in range(len(runConfigs)):
    runConfig = runConfigs[i]
    cmdLine = gradle_exec + " --args='" + runConfig + "'"
    print(cmdLine)
    os.system(cmdLine)
    os.rename("temp.txt", os.path.join(resultDir, str(i+1+baseVal)))
