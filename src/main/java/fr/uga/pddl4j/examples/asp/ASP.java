package fr.uga.pddl4j.examples.asp;

import fr.uga.pddl4j.heuristics.state.StateHeuristic;
import fr.uga.pddl4j.parser.DefaultParsedProblem;
import fr.uga.pddl4j.plan.Plan;
import fr.uga.pddl4j.plan.SequentialPlan;
import fr.uga.pddl4j.planners.AbstractPlanner;
import fr.uga.pddl4j.planners.LogLevel;
import fr.uga.pddl4j.planners.Planner;
import fr.uga.pddl4j.planners.PlannerConfiguration;
import fr.uga.pddl4j.planners.SearchStrategy;
import fr.uga.pddl4j.planners.statespace.search.StateSpaceSearch;
import fr.uga.pddl4j.problem.DefaultProblem;
import fr.uga.pddl4j.problem.Goal;
import fr.uga.pddl4j.problem.InitialState;
import fr.uga.pddl4j.problem.Problem;
import fr.uga.pddl4j.problem.State;
import fr.uga.pddl4j.problem.operator.Action;
import fr.uga.pddl4j.problem.operator.Condition;
import fr.uga.pddl4j.problem.operator.ConditionalEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.HashMap;

import fr.uga.pddl4j.examples.asp.Node;

/**
 * The class is an example. It shows how to create a simple A* search planner
 * able to solve an ADL problem by choosing the heuristic to used and its
 * weight.
 *
 * @author Mousse
 * @version 4.0 - 17/10/2022
 */
@CommandLine.Command(name = "ASP", version = "ASP 1.0", description = "Solves a specified planning problem using A* search strategy.", sortOptions = false, mixinStandardHelpOptions = true, headerHeading = "Usage:%n", synopsisHeading = "%n", descriptionHeading = "%nDescription:%n%n", parameterListHeading = "%nParameters:%n", optionListHeading = "%nOptions:%n")

public class ASP extends AbstractPlanner {

	/**
	 * The HEURISTIC property used for planner configuration.
	 */
	public static final String HEURISTIC_SETTING = "HEURISTIC";

	/**
	 * The default value of the HEURISTIC property used for planner configuration.
	 */
	public static final StateHeuristic.Name DEFAULT_HEURISTIC = StateHeuristic.Name.FAST_FORWARD;

	/**
	 * The WEIGHT_HEURISTIC property used for planner configuration.
	 */
	public static final String WEIGHT_HEURISTIC_SETTING = "WEIGHT_HEURISTIC";

	/**
	 * The default value of the WEIGHT_HEURISTIC property used for planner
	 * configuration.
	 */
	public static final double DEFAULT_WEIGHT_HEURISTIC = 1.0;

	/**
	 * MDA: Monte-Carlo Deadlock Avoidance tracking
	 */
	private final Map<Integer, Integer> successfulWalks = new HashMap<>(); // S(a)
	private final Map<Integer, Integer> failedWalks = new HashMap<>(); // F(a)

	/**
	 * MHA: Monte-Carlo with Helpful Actions tracking
	 */
	private final Map<Integer, Integer> helpfulActionCounts = new HashMap<>(); // Q(a) for MHA

	/**
	 * Statistics for deciding when to use MDA/MHA
	 */
	private int totalRandomWalks = 0;
	private int deadEndWalks = 0;
	private double totalBranchingFactor = 0.0;
	private int branchingFactorSamples = 0;

	/**
	 * The weight of the heuristic.
	 */
	private double heuristicWeight;

	private StateHeuristic.Name heuristic;

	/**
	 * The name of the heuristic used by the planner.
	 */

	/**
	 * Creates a new A* search planner with the default configuration.
	 */
	public ASP() {
		this(ASP.getDefaultConfiguration());
	}

	/**
	 * Creates a new A* search planner with a specified configuration.
	 *
	 * @param configuration the configuration of the planner.
	 */
	public ASP(final PlannerConfiguration configuration) {
		super();
		this.setConfiguration(configuration);
	}

