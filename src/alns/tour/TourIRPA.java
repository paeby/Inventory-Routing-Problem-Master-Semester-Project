package alns.tour;

import alns.algo.Penalties;
import alns.data.Data;
import alns.data.Point;
import alns.data.Truck;
import alns.param.Parameters;
import alns.schedule.ContainerTracker;

/**
 * Implements the Tour class for a benchmark IRP instance of the type Archetti
 * et al. (2007) and Archetti et al. (2012).
 *
 * @author Markov
 * @version 1.0
 */
public class TourIRPA extends Tour {

    /**
     * Implements the Tour assignment constructor.
     *
     * @param data Data object
     * @param truck Truck object
     * @param cTracker ContainerTracker object
     * @param day day in the planning horizon on which the tour is performed
     */
    public TourIRPA(Data data, Truck truck, ContainerTracker cTracker, int day) {

        // Call super constructor
        super(data, truck, cTracker, day);

        // If not already assigned, assign origin and destination starting point to the tour
        if (this.originStartingPoint == null && this.destinationStartingPoint == null) {
            // If the tour's day is the first available day of the truck,
            // the tour's origin starting point is the truck's current starting point.
            // Otherwise, it is by default the truck's home starting point.
            int truckFirstAvblDay = 0;
            for (int d = 0; d < this.data.GetPhLength(); d++) {
                if (this.truck.IsAvailable(d) == true) {
                    truckFirstAvblDay = d;
                    break;
                }
            }
            if (this.day == truckFirstAvblDay) {
                this.originStartingPoint = this.truck.GetCurrentStartingPoint();
            } else {
                this.originStartingPoint = this.truck.GetHomeStartingPoint();
            }
            // By default, the destination starting point is the truck's home starting point
            this.destinationStartingPoint = this.truck.GetHomeStartingPoint();
        }
    }

    /**
     * Implements the Tour copy constructor.
     *
     * @param tour Tour object
     * @param cTracker ContainerTracker object
     */
    public TourIRPA(Tour tour, ContainerTracker cTracker) {

        // Pass arguments to super constructor        
        super(tour, cTracker);

        // Copy the tour points using InsertPoint in order to 
        // make a deep point copy and recreate all tracking information
        for (int i = 0; i < tour.GetSize(); i++) {
            this.InsertPoint(i, tour.tour.get(i));
        }

        // Copy origin and destination starting point by value of reference
        this.originStartingPoint = tour.originStartingPoint;
        this.destinationStartingPoint = tour.destinationStartingPoint;
    }

    /**
     * Implements the Tour volume violation method for a benchmark IRP instance.
     * The volume distributed to each container is the difference between the
     * container's capacity and its volume load.
     *
     * @return the violation of the truck's maximum volume as the excess volume
     * over capacity
     */
    @Override
    public double GetVolViolation() {

        double currVolumeLoad = 0.d;
        double volumeViolation = 0.d;

        for (Point point : this.tour) {
            if (point.Is() == Parameters.pointIsContainer) {
                // We increment by the difference between the point's maximum volume and its volume load on this day. We 
                // apply a correction for negative volume load (container violation).
                currVolumeLoad += (point.GetEffectiveVolume() - Math.max(0.d, this.cTracker.GetVolumeLoad(point, this.day)));
            } else {
                volumeViolation += Math.max(0.d, currVolumeLoad - this.truck.GetMaxEffectiveVolume());
                currVolumeLoad = 0.d;
            }
        }

        // Return volume violation
        return volumeViolation;
    }

    /**
     * Implements the Tour volume violation method for a benchmark IRP instance.
     * Returns 0 as the IRPA considers no weight violation.
     *
     * @return 0
     */
    @Override
    public double GetWeightViolation() {
        return 0.d;
    }

    /**
     * Implements the Tour time window violation method for a benchmark IRP
     * instance. Returns 0 as the IRPA considers no time window violation.
     *
     * @return 0
     */
    @Override
    public double GetTWViolation() {
        return 0.d;
    }

    /**
     * Implements the Tour duration violation method for a benchmark IRP
     * instance. Returns 0 as the IRPA considers no duration violation.
     *
     * @return 0
     */
    @Override
    public double GetDurViolation() {
        return 0.d;
    }

    /**
     * Implements the Tour access violation method for a benchmark IRP instance.
     * Returns 0 as the IRPA considers no access violation.
     *
     * @return 0
     */
    @Override
    public int GetAccessViolation() {
        return 0;
    }

