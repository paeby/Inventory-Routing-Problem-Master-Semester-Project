package alns.tour;

import alns.data.Data;
import alns.data.Point;
import alns.data.Truck;
import alns.param.Parameters;
import alns.schedule.ContainerTracker;

/**
 * Implements the Tour class for a benchmark IRP instance of the type Coelho et
 * al. (2012).
 *
 * @author Markov
 * @version 1.0
 */
public class TourIRPC extends Tour {

    /**
     * Implements the Tour assignment constructor.
     *
     * @param data Data object
     * @param truck Truck object
     * @param cTracker ContainerTracker object
     * @param day day in the planning horizon on which the tour is performed
     */
    public TourIRPC(Data data, Truck truck, ContainerTracker cTracker, int day) {

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
    public TourIRPC(Tour tour, ContainerTracker cTracker) {

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
     * Returns 0 as the IRPC considers no weight violation.
     *
     * @return 0
     */
    @Override
    public double GetWeightViolation() {
        return 0.d;
    }

    /**
     * Implements the Tour time window violation method for a benchmark IRP
     * instance. Returns 0 as the IRPC considers no time window violation.
     *
     * @return 0
     */
    @Override
    public double GetTWViolation() {
        return 0.d;
    }

    /**
     * Implements the Tour duration violation method for a benchmark IRP
     * instance. Returns 0 as the IRPC considers no duration violation.
     *
     * @return 0
     */
    @Override
    public double GetDurViolation() {
        return 0.d;
    }

    /**
     * Implements the Tour access violation method for a benchmark IRP instance.
     * Returns 0 as the IRPC considers no access violation.
     *
     * @return 0
     */
    @Override
    public int GetAccessViolation() {
        return 0;
    }

    /**
     * Implements the Tour home depot violation method for a benchmark IRP
     * instance. Returns 0 as the IRPC considers no home depot violation.
     *
     * @return 0
     */
    @Override
    public int GetHomeDepotViolation() {
        return 0;
    }

    /**
     * Implements the Tour route failure cost method for a benchmark IRP
     * instance. Returns 0 as the IRPC considers no route failure.
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
     * IRP instance. Returns 0 as the IRPC implementation considers no overflow
     * cost attribution.
     *
     * @return 0
     */
    @Override
    protected double getOverflowCostAttr() {
        return 0.d;
    }

    /**
     * Implements the Schedule inventory holding and shortage cost method for a
     * benchmark IRP instance.
     *
     * @return schedule inventory holding and shortage cost for the containers
     * from day 0 to day phLength + 1
     */
    @Override
    protected double getInventoryHoldingCost() {

        double inventoryHoldingCost = 0.d;

        for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
            for (Point container : this.data.GetContainers()) {
                double containerVolumeLoad = this.cTracker.GetVolumeLoad(container, d);
                inventoryHoldingCost += (containerVolumeLoad > 0.d
                        ? containerVolumeLoad * container.GetInventoryHoldingCost()
                        : containerVolumeLoad * container.GetInventoryShortageCost());
            }
        }

        return inventoryHoldingCost;
    }

    /**
     * Returns the depot inventories from day 0 to day phLength + 1.
     *
     * @return depot inventories from day 0 to day phLength + 1
     */
    private double[] getDepotInventories() {

        // Init depot inventories as 0s
        double[] depotInventories = new double[this.data.GetPhLength() + 1];
        // Assign depot
        Point depot = this.data.GetStartingPoints().get(0);

        // The depot's inventory on day 0 is its initial volume load
        depotInventories[0] = depot.GetInitVolumeLoad();

        // Calculate the depot's inventory for each subsequent day
        for (int d = 1; d < this.data.GetPhLength() + 1; d++) {
            // Calculate the volume of the containers served on the previous day. The volume distributed to
            // each container is the difference between the container's capacity and its volume load on that day
            double totalVolume_dminus1 = 0.d;
            for (Point container : this.data.GetContainers()) {
                if (this.cTracker.GetVisit(container, d - 1) == true) {
                    totalVolume_dminus1 += (container.GetEffectiveVolume() - Math.max(0.d, this.cTracker.GetVolumeLoad(container, d - 1)));
                }
            }

            // Depot inventories on day d depend on depot inventories on day d - 1, plus the amount made available at 
            // the depot on day d - 1 minus the amount that was distributed to customers on day d - 1
            depotInventories[d] = depotInventories[d - 1] + depot.GetForecastVolumeDemand(d - 1) - totalVolume_dminus1;
        }

        return depotInventories;
    }

    /**
     * Returns the depot inventory holding cost from day 0 to day phLength + 1
     *
     * @return the depot inventory holding cost from day 0 to day phLength + 1
     */
    private double getDepotInventoryHoldingCost() {

        double depotInventoryHoldingCost = 0.d;
        Point depot = this.data.GetStartingPoints().get(0);

        double[] depotInventories = this.getDepotInventories();
        for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
            depotInventoryHoldingCost += Math.max(0.d, depotInventories[d]) * depot.GetInventoryHoldingCost();
        }

        return depotInventoryHoldingCost;
    }

    /**
     * Implements the schedule container violation method for a benchmark IRP
     * instance. Returns 0 as the IRPC implementation considers no container
     * violation.
     *
     * @return 0
     */
    @Override
    protected double getContViolation() {
        return 0.d;
    }

    /**
     * This implementation is recycled for depot inventory violation for a
     * benchmark IRP instance. In the IRPC, depot inventory violation occurs for
     * negative inventory.
     *
     * @return depot inventory violation (negative inventory) from day 0 to day
     * phLength + 1
     */
    @Override
    protected double getBackorderViolation() {

        double depotInventoryViolation = 0.d;

        // Depot inventories
        double[] depotInventories = this.getDepotInventories();
        // For each day of the planning horizon 
        for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
            // Calculate the volume of the containers served on this day. The volume distributed to
            // each container is the difference between the container's capacity and its volume load 
            double totalVolume = 0.d;
            for (Point container : this.data.GetContainers()) {
                if (this.cTracker.GetVisit(container, d) == true) {
                    totalVolume += (container.GetEffectiveVolume() - Math.max(0.d, this.cTracker.GetVolumeLoad(container, d)));
                }
            }
            // Calculate inventory violation at the depot, i.e. the difference between the total
            // volume served from the depot and the inventory at the depot
            depotInventoryViolation += Math.max(0.d, totalVolume - depotInventories[d]);
        }

        return depotInventoryViolation;
    }

    /**
     * Implements the Tour effective cost method for a benchmark IRP instance.
     * The effective cost includes the tour cost, the penalty for violating the
     * depot inventories, and the inventory holding costs at the depot and the
     * containers.
     *
     * @return the Tour's effective cost
     */
    @Override
    protected double getEffectiveCost() {

        // Tour cost
        double tourCost = this.GetCost();

        // Add the penalty for violating the depot inventories
        tourCost += this.data.GetPenalties().GetBackorderViolPenalty() * this.getBackorderViolation();

        // Add inventory holding cost at depot and containers
        tourCost += this.getDepotInventoryHoldingCost();
        tourCost += this.getInventoryHoldingCost();

        return tourCost;
    }

}
