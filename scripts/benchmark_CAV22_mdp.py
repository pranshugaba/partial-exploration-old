import subprocess
import json

RUNS_COUNT = 5

COMMANDS = [
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/ij.3.prism --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.5 --maxReward 1 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/ij.10.prism --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.5 --maxReward 1 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/virus.prism --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.1 --maxReward 1 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/cs_nfail3.prism --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.1 --maxReward 1 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/investor.prism --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.1 --maxReward 1 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/zeroconf_rewards.prism --const N=40,K=10,reset=false --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.001 --maxReward 1 --rewardModule reach --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/sensors.prism --const K=3 --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.05 --maxReward 1 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/consensus.2.prism --const K=2 --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.5 --maxReward 1 --rewardModule steps --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/pacman.prism --const MAXSTEPS=3 --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 --maxReward 1 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/wlan.0.prism --const COL=0 --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.0625 --maxReward 1 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/blackjack.prism --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.076 --maxReward 1.5 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/counter.prism --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.3333333333333333 --maxReward 10 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/recycling.prism --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.19999999999999996 --maxReward 2 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/busyRing4.prism --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.0625 --maxReward 1 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
    [
        "./gradlew",
        "run",
        "--args",
        "meanPayoff -m ./data/models/busyRingMC4.prism --revisitThreshold 6 --errorTolerance 0.01 --iterSamples 10000 --precision 0.1 -pMin 0.0625 --maxReward 1 --informationLevel BLACKBOX --updateMethod GREYBOX",
    ],
]

result = {}


def extract_info(command, input_string, run_count):
    sr = input_string.split("\n")[-5][3:].split(", ")
    if run_count == 1:
        result[command] = [
            {
                "run": run_count,
                "bounds": eval(sr[0].replace("-", ",")),
                "value": eval(sr[2]),
                "states_explored": eval(sr[1]),
                "time_taken": eval(sr[3]),
            }
        ]
    else:
        result[command].append(
            {
                "run": run_count,
                "bounds": eval(sr[0].replace("-", ",")),
                "value": eval(sr[2]),
                "states_explored": eval(sr[1]),
                "time_taken": eval(sr[3]),
            }
        )


for command in range(len(COMMANDS)):
    for run in range(RUNS_COUNT):
        command_result = subprocess.run(
            COMMANDS[command], text=True, capture_output=True
        )
        if command_result.returncode != 0:
            print(f'Error running: "{COMMANDS[command][-1].split(' ')[2]}"')
            break

        extract_info(" ".join(COMMANDS[command]), command_result.stdout, run + 1)
        print(f'Finished with "{COMMANDS[command][-1].split(' ')[2]}", run {run + 1}')


with open("benchmark_CAV22_mdp.json", "w") as file:
    json.dump(result, file, indent=4)
