import argparse
import json
from typing_extensions import runtime
import numpy as np
from typing import List

class FunEvent:
    """Represents an event i.e. a line in the yamp file contain time info."""
    def __init__(self, fn_name: str, global_time: str, thread_time: str, global_self_time: str, thread_self_time: str):
        self.fn_name:str = fn_name
        self.global_time: float = float(global_time)
        self.thread_time: float = float(thread_time)
        self.global_self_time: float = float(global_self_time)
        self.thread_self_time: float = float(thread_self_time)
    
    def __str__(self):
        result: str = f"{self.fn_name}, {self.thread_self_time}"
        return result

def get_list_of_events_from_file(input_file_path):
    first_line = True
    number_of_lines = 0
    list_of_events: List[FunEvent] = []
    with open(input_file_path) as f:
        lines_from_file = f.readlines()
        for line in lines_from_file:
            number_of_lines += 1
            if first_line:
                first_line = False
                continue
            line = line.rstrip()
            line = line.split('	')
            event: FunEvent=FunEvent(line[0], line[1], line[2], line[3], line[4])
            list_of_events.append(event)

    return list_of_events

def get_pure_runtime_sum_of_all_functions(list_of_events: List[FunEvent]):
    total_runtime:float = 0
    for event in list_of_events:
        total_runtime += event.thread_self_time
    return total_runtime

def get_stats_about_runtime_of_all_functions(list_of_events: List[FunEvent]):
    total_runtime:float = 0
    list_of_runtimes = []
    input(f'number of invocations {len(list_of_events)}')
    for event in list_of_events:
        list_of_runtimes.append(event.thread_self_time)
    print(f"median:{np.percentile(list_of_runtimes, 95)}, sum:{sum(list_of_runtimes)}, 95th:{np.percentile(list_of_runtimes, 95)}, 99th:{np.percentile(list_of_runtimes, 99)}")
    return total_runtime

def get_stats_about_sum_of_runtime_of_all_functions(list_of_runtimes: List[int]):
    print(f"median:{np.percentile(list_of_runtimes, 95)}, sum:{sum(list_of_runtimes)}, 95th:{np.percentile(list_of_runtimes, 95)}, 99th:{np.percentile(list_of_runtimes, 99)}")
    

def get_pure_runtime_sum_of_optimizable_functions(list_of_optimizable_fns: List[str], list_of_events: List[FunEvent]):
    total_runtime:float = 0
    set_of_optimizable_fns = set(list_of_optimizable_fns)
    for event in list_of_events:
        if event.fn_name in set_of_optimizable_fns:
            total_runtime += event.thread_self_time
    return total_runtime

def process_and_print_info_about_runtime_of_one_invocation_of_one_function(function_to_list_of_runtimes):
    list_of_average_runtime_per_function = []
    list_of_median_runtime_per_function = []
    list_of_stddev_runtime_per_function = []
    for k, v in function_to_list_of_runtimes.items():
        runtimes = [x for x in list(v)]
        if len(runtimes) == 1:
            list_of_average_runtime_per_function.append(runtimes[0])
            list_of_median_runtime_per_function.append(runtimes[0])
            list_of_stddev_runtime_per_function.append(0)
        else:
            list_of_average_runtime_per_function.append(np.average(runtimes))
            list_of_median_runtime_per_function.append(np.percentile(runtimes, 50))
            list_of_stddev_runtime_per_function.append(np.std(runtimes))

    with open("/disk/Code/projects/soot-instrument/python_analysis_scripts/plotting/list_of_average_runtime_per_function_invocation.json", "w") as f:
        f.write(json.dumps(list_of_average_runtime_per_function, indent=2))
    with open("/disk/Code/projects/soot-instrument/python_analysis_scripts/plotting/list_of_median_runtime_per_function_invocation.json", "w") as f:
        f.write(json.dumps(list_of_median_runtime_per_function, indent=2))
    with open("/disk/Code/projects/soot-instrument/python_analysis_scripts/plotting/list_of_stdev_runtime_per_function_invocation.json", "w") as f:
        f.write(json.dumps(list_of_stddev_runtime_per_function, indent=2))
    print(f"list of average runtime per function invocation: {list_of_average_runtime_per_function}")
    print(f"list of median runtime per function invocation: {list_of_median_runtime_per_function}")

def get_class_to_runtime_mapping(list_of_optimizable_fns: List[str], list_of_events: List[FunEvent]):
    class_to_runtime_info = {}
    set_of_optimizable_fns = set(list_of_optimizable_fns)
    class_to_num_invocations = {}
    class_to_runtime_per_invocation = {}
    for event in list_of_events:
        if event.fn_name not in set_of_optimizable_fns:
            split_up = event.fn_name.split('.')
            unique_id = '.'.join(split_up)# split_up[0]+'.'+split_up[1]
            if unique_id not in class_to_runtime_info:
                class_to_runtime_info[unique_id] = 0
                class_to_num_invocations[unique_id] = 0
                class_to_runtime_per_invocation[unique_id] = []
            class_to_runtime_info[unique_id] += event.thread_self_time
            class_to_num_invocations[unique_id] += 1
            class_to_runtime_per_invocation[unique_id].append(event.thread_self_time)
            
    print(f'distribution of number of invocations per function is {list(class_to_num_invocations.values())}')
    process_and_print_info_about_runtime_of_one_invocation_of_one_function(class_to_runtime_per_invocation)
    return class_to_runtime_info

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('input_file', help='flat output file from yamp')
    parser.add_argument('--set_of_functions_we_can_optimize', help='json file with an array of functions we can optimize')
    args = parser.parse_args()

    events = get_list_of_events_from_file(args.input_file)
    get_stats_about_runtime_of_all_functions(events)
    input("enter to continue")
    with open(args.set_of_functions_we_can_optimize, "r") as f:
        functions_we_can_optimize = [] # json.load(f)
        
        pure_runtime_of_app = get_pure_runtime_sum_of_all_functions(events)
        pure_optimizable_runtime_of_app = get_pure_runtime_sum_of_optimizable_functions(functions_we_can_optimize, events)
        print(f"sum of pure runtime of all events is {pure_runtime_of_app}")
        print(f"sum of pure runtime of events we can optimize is {pure_optimizable_runtime_of_app}")
        class_to_runtime_info = get_class_to_runtime_mapping(functions_we_can_optimize, events)
        class_to_runtime_info["optimizable"] = pure_optimizable_runtime_of_app
        print(json.dumps(class_to_runtime_info, indent=2))
        get_stats_about_sum_of_runtime_of_all_functions(list(class_to_runtime_info.values()))

        # class_to_frac_runtime = {}
        # for k, v in class_to_runtime_info.items():
        #     class_to_frac_runtime[k] = v / pure_runtime_of_app
        # frac_runtime_to_class = {}
        # for k, v in class_to_frac_runtime.items():
        #     while v in frac_runtime_to_class:
        #         v = v + 0.00000000000001
        #     frac_runtime_to_class[v] = k
        # list_of_fracs = frac_runtime_to_class.keys()
        # total_sum = 0
        # for k in sorted(list_of_fracs):
        #     print(f"{frac_runtime_to_class[k]}:{k*100}%")
        #     total_sum += k*100
        # print(f"total sum is {total_sum}")
        

    
