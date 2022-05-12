import argparse
import re
import json
from typing import Dict, List, Tuple

class FunNode:
    """Represents a function invocation and its inner function calls."""
    def __init__(self, signature: str, memoized: bool, start_timestamp: int):
        self.signature: str = signature
        self.memoized: bool = memoized
        self.non_deterministic: bool = False
        self.start_timestamp: int = start_timestamp
        self.helper_timestamp: int = -1 # right after helper is done
        self.end_timestamp: int = -1
        self.pure_runtime: int = -1
        # list of timestamps right before and after an inner invocation
        self.inner_invoc_logs: List[str] = []
        # list of children as FunNodes
        self.inner_calls: List = []
        # for the second pass analysis
        self.total_invocations: int = 0
        self.total_runtime: int = 0 # TODO(shagha, since we did a /1000 when loading, isn't all timestamp having decimal and in milliseconds? does not affect correctness, just checking the type annotation.)
        self.saved_invocations: int = 0
        self.saved_runtime: int = 0

    def __str__(self):
        result: str = f"{self.signature}: pure_t={self.pure_runtime}, "
        result += f"helper={self.helper_timestamp}, "
        result += f"end={self.end_timestamp}, "
        # result += f"m={self.memoized}, c=["
        # for inner in self.inner_calls:
        #     result += (inner.signature + ',')
        # result += ']'
        return result

    def append_child(self, child):
        self.inner_calls.append(child)

    def compute_pure_runtime(self) -> int:
        result: int = self.end_timestamp - self.helper_timestamp
        if result < 0:
            print('***BAD SHOD KE!***')
            return -1
        for inner in self.inner_calls:
            if inner.pure_runtime < 0:
                print('UNEXPECTED: all inner calls should have + pure_runtime')
            result -= (inner.end_timestamp - inner.helper_timestamp)
        if result < 0:
            print('UNEXPECTED: this pure_runtime should not be negative!')
            return -1
        self.pure_runtime = result
        return 0

    def analyze_subtree_memoization(self, network_timeline: List[Tuple[float]]):
        """Analyzes this node and all those in its sub-tree"""
        self.total_invocations = 1
        self.total_runtime = self.pure_runtime
        self.saved_invocations: int = 0
        self.saved_runtime: int = 0
        for inner in self.inner_calls:
            if inner.memoized != 2: # if inner is a function call that is not UNK
                inner.analyze_subtree_memoization(network_timeline)
                self.total_invocations += inner.total_invocations
                self.total_runtime += inner.total_runtime
                self.saved_invocations += inner.saved_invocations
                self.saved_runtime += inner.saved_runtime
        # if non_deterministic, do not count in the numerator
        if self.non_deterministic:
            return
        # else if memoizable and not concurrent with network fetch
        if self.memoized == 1 and not self.has_overlap(network_timeline):
            self.saved_invocations += 1
            self.saved_runtime += self.pure_runtime

    def has_overlap(self, network_timeline: List[Tuple[float]]) -> bool:
        """Checks if the function execution has overlap with network fetch"""
        concurrent: bool = False
        for fetch in network_timeline:
            if (self.helper_timestamp > fetch[0] and
                self.end_timestamp < fetch[1]):
                concurrent = True
                print(f"Found an overlap: {fetch} -- {self}")
                break
        return concurrent


class CallGraph:
    """Represents a call graph for a particular thread."""
    def __init__(self, thread_id: int):
        self.thread_id: str = thread_id
        self.roots: List[FunNode] = []
        self.total_invocations: int = 0
        self.total_runtime: int = 0
        self.saved_invocations: int = 0
        self.saved_runtime: int = 0

    def add_root(self, node: FunNode):
        self.roots.append(node)
    
    def get_memoizable_functions_to_list_of_runtimes_map(self):
        fn_name_to_runtimes = {}
        for root in self.roots: # for each function in this thread
            if root.memoized == 1:
                if root.signature not in fn_name_to_runtimes:
                    fn_name_to_runtimes[root.signature] = []
                fn_name_to_runtimes[root.signature].append(root.pure_runtime)
        return fn_name_to_runtimes


    def analyze_memoization(self, network_timeline: List[Tuple[float]]):
        """Walks over the graph and computes the memoization benefits"""
        for root in self.roots:
            root.analyze_subtree_memoization(network_timeline)
            self.total_invocations += root.total_invocations
            self.total_runtime += root.total_runtime
            self.saved_invocations += root.saved_invocations
            self.saved_runtime += root.saved_runtime

    def print_four_numbers(self):
        print(f"Invocations: saved={self.saved_invocations}, " +
              f"total={self.total_invocations}")
        print(f"Runtime: saved={self.saved_runtime}, " +
              f"total={self.total_runtime}")



