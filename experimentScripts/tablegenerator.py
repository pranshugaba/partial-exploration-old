import os
import csv
import argparse
import benchmarksUtil
import modelNames

parser = argparse.ArgumentParser()
parser.add_argument("--blackboxResultDir", required=True)
parser.add_argument("--greyboxResultDir", required=True)
parser.add_argument("--resultDir", required=True)
arguments = parser.parse_args()

blackbox_result_dir = arguments.blackboxResultDir
greybox_result_dir = arguments.greyboxResultDir
result_dir = arguments.resultDir
tables_dir = os.path.join(result_dir, "tables")

# Create results dir if not present
os.makedirs(tables_dir, exist_ok=True)

blackbox_results = benchmarksUtil.accumulate_results(blackbox_result_dir)
greybox_results = benchmarksUtil.accumulate_results(greybox_result_dir)


def write_headings(spamwriter):
    spamwriter.writerow(["Benchmarks", "Blackbox states explored", "Blackbox lower bound", "Blackbox upper bound",
                         "Time(s)", "Blackbox with greybox states explored", "Blackbox with greybox lower bound",
                         "Blackbox with greybox upper bound", "Time(s)"])


def write_model_result(spamwriter, modelName):
    global blackbox_results
    global greybox_results

    (b_al, b_au, b_ar, b_av_states) = benchmarksUtil.get_average_values(blackbox_results[modelName])
    (g_al, g_au, g_ar, g_av_states) = benchmarksUtil.get_average_values(greybox_results[modelName])

    # Use TO if runtime is greater than 30 minutes
    if b_ar >= 1800:
        b_ar = "TO"
    else:
        b_ar = str(round(b_ar, 4))

    # Use TO if runtime is greater than 30 minutes
    if g_ar >= 1800:
        g_ar = "TO"
    else:
        g_ar = str(round(g_ar, 4))

    spamwriter.writerow([modelName, str(b_av_states), str(round(b_al, 4)), str(round(b_au, 4)), b_ar,
                         str(g_av_states), str(round(g_al, 4)), str(round(g_au, 4)), g_ar])


with open(os.path.join(tables_dir, "experimentResults.csv"), 'w', newline='') as csvfile:
    spamwriter = csv.writer(csvfile, dialect='unix')

    write_headings(spamwriter)
    for modelName in modelNames.model_names:
        if modelName in blackbox_results:
            write_model_result(spamwriter, modelName)
