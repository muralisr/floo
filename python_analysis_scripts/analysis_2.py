import argparse
import re
from typing import Dict, List

class FunNode:
    """Represents a function invocation and its inner function calls."""
    def __init__(self, signature: str, memoized: bool, start_timestamp: int):
        self.signature: str = signature
        self.memoized: bool = memoized
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
        self.total_runtime: int = 0
        self.saved_invocations: int = 0
        self.saved_runtime: int = 0

    def __str__(self):
        result: str = f"{self.signature}: pure_t={self.pure_runtime}, "
        result += f"diff_ts={self.end_timestamp - self.start_timestamp}, "
        result += f"total_t={self.total_runtime}, "
        result += f"m={self.memoized}, c=["
        for inner in self.inner_calls:
            result += (inner.signature + ',')
        result += ']'
        return result

    def append_child(self, child):
        self.inner_calls.append(child)

    def compute_pure_runtime(self) -> int:
        result: int = self.helper_timestamp - self.start_timestamp
        # debug
        if len(self.inner_invoc_logs) % 2 != 0:
            print('Expected even number of items in inner_invoc_logs')
            print(self.inner_invoc_logs)
            print('-'*40)
            return -1

        for i in range(0, len(self.inner_invoc_logs), 2):
            # processing both i and i+1
            # TODO: check the child id. For now assuming they are ok
            child_time: int = (self.inner_invoc_logs[i+1]['timestamp'] -
                               self.inner_invoc_logs[i]['timestamp'])
            result -= child_time

        if result < 0:
            print('UNEXPECTED: this pure_runtime should not be negative!')
            return -1
        self.pure_runtime = result
        return 0

    def analyze_subtree_memoization(self):
        """Analyzes this node and all those in its sub-tree"""
        self.total_invocations = 1
        self.total_runtime = self.pure_runtime
        self.saved_invocations: int = 0
        self.saved_runtime: int = 0
        for inner in self.inner_calls:
            inner.analyze_subtree_memoization()
            self.total_invocations += inner.total_invocations
            self.total_runtime += inner.total_runtime
            self.saved_invocations += inner.saved_invocations
            self.saved_runtime += inner.saved_runtime
        if self.memoized:
            self.saved_invocations += 1
            self.saved_runtime += self.pure_runtime



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

    def analyze_memoization(self):
        """Walks over the graph and computes the memoization benefits"""
        for root in self.roots:
            root.analyze_subtree_memoization()
            self.total_invocations += root.total_invocations
            self.total_runtime += root.total_runtime
            self.saved_invocations += root.saved_invocations
            self.saved_runtime += root.saved_runtime

    def print_four_numbers(self):
        print(f"Invocations: saved={self.saved_invocations}, " +
              f"total={self.total_invocations}")
        print(f"Runtime: saved={self.saved_runtime}, " +
              f"total={self.total_runtime}")



