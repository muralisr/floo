import os
import json
import statistics

from helper import create_pct_diff_plot_2

def get_frac_of_functions_with_some_return_value(stats_json):
    list_of_stats = stats_json["has return value"]
    num_fns_with_some_return = 0
    tot_num_fns = 0
    for x in list_of_stats:
        if x == 1:
            num_fns_with_some_return += 1
        tot_num_fns += 1
    return num_fns_with_some_return / tot_num_fns

def get_frac_of_invokes_from_this_object(stats_json):
    list_of_this_fn_invokes = stats_json["num this fn invokes"]
    list_of_nonthis_fn_invokes = stats_json["num non-this fn invokes"]
    tot_num_this_invokes = 0
    tot_num_nonthis_invokes = 0
    for x in list_of_this_fn_invokes:
        if x is not None:
            tot_num_this_invokes += x
    for x in list_of_nonthis_fn_invokes:
        if x is not None:
            tot_num_nonthis_invokes += x

    return tot_num_this_invokes / (tot_num_this_invokes+tot_num_nonthis_invokes)

def get_frac_of_heap_reads_that_happen_within_this(stats_json):
    list_of_this_heap_reads = stats_json["num heap reads"]
    list_of_non_this_heap_reads = stats_json["num non-this heap reads"]
    tot_num_this_heap_reads = 0
    tot_num_nonthis_heap_reads = 0
    for x in list_of_this_heap_reads:
        if x is not None:
            tot_num_this_heap_reads += x
    for x in list_of_non_this_heap_reads:
        if x is not None:
            tot_num_nonthis_heap_reads += x

    return tot_num_this_heap_reads/(tot_num_this_heap_reads+tot_num_nonthis_heap_reads)


def get_frac_of_heap_writes_that_happen_within_this(stats_json):
    list_of_this_heap_writes = stats_json["num heap writes"]
    list_of_non_this_heap_writes = stats_json["num non-this heap writes"]
    tot_num_this_heap_writes = 0
    tot_num_nonthis_heap_writes = 0
    for x in list_of_this_heap_writes:
        if x is not None:
            tot_num_this_heap_writes += x
    for x in list_of_non_this_heap_writes:
        if x is not None:
            tot_num_nonthis_heap_writes += x

    return tot_num_this_heap_writes/(tot_num_this_heap_writes+tot_num_nonthis_heap_writes)

def get_avg_num_params(stats_json):
    list_of_num_params = stats_json["num params"]
    return statistics.mean([x for x in list_of_num_params if x is not None])

def get_avg_num_heap_reads_per_function(stats_json):
    list_of_num_heap_reads_from_this = stats_json["num heap reads"]
    list_of_num_heap_reads_from_nonthis = stats_json["num non-this heap reads"]
    list_of_num_heap_reads_per_fn = []
    for i in range(len(list_of_num_heap_reads_from_this) if len(list_of_num_heap_reads_from_this) < len(list_of_num_heap_reads_from_nonthis) else len(list_of_num_heap_reads_from_nonthis) ):
        x = list_of_num_heap_reads_from_this[i]
        y = list_of_num_heap_reads_from_nonthis[i]
        if x is not None and y is not None:
            list_of_num_heap_reads_per_fn.append(x + y)

    return statistics.mean(list_of_num_heap_reads_per_fn)


def get_avg_num_heap_writes_per_function(stats_json):
    list_of_num_heap_writes_from_this = stats_json["num heap writes"]
    list_of_num_heap_writes_from_nonthis = stats_json["num non-this heap writes"]
    list_of_num_heap_writes_per_fn = []
    for i in range(len(list_of_num_heap_writes_from_this) if len(list_of_num_heap_writes_from_this) < len(list_of_num_heap_writes_from_nonthis) else len(list_of_num_heap_writes_from_nonthis)):
        x = list_of_num_heap_writes_from_this[i]
        y = list_of_num_heap_writes_from_nonthis[i]
        if x is not None and y is not None:
            list_of_num_heap_writes_per_fn.append(x + y)

    return statistics.mean(list_of_num_heap_writes_per_fn)


def get_avg_num_invokes_per_function(stats_json):
    list_num_this_invokes = stats_json["num this fn invokes"]
    list_num_nonthis_invokes = stats_json["num non-this fn invokes"]
    list_num_fn_invokes = []
    for i in range(len(list_num_this_invokes) if len(list_num_this_invokes) < len(list_num_nonthis_invokes) else len(list_num_nonthis_invokes)):
        x = list_num_this_invokes[i]
        y = list_num_nonthis_invokes[i]
        if x is not None and y is not None:
            list_num_fn_invokes.append(x + y)
    return statistics.mean(list_num_fn_invokes)


def plot_a_series(input_list, output_file_path, legend_string):
    create_pct_diff_plot_2(
        [input_list],
        [legend_string],
        legend_string,
        "CDF",
        x_min=min(input_list),
        x_max=max(input_list),
        linestyles=['-', '-', '-', '-', '-', '-'],
        location="upper left",
        save_path=output_file_path
    )

def plot_few_series(data, x_axis_name, output_file_path, x_min=0, x_max=1):
    create_pct_diff_plot_2(
    list(data.values()),
    data.keys(),
    x_axis_name,
    "CDF",
    linestyles=['-', '-', '-', '-', '-', '-'],
    x_min=x_min,
    x_max=x_max,
    location="best",
    save_path=output_file_path
)