	/**
	 * The class logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger(ASP.class.getName());

	/**
	 * Instantiates the planning problem from a parsed problem.
	 *
	 * @param problem the problem to instantiate.
	 * @return the instantiated planning problem or null if the problem cannot be
	 *         instantiated.
	 */
	@Override
	public Problem instantiate(DefaultParsedProblem problem) {
		final Problem pb = new DefaultProblem(problem);
		pb.instantiate();
		return pb;
	}

	/**
	 * Search a solution plan to a specified domain and problem using A*.
	 *
	 * @param problem the problem to solve.
	 * @return the plan found or null if no plan was found.
	 */
	@Override
	public Plan solve(final Problem problem) {
		LOGGER.info("* Starting A* search \n");
		// Search a solution
		final long begin = System.currentTimeMillis();
		// Keep "astar" to solve using astar algoritm or replace "astar"
		// with "MCTS" to solve using Monte Carlo algorithm
		final Plan plan = this.MCTS(problem);
		final long end = System.currentTimeMillis();
		// If a plan is found update the statistics of the planner
		// and log search information
		if (plan != null) {
			LOGGER.info("* A* search succeeded\n");
			this.getStatistics().setTimeToSearch(end - begin);
		} else {
			LOGGER.info("* A* search failed\n");
		}
		// Return the plan found or null if the search fails.
		return plan;
	}

	/**
	 * Sets the weight of the heuristic.
	 *
	 * @param weight the weight of the heuristic. The weight must be greater than 0.
	 * @throws IllegalArgumentException if the weight is strictly less than 0.
	 */
	@CommandLine.Option(names = { "-w",
			"--weight" }, defaultValue = "1.0", paramLabel = "<weight>", description = "Set the weight of the heuristic (preset 1.0).")
	public void setHeuristicWeight(final double weight) {
		if (weight <= 0) {
			throw new IllegalArgumentException("Weight <= 0");
		}
		this.heuristicWeight = weight;
	}

	/**
	 * Set the name of heuristic used by the planner to the solve a planning
	 * problem.
	 *
	 * @param heuristic the name of the heuristic.
	 */
	@CommandLine.Option(names = { "-e",
			"--heuristic" }, defaultValue = "FAST_FORWARD", description = "Set the heuristic : AJUSTED_SUM, AJUSTED_SUM2, AJUSTED_SUM2M, COMBO, "
					+ "MAX, FAST_FORWARD SET_LEVEL, SUM, SUM_MUTEX (preset: FAST_FORWARD)")
	public void setHeuristic(StateHeuristic.Name heuristic) {
		this.heuristic = heuristic;
	}

	/**
	 * Returns the name of the heuristic used by the planner to solve a planning
	 * problem.
	 *
	 * @return the name of the heuristic used by the planner to solve a planning
	 *         problem.
	 */
	public final StateHeuristic.Name getHeuristic() {
		return this.heuristic;
	}

	/**
	 * Returns the weight of the heuristic.
	 *
	 * @return the weight of the heuristic.
	 */
	public final double getHeuristicWeight() {
		return this.heuristicWeight;
	}

	/**
	 * Returns the configuration of the planner.
	 *
	 * @return the configuration of the planner.
	 */
	@Override
	public PlannerConfiguration getConfiguration() {
		final PlannerConfiguration config = super.getConfiguration();
		config.setProperty(ASP.HEURISTIC_SETTING, this.getHeuristic().toString());
		config.setProperty(ASP.WEIGHT_HEURISTIC_SETTING, Double.toString(this.getHeuristicWeight()));
		return config;
	}

	/**
	 * Sets the configuration of the planner. If a planner setting is not defined in
	 * the specified configuration, the setting is initialized with its default
	 * value.
	 *
	 * @param configuration the configuration to set.
	 */
	@Override
	public void setConfiguration(final PlannerConfiguration configuration) {
		super.setConfiguration(configuration);
		if (configuration.getProperty(ASP.WEIGHT_HEURISTIC_SETTING) == null) {
			this.setHeuristicWeight(ASP.DEFAULT_WEIGHT_HEURISTIC);
		} else {
			this.setHeuristicWeight(Double.parseDouble(configuration.getProperty(ASP.WEIGHT_HEURISTIC_SETTING)));
		}
		if (configuration.getProperty(ASP.HEURISTIC_SETTING) == null) {
			this.setHeuristic(ASP.DEFAULT_HEURISTIC);
		} else {
			this.setHeuristic(StateHeuristic.Name.valueOf(configuration.getProperty(ASP.HEURISTIC_SETTING)));
		}
	}

