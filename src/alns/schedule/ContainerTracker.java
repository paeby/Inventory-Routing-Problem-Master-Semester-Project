package alns.schedule;

import alns.data.Data;
import alns.data.Point;
import alns.param.Parameters;
import java.util.ArrayList;

/**
 * Keeps track of container visits, volume and weight loads, violations
 * (overflow), and overflow cost attributions for the planning horizon. Has an
 * implementation for the benchmark IRP.
 *
 * @author Markov
 * @version 2.0
 */
public class ContainerTracker {

    // Data reference
    Data data;

    // Matrices for tracking visits, volume loads, weight loads, container violations
    // (overflow), and overflow cost attributions for each container in the planning horizon
    protected final boolean[][] visits;
    protected final double[][] volumeLoads;
    protected final double[][] weightLoads;
    protected final double[][] contViolations;
    protected final double[][] overflowCostAttr;
    // Array of trees for calculating the overflow probabilities for each container
    protected final Tree[] trees;

    /**
     * Assignment and initialization constructor.
     *
     * @param data Data object to assign
     */
    public ContainerTracker(Data data) {

        // Data 
        this.data = data;

        // Initialize matrix of false for visits and 0 for the rest of the tracking matrices
        this.visits = new boolean[this.data.GetOriginalNumPoints()][this.data.GetPhLength() + 1];
        this.volumeLoads = new double[this.data.GetOriginalNumPoints()][this.data.GetPhLength() + 1];
        this.weightLoads = new double[this.data.GetOriginalNumPoints()][this.data.GetPhLength() + 1];
        this.contViolations = new double[this.data.GetOriginalNumPoints()][this.data.GetPhLength() + 1];
        this.overflowCostAttr = new double[this.data.GetOriginalNumPoints()][this.data.GetPhLength() + 1];
        // Initialize array of trees
        this.trees = new Tree[this.data.GetOriginalNumPoints()];
    }

