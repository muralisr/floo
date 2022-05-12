import numpy as np
name_to_occurence_count = {}
name_to_optimizable_count = {}
with open("bbc_trace7.txt", "r") as f:
    lines = f.readlines()
    for line in lines:
        line = line.rstrip()
        components = line.split('_')
        if components[0] == 'ENTER':
            name = components[1]
            if name not in name_to_occurence_count:
                name_to_occurence_count[name] = 0
                name_to_optimizable_count[name] = 1
            name_to_occurence_count[name] += 1
            if components[2] == 'YES':
                name_to_optimizable_count[name] += 1
list_of_fractions_denoting_fraction_of_invokes_of_a_function_that_was_memoizable = []
for k in name_to_occurence_count:
    list_of_fractions_denoting_fraction_of_invokes_of_a_function_that_was_memoizable.append(name_to_optimizable_count[k]/name_to_occurence_count[k])
    
print(list_of_fractions_denoting_fraction_of_invokes_of_a_function_that_was_memoizable)

print(np.percentile(list_of_fractions_denoting_fraction_of_invokes_of_a_function_that_was_memoizable, 50))
print(np.percentile(list_of_fractions_denoting_fraction_of_invokes_of_a_function_that_was_memoizable, 90))