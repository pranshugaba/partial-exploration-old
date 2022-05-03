
# Blackbox with update method blackbox
./gradlew -p ./ run --args='meanPayoff -m data/ctmdpModels/SJS-procn_6_jobn_2_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.33 --maxSuccessors 2 --iterSample 10000 --informationLevel BLACKBOX --updateMethod BLACKBOX --outputPath ./blackbox_results/4'

# Blackbox with update method greybox
./gradlew -p ./ run --args='meanPayoff -m data/ctmdpModels/SJS-procn_6_jobn_2_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.33 --maxSuccessors 2 --iterSample 10000 --informationLevel BLACKBOX --updateMethod GREYBOX --outputPath ./greybox_results/4'

# Plot graphs
python3 ./experimentScripts/plotGraphs.py --blackboxResultDir "./blackbox_results/" --greyboxResultDir "./greybox_results/" --resultDir "./"
