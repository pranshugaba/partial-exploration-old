nExperiments=1
nThreads=3

while getopts n:t: flag; do
  case "${flag}" in
  n) nExperiments=${OPTARG} ;;
  t) nThreads=${OPTARG} ;;
  *) exit 1 ;;
  esac
done

# Run MDP blackbox with update method blackbox
python3 ./experimentScripts/runNExperiments.py --informationLevel BLACKBOX --updateMethod BLACKBOX --nExperiments "${nExperiments}" --nThreads "${nThreads}" --outputDirectory "./mdp_blackbox_results"

# Run MDP blackbox with update method greybox
python3 ./experimentScripts/runNExperiments.py --informationLevel BLACKBOX --updateMethod GREYBOX --nExperiments "${nExperiments}" --nThreads "${nThreads}" --outputDirectory "./mdp_greybox_results"

# Run plot graphs
python3 ./experimentScripts/plotGraphs.py --blackboxResultDir "./mdp_blackbox_results/iteration0" --greyboxResultDir "./mdp_greybox_results/iteration0" --resultDir "./results"

# Run table generator
python3 ./experimentScripts/tablegenerator.py --blackboxResultDir "./mdp_blackbox_results" --greyboxResultDir "./mdp_greybox_results" --resultDir "./results"