	/**
	 * This method return the default arguments of the planner.
	 *
	 * @return the default arguments of the planner.
	 * @see PlannerConfiguration
	 */
	public static PlannerConfiguration getDefaultConfiguration() {
		PlannerConfiguration config = Planner.getDefaultConfiguration();
		config.setProperty(ASP.HEURISTIC_SETTING, ASP.DEFAULT_HEURISTIC.toString());
		config.setProperty(ASP.WEIGHT_HEURISTIC_SETTING, Double.toString(ASP.DEFAULT_WEIGHT_HEURISTIC));
		return config;
	}

	/**
	 * Checks the planner configuration and returns if the configuration is valid. A
	 * configuration is valid if (1) the domain and the problem files exist and can
	 * be read, (2) the timeout is greater than 0, (3) the weight of the heuristic
	 * is greater than 0 and (4) the heuristic is a not null.
	 *
	 * @return <code>true</code> if the configuration is valid <code>false</code>
	 *         otherwise.
	 */
	public boolean hasValidConfiguration() {
		return super.hasValidConfiguration() && this.getHeuristicWeight() > 0.0 && this.getHeuristic() != null;
	}

	/**
	 * The main method of the <code>ASP</code> planner.
	 *
	 * @param args the arguments of the command line.
	 */

	////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////// ASTAR IMPLEMENTATION////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////// 
	////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Search a solution plan for a planning problem using an A* search strategy.
	 *
	 * @param problem the problem to solve.
	 * @return a plan solution for the problem or null if there is no solution
	 */
	public Plan astar(Problem problem) {

		// First we create an instance of the heuristic to use to guide the search
		final StateHeuristic heuristic = StateHeuristic.getInstance(this.getHeuristic(), problem);

		// We get the initial state from the planning problem
		final State init = new State(problem.getInitialState());

		// We initialize the closed list of nodes (store the nodes explored)
		final Set<Node> close = new HashSet<>();

		// We initialize the opened list to store the pending node according to function
		// f
		final double weight = this.getHeuristicWeight();
		final PriorityQueue<Node> open = new PriorityQueue<>(100, new Comparator<Node>() {
			public int compare(Node n1, Node n2) {
				double f1 = weight * n1.getHeuristic() + n1.getCost();
				double f2 = weight * n2.getHeuristic() + n2.getCost();
				return Double.compare(f1, f2);
			}
		});

		// We create the root node of the tree search
		final Node root = new Node(init, null, -1, 0, heuristic.estimate(init, problem.getGoal()));

		// We add the root to the list of pending nodes
		open.add(root);
		Plan plan = null;

		// We set the timeout in ms allocated to the search
		final int timeout = this.getTimeout() * 1000;
		long time = 0;

		// We start the search
		while (!open.isEmpty() && plan == null && time < timeout) {

			// We pop the first node in the pending list open
			final Node current = open.poll();
			close.add(current);

			// If the goal is satisfied in the current node then extract the search and
			// return it
			if (current.satisfy(problem.getGoal())) {
				return this.extractPlan(current, problem);
			} else { // Else we try to apply the actions of the problem to the current node
				for (int i = 0; i < problem.getActions().size(); i++) {
					// We get the actions of the problem
					Action a = problem.getActions().get(i);
					// If the action is applicable in the current node
					if (a.isApplicable(current)) {
						Node next = new Node(current);
						// We apply the effect of the action
						final List<ConditionalEffect> effects = a.getConditionalEffects();
						for (ConditionalEffect ce : effects) {
							if (current.satisfy(ce.getCondition())) {
								next.apply(ce.getEffect());
							}
						}
						// We set the new child node information
						final double g = current.getCost() + 1;
						if (!close.contains(next)) {
							next.setCost(g);
							next.setParent(current);
							next.setAction(i);
							next.setHeuristic(heuristic.estimate(next, problem.getGoal()));
							open.add(next);
						}
					}
				}
			}
		}

		// Finally, we return the search computed or null if no search was found
		return plan;
	}
	////////////////////////////////////////////////////////////////////////////////////////

