
import os 
import numpy as np
import matplotlib.pyplot as plt
import matplotlib
from matplotlib import rc

import json
BUCKETS = 1000

def get_list_from_json_file(input_file):
    with open(input_file, "r") as f:
        return list(json.loads(f.read()).values())

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

def crop_pdf_file(input_file, percentage=5, ignore_bottom=False):
    if ignore_bottom is False:
        os.system(f"python /disk/ubuntu_data/projects/pdfCropMargins/bin/pdfCropMargins.py -p {percentage} {input_file} -o {input_file.replace('.pdf', '.cropped.pdf')}")
    else:
        os.system(f"python /disk/ubuntu_data/projects/pdfCropMargins/bin/pdfCropMargins.py -p4 {percentage} 100 {percentage} {percentage} {input_file} -o {input_file.replace('.pdf', '.cropped.pdf')}")


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


def do_clustered_bar_plotting(
    list_of_time_delta_names,
    list_of_medians,
    list_of_25ths,
    list_of_75ths,
    list_of_network_names,
    output_file_name,
    y_axis_name=None,
    x_axis_name=None,
    label_size=17,
    tick_label_size=None,
    fig_width=7,
    fig_height=6,
    colors=None,
    legend_size=None,
    use_patterns=True,
    legend_loc=None,
    legend_handle_size=None,
    legend_handletextpad=None,
    rc_params_input = None,
    legend_num_cols=None,
    hide_legend=False,
):

    if rc_params_input is None:

        matplotlib.rcParams.update(
        {
            "figure.subplot.left":0.125,
            "figure.subplot.bottom":0.195, 
            "figure.subplot.right":0.9, 
            "figure.subplot.top":1, 
            "figure.subplot.wspace":0.2, 
            "figure.subplot.hspace":0.2
        })
    else:
        matplotlib.rcParams.update(rc_params_input)
    
    fig, ax = plt.subplots(figsize=(fig_width, fig_height), dpi=300)

    # fig.SubplotParams(left=0.125, bottom=0.179, right=0.9, top=1, wspace=0.2, hspace=0.2)
    width = 0.15
    list_of_bars = []
    ind = np.arange(len(list_of_time_delta_names))
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
    
    linestyles = ["-.", "--"]
    hatches=["*", "\\", "\//", "x", "o", "O", ".", "*"]

    for i in range(len(list_of_network_names)):
        if list_of_25ths is None:
            p = ax.bar(
                ind + (width * i),
                list_of_medians[i],
                width,
                color=colors[i],
                edgecolor="black",
                linewidth=0.4
            )
        else:
            p = ax.bar(
                ind + (width * i),
                list_of_medians[i],
                width,
                yerr=[list_of_25ths[i], list_of_75ths[i]],
                color=colors[i],
                edgecolor="black",
                linewidth=0.4
            )
        if i > 0 and use_patterns:
            for p1 in p:
                p1.set_hatch(hatches[i])
        list_of_bars.append(p)
    # ax.set_title(
    #     "median % optimization achieved per interaction. error bars represent 25, 75th percentile"
    # )
    
    if y_axis_name is not None:
        # ax.set_ylabel(y_axis_name, {'fontsize': 18})
        plt.ylabel(y_axis_name, fontsize=label_size)
    if x_axis_name is not None:
        # ax.set_ylabel(x_axis_name, {'fontsize': 18})
        plt.xlabel(x_axis_name, fontsize=label_size)
    if tick_label_size is None:
        tick_label_size = label_size
    ax.tick_params(axis="both", which="major", labelsize=tick_label_size)
    ax.set_xticks(ind + width / 2)
    ax.set_xticklabels(list_of_time_delta_names)
    if legend_size is None:
        legend_size = label_size
    if legend_loc is None:
        legend_loc = "upper left"
    if legend_handle_size is None:
        legend_handle_size = 3
    if legend_handletextpad is None:
        legend_handletextpad = 0.5
    if legend_num_cols is None:
        legend_num_cols = 1
    if hide_legend==False:
        ax.legend([x[0] for x in list_of_bars], list_of_network_names, loc=legend_loc, handlelength=legend_handle_size,
            handletextpad=legend_handletextpad, borderpad=0.2, fontsize=legend_size, ncol=legend_num_cols)
  
    # plt.show()
    if output_file_name is None:
        plt.show()
    else:
        plt.savefig(output_file_name)


