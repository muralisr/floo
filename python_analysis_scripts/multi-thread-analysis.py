import argparse
import re
from typing import Dict, List

class FunNode:
    """Represents a function invocation and its inner function calls."""
    def __init__(self, signature: str, memoized: bool, start_timestamp: int):
        self.signature: str = signature
        self.memoized: bool = memoized
        self.start_timestamp: int = start_timestamp
        self.end_timestamp: int = -1
        self.pure_runtime: int = -1
        self.inner_calls: List = [] # list of children as FunNodes
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
        result: int = self.end_timestamp - self.start_timestamp
        if result < 0:
            print('***BAD SHOD KE!***')
            return -1
        for inner in self.inner_calls:
            if inner.pure_runtime < 0:
                print('UNEXPECTED: all inner calls should have + pure_runtime')
            result -= (inner.end_timestamp - inner.start_timestamp)
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
    count: int = 0
    for log in logs:
        if log['checkpoint'] == 'enter':
            node: FunNode = FunNode(log['fn_signature'], log['memoized'],
                                    log['timestamp'])
            open_fn_stack.append(node)
        else:
            # Expect: function name match the top of open_fn_stack
            top_node: FunNode = open_fn_stack.pop()
            while (log['fn_signature'] != top_node.signature and
                   len(open_fn_stack) > 0):
                print(f"*ERROR-1: expected: <{top_node.signature}>" +
                      f" -> got: <{log['fn_signature']}>")
                top_node: FunNode = open_fn_stack.pop()

            if log['fn_signature'] != top_node.signature:
                print(f"*ERROR-2: expected: <{top_node.signature}>" +
                      f" -> got: <{log['fn_signature']}>")
                continue

            count += 1
            top_node.end_timestamp = log['timestamp']
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

    print(f"Total matched enter/exit: {count}")
    print(f"Total log calls: {len(logs)}")
    print('-'*40)
    return call_graph


def read_input_file(input_file_path: str) -> Dict[int, List[Dict]]:
    thread_log_map: Dict[int, List[Dict]] = {}
    with open(input_file_path, 'r') as input_file:
        lines: List[str] = input_file.readlines()
        for line in lines:
            line = line.strip()
            log_line: Dict = {
                'checkpoint': '',
                'fn_signature': '',
                'memoized': False,
                'tid': 0,
                'timestamp': 0,
            }
            if line.startswith('ENTER:'):
                pattern = (r'ENTER:<(.*?)>' + # fn_signature
                           r'__FLOOMEMO__(YES|NO)__(\d+)' + # memoiz, thread_id
                           r'__FLOOMEMO__(\d+)__FLOOMEMO__') # timestamp
                matched = re.match(pattern, line)
                if not matched or len(matched.groups()) != 4:
                    print(f"*ERROR: ENTER regex not matched for {line}*")
                    continue
                log_line['checkpoint'] = 'enter'
                log_line['fn_signature'] = matched.groups()[0]
                log_line['memoized'] = (matched.groups()[1] == 'YES')
                log_line['tid'] = int(matched.groups()[2])
                log_line['timestamp'] = int(matched.groups()[3])

            elif line.startswith('EXIT'):
                pattern = (r'EXIT__FLOOMEMO__<(.*?)>' + # fn_signature
                           r'__FLOOMEMO__(\d+)' + # timestamp
                           r'__FLOOMEMO__(\d+)__FLOOMEMO__') # thread_id
                matched = re.match(pattern, line)
                # print(f"{line} -> {matched.groups()}")
                if not matched or len(matched.groups()) != 3:
                    print(f"*ERROR: EXIT regex not matched for {line}*")
                    continue
                log_line['checkpoint'] = 'exit'
                log_line['fn_signature'] = matched.groups()[0]
                log_line['timestamp'] = int(matched.groups()[1])
                log_line['tid'] = int(matched.groups()[2])

            # divide logs based on thread_id -- could do groupby
            if not thread_log_map.get(log_line['tid']):
                thread_log_map[log_line['tid']] = []
            thread_log_map[log_line['tid']].append(log_line)

    return thread_log_map


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
    # if total_invocs == 0:
    #     total_invocs = 1
    if total_runtime == 0:
        total_runtime = 1
    print(f"#Invocation Improvement:{(saved_invocs*100/total_invocs):.2f}%")
    print(f"Runtime Improvement:{(saved_runtime*100/total_runtime):.2f}%")
    print('DONE')