def build_call_graph(thread_id: int, logs: List[Dict],
                     non_deter_funs: List[str]) -> CallGraph:
    """Given an ordered list of enter and exit logs (all executed in
    thread_id), builds and returns a call graph.
    It uses the timestamps of a function enter and exit to infer
    nested (inner) function calls.
    """
    call_graph: CallGraph = CallGraph(thread_id)
    open_fn_stack: List[FunNode] = []
    # the list of child logs during a parent invocations
    # fn_inner_invocs_map: Dict[FunNode, List] = {}
    count: int = 0
    mismatches: int = 0
    index: int = -1
    # for index, log in enumerate(logs):
    while (index+1) < len(logs):
        index += 1
        log: Dict = logs[index]
        if log['checkpoint'] == 'ENTER':
            node: FunNode = FunNode(log['invoc_id'], log['memoized'],
                                    log['timestamp'])
            if log['invoc_id'] in non_deter_funs:
                node.non_deterministic = True
            open_fn_stack.append(node)
            # fn_inner_invocs_map[node] = []
        elif log['checkpoint'] == 'HELPEREND':
            if len(open_fn_stack) == 0:
                mismatches += 1
                # print(f'*ERROR-stack-empty: expected open node (helper)!*')
                continue
            top_node: FunNode = open_fn_stack[-1]
            if top_node.signature == log['invoc_id']:
                top_node.helper_timestamp = log['timestamp']
            else:
                mismatches += 1
                # print(f"*ERROR-helper: expected: <{top_node.signature}>" +
                #       f" -> got: <{log['invoc_id']}>")
        elif (log['checkpoint'] == 'CHILDSTART' or
              log['checkpoint'] == 'CHILDEND'):
            continue # ignore CHILD events
        elif log['checkpoint'] == 'EXIT':
            while (len(open_fn_stack) > 0 and
                   open_fn_stack[-1].signature != log['invoc_id']):
                # print(f"*ERROR-end: expected: <{open_fn_stack[-1].signature}>" +
                #       f" -> got: <{log['invoc_id']}>")
                mismatches += 1
                open_fn_stack.pop() # throw away top of the stack
            if len(open_fn_stack) == 0:
                mismatches += 1
                # print('*ERROR-stack-empty: threw away all nodes!')
                continue

            # found a match
            count += 1
            top_node: FunNode = open_fn_stack.pop()
            top_node.end_timestamp = log['timestamp']
            # Expected to have top_node already in the invoc map
            # if not fn_inner_invocs_map.get(top_node):
            #     print(f"Error-end-map: Expected to find {top_node.signature}" +
            #           ' in the map, but not found!')
            #     continue
            # top_node.inner_invoc_logs = fn_inner_invocs_map[top_node]
            check_runtime: int = top_node.compute_pure_runtime()
            if check_runtime == -1:
                mismatches+=1
                # throw away this function
                # print(f"ERROR in computing runtime {top_node}")
                continue
            # If stack is empty now, we have a root at our hand
            if len(open_fn_stack) == 0:
                call_graph.add_root(top_node)
                # print(f"ROOT: {top_node}")
            else:
                open_fn_stack[-1].append_child(top_node)
        else:
            print('Invalid log line!')

    print(f"Total matched enter/exit: {count}, mismatched: {mismatches}")
    # print(f"Total log calls: {len(logs)}")
    # print('-'*40)
    return call_graph


