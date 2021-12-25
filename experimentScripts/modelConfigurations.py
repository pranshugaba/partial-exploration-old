mdpConfigs = ["meanPayoff -m data/models/zeroconf_rewards.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0002 --iterSample 10000 --const N=40,K=10,reset=false --rewardModule reach",
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

# mdpMecConfigs = ["meanPayoff -m data/mdpMecModels/mec50.prism --precision 0.01 --maxReward 50 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.01 --iterSample 10000",
#                  "meanPayoff -m data/mdpMecModels/mec200.prism --precision 0.01 --maxReward 50 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.01 --iterSample 10000",
#                  "meanPayoff -m data/mdpMecModels/mec400.prism --precision 0.01 --maxReward 50 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.01 --iterSample 10000",
#                  "meanPayoff -m data/mdpMecModels/mec1000.prism --precision 0.01 --maxReward 50 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.01 --iterSample 10000",
#                  "meanPayoff -m data/mdpMecModels/mec4000.prism --precision 0.01 --maxReward 50 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.01 --iterSample 10000"]

mdpMecConfigs = ["meanPayoff -m data/mdpMecModels/mec50.prism --precision 0.01 --maxReward 50 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.01 --iterSample 10000"]


runConfigs = mdpMecConfigs