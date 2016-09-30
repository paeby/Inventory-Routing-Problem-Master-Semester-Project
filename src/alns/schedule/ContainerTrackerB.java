package alns.schedule;

import alns.data.Data;
import alns.data.Point;
import alns.param.Parameters;

/**
 * Implements the ContainerTracker class for a benchmark IRP instance.
 *
 * @author Markov
 * @version 1.0
 */
public class ContainerTrackerB extends ContainerTracker {

    // Inventory holding cost for containers and depot
    private double containerInventoryHoldingCost;
    private double depotInventoryHoldingCost;

    // Depot volume loads, violations, and delivered volume loads on each day
    private final double[] depotVolumeLoads;
    private final double[] depotViolations;
    private final double[] depotDeliveredVolumeLoads;

    /**
     * Assignment and initialization constructor.
     *
     * @param data Data object to assign
     */
    public ContainerTrackerB(Data data) {

        // Call super constructor
        super(data);

        // Initialize container and depot inventory holding cost 
        this.containerInventoryHoldingCost = 0.d;
        this.depotInventoryHoldingCost = 0.d;

        // Initialize depot volume loads, violations, and delivered volume loads
        this.depotVolumeLoads = new double[this.data.GetPhLength() + 1];
        this.depotViolations = new double[this.data.GetPhLength() + 1];
        this.depotDeliveredVolumeLoads = new double[this.data.GetPhLength() + 1];
    }

    /**
     * Overrides the method for initializing tracking information for the case
     * of the benchmark IRP. Since weight loads, trees and overflow cost
     * attributions are unnecessary, they are not tracked. Volume loads are
     * tracked assuming a distribution rather than a collection problem.
     * Therefore, container violations occur when the inventory is negative
     * rather than when there is an overflow. In addition this class tracks,
     * depot inventories and depot inventory violations, as well as the
     * inventory holding cost at containers and depots.
     */
    @Override
    public void InitTrackingInfo() {

        // Depot reference
        Point depot = this.data.GetStartingPoints().get(0);

        // Initialize tracking information for depot
        // The first volume load is the initial volume load
        this.depotVolumeLoads[0] = depot.GetInitVolumeLoad();
        // The first depot violation is the violation of the initial volume load
        this.depotViolations[0] = Math.max(0.d, -depot.GetInitVolumeLoad());
        // The depot inventory holding cost takes into account the initial volume load
        this.depotInventoryHoldingCost += Math.max(0.d, this.depotVolumeLoads[0] * depot.GetInventoryHoldingCost());
        // The volume load for each subsequent day is equal to the volume load of the previous day 
        // plus the volume produced on the previous day. The depot violation for each day is equal to the 
        // violation of the volume load for that day. The depot inventory holding cost takes into 
        // account the volume load on each subsequent day.
        for (int d = 1; d < this.data.GetPhLength() + 1; d++) {
            this.depotVolumeLoads[d] = this.depotVolumeLoads[d - 1] + depot.GetForecastVolumeDemand(d - 1);
            this.depotViolations[d] = Math.max(0.d, -this.depotVolumeLoads[d]);
            this.depotInventoryHoldingCost += Math.max(0.d, this.depotVolumeLoads[d] * depot.GetInventoryHoldingCost());
        }

        // Initialize tracking information for containers only
        for (Point container : this.data.GetContainers()) {

            // Shorthand for the point simple container wid
            int simpleContWid = container.GetSimpleContWid();

            // The first volume load is the initial volume load
            this.volumeLoads[simpleContWid][0] = container.GetInitVolumeLoad();
            // The first container violation is the violation of the initial volume load
            this.contViolations[simpleContWid][0] = Math.max(0.d, -container.GetInitVolumeLoad());
            // The container inventory holding cost takes into account the initial volume load
            this.containerInventoryHoldingCost += Math.max(0.d, this.volumeLoads[simpleContWid][0]) * container.GetInventoryHoldingCost();
            // The volume load for each subsequent day is equal to the volume load of the previous day 
            // minus the consumption of the previous day. The container violation for each day is equal to the 
            // violation of the volume load for that day. The container inventory holding cost takes into account 
            // the volume load on each subsequent day.
            for (int d = 1; d < this.data.GetPhLength() + 1; d++) {
                this.volumeLoads[simpleContWid][d] = this.volumeLoads[simpleContWid][d - 1] - container.GetForecastVolumeDemand(d - 1);
                this.contViolations[simpleContWid][d] = Math.max(0.d, -this.volumeLoads[simpleContWid][d]);
                this.containerInventoryHoldingCost += Math.max(0.d, this.volumeLoads[simpleContWid][d]) * container.GetInventoryHoldingCost();
            }
        }
    }

