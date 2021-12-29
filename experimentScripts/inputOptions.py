import argparse


class InputValues:
    def __init__(self, information_level, update_method, simulate_mec, get_error_probability, output_directory, delta_t_method):
        self.information_level = information_level
        self.update_method = update_method
        self.simulate_mec = simulate_mec
        self.get_error_probability = get_error_probability
        self.output_directory = output_directory
        self.delta_t_method = delta_t_method


information_level_option = "--informationLevel"
update_method_option = "--updateMethod"
get_error_probability_option = "--getErrorProbability"
simulate_mec_option = "--simulateMec"
deltat_method_option = "--deltaTMethod"
output_directory_option = "--outputDirectory"

white_box_value = "WHITEBOX"
black_box_value = "BLACKBOX"
grey_box_value = "GREYBOX"
update_method_both = "BOTH"

simulate_mec_standard = "STANDARD"
simulate_mec_cheat = "CHEAT"
simulate_mec_heuristic = "HEURISTIC"


deltat_method_p_min = "P_MIN"
deltat_method_max_successors = "MAX_SUCCESSORS"

information_level_choices = [white_box_value, black_box_value, grey_box_value]
update_method_choices = [black_box_value, grey_box_value, update_method_both]
simulate_mec_choices = [simulate_mec_standard, simulate_mec_cheat, simulate_mec_heuristic]
deltat_method_choices = [deltat_method_p_min, deltat_method_max_successors]


def parse_user_input():
    parser = argparse.ArgumentParser()
    parser.add_argument(information_level_option, choices=information_level_choices, required=True)
    parser.add_argument(update_method_option, choices=update_method_choices)
    parser.add_argument(get_error_probability_option, action="store_true")
    parser.add_argument(simulate_mec_option, choices=simulate_mec_choices)
    parser.add_argument(output_directory_option, default='results/')
    parser.add_argument(deltat_method_option, choices=deltat_method_choices)
    arguments = parser.parse_args()

    info_level = arguments.informationLevel
    update_method = arguments.updateMethod
    simulate_mec = arguments.simulateMec
    get_error_probability = arguments.getErrorProbability
    output_directory = arguments.outputDirectory
    delta_t_method = arguments.deltaTMethod

    return InputValues(info_level, update_method, simulate_mec, get_error_probability, output_directory, delta_t_method)
