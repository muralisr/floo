from helper import *

list_of_time_delta_names = ["Back-to-back", "4 hours", "12 hours", "1 day"]
list_of_medians = [[49.22, 38.22, 37.33, 34.5]]
list_of_25ths = [[49.22-37.39, 38.22-28.5, 37.33-29.07, 34.5-30.67]]
list_of_75ths = [[86.34-49.22, 85.825-38.22, 69.085-37.33, 65.645-34.5]]
list_of_network_names = ["Frac. of Runtime"]

output_file_name = "/disk/Code/projects/soot-instrument/python_analysis_scripts/plotting/bar chart showing frac of runtime that is memoizable for a set of apps over varying time deltas.pdf"

do_clustered_bar_plotting(list_of_time_delta_names, list_of_medians, list_of_25ths, list_of_75ths, list_of_network_names, output_file_name, y_axis_name="% runtime memoizable", label_size=14, x_axis_name="$\delta$",colors=["#e41a1c", "#377eb8", "#000000"], legend_num_cols=2, hide_legend=True)

output_file_name = "/disk/Code/projects/soot-instrument/python_analysis_scripts/plotting/bar chart showing fraction of invocations that are memoizable for apps over varying time deltas.pdf"
list_of_medians = [[92.06,88.38,86.99,79.33]]
list_of_25ths = [[92.06-89.44,88.38-79.9,86.99-76.5,79.33-66.77]]
list_of_75ths = [[99.49-92.06,99.48-88.38,98.01-86.99,97.66-79.33]]

do_clustered_bar_plotting(list_of_time_delta_names, list_of_medians, list_of_25ths, list_of_75ths, list_of_network_names, output_file_name, y_axis_name="% of num invocations memoizable", label_size=14, x_axis_name="$\delta$",colors=["#e41a1c", "#377eb8", "#000000"], legend_num_cols=2, hide_legend=True)

output_file_name = "/disk/Code/projects/soot-instrument/python_analysis_scripts/plotting/clustered bar chart showing frac of runtime and frac of invocations that are memoizable over varying deltas.pdf"
list_of_medians = [[49.22, 38.22, 37.33, 34.5], [92.06,88.38,86.99,79.33]]
list_of_25ths = [[49.22-37.39, 38.22-28.5, 37.33-29.07, 34.5-30.67], [92.06-89.44,91.38-79.9,86.99-76.5,79.33-66.77]]
list_of_75ths = [[86.34-49.22, 85.825-38.22, 69.085-37.33, 65.645-34.5], [99.49-92.06,99.48-91.38,98.01-86.99,97.66-79.33]]
list_of_network_names = ["Frac. of Runtime", "Frac of num invocations"]
do_clustered_bar_plotting(list_of_time_delta_names, list_of_medians, list_of_25ths, list_of_75ths, list_of_network_names, output_file_name, y_axis_name="% memoizable. err bar=25th,95th", label_size=14, x_axis_name="$\delta$",colors=["#e41a1c", "#377eb8", "#000000"], legend_num_cols=2)