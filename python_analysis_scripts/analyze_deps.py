import json

fn_rw_file = "/disk/ubuntu_data/projects/SootTutorial/demo/HeapRW/buzzfeed_deps.json"
fn_call_file = "/disk/ubuntu_data/projects/SootTutorial/demo/HeapRW/buzzfeed_fns.json"

list_of_fns = set()

if __name__ == "__main__":
    with open(fn_rw_file, "r") as f:
        deps = json.loads(f.read())
        # print(deps)
    num_fn_calls_with_intersection = 0
    num_fn_calls = 0
    with open(fn_call_file, "r") as f:
        lines = f.readlines()
        for line in lines:
            line = line.rstrip("\n")
            [child, parent] = line.split("__M__", 1)
            # print(f"child is {child}, parent is {parent}")
            if (parent in deps) and (child in deps):
                parent_write_set = set(deps[parent]['writes'])
                child_read_set = set(deps[child]['reads'])
                if len(child_read_set.intersection(parent_write_set)) > 0:
                    num_fn_calls_with_intersection += 1
            num_fn_calls += 1
            list_of_fns.add(child)
            list_of_fns.add(parent)
    print(f"tot num fns is {len(list_of_fns)}")
    print(f"num intersection is {num_fn_calls_with_intersection} and num calls is {num_fn_calls}")