def do_scatter_plot(x_axis, y_axis, output_file_name, x_label = None, y_label = None, plt_title = "", axis_label_size = 12, fig_width=7, fig_height=6, color=None, tick_label_size=None):
    matplotlib.rcParams.update(
    {
        "figure.subplot.left":0.125,
        "figure.subplot.bottom":0.179, 
        "figure.subplot.right":0.9, 
        "figure.subplot.top":1, 
        "figure.subplot.wspace":0.2, 
        "figure.subplot.hspace":0.2
    })
    fig, ax = plt.subplots(figsize=(fig_width, fig_height), dpi=300)
    index = 0
    if color is None:
        color = "#000000"
    while index < len(x_axis):
        ax.scatter(x_axis[index], y_axis[index], color=color, s=14)
        index += 1 
    if tick_label_size is None:
        tick_label_size = axis_label_size
    plt.tick_params(axis="both", which="major", labelsize=tick_label_size)
    if x_label is not None:
        ax.set_xlabel(x_label, fontsize=axis_label_size)
    if y_label is not None:
        ax.set_ylabel(y_label, fontsize=axis_label_size)
    ax.set_title(
        plt_title
    )
    plt.savefig(output_file_name)


def do_bar_chart_plotting(
    list_of_time_delta_names,
    list_of_medians,
    list_of_25ths,
    list_of_75ths,
    output_file_name,
    y_axis_name=None,
    x_axis_name=None,
    label_size=17,
    tick_label_size=None,
    fig_width=7,
    fig_height=6,
    colors=None,
    legend_size=None,
    hide_legend=False,
    use_patterns=True
):

    # matplotlib.rcParams.update(
    # {
    #     "figure.subplot.left":0.125,
    #     "figure.subplot.bottom":0.195, 
    #     "figure.subplot.right":0.9, 
    #     "figure.subplot.top":1, 
    #     "figure.subplot.wspace":0.2, 
    #     "figure.subplot.hspace":0.2
    # })
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
    
    linestyles = ["-.", "--"]
    if use_patterns:
        hatches=["", "\\", "\\/", "--", "o", "O", ".", "*"]
    else:
        hatches=["", "", "", "", "", "", "", ""]

    fig, ax = plt.subplots(figsize=(fig_width, fig_height), dpi=300)
    location_of_bars = [x/4 for x in np.arange(len(list_of_time_delta_names))]

    p = ax.bar(location_of_bars, list_of_medians, yerr=[list_of_25ths, list_of_75ths], color=colors, edgecolor="black", linewidth=0.4, width=0.15, align='center')
    for bar, pattern in zip (p, hatches):
        bar.set_hatch(pattern)
    ax.tick_params(axis="both", which="major", labelsize=tick_label_size)
    ax.set_xticks(location_of_bars)
    ax.set_xticklabels(list_of_time_delta_names)
    # ax.yaxis.grid(True)

    
    if y_axis_name is not None:
        # ax.set_ylabel(y_axis_name, {'fontsize': 18})
        plt.ylabel(y_axis_name, fontsize=label_size)
    if x_axis_name is not None:
        # ax.set_ylabel(x_axis_name, {'fontsize': 18})
        plt.xlabel(x_axis_name, fontsize=label_size)
    if tick_label_size is None:
        tick_label_size = label_size
    
    if legend_size is None:
        legend_size = label_size
    if not hide_legend:
        ax.legend(p, list_of_time_delta_names, loc="upper right", handlelength=3,
            handletextpad=0.5, borderpad=0.1, fontsize=legend_size)
  
    # plt.show()
    plt.savefig(output_file_name)