import os
import shutil
import benchmarksUtil
import inputOptions


def get_exec_command_from_input():
    input_values = inputOptions.parse_user_input()
    command = 'python3 runExperiments.py'
    if input_values.information_level:
        command += ' ' + inputOptions.information_level_option + ' ' + input_values.information_level
    if input_values.update_method:
        command += ' ' + inputOptions.update_method_option + ' ' + input_values.update_method
    if input_values.simulate_mec:
        command += ' ' + inputOptions.simulate_mec_option + ' ' + input_values.simulate_mec
    if input_values.get_error_probability:
        command += ' ' + inputOptions.get_error_probability_option
    return command


# todo change experimentResults
def run_benchmarks(n, command):
    for i in range(n):
        os.system(command)
        shutil.move('results', f'experimentResults/iteration{i}')
        os.mkdir('results')


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


def write_model_result(result_file, model_result):
    result_file.write('Execution time: ' + str(model_result.get_runtime()) + '\n')
    result_file.write('Lower bound: ' + str(model_result.lower_bounds[-1]) + '\n')
    result_file.write('Upper bound: ' + str(model_result.upper_bounds[-1]) + '\n')
    result_file.write('Iteration number: ' + str(model_result.iteration_number) + '\n')
    result_file.write('\n')
    result_file.write('\n')
    result_file.write('\n')


def write_model_results(model_name, model_result_list):
    result_file_name = model_name + '.txt'
    with open(result_file_name, 'w') as result_file:
        for model_result in model_result_list:
            write_model_result(result_file, model_result)


def write_results(results):
    for model_name, model_result_list in results.items():
        model_result_list.sort(key=result_comparator)
        write_model_results(model_name, model_result_list)


def remove_old_results():
    is_dir_exists = os.path.isdir(resultDirectory)
    if is_dir_exists:
        shutil.rmtree(resultDirectory)


resultDirectory = 'experimentResults/'
remove_old_results()
exec_command = get_exec_command_from_input()
run_benchmarks(10, exec_command)
benchmarkInfo = accumulate_results()
write_results(benchmarkInfo)