import json
'''
EXIT__FLOOMEMO__<bbc.mobile.news.v3.di.DaggerMultiDexApplication: void attachBaseContext(android.content.Context)>__FLOOMEMO__87865011443048__FLOOMEMO__2__FLOOMEMO__
ENTER__FLOOMEMO__<net.hockeyapp.android.CrashManager: void a(android.content.Context,java.lang.String)>__FLOOMEMO__87865012219032__FLOOMEMO__2__FLOOMEMO__
ENTER__FLOOMEMO__<net.hockeyapp.android.CrashManager: void a(android.content.Context,java.lang.String,java.lang.String,net.hockeyapp.android.CrashManagerListener)>__FLOOMEMO__87865012266993__FLOOMEMO__2__FLOOMEMO__
ENTER__FLOOMEMO__<net.hockeyapp.android.CrashManager: void a(android.content.Context,java.lang.String,java.lang.String,net.hockeyapp.android.CrashManagerListener,boolean)>__FLOOMEMO__87865012273582__FLOOMEMO__2__FLOOMEMO__
'''


from os import times


file_path = "/disk/Code/projects/soot-instrument/python_analysis_scripts/bbc_time_data_1.txt"
fn_name_to_runtime_list = "/disk/Code/projects/soot-instrument/python_analysis_scripts/parsed_runtime_of_fns_in_bbc.txt"


class FunctionInformation:
    function_name: str
    thread_id: int
    timestamp: float
    is_entry: bool
    duration_of_children: float

    def __init__ (self, fn_name, thread_id, timestamp, is_entry):
        self.function_name = fn_name
        self.thread_id = thread_id
        self.timestamp = timestamp
        self.is_entry = is_entry
        self.duration_of_children = 0

def parse_line(input_line):
    partitions = input_line.split("__FLOOMEMO__")
    is_entry = partitions[0] == "ENTER"
    fn_name = partitions[1]
    timestamp = float(partitions[2])
    thread_id = int(partitions[3])
    return fn_name, thread_id, is_entry, timestamp

def main():
    lines_to_process = []
    currently_open_functions = []
    fn_name_to_runtime = {}
    with open(file_path, "r") as f:
        lines_to_process = f.readlines() 
    for i in range(len(lines_to_process)):
        line = lines_to_process[i].rstrip()
        try:
            fn_name, thread_id, is_entry, timestamp = parse_line(line) 
        except Exception as _:
            continue
        if i % 1000 == 0 :
            print(f"working on {i} of {len(lines_to_process)}")
        if is_entry:
            currently_open_functions.append(FunctionInformation(fn_name, thread_id, timestamp, is_entry))
        else:
            item = FunctionInformation("", 0, 0, False)
            for i in range(len(currently_open_functions)):
                item = currently_open_functions[i]
                if item.function_name == fn_name and item.thread_id == thread_id:
                    del currently_open_functions[i]
                    break
            if item.function_name != "":  # we just matched an exit with an entry
                # runtime of the exit - entry is: exit time minus entry time minus sum of duration of any children
                runtime_of_just_removed_function = timestamp - item.timestamp - item.duration_of_children 
                if item.function_name not in fn_name_to_runtime:
                    fn_name_to_runtime[item.function_name] = []
                # a function may occur multiple times. so each runtime is appended to a list
                fn_name_to_runtime[item.function_name].append(runtime_of_just_removed_function)
                # now that we know the exact runtime of this function, let us update all of this functions parent
                # saying that one of their child has the runtime of X
                for i in range(len(currently_open_functions)):
                    if currently_open_functions[i].thread_id == item.thread_id:
                        currently_open_functions[i].duration_of_children += runtime_of_just_removed_function

    with open(fn_name_to_runtime_list, "w") as f:
        f.write(json.dumps(fn_name_to_runtime, indent=2))

if __name__ == "__main__":
    main()    