import os
import shutil
import benchmarksUtil
import inputOptions


def get_exec_command_from_input():
    global input_values
    command = 'python3 runExperiments.py'
    if input_values.information_level:
        command += ' ' + inputOptions.information_level_option + ' ' + input_values.information_level
    if input_values.update_method:
        command += ' ' + inputOptions.update_method_option + ' ' + input_values.update_method
    if input_values.simulate_mec:
        command += ' ' + inputOptions.simulate_mec_option + ' ' + input_values.simulate_mec
    if input_values.get_error_probability:
        command += ' ' + inputOptions.get_error_probability_option
    if input_values.delta_t_method:
        command += ' ' + inputOptions.deltat_method_option + ' ' + input_values.delta_t_method
    return command


def run_benchmarks(n, command, output_directory):
    for i in range(n):
        output_directory_option = inputOptions.output_directory_option + ' ' + output_directory + '/' + f'iteration{i}'
        os.system(command + ' ' + output_directory_option)


def accumulate_results():
    # Now store all the information in variables
    benchmark_info = {}
    num_iterations = 0
    for directory in os.listdir(resultDirectory):
        files = os.listdir(os.path.join(resultDirectory, directory))

        for file in files:
            if not file.split(".")[0].isnumeric():
                continue

            file_path = os.path.join(resultDirectory, directory, file)
            model_result = benchmarksUtil.parse_output_file(file_path, num_iterations)

            if model_result.model_name not in benchmark_info:
                benchmark_info[model_result.model_name] = []

            benchmark_info[model_result.model_name].append(model_result)

        num_iterations = num_iterations + 1

    return benchmark_info


def result_comparator(model_result):
    return model_result.get_bounds_diff()


def get_average_values(model_result_list):
    average_lower_bound = 0
    average_upper_bound = 0
    average_run_time = 0
    for model_result in model_result_list:
        average_lower_bound += model_result.lower_bounds[-1]
        average_upper_bound += model_result.upper_bounds[-1]
        average_run_time += model_result.get_runtime()

    average_lower_bound /= len(model_result_list)
    average_upper_bound /= len(model_result_list)
    average_run_time /= len(model_result_list)

    return average_lower_bound, average_upper_bound, average_run_time


def write_model_result(result_file, model_result):
    result_file.write('Execution time: ' + str(model_result.get_runtime()) + '\n')
    result_file.write('Lower bound: ' + str(model_result.lower_bounds[-1]) + '\n')
    result_file.write('Upper bound: ' + str(model_result.upper_bounds[-1]) + '\n')
    result_file.write('Iteration number: ' + str(model_result.iteration_number) + '\n')
    result_file.write('\n')
    result_file.write('\n')
    result_file.write('\n')


def write_model_results(model_name, model_result_list, result_directory):
    result_file_name = result_directory + model_name + '.txt'

    with open(result_file_name, 'w') as result_file:
        for model_result in model_result_list:
            write_model_result(result_file, model_result)

        (al, au, ar) = get_average_values(model_result_list)
        precision = au - al
        result_file.write('Average Lower Bound: ' + str(al) + '\n')
        result_file.write('Average Upper Bound: ' + str(au) + '\n')
        result_file.write('Average Run time: ' + str(ar) + '\n')
        result_file.write('Precision: ' + str(precision) + '\n')


def write_results(results, result_directory):
    for model_name, model_result_list in results.items():
        model_result_list.sort(key=result_comparator)
        write_model_results(model_name, model_result_list, result_directory)


def remove_old_results():
    is_dir_exists = os.path.isdir(resultDirectory)
    if is_dir_exists:
        shutil.rmtree(resultDirectory)


input_values = inputOptions.parse_user_input()
resultDirectory = input_values.output_directory + '/'
remove_old_results()
exec_command = get_exec_command_from_input()
run_benchmarks(10, exec_command, input_values.output_directory)
benchmarkInfo = accumulate_results()
write_results(benchmarkInfo, resultDirectory)