	////////////////////////////////////////////////////////////////////////////////////////
	/////////////// MONTE CARLO TREE SEARCH IMPLEMENTATION//////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////// 
	////////////////////////////////////////////////////////////////////////////////////////

	private final int MAX_STEPS = 10;
	private final double MDA_THRESHOLD = 0.3; // 30% dead-end threshold
	private final double MHA_THRESHOLD = 100.0; // average branching factor threshold
	private final int MIN_SAMPLES = 50;

	// Performance optimization: cache action indices
	private final Map<Action, Integer> actionIndexCache = new HashMap<>();

	public Plan MCTS(Problem problem) {
		// Initialize the heuristic
		StateHeuristic heuristic = StateHeuristic.getInstance(getHeuristic(), problem);

		// Get the initial state of the problem
		State initialState = new State(problem.getInitialState());

		// Create the goal condition
		final Goal goal = new Goal(problem.getGoal());

		// Initialize the node associated with the initial state
		Node currentNode = new Node(initialState, null, -1, 0, 0, heuristic.estimate(initialState, problem.getGoal()));

		// Get the heuristic value of the current node
		double minHeuristic = currentNode.getHeuristic();

		// Initialize the counter
		int counter = 0;

		// Keep iterating until the current node satisfies the goal condition
		while (!currentNode.satisfy(goal)) {
			// If the counter exceeds the maximum number of iterations or the current node
			// is a dead end, reset the current node to the initial state and reset the
			// counter
			if (counter > MAX_STEPS || DeadEnd(currentNode, problem)) {
				currentNode = new Node(initialState, null, -1, 0, 0,
						heuristic.estimate(initialState, problem.getGoal()));
				counter = 0;
			}

			// Find the best node from the current node based on the enhanced Monte Carlo
			// Tree Search algorithm
			currentNode = findBestNodeEnhanced(currentNode, problem, heuristic);
			// If the heuristic value of the current node is less than the current minimum
			// heuristic value, update the minimum heuristic value and reset the counter
			if (currentNode.getHeuristic() < minHeuristic) {
				minHeuristic = currentNode.getHeuristic();
				counter = 0;
			} else {
				// If the heuristic value of the current node is not less than the current
				// minimum heuristic value, increment the counter
				counter++;
			}
		}

		// Extract and return the plan from the current node
		return extractPlan(currentNode, problem);
	}

