import json

file_path_1 = "/disk/ubuntu_data/projects/compute-caching-analysis/logcat_output/bbc_fn_durations.json"
file_path_2 = "/disk/ubuntu_data/projects/compute-caching-analysis/logcat_output/guardian_fn_durations.json"

# file_path_1 = "/disk/ubuntu_data/projects/compute-caching-analysis/logcat_output/simple_sample.json"

def get_lib_name_from_line(input):
    # print(f"input is {input}")
    split_1 = input.split(' ', 1)[0]
    # print(f"split1 is {split_1}")
    split_2 = split_1.rsplit('.', 1)[0]
    # print(f"split2 is {split_2}")
    return split_2

with open(file_path_2, "r") as f:
    durations = json.loads(f.read())
    total_runtime = 0
    runtime_per_lib = {}
    for k, v in durations.items():
        lib_name = get_lib_name_from_line(k)
        if lib_name not in runtime_per_lib:
            runtime_per_lib[lib_name] = 0
        for r in v:
            runtime_per_lib[lib_name] += r
            total_runtime += r

    for lib in runtime_per_lib:
        runtime_frac_to_print = runtime_per_lib[lib]/total_runtime
        if runtime_frac_to_print > 0.01:
            print(f"lib:{lib}. frac of runtime is {runtime_frac_to_print}")