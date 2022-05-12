from helper import *


to_plot = {
    'Key': [34.47, 25.76, 39.25, 30.76, 26.27, 21.66, 29.35, 19.59, 27.41, 36.34, 16.33]
}

create_pct_diff_plot_2(
        list(to_plot.values()),
        to_plot.keys(),
        legend_size=14,
        save_path="/disk/Code/projects/soot-instrument/python_analysis_scripts/plotting/percent_of_runtime_that_can_be_memoized.png",
        xlabel="% of runtime that can be memoized per app",
        ylabel="CDF",
        location="lower right",
        log_scale=False,
        colors=["#e41a1c", "#377eb8", "#000000", "#4daf4a"],
        curve_line_width=2,
        fig_width=5,
        fig_height=2.5,
        x_min=0,
        axes_font_size=14,
        hide_legend=True,
)