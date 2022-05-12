
import argparse
import re
import json
from typing import Dict, List, Tuple
def get_non_deterministic_list(non_deter_file_path: str) -> List[str]:
    result: List[str] = []
    with open(non_deter_file_path, 'r') as non_deter_file:
        json_data: Dict = json.load(non_deter_file)
    for fun_name in json_data:
        result.append(fun_name)
    return result


parser = argparse.ArgumentParser()
parser.add_argument('--input', help='Log of compute activities')
parser.add_argument('--nondet', help='List of non-deterministic functions')

args = parser.parse_args()
non_deter_file_path=args.nondet
input_file_path=args.input

set_of_non_deter_fns = set(get_non_deterministic_list(non_deter_file_path))
count_of_non_deter_invokes = 0
count_of_deter_invokes = 0
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
            if parts[1] in set_of_non_deter_fns:
                count_of_non_deter_invokes += 1
            else:
                count_of_deter_invokes += 1
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

print(f"count of nondeter={count_of_non_deter_invokes}, count of deter={count_of_deter_invokes}")