    /**
     * Initializes the tracking information for volume loads, weight loads,
     * container violations (overflow), overflow probabilities, and overflow
     * cost attributions for each point and each day of the planning horizon.
     * Since only containers have distinct simple container wids, only they will
     * be tracked correctly.
     */
    public void InitTrackingInfo() {

        // Initialize tracking information for containers only
        for (Point container : this.data.GetContainers()) {

            // Shorthand for the point simple container wid
            int simpleContWid = container.GetSimpleContWid();

            // The first volume load is the initial volume load
            this.volumeLoads[simpleContWid][0] = container.GetInitVolumeLoad();
            // The first weight load is the initial weight load
            this.weightLoads[simpleContWid][0] = container.GetInitWeightLoad();
            // The first container violation is the violation of the initial volume load
            this.contViolations[simpleContWid][0] = Math.max(0.d, container.GetInitVolumeLoad() - container.GetEffectiveVolume());

            // Initialize a tree for this container in the array of trees, passing container reference,
            // length of the planning horizon, and reference to the array of visits
            this.trees[simpleContWid] = new Tree(container, this.data.GetPhLength(), this.visits[simpleContWid]);
            // For each day from 0 to phLength + 1, set the overflow cost attribution for this container.
            // Given that we start with no scheduled visits, we multiply the overflow probability by both the 
            // emergency cost and the overflow cost
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                this.overflowCostAttr[simpleContWid][d]
                        = this.trees[simpleContWid].GetOverflowProbability(d) * (this.data.GetEmegencyCost() + this.data.GetOverflowCost());
            }

            // The  volume load and weight load for each subsequent day are equal to the volume load and weight load 
            // of the previous day plus the demand of the previous day. The container violation for each day is equal to the 
            // violation of the volume load for that day.
            for (int d = 1; d < this.data.GetPhLength() + 1; d++) {
                this.volumeLoads[simpleContWid][d] = this.volumeLoads[simpleContWid][d - 1] + container.GetForecastVolumeDemand(d - 1);
                this.weightLoads[simpleContWid][d] = this.weightLoads[simpleContWid][d - 1] + container.GetForecastWeightDemand(d - 1);
                this.contViolations[simpleContWid][d] = Math.max(0.d, this.volumeLoads[simpleContWid][d] - container.GetEffectiveVolume());
            }
        }
    }

    /**
     * Updates tracking info for the passed point.
     *
     * @param point point to update tracking info for
     * @param dChange day on which the change occurred
     * @param inOut type of change: true insertion of the point on this day,
     * false removal of the point from this day
     */
    public void Update(Point point, int dChange, boolean inOut) {

        // Update only if the point is a container
        if (point.Is() == Parameters.pointIsContainer) {

            // Shorthand for the point simple container wid
            int simpleContWid = point.GetSimpleContWid();

            // Update visits
            this.visits[simpleContWid][dChange] = inOut;

            // Update the tree
            this.trees[simpleContWid].UpdateTree(dChange, inOut);

            // Update the overflow cost attributions. If there is a visit on a given day from 0 to phLength + 1,
            // only apply the overflow cost, otherwise the overflow cost and the emergency collection cost
            for (int d = dChange; d < this.data.GetPhLength() + 1; d++) {
                if (this.visits[simpleContWid][d] == true) {
                    this.overflowCostAttr[simpleContWid][d]
                            = this.trees[simpleContWid].GetOverflowProbability(d)
                            * this.data.GetOverflowCost();
                } else {
                    this.overflowCostAttr[simpleContWid][d]
                            = this.trees[simpleContWid].GetOverflowProbability(d)
                            * (this.data.GetEmegencyCost() + this.data.GetOverflowCost());
                }
                // If we reached a visit, break. No need to update after it.
                if (d > dChange && this.visits[simpleContWid][d] == true) {
                    break;
                }
            }

            // Update volume loads, weight loads, and container violations (overflow)
            for (int d = dChange + 1; d < this.data.GetPhLength() + 1; d++) {
                // If a visit was added, restart volume loads, weight loads and container violations,
                // otherwise pick up from visit day 
                if (this.visits[simpleContWid][d - 1] == true) {
                    this.volumeLoads[simpleContWid][d] = point.GetForecastVolumeDemand(d - 1);
                    this.weightLoads[simpleContWid][d] = point.GetForecastWeightDemand(d - 1);
                    this.contViolations[simpleContWid][d] = Math.max(0.d, this.volumeLoads[simpleContWid][d] - point.GetEffectiveVolume());
                } else {
                    this.volumeLoads[simpleContWid][d] = this.volumeLoads[simpleContWid][d - 1] + point.GetForecastVolumeDemand(d - 1);
                    this.weightLoads[simpleContWid][d] = this.weightLoads[simpleContWid][d - 1] + point.GetForecastWeightDemand(d - 1);
                    this.contViolations[simpleContWid][d] = Math.max(0.d, this.volumeLoads[simpleContWid][d] - point.GetEffectiveVolume());
                }
                // If we reached a visit, break. No need to update after it.
                if (this.visits[simpleContWid][d] == true) {
                    break;
                }
            }
        }
    }

    /**
     * Returns an ArrayList of containers that are not visited on the passed
     * day.
     *
     * @param d day in the planning horizon
     * @return an ArrayList of containers that are not visited on the passed day
     */
    public ArrayList<Point> GetAvblContainers(int d) {

        ArrayList<Point> avblContainers = new ArrayList<>(this.data.GetContainers().size());

        for (Point container : this.data.GetContainers()) {
            if (this.visits[container.GetSimpleContWid()][d] == false) {
                avblContainers.add(container);
            }
        }

        return avblContainers;
    }

    /**
     * Returns point visit on the specified day of the planning horizon.
     *
     * @param point point to return visit for
     * @param d day of the planning horizon
     * @return true if point is visited on the specified day of the planning
     * horizon, false otherwise
     */
    public boolean GetVisit(Point point, int d) {
        return this.visits[point.GetSimpleContWid()][d];
    }

    /**
     * Returns the point's number of random demand days wrt the passed day in
     * the planning horizon. The number of random demand days is the number of
     * days since the last collection, or the number of days since the start of
     * the planning horizon.
     *
     * @param point point to return number of random demand days for
     * @param d day in the planning horizon
     * @return the point's number of random demand days wrt the passed day in
     * the planning horizon
     */
    public int GetNumRandomDemandDays(Point point, int d) {

        // Initialize the number of random demand days as 0
        int numRandomDemandDays = 0;

        // If d is greater than 0, count the number of days backward until
        // we reach a visit, or the start of the planning horizon. The actual day d 
        // is not counted because we assume visits happen before demand is realized
        if (d > 0) {
            for (int g = d - 1; g >= 0; g--) {
                numRandomDemandDays++;
                if (this.visits[point.GetSimpleContWid()][g] == true) {
                    break;
                }
            }
        }

        // Return
        return numRandomDemandDays;
    }

    /**
     * Returns point volume load on the specified day of the planning horizon.
     *
     * @param point point to return volume load for
     * @param d day of the planning horizon
     * @return point volume load on the specified day of the planning horizon
     */
    public double GetVolumeLoad(Point point, int d) {
        return this.volumeLoads[point.GetSimpleContWid()][d];
    }

    /**
     * Returns point weight load on the specified day of the planning horizon.
     *
     * @param point point to return weight load for
     * @param d day of the planning horizon
     * @return point weight load on the specified day of the planning horizon
     */
    public double GetWeightLoad(Point point, int d) {
        return this.weightLoads[point.GetSimpleContWid()][d];
    }

    /**
     * Returns point container violation (overflow) on the specified day of the
     * planning horizon.
     *
     * @param point point to return container violation (overflow) for
     * @param d day of the planning horizon
     * @return point container violation (overflow) on the specified day of the
     * planning horizon
     */
    public double GetContainerViolation(Point point, int d) {
        return this.contViolations[point.GetSimpleContWid()][d];
    }

    /**
     * Returns the total inventory holding cost at the containers.
     *
     * @return the total inventory holding cost at the containers
     */
    public double GetContainerInventoryHoldingCost() {
        return 0.d;
    }

    /**
     * Returns the depot volume load on day d.
     *
     * @param d day in the planning horizon
     * @return the depot volume load on day d
     */
    public double GetDepotVolumeLoad(int d) {
        return 0.d;
    }

    /**
     * Returns the depot violation on day d.
     *
     * @param d day in the planning horizon
     * @return the depot violation on day d
     */
    public double GetDepotViolation(int d) {
        return 0.d;
    }

    /**
     * Returns the total inventory holding cost at the depot.
     *
     * @return the total inventory holding cost at the depot
     */
    public double GetDepotInventoryHoldingCost() {
        return 0;
    }

    /**
     * Returns point overflow cost attribution on the specified day of the
     * planning horizon.
     *
     * @param point point to return overflow cost attribution for
     * @param d day of the planning horizon
     * @return point overflow cost attribution on the specified day of the
     * planning horizon
     */
    public double GetOverflowCostAttr(Point point, int d) {
        return this.overflowCostAttr[point.GetSimpleContWid()][d];
    }

    /**
     * Returns point probability of overflow on the specified day of the
     * planning horizon.
     *
     * @param point point to return probability of overflow for
     * @param d day of the planning horizon
     * @return point probability of overflow on the specified day of the
     * planning horizon
     */
    public double GetOverflowProbability(Point point, int d) {
        return this.trees[point.GetSimpleContWid()].GetOverflowProbability(d);
    }

    /**
     * Generates a demand scenario by adding random errors to the demand
     * forecasts which are based on the estimated distribution of the volume
     * forecasting errors.
     *
     * @param randomize true to generate a demand scenario with random errors,
     * false to generate original point forecasts
     */
    public void SimulateDemandScenario(boolean randomize) {

        // For each container
        for (Point container : this.data.GetContainers()) {

            // Shorthand for the point simple container wid
            int simpleContWid = container.GetSimpleContWid();

            // Update volume loads, weight loads, and container violations (overflow)
            for (int d = 1; d < this.data.GetPhLength() + 1; d++) {
                // Draw a random Gaussian, and convert to a volume and weight error which is added to the 
                // forecasted value with the purpose of generating a demand realization scenario
                double randSN = 0.d;
                if (randomize) {
                    randSN = this.data.GetRand().nextGaussian();
                }
                double randVolume = randSN * this.data.GetErrorSigma();
                double randWeight = randVolume * container.GetFlowSpecWeight() / Parameters.flowSpecWeightCF;
                // If there is a visit, restart volume loads, weight loads and container violations,
                // otherwise pick up from visit day 
                if (this.visits[simpleContWid][d - 1] == true) {
                    this.volumeLoads[simpleContWid][d] = container.GetForecastVolumeDemand(d - 1) + randVolume;
                    this.weightLoads[simpleContWid][d] = container.GetForecastWeightDemand(d - 1) + randWeight;
                    this.contViolations[simpleContWid][d] = Math.max(0.d, this.volumeLoads[simpleContWid][d] - container.GetVolume());
                } else {
                    this.volumeLoads[simpleContWid][d] = this.volumeLoads[simpleContWid][d - 1] + container.GetForecastVolumeDemand(d - 1) + randVolume;
                    this.weightLoads[simpleContWid][d] = this.weightLoads[simpleContWid][d - 1] + container.GetForecastWeightDemand(d - 1) + randWeight;
                    this.contViolations[simpleContWid][d] = Math.max(0.d, this.volumeLoads[simpleContWid][d] - container.GetVolume());
                }
            }
        }
    }
}
