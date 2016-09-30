package alns.schedule;

import alns.data.Point;
import alns.param.Parameters;

/**
 * Represents the state probability tree, and extends getter methods and methods
 * for updating the tree.
 *
 * @author Markov
 * @version 2.0
 */
public class Tree {

    // The container associated with this tree
    private final Point container;

    // Planning horizon length
    private final int phLength;

    // The container visits 
    private final boolean[] visits;

    // Root node
    private final Node root;

    // Node references of the top node for each day in the planning horizon
    private final Node[] topsOnDays;
    // Probability of overflow on each day of the planning horizon
    private final double[] overflowProbabilities;

    /**
     * Assignment and initialization constructor.
     *
     * @param container container associated with this tree
     * @param phLength planning horizon length
     * @param visits sequence of visits to the container for the days of the
     * planning horizon
     */
    public Tree(Point container, int phLength, boolean[] visits) {

        // Assign container 
        this.container = container;

        // Assign planning horizon length
        this.phLength = phLength;

        // Assign visits
        this.visits = visits;

        // Initialize overflowProbabilities from 0 to phLength + 1 as an array of 0s
        this.overflowProbabilities = new double[this.phLength + 1];

        // Initialize the root node depending on initial container volume load
        if (this.container.GetInitVolumeLoad() >= this.container.GetEffectiveVolume()) {
            this.root = new Node("s01", 0, Parameters.hStateOverflow, 0, 1.d);
            this.overflowProbabilities[0] = 1.d;
        } else {
            this.root = new Node("s00", 0, Parameters.hStateNoOverflow, 0, 1.d);
            this.overflowProbabilities[0] = 0.d;
        }
        // Set the branch probability of the root node to 1
        this.root.SetBranchProbability(1.d);

        // Initialize the array of the top node for each day in the planning horizon
        this.topsOnDays = new Node[this.phLength + 1];
        // Add the root node to day 0
        this.topsOnDays[0] = this.root;

        // Populate the tree and update the array of overflow probabilities
        this.populateTree(this.root);
        this.updateOverflowProbabilities(this.root, this.phLength);
    }

    /**
     * Adds the lower child to a parent. The lower child always has the state of
     * overflow.
     *
     * @param parent parent node
     * @return the lower child
     */
    private Node addLowerChild(Node parent) {

        // The child's day is the parent's day plus 1
        int day = parent.GetDay() + 1;
        // The lower child state is always overflow
        int state = Parameters.hStateOverflow;
        // The key is composed of the day and the state
        String key = "s" + day + state;

        // The condition number depends on the parent's state: If the latter is overflow
        // the condition number is 1, otherwise it is the parent's condition number plus 1
        int conditionNumber = parent.GetState() == Parameters.hStateOverflow ? 1 : parent.GetConditionNumber() + 1;
        // When the tree is initially built, there are no visits. The node probabilities are based 
        // on the day of the planning horizon and the condition number.
        double nodeProbability;
        if (day == 1) {
            // Probability on day 1 is unconditional and uses the starting inventory
            nodeProbability = this.container.GetUnconditionalStartInv();
        } else if (conditionNumber == 1) {
            // Probability after an emergency collection is unconditional and uses zero inventory
            nodeProbability = this.container.GetUnconditionalZeroInv(day);
        } else if (day - conditionNumber == 0) {
            // Probability of lower child nodes branching down from the uppermost branch of the tree
            // is conditional and uses the starting inventory
            nodeProbability = this.container.GetConditionalStartInv(day);
        } else {
            // The rest of the conditional probabilities depend on day and condition number
            nodeProbability = this.container.GetConditionalZeroInv(day, day - conditionNumber);
        }

        // Initialize the lower child
        Node lowerChild = new Node(key, day, state, conditionNumber, nodeProbability);
        // Set the lower child branch probability as the product of the parent's branch probability and
        // the lower child's node probability
        lowerChild.SetBranchProbability(parent.GetBranchProbability() * nodeProbability);
        // Set the lower child as the parent's lower child
        parent.SetLowerChild(lowerChild);

        // Return the lower child
        return lowerChild;
    }