    /**
     * Updates tracking info for the passed point and the effect on the depot.
     *
     * @param point point to update tracking info for
     * @param dChange day on which the change occurred
     * @param inOut type of change: true insertion of the point on this day,
     * false removal of the point from this day
     */
    @Override
    public void Update(Point point, int dChange, boolean inOut) {

        // Update only if the point is a container
        if (point.Is() == Parameters.pointIsContainer) {

            // Depot reference
            Point depot = this.data.GetStartingPoints().get(0);

            // Shorthand for the point simple container wid
            int simpleContWid = point.GetSimpleContWid();

            // Find next visit day for this container and the volume load there
            // before the updates are made below
            int nextVisitDay = Parameters._404;
            double volumeLoadNextVisitDay = Parameters._404;
            for (int d = dChange + 1; d < this.data.GetPhLength() + 1; d++) {
                if (this.visits[simpleContWid][d] == true) {
                    nextVisitDay = d;
                    volumeLoadNextVisitDay = this.volumeLoads[simpleContWid][d];
                    break;
                }
            }

            // Update visits
            this.visits[simpleContWid][dChange] = inOut;

            // Update container volume loads and violations (negative inventory)
            // and container inventory holding cost
            for (int d = dChange + 1; d < this.data.GetPhLength() + 1; d++) {
                // Inventory holding cost on day d before update
                double cihcDayBeforeUpdate = Math.max(0.d, this.volumeLoads[simpleContWid][d]) * point.GetInventoryHoldingCost();
                // If a visit was added, restart volume loads and container violations,
                // otherwise pick up from visit day 
                if (this.visits[simpleContWid][d - 1] == true) {
                    this.volumeLoads[simpleContWid][d] = point.GetEffectiveVolume() - point.GetForecastVolumeDemand(d - 1);
                    this.contViolations[simpleContWid][d] = Math.max(0.d, -this.volumeLoads[simpleContWid][d]);
                } else {
                    this.volumeLoads[simpleContWid][d] = this.volumeLoads[simpleContWid][d - 1] - point.GetForecastVolumeDemand(d - 1);
                    this.contViolations[simpleContWid][d] = Math.max(0.d, -this.volumeLoads[simpleContWid][d]);
                }
                // Inventory holding cost on day d after update
                double cihcDayAfterUpdate = Math.max(0.d, this.volumeLoads[simpleContWid][d]) * point.GetInventoryHoldingCost();
                // Update inventody holding cost
                this.containerInventoryHoldingCost += (cihcDayAfterUpdate - cihcDayBeforeUpdate);
                // If we reached a visit, break. No need to update after it.
                if (this.visits[simpleContWid][d] == true) {
                    break;
                }
            }

            // For updating correctly the changes at the depot, we need to track the volumes that the depot delivers every day.
            // Since the policy is OU, we deliver the amount necessary to fill up the container inventory. We update the delivered
            // volumes from the depot on day dChange based on whether a visit to the point was added or removed.
            // THERE SHOULD BE A STRONGER SANITY CHECK FOR THIS IN THE TOUR CLASS, PERHAPS...
            if (inOut) {
                this.depotDeliveredVolumeLoads[dChange] += (point.GetEffectiveVolume() - Math.max(0, this.volumeLoads[simpleContWid][dChange]));
            } else {
                this.depotDeliveredVolumeLoads[dChange] -= (point.GetEffectiveVolume() - Math.max(0, this.volumeLoads[simpleContWid][dChange]));
            }
            // Importantly, the addition or removal of a visit to the point on day dChange has an immediate effect on the delivered volume 
            // to this point on its next visit day (if such exists). We need to account for this in the volumes delivered from the depot.
            if (nextVisitDay != Parameters._404) {
                double volumeDiffNextVisitDay = Math.max(0, this.volumeLoads[simpleContWid][nextVisitDay])
                        - Math.max(0, volumeLoadNextVisitDay);
                this.depotDeliveredVolumeLoads[nextVisitDay] -= volumeDiffNextVisitDay;
            }
            // Update depot volume loads and inventory holding cost (from day dChange + 1)
            for (int d = dChange + 1; d < this.data.GetPhLength() + 1; d++) {
                double dihcDayBeforeUpdate = Math.max(0.d, this.depotVolumeLoads[d] * depot.GetInventoryHoldingCost());
                this.depotVolumeLoads[d] = this.depotVolumeLoads[d - 1] + depot.GetForecastVolumeDemand(d - 1) - this.depotDeliveredVolumeLoads[d - 1];
                double dihcDayAfterUpdate = Math.max(0.d, this.depotVolumeLoads[d] * depot.GetInventoryHoldingCost());
                this.depotInventoryHoldingCost += (dihcDayAfterUpdate - dihcDayBeforeUpdate);
            }
            // Update depot violations from day (from day dChange)
            for (int d = dChange; d < this.data.GetPhLength() + 1; d++) {
                this.depotViolations[d] = Math.max(0.d, this.depotDeliveredVolumeLoads[d] - this.depotVolumeLoads[d]);
            }
        }
    }

    /**
     * Returns the total inventory holding cost at the containers.
     *
     * @return the total inventory holding cost at the containers
     */
    @Override
    public double GetContainerInventoryHoldingCost() {
        return this.containerInventoryHoldingCost;
    }

    /**
     * Returns the depot volume load on day d.
     *
     * @param d day in the planning horizon
     * @return the depot volume load on day d
     */
    @Override
    public double GetDepotVolumeLoad(int d) {
        return this.depotVolumeLoads[d];
    }

    /**
     * Returns the depot violation on day d.
     *
     * @param d day in the planning horizon
     * @return the depot violation on day d
     */
    @Override
    public double GetDepotViolation(int d) {
        return this.depotViolations[d];
    }

    /**
     * Returns the total inventory holding cost at the depot.
     *
     * @return the total inventory holding cost at the depot
     */
    @Override
    public double GetDepotInventoryHoldingCost() {
        return this.depotInventoryHoldingCost;
    }
}
