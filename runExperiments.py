import argparse
import os

information_level_option = "--informationLevel"
update_method_option = "--updateMethod"
get_error_probability_option = "--getErrorProbability"

white_box_value = "WHITEBOX"
black_box_value = "BLACKBOX"
grey_box_value = "GREYBOX"
update_method_both = "BOTH"

information_level_choices = [white_box_value, black_box_value, grey_box_value]
update_method_choices = [black_box_value, grey_box_value, update_method_both]


def find_curr_max_dir():
    files = os.listdir(resultDir)
    maxFile = 0
    for file in files:
        if file.split(".")[0].isnumeric():
            maxFile = max(int(file), maxFile)

    return maxFile


runConfigs = ["meanPayoff -m data/models/zeroconf_rewards.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel GREYBOX --errorTolerance 0.1 --pMin 0.0002 --iterSample 10000 --const N=40,K=10,reset=false --rewardModule reach",
              "meanPayoff -m data/models/sensors.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.05 --iterSample 10000 --const K=3",
              "meanPayoff -m data/models/investor.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.016 --iterSample 10000",
              "meanPayoff -m data/models/cs_nfail3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.1 --iterSample 10000",
              "meanPayoff -m data/models/consensus.2.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --iterSample 10000 -c K=2 --rewardModule custom",
              "meanPayoff -m data/models/ij.10.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --iterSample 10000",
              "meanPayoff -m data/models/ij.3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --iterSample 10000",
              "meanPayoff -m data/models/pacman.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.08 --iterSample 10000 -c MAXSTEPS=5",
              "meanPayoff -m data/models/wlan.0.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0625 --iterSample 10000 -c COL=0 --rewardModule default",
              "meanPayoff -m data/models/virus.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.1 --iterSample 10000",
              "meanPayoff -m data/models/pnueli-zuck.3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --iterSample 10000",
              "meanPayoff -m data/models/phil-nofair3.prism --precision 0.01 --maxReward 3 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --iterSample 10000 --rewardModule both"
              ]


parser = argparse.ArgumentParser()
parser.add_argument(information_level_option, choices=information_level_choices, required=True)
parser.add_argument(update_method_option, choices=update_method_choices)
parser.add_argument(get_error_probability_option, action="store_true")
arguments = parser.parse_args()

info_level = arguments.informationLevel
update_method = arguments.updateMethod


# Information level
for i in range(len(runConfigs)):
    runConfigs[i] += " --informationLevel " + info_level


# Update method
if update_method == update_method_both:
    for i in range(len(runConfigs)):
        runConfigs.append(runConfigs[i] + " --updateMethod " + black_box_value)
elif update_method:
    for i in range(len(runConfigs)):
        runConfigs[i] += " --updateMethod " + update_method


# get error probability
if arguments.getErrorProbability:
    for i in range(len(runConfigs)):
        runConfigs[i] += " --getErrorProbability"


resultDir = "results/"

exec = "./gradlew run"

baseVal = find_curr_max_dir()

for i in range(len(runConfigs)):

    runConfig = runConfigs[i]

    cmdLine = exec + " --args='"+runConfig+"'"

    modelName = runConfig.split()[2].split("/")[-1].split(".")[0]
    print(cmdLine)

    os.system(cmdLine)

    os.rename("temp.txt", os.path.join(resultDir, str(i+1+baseVal)))
