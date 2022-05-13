import numpy as np
import json
import random

input_runtime_file_path = "/disk/Code/projects/helen-profiler/output/bbc_function_to_memoized_runtime.txt"
output_list_of_cvs_file_path = "/disk/Code/projects/helen-profiler/output/bbc_function_to_cv_memoized_functions_only.txt"
list_of_runtimes = json.load(open(input_runtime_file_path, "r"))
list_of_cvs = []
for original_function_runtime_list in list_of_runtimes:
    memo_factor = random_factor = (random.randint(1, 4))
    memoized_function_runtime_list = [x / memo_factor for x in original_function_runtime_list]
    cv = np.std(memoized_function_runtime_list) / np.mean(memoized_function_runtime_list)
    list_of_cvs.append(cv)

json.dump(list_of_cvs, open(output_list_of_cvs_file_path, "w"))