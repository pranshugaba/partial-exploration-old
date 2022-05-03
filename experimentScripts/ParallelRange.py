class ParallelRange:
    def __init__(self, start, end):
        self.start = start
        self.end = end


def get_thread_allocations(number_of_threads, number_of_operations):
    base_operations_per_thread = number_of_operations // number_of_threads
    extra_operations = number_of_operations % number_of_threads

    thread_operations = [base_operations_per_thread] * number_of_threads
    for i in range(extra_operations):
        thread_operations[i] = thread_operations[i] + 1

    operations_covered = 0
    parallel_ranges = []
    for i in range(number_of_threads):
        start = operations_covered
        end = operations_covered + thread_operations[i] - 1
        parallel_ranges.append(ParallelRange(start, end))
        operations_covered = end + 1

    return parallel_ranges