    /**
     * Adds the upper child to a parent. The upper child always has the state of
     * no overflow.
     *
     * @param parent parent node
     * @return the upper child
     */
    private Node addUpperChild(Node parent) {

        // The child's day is the parent's day plus 1
        int day = parent.GetDay() + 1;
        // The upper child state is always no overflow
        int state = Parameters.hStateNoOverflow;
        // The key is composed of the day and the state
        String key = "s" + day + state;

        // The condition number depends on the parent's state: If the latter is overflow
        // the condition number is 1, otherwise it is the parent's condition number plus 1
        int conditionNumber = parent.GetState() == Parameters.hStateOverflow ? 1 : parent.GetConditionNumber() + 1;
        // The node probability of the upper child is one minus the node probability of the lower child,
        // which should already have been added
        double nodeProbability = 1 - parent.GetLowerChild().GetNodeProbability();

        // Initialize the upper child
        Node upperChild = new Node(key, day, state, conditionNumber, nodeProbability);
        // Set the upper child branch probability as the product of the parent's branch probability and
        // the upper child's node probability
        upperChild.SetBranchProbability(parent.GetBranchProbability() * nodeProbability);
        // Set the upper child as the parent's upper child
        parent.SetUpperChild(upperChild);
        // Add the upper child to the ArrayList of nodes for its day
        this.topsOnDays[upperChild.GetDay()] = upperChild;

        // Return the upper child
        return upperChild;
    }

    /**
     * Populates recursively the tree until phLength + 1.
     *
     * @param node node in the tree
     */
    private void populateTree(Node node) {
        if (node.GetDay() < this.phLength) {
            this.populateTree(this.addLowerChild(node));
            this.populateTree(this.addUpperChild(node));
        }
    }

    /**
     * Returns the overflow probability on the specified day.
     *
     * @param day day in the planning horizon
     * @return the overflow probability on the specified day
     */
    public double GetOverflowProbability(int day) {
        return this.overflowProbabilities[day];
    }

    /**
     * Updates the overflow probability on the day of the lower child of the
     * passed node.
     *
     * @param node node in the tree
     * @return the lower child of the passed node in the tree
     */
    private Node updateOverflowProbabilityLower(Node node) {

        // Lower child
        Node lowerChild = node.GetLowerChild();
        // Update overflow probability with branch probability of lower child
        this.overflowProbabilities[lowerChild.GetDay()] += lowerChild.GetBranchProbability();
        // Return lower child
        return lowerChild;
    }

    /**
     * Clears the array of overflow probabilities from startDay to endDay,
     * inclusive.
     *
     * @param startDay day in the planning horizon
     * @param endDay day in the planning horizon
     */
    private void clearOverflowProbabilities(int startDay, int endDay) {

        for (int d = startDay; d <= endDay; d++) {
            this.overflowProbabilities[d] = 0.d;
        }
    }

    /**
     * Updates the array of overflow probabilities until endDay, inclusive.
     *
     * @param node node in the tree
     * @param endDay day in the planning horizon
     */
    private void updateOverflowProbabilities(Node node, int endDay) {

        if (node.GetDay() < endDay) {
            updateOverflowProbabilities(this.updateOverflowProbabilityLower(node), endDay);
            updateOverflowProbabilities(node.GetUpperChild(), endDay);
        }
    }

    /**
     * Returns the tree's root node
     *
     * @return the tree's root node
     */
    public Node GetRoot() {
        return this.root;
    }

    /**
     * Updates the probability of the lower child of the passed node.
     *
     * @param parent node in the tree
     * @return the lower child of the passed node
     */
    private Node updateNodeAndBranchProbabilityLowerChild(Node parent) {

        // Lower child
        Node lowerChild = parent.GetLowerChild();
        // Lower child day
        int day = lowerChild.GetDay();

        // Update condition number
        int conditionNumber = parent.GetState() == Parameters.hStateOverflow ? 1 : parent.GetConditionNumber() + 1;
        lowerChild.SetConditionNumber(conditionNumber);

        // Calculate the node probability
        double nodeProbability;
        // There are two basic cases - if there is a visit on day one or not. In the first case, 
        // we need to consider initial inventory, in the second case not.
        if (this.visits[0] == false) {
            if (day == 1) {
                // Probability on day 1 is unconditional and uses the starting inventory
                nodeProbability = this.container.GetUnconditionalStartInv();
            } else if (conditionNumber == 1) {
                // Probability after an emergency collection is unconditional and uses zero inventory
                nodeProbability = this.container.GetUnconditionalZeroInv(day);
            } else if (day - conditionNumber == 0) {
                // Probability of lower child nodes branching down from the uppermost branch of the tree
                // is conditional and uses the starting inventory
                nodeProbability = this.container.GetConditionalStartInv(day);
            } else {
                // The rest of the conditional probabilities depend on day and condition number
                nodeProbability = this.container.GetConditionalZeroInv(day, day - conditionNumber);
            }
        } else {
            nodeProbability = conditionNumber == 1
                    ? this.container.GetUnconditionalZeroInv(day)
                    : this.container.GetConditionalZeroInv(day, day - conditionNumber);
        }
        lowerChild.SetNodeProbability(nodeProbability);
        // Update lower child branch probability
        lowerChild.SetBranchProbability(parent.GetBranchProbability() * nodeProbability);

        // Return lower child
        return lowerChild;
    }

