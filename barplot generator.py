import matplotlib.pyplot as plt

def create_barplot(probs, MCTS, Astar, title):
  # Set the bar width
  bar_width = 0.4

  # Set the position of the bars on the x-axis
  r1 = [x for x in range(len(MCTS))]
  r2 = [x + bar_width for x in range(len(Astar))]

  # Create the barplot
  plt.bar(r1, MCTS, width=bar_width, label='MCTS')
  plt.bar(r2, Astar, width=bar_width, label='Astar')

  # Add the prob values as labels on the x-axis
  plt.xticks([x + bar_width/2 for x in range(len(probs))], probs)

  # Add a legend
  plt.legend()

  # Add a title
  plt.title(title, fontsize=16, fontweight='bold')

  # Show the plot
  plt.show()

# Create the first barplot
create_barplot(['prob1', 'prob2', 'prob3'], [0.33, 0.19, 0.86], [0.13, 0.14, 0.19], "Runtime for Blocksworld")

# Create the second barplot
create_barplot(['prob1', 'prob2', 'prob3'], [0.35, 3.7, 8.86], [0.2, 0.49, 6.42], "Runtime for Depot")
# Create the second barplot
create_barplot(['prob1', 'prob2', 'prob3'], [0.44, 0.19, 1.96], [0.81, 0, 0], "Runtime for Gripper")
# Create the second barplot
create_barplot(['prob1', 'prob2', 'prob3'], [0.58, 1.9, 1.87], [0.37, 0.36, 0.83], "Runtime for Logistics")
# Create the second barplot
create_barplot(['prob1', 'prob2', 'prob3'], [6, 6, 20], [6, 6, 10], "Plan length for Blocksworld")
# Create the second barplot
create_barplot(['prob1', 'prob2', 'prob3'], [14, 29, 49], [10, 15, 29], "Plan length for Depot")
# Create the second barplot
create_barplot(['prob1', 'prob2', 'prob3'], [16, 93, 185], [9, 0, 0], "Plan length for Gripper")
# Create the second barplot
create_barplot(['prob1', 'prob2', 'prob3'], [20, 26, 29], [20, 15, 17], "Plan length for Logistics")
