import os
import shutil

n = 5
resultDirectory = 'probabilityResults/'

# Run benchmarks for n times as in runExperiments.py
for i in range(n):
    os.system('python3 runExperiments.py -getErrorProbability')
    shutil.move('results', f'probabilityResults/iteration{i}')
    os.mkdir('results')

# Now store all the information in variables
benchmarkInfo = {}

TIMES = 0
LOWER_BOUNDS = 1
UPPER_BOUNDS = 2
MISSING_PROBABILITIES = 3

# Go through all the files and store the result in benchmarkInfo
numIterations = 0
for directory in os.listdir(resultDirectory):
    files = os.listdir(os.path.join(resultDirectory, directory))
    iterationResults = {}
    for file in files:
        if not file.split(".")[0].isnumeric():
            continue

        fileStream = open(os.path.join(resultDirectory, directory, file))
        content = fileStream.readlines()
        fileStream.close()

        options = content[0].split(" -")
        modelName = options[0].split("/")[-1].split(".prism")[0]

        if modelName not in benchmarkInfo:
            benchmarkInfo[modelName] = []

        content = content[1:]
        times = list(map(float, content[0].split()))
        lowerBounds = list(map(float, content[1].split()))
        upperBounds = list(map(float, content[2].split()))
        missingProbability = float(content[3])

        results = {TIMES: times, LOWER_BOUNDS: lowerBounds, UPPER_BOUNDS: upperBounds,
                   MISSING_PROBABILITIES: missingProbability}
        benchmarkInfo[modelName].append(results)

    numIterations = numIterations + 1


# benchmarkInfo has accumulated all the results.
with open('tempResults.txt', 'w') as tempResults:
    for model, iterationResults in benchmarkInfo.items():
        tempResults.write(model + '\n')

        averageMissingProbability = 0
        bestLowerBound = -1
        bestUpperBound = -1
        maxBoundsDiff = 0
        execTime = -1

        for iterationResult in iterationResults:
            averageMissingProbability = averageMissingProbability + iterationResult[MISSING_PROBABILITIES]
            lastLowerBound = iterationResult[LOWER_BOUNDS][-1]
            lastUpperBound = iterationResult[UPPER_BOUNDS][-1]
            diff = lastUpperBound - lastLowerBound

            if diff >= maxBoundsDiff:
                bestLowerBound = lastLowerBound
                bestUpperBound = lastUpperBound
                execTime = (iterationResult[TIMES][-1]) - (iterationResult[TIMES][0])

        tempResults.write('Execution time: ' + str(execTime) + '\n')
        tempResults.write('Lower bound: ' + str(bestLowerBound) + '\n')
        tempResults.write('Upper bound: ' + str(bestUpperBound) + '\n')
        tempResults.write('Average Error Probability: ' + str(averageMissingProbability/(len(iterationResults))) + '\n')
        tempResults.write('\n')
        tempResults.write('\n')
        tempResults.write('\n')