    /**
     * Updates the probability of the upper child of the passed node.
     *
     * @param parent node in the tree
     * @return the upper child of the passed node
     */
    private Node updateNodeAndBranchProbabilityUpperChild(Node parent) {

        // Upper child
        Node upperChild = parent.GetUpperChild();

        // Update condition number
        int conditionNumber = parent.GetState() == Parameters.hStateOverflow ? 1 : parent.GetConditionNumber() + 1;
        upperChild.SetConditionNumber(conditionNumber);

        // Update upper child node probability to be one minus the lower child node probability,
        // which should already have been updated
        upperChild.SetNodeProbability(1 - parent.GetLowerChild().GetNodeProbability());
        // Update upper child branch probability
        upperChild.SetBranchProbability(parent.GetBranchProbability() * upperChild.GetNodeProbability());

        // Return upper child
        return upperChild;
    }

    /**
     * Updates the node and branch probabilities until endDay, inclusive.
     *
     * @param node node in the probability tree
     * @param endDay day in the planning horizon
     */
    private void updateNodeAndBranchProbabilities(Node node, int endDay) {

        if (node.GetDay() < endDay) {
            this.updateNodeAndBranchProbabilities(updateNodeAndBranchProbabilityLowerChild(node), endDay);
            this.updateNodeAndBranchProbabilities(updateNodeAndBranchProbabilityUpperChild(node), endDay);
        }
    }

    /**
     * Updates the tree and the overflow probabilities that are affected by a
     * visit that is added or removed on a particular day in the planning
     * horizon.
     *
     * @param day day in the planning horizon
     * @param inOut true if a visit was added, false if removed
     */
    public void UpdateTree(int day, boolean inOut) {

        // Previous visit day
        int previousVisitDay = 0;
        for (int d = day; d >= 0; d--) {
            if (d < day && this.visits[d] == true) {
                previousVisitDay = d;
                break;
            }
        }

        // End day of the updates
        int endDay = this.phLength;
        for (int d = day; d < this.phLength; d++) {
            if (d > day && this.visits[d] == true) {
                endDay = d;
                break;
            }
        }

        // Updates differ depending on whether a visit was added or removed
        if (inOut == true) {
            // If a visit is added, the probabilities thereafter only need to be calculated
            // on one of the trees starting on this day, and we choose the uppermost one
            Node node = this.topsOnDays[day];
            // Set the node's condition number to 0 and its branch probability to 1
            node.SetConditionNumber(0);
            node.SetBranchProbability(1.d);
            // Update the node and branch probabilities from the next day to the next visit inclusive
            this.updateNodeAndBranchProbabilities(node, endDay);
            // Clear and update the overflow probabilities from the next day to the next visit inclusive    
            this.clearOverflowProbabilities(day + 1, endDay);
            this.updateOverflowProbabilities(node, endDay);
        } else {
            // If a visit is removed, the procedure is the same, but we start from the most recent visit
            // or from the root node, and update until the next visit (inclusive) or the end of planning horizon
            Node node = this.topsOnDays[previousVisitDay];
            // Set the node's condition number to 0 and its branch probability to 1 as it may have been 
            // changed by updates prior to it already
            node.SetConditionNumber(0);
            node.SetBranchProbability(1.d);
            // Update the node and branch probabilities from the next day to the next visit inclusive
            this.updateNodeAndBranchProbabilities(node, endDay);
            // Clear and update the overflow probabilities from the next day to the next visit inclusive    
            this.clearOverflowProbabilities(previousVisitDay + 1, endDay);
            this.updateOverflowProbabilities(node, endDay);
        }
    }
}
