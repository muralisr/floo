import numpy as np
import json


input_file_path = "/disk/Code/projects/helen-profiler/output/bbc_trace.txt"
output_file_path = "/disk/Code/projects/helen-profiler/output/bbc_function_to_cv.txt"
output_runtime_file_path_memoized = "/disk/Code/projects/helen-profiler/output/bbc_function_to_memoized_runtime.txt"
# to plot the output: /disk/Code/projects/soot-instrument/python_analysis_scripts/plotting/function_runtime_cv_cdf.py 
function_to_start_time = {}
function_to_runtimes = {}

line_num = 1
with open(input_file_path, "r") as f:
    while True:
        if line_num % 10000 == 0:
            print(f"line_num {line_num} of 38383024")
        line_num += 1
        line = f.readline()
        if not line:
            break
        line = line.strip()
        components = line.split('-')
        f_name = components[0]
        timestamp_as_string = components[1].split(' ')[-1]
        try:
            timestamp = int(timestamp_as_string)
        except: # timestamp was not a proper base 10 integer
            continue
        true_if_enter = "enter" in components[1]
        if "exit_unroll" in components[1]: # skip unrolls
            continue
        if true_if_enter:
            function_to_start_time[f_name] = timestamp
        else:
            if f_name not in function_to_start_time: # enter was not seen before exit
                continue
            corresponding_start = function_to_start_time[f_name]
            current_end_time = timestamp
            if f_name not in function_to_runtimes:
                function_to_runtimes[f_name] = []
            runtime_of_function = current_end_time - corresponding_start
            if runtime_of_function < 0:
                continue
            if runtime_of_function < 1:
                runtime_of_function = 1
            function_to_runtimes[f_name].append(runtime_of_function)

list_of_cvs = []
list_of_runtimes = []
for k, v in function_to_runtimes.items():
    if len(v) > 0:
        list_of_runtimes.append(v)
        cv = np.std(list(v)) / np.mean(list(v))
        list_of_cvs.append(cv)

        
json.dump(list_of_runtimes, open(output_runtime_file_path_memoized, "w"))
json.dump(list_of_cvs, open(output_file_path, "w"))