    /**
     * Implements the Tour home depot violation method for a benchmark IRP
     * instance. Returns 0 as the IRPA considers no home depot violation.
     *
     * @return 0
     */
    @Override
    public int GetHomeDepotViolation() {
        return 0;
    }

    /**
     * Implements the Tour route failure cost method for a benchmark IRP
     * instance. Returns 0 as the IRPA considers no route failure.
     *
     * @return 0
     */
    @Override
    public double GetRouteFailureCost() {
        return 0.d;
    }

    /**
     * Implements the Tour cost method for a benchmark IRP instance. The Tour
     * cost includes the volume violation penalty and the tour length.
     *
     * @return tour cost
     */
    @Override
    public double GetCost() {

        // For debugging purposes, check spDumpFeasible each time cost is calculated.
        // If the code is error free, this should never happen
        if (!this.spDumpFeasible()) {
            System.err.println(Thread.currentThread().getStackTrace()[1].getClassName()
                    + "." + Thread.currentThread().getStackTrace()[1].getMethodName()
                    + "() says:");
            System.err.println(">> Tour executed by truck " + this.truck.GetWid() + " on day " + this.day);
            System.err.println(">> has no starting point at position 0, or no dump (or starting point) at position (size - 2), ");
            System.err.println(">> or no starting point at position (size - 1). Unknown subsequent behavior.");
            System.err.println(">> Possible reason: Operators change points at incorrect indexes. "
                    + "Find last applied operator method in the Schedule and Tour classes.");
        }

        // If there is at least one container in the tour, calculate cost in full
        double tourCost = 0.d;
        if (this.GetNumContainers() > 0) {
            // Include the volume violation penalty and the tour length
            tourCost = this.data.GetPenalties().GetVolumeViolPenalty() * this.GetVolViolation();
            tourCost += this.truck.GetDistanceCost() * this.GetLength();

        }
        // Return cost
        return tourCost;
    }

    /**
     * Implements the Schedule overflow cost attribution method for a benchmark
     * IRP instance. Returns 0 as the IRPA implementation considers no overflow
     * cost attribution.
     *
     * @return 0
     */
    @Override
    protected double getOverflowCostAttr() {
        return 0.d;
    }

    /**
     * Implements the Schedule inventory holding cost method for a benchmark IRP
     * instance.
     *
     * @return schedule inventory holding cost for the containers from day 0 to
     * day phLength + 1
     */
    @Override
    protected double getInventoryHoldingCost() {
        return this.cTracker.GetContainerInventoryHoldingCost();
    }

    /**
     * Returns the depot inventory holding cost from day 0 to day phLength + 1
     *
     * @return the depot inventory holding cost from day 0 to day phLength + 1
     */
    private double getDepotInventoryHoldingCost() {
        return this.cTracker.GetDepotInventoryHoldingCost();
    }

    /**
     * Implements the schedule container violation method for a benchmark IRP
     * instance. For the IRPA, container violations occur for negative
     * inventory.
     *
     * @return the schedule total violation (negative inventory) of the
     * containers on days 0 to phLength + 1
     */
    @Override
    protected double getContViolation() {

        double contViolation = 0.d;

        for (Point container : this.data.GetContainers()) {
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                contViolation += this.cTracker.GetContainerViolation(container, d);
            }
        }

        return contViolation;
    }

    /**
     * This implementation is recycled for depot inventory violation for a
     * benchmark IRP instance. In the IRPA, depot inventory violation occurs for
     * negative inventory.
     *
     * @return depot inventory violation (negative inventory) from day 0 to day
     * phLength + 1
     */
    @Override
    protected double getBackorderViolation() {

        double depotViolation = 0.d;

        for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
            depotViolation += this.cTracker.GetDepotViolation(d);
        }

        return depotViolation;
    }

    /**
     * Implements the Tour effective cost method for a benchmark IRP instance.
     * The effective cost includes the tour cost, the penalties for violating
     * the depot and container inventories, and the inventory holding costs at
     * the depot and the containers.
     *
     * @return the Tour's effective cost
     */
    @Override
    protected double getEffectiveCost() {

        // Tour cost
        double tourCost = this.GetCost();

        // Add the penalties for violating the depot and container inventories
        Penalties penalties = this.data.GetPenalties();
        tourCost += penalties.GetBackorderViolPenalty() * this.getBackorderViolation();
        tourCost += penalties.GetContViolPenalty() * this.getContViolation();

        // Add inventory holding cost at depot and containers
        tourCost += this.getDepotInventoryHoldingCost();
        tourCost += this.getInventoryHoldingCost();

        return tourCost;
    }

}