	public Node findBestNodeEnhanced(Node currentNode, Problem problem, StateHeuristic heuristic) {
		// Number of iterations to perform for the Monte Carlo random walk
		final int NUM_WALK = 2000;
		// Length of each random walk
		final int LENGTH_WALK = 10;

		Node minNode = null;
		// Initialize minimum heuristic value to a very high number
		double minHeuristic = Double.MAX_VALUE;

		// Check if we should use MDA or MHA
		boolean useMDA = shouldUseMDA();
		boolean useMHA = shouldUseMHA();

		// Perform the Monte Carlo random walk numIterations times
		for (int i = 0; i < NUM_WALK; i++) {
			totalRandomWalks++;

			// Track actions used in this walk for MDA
			Set<Integer> actionsInWalk = new HashSet<>();
			boolean walkHitDeadEnd = false;

			// Start the random walk at the current node
			Node testNode = currentNode;

			// Perform the random walk for walkLength steps
			for (int j = 0; j < LENGTH_WALK; j++) {
				// Get a list of applicable actions for the current state
				List<Action> applicableActions = getActions(testNode, problem);

				// Update branching factor statistics
				totalBranchingFactor += applicableActions.size();
				branchingFactorSamples++;

				// Check for dead-end
				if (applicableActions.isEmpty()) {
					walkHitDeadEnd = true;
					deadEndWalks++;
					break;
				}

				// Select action based on strategy
				Action selectedAction;
				int actionIndex;

				if (useMDA) {
					// Use MDA strategy - select action with best Q(a) score
					selectedAction = selectActionMDA(applicableActions, problem);
					actionIndex = problem.getActions().indexOf(selectedAction);
				} else if (useMHA) {
					// Use MHA strategy - prefer helpful actions
					selectedAction = selectActionMHA(applicableActions, problem, testNode, heuristic);
					actionIndex = problem.getActions().indexOf(selectedAction);
				} else {
					// Use pure random selection
					Collections.shuffle(applicableActions);
					selectedAction = applicableActions.get(0);
					actionIndex = problem.getActions().indexOf(selectedAction);
				}

				// Track action for MDA
				actionsInWalk.add(actionIndex);

				// Get the list of conditional effects for the selected action
				final List<ConditionalEffect> effects = selectedAction.getConditionalEffects();
				// Create a new state based on the current node
				State newState = new State(testNode);
				// Apply the effects of the selected action to the new state
				newState.apply(effects);
				// Create a new child node based on the new state and the current node
				Node childNode = new Node(newState, testNode, actionIndex,
						testNode.getCost() + 1, testNode.getDepth() + 1, 0);
				// Set the heuristic value for the child node
				childNode.setHeuristic(heuristic.estimate(childNode, problem.getGoal()));
				// Set the current node to the child node for the next iteration of the inner loop
				testNode = childNode;

				// If the current node satisfies the goal, update MDA statistics and return
				if (testNode.satisfy(problem.getGoal())) {
					updateMDAStatistics(actionsInWalk, false); // successful walk
					updateMHAStatistics(testNode, problem, heuristic); // update helpful actions
					return testNode;
				}
			}

			// Update MDA statistics for this walk
			updateMDAStatistics(actionsInWalk, walkHitDeadEnd);

			// Update MHA statistics if we have a valid endpoint
			if (!walkHitDeadEnd) {
				updateMHAStatistics(testNode, problem, heuristic);
			}

			// If the heuristic value of the current node is less than the current minimum
			// heuristic value, update the minimum node and heuristic value
			if (testNode.getHeuristic() < minHeuristic) {
				minNode = testNode;
				minHeuristic = testNode.getHeuristic();
			}
		}

		// If no node was found that satisfies the goal, return the node with the
		// minimum heuristic value. If no such node was found, return the current node.
		if (minNode == null) {
			return currentNode;
		}
		return minNode;
	}

	/**
	 * Determines if the given node is a dead end in the given problem.
	 *
	 * A node is considered a dead end if there are no applicable actions for the
	 * current state.
	 *
	 * @param node    the node to check
	 * @param problem the problem the node belongs to
	 * @return true if the node is a dead end, false otherwise
	 */
	public boolean DeadEnd(Node node, Problem problem) {
		// Get a list of applicable actions for the current state
		List<Action> applicableActions = getActions(node, problem);
		// Return true if the list of applicable actions is empty (there are no
		// actions that can be taken from the current state)
		return applicableActions.isEmpty();
	}

	/**
	 * Gets a list of actions that are applicable for the given node in the given
	 * problem.
	 *
	 * @param node    the node to check
	 * @param problem the problem the node belongs to
	 * @return a list of applicable actions
	 */
	public static List<Action> getActions(Node node, Problem problem) {
		// Initialize an empty list of applicable actions
		List<Action> applicableActions = new ArrayList<>();
		// Iterate through all actions in the problem
		for (Action action : problem.getActions()) {
			// If the action is applicable for the given node, add it to the list of
			// applicable actions
			if (action.isApplicable(node)) {
				applicableActions.add(action);
			}
		}
		return applicableActions;
	}

	/**
	 * Check if MDA should be used (more than 50% of walks hit dead-ends)
	 */
	private boolean shouldUseMDA() {
		if (totalRandomWalks < 100)
			return false;
		return (double) deadEndWalks / totalRandomWalks > MDA_THRESHOLD;
	}

	/**
	 * Check if MHA should be used (average branching factor > 1000)
	 */
	private boolean shouldUseMHA() {
		if (branchingFactorSamples < 100)
			return false; // Need some samples first
		return totalBranchingFactor / branchingFactorSamples > MHA_THRESHOLD;
	}

