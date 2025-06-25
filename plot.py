import matplotlib.pyplot as plt

merged_results = {
    'blocks': {
        'pblocks1': {'mcts_opt_steps': 10, 'mcts_opt_time': 0.16, 'mcts_prw_steps': 6, 'mcts_prw_time': 0.15, 'astar_steps': 6, 'astar_time': 0.13},
        'pblocks2': {'mcts_opt_steps': 6, 'mcts_opt_time': 0.14, 'mcts_prw_steps': 10, 'mcts_prw_time': 0.17, 'astar_steps': 6, 'astar_time': 0.14},
        'pblocks3': {'mcts_opt_steps': 14, 'mcts_opt_time': 0.57, 'mcts_prw_steps': 12, 'mcts_prw_time': 0.50, 'astar_steps': 10, 'astar_time': 0.19},
    },
    'depot': {
        'pdepot1': {'mcts_opt_steps': 19, 'mcts_opt_time': 0.82, 'mcts_prw_steps': 19, 'mcts_prw_time': 0.65, 'astar_steps': 10, 'astar_time': 0.2},
        'pdepot2': {'mcts_opt_steps': 26, 'mcts_opt_time': 2.80, 'mcts_prw_steps': 24, 'mcts_prw_time': 2.09, 'astar_steps': 15, 'astar_time': 0.49},
        'pdepot3': {'mcts_opt_steps': 44, 'mcts_opt_time': 9.98, 'mcts_prw_steps': 66, 'mcts_prw_time': 11.00, 'astar_steps': 29, 'astar_time': 6.42},
    },
    'gripper': {
        'pgripper1': {'mcts_opt_steps': 17, 'mcts_opt_time': 0.52, 'mcts_prw_steps': 20, 'mcts_prw_time': 0.47, 'astar_steps': 9, 'astar_time': 0.81},
        'pgripper2': {'mcts_opt_steps': 35, 'mcts_opt_time': 5.16, 'mcts_prw_steps': 49, 'mcts_prw_time': 1.97, 'astar_steps': 0, 'astar_time': 0.0},
        'pgripper3': {'mcts_opt_steps': 68, 'mcts_opt_time': 44.23, 'mcts_prw_steps': 90, 'mcts_prw_time': 8.41, 'astar_steps': 0, 'astar_time': 0.0},
    },
    'logistics': {
        'plogistics1': {'mcts_opt_steps': 32, 'mcts_opt_time': 1.90, 'mcts_prw_steps': 39, 'mcts_prw_time': 1.53, 'astar_steps': 20, 'astar_time': 0.37},
        'plogistics2': {'mcts_opt_steps': 28, 'mcts_opt_time': 1.73, 'mcts_prw_steps': 25, 'mcts_prw_time': 1.61, 'astar_steps': 15, 'astar_time': 0.36},
        'plogistics3': {'mcts_opt_steps': 31, 'mcts_opt_time': 1.87, 'mcts_prw_steps': 21, 'mcts_prw_time': 1.41, 'astar_steps': 17, 'astar_time': 0.83},
    }
}

def prepare_barplot_data(domain):
    problems = sorted(merged_results[domain].keys())
    labels = [f"prob {i+1}" for i in range(len(problems))]

    def get(method, metric):
        return [merged_results[domain][p].get(f"{method}_{metric}", 0) for p in problems]

    return {
        "labels": labels,
        "mcts_opt_time": get("mcts_opt", "time"),
        "mcts_prw_time": get("mcts_prw", "time"),
        "astar_time": get("astar", "time"),
        "mcts_opt_steps": get("mcts_opt", "steps"),
        "mcts_prw_steps": get("mcts_prw", "steps"),
        "astar_steps": get("astar", "steps")
    }

def plot_all_domains():
    domains = ["blocks", "depot", "gripper", "logistics"]
    fig, axes = plt.subplots(4, 2, figsize=(18, 20))
    bar_width = 0.25

    for i, domain in enumerate(domains):
        data = prepare_barplot_data(domain)
        x = range(len(data["labels"]))
        offset1 = [xi - bar_width for xi in x]
        offset2 = x
        offset3 = [xi + bar_width for xi in x]

        #Runtime
        ax_time = axes[i][0]
        ax_time.bar(offset1, data["mcts_opt_time"], width=bar_width, label='MCTS (MDA & MHA)')
        ax_time.bar(offset2, data["astar_time"], width=bar_width, label='A*')
        ax_time.bar(offset3, data["mcts_prw_time"], width=bar_width, label='MCTS (PRW)')
        ax_time.set_title(f'Runtime - {domain.capitalize()}', fontsize=14, fontweight='bold')
        ax_time.set_xticks(x)
        ax_time.set_xticklabels(data["labels"])
        ax_time.set_ylabel("Time (s)")
        ax_time.legend()

        #steps
        ax_steps = axes[i][1]
        ax_steps.bar(offset1, data["mcts_opt_steps"], width=bar_width, label='MCTS (MDA & MHA)')
        ax_steps.bar(offset2, data["astar_steps"], width=bar_width, label='A*')
        ax_steps.bar(offset3, data["mcts_prw_steps"], width=bar_width, label='MCTS (PRW)')
        ax_steps.set_title(f'Plan Steps - {domain.capitalize()}', fontsize=14, fontweight='bold')
        ax_steps.set_xticks(x)
        ax_steps.set_xticklabels(data["labels"])
        ax_steps.set_ylabel("Steps")
        ax_steps.legend()

    plt.tight_layout()
    plt.savefig("comparison_plots.png", dpi=300)
    plt.show()

plot_all_domains()