if __name__ == "__main__":
    folder_to_load_from = "/disk/ubuntu_data/projects/SootTutorial/demo/HeapRW"
    list_of_frac_of_functions_with_a_return_value = []
    list_of_frac_of_invokes_from_this = []
    list_of_frac_of_heap_reads_that_happen_within_this = []
    list_of_frac_of_heap_writes_that_happen_within_this = []
    list_of_avg_num_params = []
    list_of_avg_num_heap_reads_per_fn = []
    list_of_avg_num_heap_writes_per_fn = []
    list_of_avg_num_invokes_per_fn = []

    for file_name in os.listdir(folder_to_load_from):
        if file_name.endswith(".json") and file_name.startswith("stats_out"):
            file_to_read = os.path.join(folder_to_load_from, file_name)
            print(f"working on {file_to_read}")
            with open(file_to_read, "r") as f:
                file_stats = json.loads(f.read())

                frac_of_fns_with_some_return_value = get_frac_of_functions_with_some_return_value(file_stats)
                list_of_frac_of_functions_with_a_return_value.append(frac_of_fns_with_some_return_value)

                frac_of_invokes_from_this = get_frac_of_invokes_from_this_object(file_stats)
                list_of_frac_of_invokes_from_this.append(frac_of_invokes_from_this)

                frac_of_heap_reads_within_this = get_frac_of_heap_reads_that_happen_within_this(file_stats)
                list_of_frac_of_heap_reads_that_happen_within_this.append(frac_of_heap_reads_within_this)

                frac_of_heap_writes_within_this = get_frac_of_heap_writes_that_happen_within_this(file_stats)
                list_of_frac_of_heap_writes_that_happen_within_this.append(frac_of_heap_writes_within_this)

                avg_num_params = get_avg_num_params(file_stats)
                list_of_avg_num_params.append(avg_num_params)

                avg_num_heap_reads_per_fn = get_avg_num_heap_reads_per_function(file_stats)
                list_of_avg_num_heap_reads_per_fn.append(avg_num_heap_reads_per_fn)
                
                avg_num_heap_writes_per_fn = get_avg_num_heap_writes_per_function(file_stats)
                list_of_avg_num_heap_writes_per_fn.append(avg_num_heap_writes_per_fn)

                avg_num_invokes_per_fn = get_avg_num_invokes_per_function(file_stats)
                list_of_avg_num_invokes_per_fn.append(avg_num_invokes_per_fn)


    print(f"avg frac of fns that have a return value is {statistics.mean(list_of_frac_of_functions_with_a_return_value)} from a total of {len(list_of_frac_of_functions_with_a_return_value)} apps")
    print(f"avg frac of invokes that was invoking within this object is {statistics.mean(list_of_frac_of_invokes_from_this)}")
    print(f"avg frac of heap reads that was in this object is {statistics.mean(list_of_frac_of_heap_reads_that_happen_within_this)}")
    print(f"avg frac of heap writes that was in this object is {statistics.mean(list_of_frac_of_heap_writes_that_happen_within_this)}")
    print(f"avg num of avg num params {statistics.mean(list_of_avg_num_params)}")
    print(f"avg num of avg num heap reads per fn {statistics.mean(list_of_avg_num_heap_reads_per_fn)}")
    print(f"avg num of avg num heap writes per fn {statistics.mean(list_of_avg_num_heap_writes_per_fn)}")
    print(f"avg num of avg num invokes fn {statistics.mean(list_of_avg_num_invokes_per_fn)}")

    
    heap_rw_within_same_object_data = {
        "frac_of_heap_reads_within_same_object": list_of_frac_of_heap_reads_that_happen_within_this,
        "frac_of_heap_writes_within_same_object": list_of_frac_of_heap_writes_that_happen_within_this,
        "frac_of_invokes_within_same_object": list_of_frac_of_invokes_from_this
    }

    
    read_write_dependencies_per_function = {
        "params per fn": list_of_avg_num_params,
        "heap reads per fn": list_of_avg_num_heap_reads_per_fn,
        "heap writes per fn": list_of_avg_num_heap_writes_per_fn,
        "fn calls per fn": list_of_avg_num_invokes_per_fn
    }

    plot_a_series(list_of_frac_of_functions_with_a_return_value, "/disk/ubuntu_data/projects/SootTutorial/demo/stats_graphs/frac_of_fns_with_a_return_value.pdf", "fraction of functions that have a return value")
    plot_a_series(list_of_frac_of_invokes_from_this, "/disk/ubuntu_data/projects/SootTutorial/demo/stats_graphs/frac_of_invokes_that_were_within_a_class.pdf", "fraction of invokes that called intra class functions")
    plot_few_series(heap_rw_within_same_object_data, "fraction of heap rw that happen within a class", "/disk/ubuntu_data/projects/SootTutorial/demo/stats_graphs/frac_of_heap_rw_within_class.pdf")
    plot_few_series(read_write_dependencies_per_function, "avg num rw deps per function", "/disk/ubuntu_data/projects/SootTutorial/demo/stats_graphs/deps_per_fn.pdf", x_min=0, x_max=5)