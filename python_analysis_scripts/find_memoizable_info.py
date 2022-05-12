import statistics
import json

memo_table_file = "/disk/Code/projects/soot-instrument/python_analysis_scripts/bbc_memo_data_1.txt"
runtime_info_file = "/disk/Code/projects/soot-instrument/python_analysis_scripts/parsed_runtime_of_fns_in_bbc.txt"

def get_median(list_of_info):
    if len(list_of_info) == 1:
        return list_of_info[0]
    elif len(list_of_info) == 0:
        return 0
    else:
        return statistics.median(list_of_info)

def main():
    total_number_of_functions = 0
    number_of_memoizable_functions = 0
    total_runtime_of_functions = 0
    memoizable_runtime_of_functions = 0
    function_name_to_runtime_mapping = {}
    with open(runtime_info_file, "r") as f:
        function_name_to_runtime_mapping = json.loads(f.read())
    function_name_to_median_runtime = {}
    for k, v in function_name_to_runtime_mapping.items():
        function_name_to_median_runtime[k] = get_median(v)
    with open(memo_table_file, "r") as f:
        lines = f.readlines()
        for line in lines:
            line = line.rstrip()
            partitions = line.split("__FLOOMEMO_")
            print(f"partitions is {partitions}")
            function_name = partitions[0].split(":", 1)[1]
            is_memoizable = partitions[1] == "YES"
            total_number_of_functions += 1
            if is_memoizable:
                number_of_memoizable_functions += 1
            if function_name in function_name_to_median_runtime:
                if function_name_to_median_runtime[function_name] <= 0:
                    continue
                total_runtime_of_functions += function_name_to_median_runtime[function_name]
                if is_memoizable:
                    memoizable_runtime_of_functions += function_name_to_median_runtime[function_name]

    print(f"fraction of memoizable functions: {number_of_memoizable_functions/total_number_of_functions}")
    print(f"fraction of memoizable runtime: {memoizable_runtime_of_functions/total_runtime_of_functions}")
    


if __name__ == "__main__":
    main()   