	/**
	 * Select action using MDA strategy
	 */
	private Action selectActionMDA(List<Action> applicableActions, Problem problem) {
		Action bestAction = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (Action action : applicableActions) {
			int actionIndex = problem.getActions().indexOf(action);
			double score = getMDAScore(actionIndex);

			if (score > bestScore) {
				bestScore = score;
				bestAction = action;
			}
		}

		// If all actions have the same score, select randomly
		if (bestAction == null) {
			Collections.shuffle(applicableActions);
			bestAction = applicableActions.get(0);
		}

		return bestAction;
	}

	/**
	 * Select action using MHA strategy
	 */
	private Action selectActionMHA(List<Action> applicableActions, Problem problem, Node node,
			StateHeuristic heuristic) {
		List<Action> helpfulActions = new ArrayList<>();

		for (Action action : applicableActions) {
			int actionIndex = problem.getActions().indexOf(action);
			if (helpfulActionCounts.getOrDefault(actionIndex, 0) > 0) {
				helpfulActions.add(action);
			}
		}

		// If we have helpful actions, select the most helpful one
		if (!helpfulActions.isEmpty()) {
			Action bestAction = null;
			int maxHelpfulCount = 0;

			for (Action action : helpfulActions) {
				int actionIndex = problem.getActions().indexOf(action);
				int count = helpfulActionCounts.getOrDefault(actionIndex, 0);
				if (count > maxHelpfulCount) {
					maxHelpfulCount = count;
					bestAction = action;
				}
			}

			if (bestAction != null) {
				return bestAction;
			}
		}

		// Fall back to random selection
		Collections.shuffle(applicableActions);
		return applicableActions.get(0);
	}

	/**
	 * Calculate MDA score for an action: Q(a) = -F(a)/(S(a) + F(a))
	 */
	private double getMDAScore(int actionIndex) {
		int successful = successfulWalks.getOrDefault(actionIndex, 0);
		int failed = failedWalks.getOrDefault(actionIndex, 0);

		if (successful + failed == 0) {
			return 0.0;
		}

		return -(double) failed / (successful + failed);
	}

	/**
	 * Update MDA statistics after a walk
	 */
	private void updateMDAStatistics(Set<Integer> actionsInWalk, boolean walkFailed) {
		for (int actionIndex : actionsInWalk) {
			if (walkFailed) {
				failedWalks.put(actionIndex, failedWalks.getOrDefault(actionIndex, 0) + 1);
			} else {
				successfulWalks.put(actionIndex, successfulWalks.getOrDefault(actionIndex, 0) + 1);
			}
		}
	}

	/**
	 * Update MHA statistics - identify helpful actions at endpoint
	 */
	private void updateMHAStatistics(Node endpoint, Problem problem, StateHeuristic heuristic) {

		List<Action> applicableActions = getActions(endpoint, problem);

		for (Action action : applicableActions) {
			State newState = new State(endpoint);
			final List<ConditionalEffect> effects = action.getConditionalEffects();
			for (ConditionalEffect ce : effects) {
				if (endpoint.satisfy(ce.getCondition())) {
					newState.apply(ce.getEffect());
				}
			}

			double newHeuristic = heuristic.estimate(newState, problem.getGoal());
			if (newHeuristic < endpoint.getHeuristic()) {
				int actionIndex = problem.getActions().indexOf(action);
				helpfulActionCounts.put(actionIndex, helpfulActionCounts.getOrDefault(actionIndex, 0) + 1);
			}
		}
	}

	///////////////////////////////////////////////////////////////////////

	/**
	 * Extracts a search from a specified node.
	 *
	 * @param node    the node.
	 * @param problem the problem.
	 * @return the search extracted from the specified node.
	 */
	private Plan extractPlan(final Node node, final Problem problem) {
		Node n = node;
		final Plan plan = new SequentialPlan();
		while (n.getAction() != -1) {
			final Action a = problem.getActions().get(n.getAction());
			plan.add(0, a);
			n = n.getParent();
		}
		return plan;
	}

	public static void main(String[] args) {
		try {
			final ASP planner = new ASP();
			CommandLine cmd = new CommandLine(planner);
			planner.setTimeout(1000);
			cmd.execute(args);
		} catch (IllegalArgumentException e) {
			LOGGER.fatal(e.getMessage());
		}
	}

}