def build_call_graph(thread_id: int, logs: List[Dict]) -> CallGraph:
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
    index: int = -1
    # for index, log in enumerate(logs):
    while (index+1) < len(logs):
        index += 1
        log: Dict = logs[index]
        if log['checkpoint'] == 'ENTER':
            node: FunNode = FunNode(log['invoc_id'], log['memoized'],
                                    log['timestamp'])
            open_fn_stack.append(node)
            # fn_inner_invocs_map[node] = []
        elif log['checkpoint'] == 'HELPEREND' and len(open_fn_stack) > 0:
            top_node: FunNode = open_fn_stack[-1]
            if top_node.signature == log['invoc_id']:
                top_node.helper_timestamp = log['timestamp']
            else:
                print(f"*ERROR-helper: expected: <{top_node.signature}>" +
                      f" -> got: <{log['invoc_id']}>")
        elif (log['checkpoint'] == 'CHILDSTART' or
              log['checkpoint'] == 'CHILDEND'):
            # throw away extra child logs
            if (index+1) >= len(logs):
                print(f"We have a problem: child start, t{thread_id} ended")
                continue
            if (logs[index]['checkpoint'] == 'CHILDSTART' and
                logs[index+1]['checkpoint'] == 'CHILDEND' and
                logs[index]['invoc_id'] == logs[index+1]['invoc_id']):
                index += 1 # skip an extra
                continue
            if len(open_fn_stack) > 0:
                top_node: FunNode = open_fn_stack[-1]
                top_node.inner_invoc_logs.append(log)
                # if fn_inner_invocs_map.get(top_node):
                #     fn_inner_invocs_map[top_node].append(log)
                # else:
                #     fn_inner_invocs_map[top_node] = [log]
            else:
                print('*ERROR-child-log: expected an open node in stack')
        elif log['checkpoint'] == 'EXIT':
            while (len(open_fn_stack) > 0 and
                   open_fn_stack[-1].signature != log['invoc_id']):
                print(f"*ERROR-end: expected: <{open_fn_stack[-1].signature}>" +
                      f" -> got: <{log['invoc_id']}>")
                open_fn_stack.pop() # throw away top of the stack
            if len(open_fn_stack) == 0:
                print('*ERROR-stack-empty: threw away all nodes!')
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
                # throw away this function
                print(f"ERROR in computing runtime {top_node}")
                continue
            # If stack is empty now, we have a root at our hand
            if len(open_fn_stack) == 0:
                call_graph.add_root(top_node)
                # print(f"ROOT: {top_node}")
            else:
                open_fn_stack[-1].append_child(top_node)
        else:
            print('Invalid log line!')

    # print(f"Total matched enter/exit: {count}")
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
                'memoized': False,
                'tid': 0,
                'timestamp': 0,
            }
            try:
                parts: List[str] = line.split('_')
                log_line['checkpoint'] = parts[0]
                log_line['invoc_id'] = parts[1]
                index: int = 2
                if parts[0] == 'ENTER':
                    log_line['memoized'] = (parts[2] == 'YES')
                    index = 3
                log_line['timestamp'] = int(parts[index])
                log_line['tid'] = int(parts[index+1])

                # divide logs based on thread_id -- could do groupby
                if not thread_log_map.get(log_line['tid']):
                    thread_log_map[log_line['tid']] = []
                thread_log_map[log_line['tid']].append(log_line)
            except Exception as e:
                pass

    return thread_log_map

def write_to_file(thread_id: int, call_logs: List[Dict]):
    with open(f"logs-{thread_id}.txt", 'w') as out_file:
        for log in call_logs:
            out_file.write(str(log)+'\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('input_file', help='timestamp_thread_id file path')
    args = parser.parse_args()

    thread_log_map: Dict[int, List[Dict]] = read_input_file(args.input_file)
    print(f"#threads={len(thread_log_map)}")

    total_invocs: int = 0
    total_runtime: int = 0
    saved_invocs: int = 0
    saved_runtime: int = 0
    for thread_id, call_logs in thread_log_map.items():
        print(f"TID: {thread_id}")
        write_to_file(thread_id, call_logs)
        thread_call_graph: CallGraph = build_call_graph(thread_id, call_logs)
        thread_call_graph.analyze_memoization()
        # thread_call_graph.print_four_numbers()
        # for root in thread_call_graph.roots:
        #     print(root)
        total_invocs += thread_call_graph.total_invocations
        total_runtime += thread_call_graph.total_runtime
        saved_invocs += thread_call_graph.saved_invocations
        saved_runtime += thread_call_graph.saved_runtime

    print('-'*40)
    print('RESULTS:')
    if total_invocs == 0:
        total_invocs = 1
    if total_runtime == 0:
        total_runtime = 1
    print(f"total runtime is {total_runtime}")
    print(f"#Invocation Improvement:{(saved_invocs*100/total_invocs):.2f}%")
    print(f"Runtime Improvement:{(saved_runtime*100/total_runtime):.2f}%")
    print('DONE')
