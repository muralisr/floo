

import os 
import numpy as np
import matplotlib.pyplot as plt
import matplotlib
from matplotlib import rc

import json
BUCKETS = 1000

def round3(x):
    return round(x, 2)

def fix_hist_step_vertical_line_at_end(ax):
    axpolygons = [
        poly
        for poly in ax.get_children()
        if isinstance(poly, matplotlib.patches.Polygon)
    ]
    for poly in axpolygons:
        poly.set_xy(poly.get_xy()[:-1])


def create_pct_diff_plot_2(
    series,
    labels,
    xlabel="% Improvement",
    ylabel="CDF",
    legend_size=6.5,
    save_path=None,
    location="lower right",
    log_scale=False,
    colors=None,
    curve_line_width=2,
    fig_width=5,
    fig_height=2.3,
    x_min=None,
    x_max=None,
    axes_font_size=None,
    tick_label_size=None,
    linestyles=None,
    hide_legend=False,
    rc_params_input=None,
    x_ticks=None
):
    # colors = ["#7fc97f", "#beaed4", "#fdc086", "#ffff99", "#386cb0", "#f0027f", "#bf5b17", "#666666"]
    # colors = [
    #     "#fdc086",
    #     "#beaed4",
    #     "#386cb0",
    #     "#f0027f",
    #     "#bf5b17",
    #     "#666666",
    #     "#fdc086",
    #     "#beaed4",
    #     "#386cb0",
    #     "#f0027f",
    #     "#bf5b17",

    # ]
    if colors is None:
        colors = [
            "#fdc086",
            "#beaed4",
            "#386cb0",
            "#f0027f",
            "#bf5b17",
            "#666666",
            "#fdc086",
            "#beaed4",
            "#386cb0",
            "#f0027f",
            "#bf5b17",
            "#fb9a99",
            "#a6cee3"
        ]
    

    matplotlib.rcParams.update(
    {
        'text.usetex': False,
        'font.family': 'stixgeneral',
        'mathtext.fontset': 'stix',
    }
    )
    if rc_params_input is not None:
        matplotlib.rcParams.update(rc_params_input)
    

    if linestyles is None:
        linestyles = ["-", "--", ":", "-."]
    # linestyles = ["-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-"]
    percentiles = {}
    fig, ax = plt.subplots(figsize=(fig_width, fig_height), dpi=1000)
    for i, (serie, label, color, linestyle) in enumerate(
        zip(series, labels, colors, linestyles)
    ):
        plt.hist(
            serie,
            BUCKETS,
            color=color,
            linestyle=linestyle,
            density=True,
            histtype="step",
            cumulative=True,
            label=label,
            linewidth=curve_line_width,
        )
        print(
            label,
            tuple(
                map(
                    round3,
                    (
                        np.percentile(serie, 5),
                        np.percentile(serie, 25),
                        np.percentile(serie, 50),
                        np.percentile(serie, 75),
                        np.percentile(serie, 95),
                    ),
                )
            ),
        )
        percentiles[label] = [
            np.percentile(serie, 5),
            np.percentile(serie, 25),
            np.percentile(serie, 50),
            np.percentile(serie, 75),
            np.percentile(serie, 80),
            np.percentile(serie, 85),
            np.percentile(serie, 90),
            np.percentile(serie, 95),
        ]
    if hide_legend is False:
        plt.legend(
            loc=location,
            prop={"size": legend_size},
            handlelength=0.7,
            handletextpad=0.5,
        )
    if axes_font_size is None:
        axes_font_size = legend_size
    if tick_label_size is None:
        tick_label_size = legend_size
    plt.tick_params(axis="both", which="major", labelsize=tick_label_size)
    plt.ylabel(ylabel, fontsize=axes_font_size)
    plt.xlabel(xlabel, fontsize=axes_font_size)
    if log_scale:
        plt.xscale("log")
    plt.tight_layout()
    plt.ylim(0, 1)
    if x_ticks is not None:
        print(f"setting xtics")
        ax.set_xticks(x_ticks)
    if not log_scale:
        # print(f'series is {series}')
        if x_min is None:
            plt.xlim(min(map(min, series)), max(map(max, series)))
        else:
            plt.xlim(x_min, x_max)

    fix_hist_step_vertical_line_at_end(ax)
    # matplotlib.rcParams.update({
    #     "figure.subplot.left":0.117,
    #     "figure.subplot.bottom":0.23, 
    #     "figure.subplot.right":0.96, 
    #     "figure.subplot.top":0.962, 
    #     "figure.subplot.wspace":0.2, 
    #     "figure.subplot.hspace":0.2
    # })
    if save_path:
        plt.savefig(save_path, dpi=1000)
    else:
        plt.show()
    return percentiles