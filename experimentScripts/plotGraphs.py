import os
import numpy as np
import matplotlib.pyplot as plt

resultDir = os.path.abspath("./experimentResults/")
plotsDir = os.path.join(resultDir, "plots")

# runConfigs = open(os.path.join(resultDir, "configInfo.txt"), 'r').readlines()

models = ["zeroconf_rewards", "sensors", "investor", "cs_nfail3", "consensus.2", "ij.10", "ij.3", "pacman", "pnueli-zuck.3", "wlan.0", "virus", "phil-nofair3"]

values = {"zeroconf_rewards": 1, "sensors": 0.333, "investor": 0.95, "cs_nfail3": 0.333, "consensus.2": 0.1083, "ij.10": 1, "ij.3": 1,
          "pacman": 0.5511, "pnueli-zuck.3": 1, "wlan.0": 1, "virus": 0, "phil-nofair3": 2.4286}

modelResults = {model: ["", ""] for model in models}

for file in os.listdir(resultDir):
    if not file.split(".")[0].isnumeric():
        continue
    content = open(os.path.join(resultDir, file)).readlines()
    grey = True
    options = content[0].split(" -")
    model = options[0].split("/")[-1].split(".prism")[0]
    for option in options:
        if "-updateMethod" in option:
            if "GREY" in option:
                grey = True
            elif "BLACK" in option:
                grey = False

    modelResults[model][0] = content[1:]
    # if grey:
    #     modelResults[model][0] = content[1:]
    # else:
    #     modelResults[model][1] = content[1:]

for model in modelResults:
    blackResult = modelResults[model][0]
    if len(blackResult) == 0:
        continue
    times = (np.array(list(map(float, blackResult[0].split()))))
    times -= times.min()
    lowerBounds = np.array(list(map(float, blackResult[1].split())))
    upperBounds = np.array(list(map(float, blackResult[2].split())))

    plt.plot(times/60000.0, lowerBounds, label="Lower Bounds (B): "+str(np.around(lowerBounds[-1], 8)))
    plt.plot(times/60000.0, upperBounds, label="Upper Bounds (B): "+str(np.around(upperBounds[-1], 8)))

    plt.plot(times/60000.0, [values[model]]*len(times), label="True Value: "+str(values[model]), linestyle="dotted")
    lasttime = times[-1]

#     greyResult = modelResults[model][0]
#     times = (np.array(list(map(float, greyResult[0].split()))))
#     times -= times.min()
# # 	times = np.append(times, lasttime)
#     lowerBounds = np.array(list(map(float, greyResult[1].split())))
# # 	lowerBounds = np.append(lowerBounds, lowerBounds[-1])
#     upperBounds = np.array(list(map(float, greyResult[2].split())))
# # 	upperBounds = np.append(upperBounds, upperBounds[-1])
#
#     plt.plot(times/60000.0, lowerBounds, label="Lower Bounds (G): "+str(np.around(lowerBounds[-1], 8)))
#     plt.plot(times/60000.0, upperBounds, label="Upper Bounds (G): "+str(np.around(upperBounds[-1], 8)))

    plt.legend()

    plt.xlabel("times (minutes)")
    plt.ylabel("mean payoff")
    model = model.replace(".", "-")
    plt.title(model)
    print(os.path.join(plotsDir, model))
    plt.savefig(os.path.join(plotsDir, model))
    plt.close()
