# This script runs experiments for InformationLevel BLACKBOX, UpdateMethod BLACKBOX for different variants of
# simulateMec options. Each variant is run in a new process.

# The output directory for each variant is named after the first letter of the option provided for InformationLevel,
# UpdateMethod and SimulateMec option. For example, for the following configuration
# informationLevel BLACKBOX, updateMethod BLACKBOX, simulateMec STANDARD
# result will be stored in the directory 'BBS', where first 'B' corresponds to informationLevel, second 'B' corresponds
# to updateMethod and the third 'S' corresponds to simulateMec value.

import os
from multiprocessing import Process

import inputOptions

base_dir = 'experimentResults'
base_command = 'python3 runNExperiments.py --informationLevel BLACKBOX --updateMethod BLACKBOX'


# SimulateMec - Standard
def run_configuration_1():
    simulate_mec_param = inputOptions.simulate_mec_option + ' ' + inputOptions.simulate_mec_standard
    output_directory = base_dir + '/' + 'BBS'
    output_directory_param = inputOptions.output_directory_option + ' ' + output_directory
    os.system(base_command + ' ' + simulate_mec_param + ' ' + output_directory_param)


# SimulateMec - Heuristic
def run_configuration_2():
    simulate_mec_param = inputOptions.simulate_mec_option + ' ' + inputOptions.simulate_mec_heuristic
    output_directory = base_dir + '/' + 'BBH'
    output_directory_param = inputOptions.output_directory_option + ' ' + output_directory
    os.system(base_command + ' ' + simulate_mec_param + ' ' + output_directory_param)


# SimulateMec - Cheat
def run_configuration_3():
    simulate_mec_param = inputOptions.simulate_mec_option + ' ' + inputOptions.simulate_mec_cheat
    output_directory = base_dir + '/' + 'BBC'
    output_directory_param = inputOptions.output_directory_option + ' ' + output_directory
    os.system(base_command + ' ' + simulate_mec_param + ' ' + output_directory_param)


process1 = Process(target=run_configuration_1)
process2 = Process(target=run_configuration_2)
process3 = Process(target=run_configuration_3)

process1.start()
process2.start()
process3.start()

process1.join()
process2.join()
process3.join()
