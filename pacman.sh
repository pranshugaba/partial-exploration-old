
# Blackbox with update method blackbox
./gradlew -p ./ run --args='meanPayoff -m data/models/pacman.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.08 --maxSuccessors 6 --iterSample 10000 -c MAXSTEPS=5 --informationLevel BLACKBOX --updateMethod BLACKBOX --outputPath ./blackbox_results/2'

# Blackbox with update method greybox
./gradlew -p ./ run --args='meanPayoff -m data/models/pacman.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.08 --maxSuccessors 6 --iterSample 10000 -c MAXSTEPS=5 --informationLevel BLACKBOX --updateMethod GREYBOX --outputPath ./greybox_results/2'

# Plot graph
python3 ./experimentScripts/plotGraphs.py --blackboxResultDir "./blackbox_results/" --greyboxResultDir "./greybox_results/" --resultDir "./individual_results/"
