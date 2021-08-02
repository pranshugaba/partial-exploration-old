import os
import subprocess
import matplotlib.pyplot as plt
import numpy as np
import shutil
import pickle

runConfigs = ["meanPayoff -m data/models/zeroconf_rewards.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.1 --iterSample 10000 --const N=40,K=10,reset=false --rewardModule reach",
              "meanPayoff -m data/models/zeroconf_rewards.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.1 --iterSample 10000 --const N=300,K=15,reset=false --rewardModule reach",
              "meanPayoff -m data/models/sensors.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.1 --iterSample 10000 --const K=2",
              "meanPayoff -m data/models/sensors.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.1 --iterSample 10000 --const K=3",
              "meanPayoff -m data/models/investor.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.1 --iterSample 10000",
              "meanPayoff -m data/models/phil-nofair3.prism --precision 0.01 --maxReward 3 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.5 --iterSample 10000 --rewardModule both",
              "meanPayoff -m data/models/cs_nfail3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.1 --iterSample 10000",
#               "meanPayoff -m data/models/haddad-monmege.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.3 --iterSample 10000 -c N=20,p=0.7",
#               "meanPayoff -m data/models/leader_sync_3_2.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.5 --iterSample 10000",
              "meanPayoff -m data/models/consensus.2.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.5 --iterSample 10000 -c K=2 --rewardModule custom",
#               "meanPayoff -m data/models/csma.2-2.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.25 --iterSample 10000",
              "meanPayoff -m data/models/ij.10.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.5 --iterSample 10000",
              "meanPayoff -m data/models/ij.3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.5 --iterSample 10000",
              "meanPayoff -m data/models/pacman.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.05 --iterSample 10000 -c MAXSTEPS=5",
              "meanPayoff -m data/models/pnueli-zuck.3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.5 --iterSample 10000",
              "meanPayoff -m data/models/wlan.0.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.0625 --iterSample 10000 -c COL=0 --rewardModule default",
              "meanPayoff -m data/models/virus.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.5 --iterSample 10000",
#               "meanPayoff -m data/models/rabin4.prism --precision 0.01 --maxReward 3 --revisitThreshold 6 --informationLevel BLACKBOX --errorTolerance 0.1 --pMin 0.5 --iterSample 10000 --rewardModule trying"
              ]

for i in range(len(runConfigs)):
    runConfigs.append(runConfigs[i]+" --updateMethod BLACKBOX")

resultDir = "results/"
plotsDir = "results/plots"

f = open(os.path.join(resultDir, "configInfo.txt"), 'w')

# pickle.dump(runConfigs, open(os.path.join(resultDir, "runConfigs.pkl"), 'wb'))

exec = "./gradlew run"

for i in range(len(runConfigs)):

    f.write(runConfigs[i]+"\n")
    f.flush()

    if i < 24:
        continue

    runConfig = runConfigs[i]

    cmdLine = exec + " --args='"+runConfig+"'"

    modelName = runConfig.split()[2].split("/")[-1].split(".")[0]
    print(cmdLine)

    os.system(cmdLine)

    result = open("temp.txt", 'r').readlines()[1:]
    result = list(map(str.strip, result))
    print(result)
    times = (np.array(list(map(float, result[0].split())))/60000.0)
    times -= times.min()
    lowerBounds = np.array(list(map(float, result[1].split())))
    upperBounds = np.array(list(map(float, result[2].split())))
    values = (lowerBounds+upperBounds)/2

    plt.plot(times, lowerBounds)
    plt.plot(times, upperBounds)
    plt.plot(times, values)

    plt.xlabel("times (minutes)")
    plt.savefig(os.path.join(plotsDir, str(i+1)))
    plt.close()

    os.rename("temp.txt", os.path.join(resultDir, str(i+1)))

f.close()
