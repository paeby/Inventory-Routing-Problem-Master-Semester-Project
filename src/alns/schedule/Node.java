package alns.schedule;

/**
 * Represents a node in the state probability tree.
 *
 * @author Markov
 * @version 1.0
 */
public class Node {

    // Node characteristics 
    private final String key;                   // node key
    private final int day;                      // node day in the planning horizon
    private final int state;                    // node state (no overflow or overflow)
    private int conditionNumber;                // node condition number (distance to closest previous node in state of overflow)
    private double nodeProbability;             // node probability (with respect to its parent)
    private double branchProbability = 0.d;     // branch probability (with respect to the root)

    // Node children
    private Node lowerChild;    // lower child - always in a state of overflow
    private Node upperChild;    // upper child - always in a state of no overflow

    /**
     * Assignment constructor.
     *
     * @param key node key
     * @param day node day in the planning horizon
     * @param state node state (no overflow or overflow)
     * @param conditionNumber node condition number (distance to closest node in
     * state of overflow)
     * @param nodeProbability node probability (with respect to its parent)
     */
    public Node(String key, int day, int state, int conditionNumber, double nodeProbability) {
        this.key = key;
        this.day = day;
        this.state = state;
        this.conditionNumber = conditionNumber;
        this.nodeProbability = nodeProbability;
    }

    /**
     * Returns node key.
     *
     * @return node key
     */
    public String GetKey() {
        return this.key;
    }

    /**
     * Returns node day.
     *
     * @return node day
     */
    public int GetDay() {
        return this.day;
    }

    /**
     * Returns node state (no overflow or overflow).
     *
     * @return node state (no overflow or overflow)
     */
    public int GetState() {
        return this.state;
    }

    /**
     * Returns node condition number (distance to closest node in state of
     * overflow).
     *
     * @return node condition number (distance to closest node in state of
     * overflow)
     */
    public int GetConditionNumber() {
        return this.conditionNumber;
    }

    /**
     * Sets node condition number (distance to closest node in state of
     * overflow).
     *
     * @param conditionNumber node condition number (distance to closest node in
     * state of overflow)
     */
    public void SetConditionNumber(int conditionNumber) {
        this.conditionNumber = conditionNumber;
    }

    /**
     * Returns node probability (with respect to its parent).
     *
     * @return node probability (with respect to its parent)
     */
    public double GetNodeProbability() {
        return this.nodeProbability;
    }

    /**
     * Sets node probability (with respect to its parent)
     *
     * @param nodeProbability node probability (with respect to its parent)
     */
    public void SetNodeProbability(double nodeProbability) {
        this.nodeProbability = nodeProbability;
    }

    /**
     * Returns branch probability (with respect to root).
     *
     * @return branch probability (with respect to root)
     */
    public double GetBranchProbability() {
        return this.branchProbability;
    }

    /**
     * Sets branch probability (with respect to root).
     *
     * @param branchProbability branch probability (with respect to root)
     */
    public void SetBranchProbability(double branchProbability) {
        this.branchProbability = branchProbability;
    }

    /**
     * Returns the lower child, which is always in a state of overflow.
     *
     * @return the lower child, which is always in a state of overflow
     */
    public Node GetLowerChild() {
        return this.lowerChild;
    }

    /**
     * Sets the lower child, which is always in a state of overflow.
     *
     * @param lowerChild lower child node
     */
    public void SetLowerChild(Node lowerChild) {
        this.lowerChild = lowerChild;
    }

    /**
     * Returns the upper child, which is always in a state of no overflow.
     *
     * @return the upper child, which is always in a state of no overflow
     */
    public Node GetUpperChild() {
        return this.upperChild;
    }

    /**
     * Sets the upper child, which is always in a state of no overflow.
     *
     * @param upperChild upper child node
     */
    public void SetUpperChild(Node upperChild) {
        this.upperChild = upperChild;
    }
}