def read_input_file(input_file_path: str) -> Dict[int, List[Dict]]:
    thread_log_map: Dict[int, List[Dict]] = {}
    with open(input_file_path, 'r') as input_file:
        lines: List[str] = input_file.readlines()
        for line in lines:
            line = line.strip()
            log_line: Dict = {
                'checkpoint': '',
                'invoc_id': '',
                'memoized': 0, # 0=False,1=True,2=Unknown
                'tid': 0,
                'timestamp': 0,
            }
            parts: List[str] = line.split('_')
            if len(parts) < 4:
                print(f"Unexpected line format: *{line}*")
                continue
            try:
                log_line['checkpoint'] = parts[0]
                log_line['invoc_id'] = parts[1]
                index: int = 2
                if parts[0] == 'ENTER':
                    if parts[2] == 'YES':
                        log_line['memoized'] = 1
                    elif parts[2] == 'NO':
                        log_line['memoized'] = 0
                    else:
                        log_line['memoized'] = 2
                    index = 3
                log_line['timestamp'] = float(parts[index])/1000 # convert to s
                log_line['tid'] = int(parts[index+1])
            except ValueError as e:
                print(f"Unexpected line format: *{line}*")
                continue

            # divide logs based on thread_id -- could do groupby
            if not thread_log_map.get(log_line['tid']):
                thread_log_map[log_line['tid']] = []
            thread_log_map[log_line['tid']].append(log_line)

    return thread_log_map

def write_to_file(thread_id: int, call_logs: List[Dict]):
    print(f"TID: {thread_id}")
    with open(f"logs-{thread_id}.txt", 'w') as out_file:
        for log in call_logs:
            out_file.write(str(log)+'\n')

def get_network_timeline(network_file_path: str) -> List[Tuple[float]]:
    result: List[Tuple[float]] = []
    with open(network_file_path, 'r') as network_file:
        lines: List[str] = network_file.readlines()
        for line in lines:
            try:
                parts = line.strip().split(',')
                start: float = float(parts[0][6:])
                end: float = float(parts[1][4:])
                result.append((start, end))
            except ValueError:
                print(f"Line format error: {line}")
    return result


def get_non_deterministic_list(non_deter_file_path: str) -> List[str]:
    result: List[str] = []
    with open(non_deter_file_path, 'r') as non_deter_file:
        json_data: Dict = json.load(non_deter_file)
    for fun_name in json_data:
        result.append(fun_name)
    return result



if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('invocation_logs', help='Log of invocs with memo info')
    parser.add_argument('--network', help='Log of network activities')
    parser.add_argument('--nondet', help='List of non-deterministic functions')

    args = parser.parse_args()

    thread_log_map: Dict[int, List[Dict]] = read_input_file(args.invocation_logs)
    print(f"#threads={len(thread_log_map)}")

    main_id: int = 0
    max_invoc_num: int = -1
    non_deter_funs: List[str] = []
    if args.nondet:
        non_deter_funs = get_non_deterministic_list(args.nondet)
    network_timeline: List[Tuple[float]] = []
    if args.network:
        network_timeline = get_network_timeline(args.network)
    max_runtime = 0
    for thread_id, call_logs in thread_log_map.items():
        print(f"TID: {thread_id}")
        thread_call_graph: CallGraph = build_call_graph(thread_id, call_logs, non_deter_funs)
        thread_call_graph.analyze_memoization(network_timeline)
        if thread_call_graph.total_runtime > max_runtime:
            max_runtime = thread_call_graph.total_runtime
            main_id = thread_id
        
    print(f"Main thread ID: {main_id}")

    total_invocs: int = 0
    total_runtime: int = 0
    saved_invocs: int = 0
    saved_runtime: int = 0
    call_graph: CallGraph = build_call_graph(main_id, thread_log_map[main_id],
                                             non_deter_funs)
    call_graph.analyze_memoization(network_timeline)
    total_invocs += call_graph.total_invocations
    total_runtime += call_graph.total_runtime
    saved_invocs += call_graph.saved_invocations
    saved_runtime += call_graph.saved_runtime

    print('-'*40)
    print('RESULTS:')
    if total_invocs == 0:
        total_invocs = 1
    if total_runtime == 0:
        total_runtime = 1
    print(f"Total runtime: {total_runtime}")
    print(f"#Invocation Improvement:{(saved_invocs*100/total_invocs):.2f}%")
    print(f"Runtime Improvement:{(saved_runtime*100/total_runtime):.2f}%")
    print('DONE')


    memoizable_fn_name_to_list_of_runtimes = call_graph.get_memoizable_functions_to_list_of_runtimes_map()
    with open("/disk/Code/projects/soot-instrument/python_analysis_scripts/plotting/memoizable_fn_to_list_of_its_runtimes.json", "w") as f:
        json.dump(memoizable_fn_name_to_list_of_runtimes, f)