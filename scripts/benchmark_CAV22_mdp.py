import subprocess
import json

RUNS_COUNT = 5

COMMANDS = [
    "meanPayoff -m data/models/virus.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.1 --maxSuccessors 2 --iterSample 10000 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/cs_nfail3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.1 --maxSuccessors 2 --iterSample 10000 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/investor.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.016 --maxSuccessors 8 --iterSample 10000 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/zeroconf_rewards.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0002 --maxSuccessors 6 --iterSample 10000 --const N=40,K=10,reset=false --rewardModule reach --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/sensors.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.05 --maxSuccessors 2 --iterSample 10000 --const K=3 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/consensus.2.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --maxSuccessors 2 --iterSample 10000 -c K=2 --rewardModule custom --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/ij.10.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --maxSuccessors 2 --iterSample 10000 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/ij.3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --maxSuccessors 2 --iterSample 10000 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/pacman.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.08 --maxSuccessors 6 --iterSample 10000 -c MAXSTEPS=5 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/wlan.0.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0625 --maxSuccessors 16 --iterSample 10000 -c COL=0 --rewardModule default --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/blackjack.prism --precision 0.01 --maxReward 1.5 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.076 --maxSuccessors 10 --iterSamples 10000 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/counter.prism --precision 0.01 --maxReward 10 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.333 --maxSuccessors 2 --iterSamples 10000 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/recycling.prism --precision 0.01 --maxReward 2 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.199 --maxSuccessors 2 --iterSamples 10000 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/busyRing4.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.125 --iterSample 10000 --informationLevel BLACKBOX --updateMethod GREYBOX",
    "meanPayoff -m data/models/busyRingMC4.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0625 --iterSample 10000 --informationLevel BLACKBOX --updateMethod GREYBOX",
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
            ["./gradlew", "run", "--args", COMMANDS[command]],
            text=True,
            capture_output=True,
        )
        if command_result.returncode != 0:
            print(f'Error running: "{COMMANDS[command].split(' ')[2]}"')
            break

        extract_info(COMMANDS[command], command_result.stdout, run + 1)
        print(f'Finished with "{COMMANDS[command].split(' ')[2]}", run {run + 1}')


with open("benchmark_CAV22_mdp.json", "w") as file:
    json.dump(result, file, indent=4)
