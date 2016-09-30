package alns.schedule;

import alns.data.Data;
import alns.data.FeedbackForm;
import alns.data.Point;
import alns.data.Truck;
import alns.tour.Tour;
import alns.tour.TourIRPC;
import java.util.ArrayList;

/**
 * Implements the Schedule class for a benchmark IRP instance of the type Coelho
 * et al. (2012).
 *
 * @author Markov
 * @version 1.0
 */
public class ScheduleIRPC extends Schedule {

    /**
     * Implements the Schedule assignment constructor.
     *
     * @param data Data object
     */
    public ScheduleIRPC(Data data) {

        // Call super constructor
        super(data);

        // Initialize container tracker
        this.cTracker = new ContainerTrackerB(this.data);
        this.cTracker.InitTrackingInfo();
    }

    /**
     * Implements the Schedule copy constructor.
     *
     * @param schedule Schedule object to copy
     */
    public ScheduleIRPC(Schedule schedule) {

        // Pass schedule to super constructor
        super(schedule);

        // Initialize container tracker
        this.cTracker = new ContainerTrackerB(this.data);
        this.cTracker.InitTrackingInfo();

        // Copy each tour using Tour's copy constructor but passing the new container tracker
        for (Tour tour : schedule.GetTours()) {
            this.schedule.add(new TourIRPC(tour, this.cTracker));
        }
    }

    /**
     * Implements the method which adds a ready tour to the solution. It is only
     * used for testing purposes.
     *
     * @param day day in the planning horizon
     * @param truckWid trick wid
     * @param pointDwids point dwids
     */
    @Override
    public void AddTour(int day, int truckWid, ArrayList<Integer> pointDwids) {

    }

    /**
     * Implements the Schedule construction method for a benchmark IRP instance.
     * Constructs sequentially a tour on each day and for each available truck
     * on that day.
     */
    @Override
    public void Construct() {

        // For each day, open a tour for each available truck
        for (int d = 0; d < this.data.GetPhLength(); d++) {
            for (Truck truck : this.data.GetTrucks()) {
                if (truck.IsAvailable(d)) {
                    Tour tour = new TourIRPC(this.data, truck, this.cTracker, d);
                    tour.Construct();
                    this.schedule.add(tour);
                }
            }
        }
    }

    /**
     * Implements the Schedule neighbor generation method with the operators
     * pertinent to the IRPC implementation.
     *
     * @param destroyOpIndex index of the destroy operator to apply
     * @param repairOpIndex index of the repair operator to apply
     * @return an FeedbackForm holding the number of times the destroy and the
     * repair operators were applied and the time needed to run the operations
     */
    @Override
    public FeedbackForm GenerateNeighbor(int destroyOpIndex, int repairOpIndex) {

        // Number of times destroy and repair operators are applied
        int nbTimesAppliedDestroyOp = 0;
        int nbTimesAppliedRepairOp = 0;
        long destroyOperatorStartTime = System.nanoTime();

        // Apply the selected destroy operator
        switch (destroyOpIndex) {
            case 0:
                nbTimesAppliedDestroyOp = this.removeRandomRhoContainers();
                break;
            case 1:
                nbTimesAppliedDestroyOp = this.removeWorstRhoContainers();
                break;
            case 2:
                nbTimesAppliedDestroyOp = this.removeShawContainers();
                break;
            case 3:
                nbTimesAppliedDestroyOp = this.emptyOneDay();
                break;
            case 4:
                nbTimesAppliedDestroyOp = this.removeConsecutiveVisits();
                break;
            default:
                System.out.println("Destroy operator index out of range...");
                break;
        }
        
        long destroyOperatorEndTime = System.nanoTime();

        // Apply the selected repair operator
        switch (repairOpIndex) {
            case 0:
                nbTimesAppliedRepairOp = this.insertRandomRhoContainers();
                break;
            case 1:
                nbTimesAppliedRepairOp = this.insertBestRhoContainers();
                break;
            case 2:
                nbTimesAppliedRepairOp = this.insertShawContainers();
                break;
            case 3:
                nbTimesAppliedRepairOp = this.swapAssgContainers();
                break;
            default:
                System.out.println("Repair operator index out of range...");
                break;
        }
        
        // Timing of the operators
        long repairOperatorEndTime = System.nanoTime();
        long destroyOperatorDuration = (destroyOperatorEndTime - destroyOperatorStartTime); // nanosecond
        long repairOperatorDuration = (repairOperatorEndTime - destroyOperatorEndTime); // nanosecond

        // Do local search before applying acceptance criterion
        // this.DoLocalSearch();
        
        // Save the data to return in a Feedback Form
        FeedbackForm feedback = new FeedbackForm();
        feedback.setDurationData(new long[] {destroyOperatorDuration, repairOperatorDuration});
        feedback.setNbTimesAppliedData(new int[] {nbTimesAppliedDestroyOp, nbTimesAppliedRepairOp});
        
        return feedback;
    }

    /**
     * Implements the Schedule overflow cost attribution method for a benchmark
     * IRP instance. Returns 0 as the IRPC implementation considers no overflow
     * cost attribution.
     *
     * @return 0
     */
    @Override
    public double GetOverflowCostAttr() {
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
    public double GetInventoryHoldingCost() {

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
    public double[] GetDepotInventories() {

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
     * Implements the Schedule depot inventory holding cost method for a
     * benchmark IRP instance.
     *
     * @return the depot inventory holding cost from day 0 to day phLength + 1
     */
    @Override
    public double GetDepotInventoryHoldingCost() {

        double depotInventoryHoldingCost = 0.d;
        Point depot = this.data.GetStartingPoints().get(0);

        double[] depotInventories = this.GetDepotInventories();
        for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
            depotInventoryHoldingCost += Math.max(0.d, depotInventories[d]) * depot.GetInventoryHoldingCost();
        }

        return depotInventoryHoldingCost;
    }

    /**
     * Implements the Schedule container violation method for a benchmark IRP
     * instance. Returns 0 as the IRPC implementation considers no container
     * violation.
     *
     * @return 0
     */
    @Override
    public double GetContViolation() {
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
    public double GetBackorderViolation() {

        double depotInventoryViolation = 0.d;

        // Depot inventories
        double[] depotInventories = this.GetDepotInventories();
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
     * Implements the Schedule cost method for a benchmark IRP instance. The
     * schedule cost includes the Tour costs, the penalty for violating the
     * depot inventories, and the inventory holding costs at the depot and the
     * containers.
     *
     * @return schedule cost
     */
    @Override
    public double GetCost() {

        // Calculate schedule cost as the sum of tours costs
        double scheduleCost = 0.d;
        for (Tour tour : this.schedule) {
            scheduleCost += tour.GetCost();
        }

        // Add the penalty for violating the depot inventories
        scheduleCost += this.data.GetPenalties().GetBackorderViolPenalty() * this.GetBackorderViolation();

        // Add inventory holding cost at the depot and containers
        scheduleCost += this.GetDepotInventoryHoldingCost();
        scheduleCost += this.GetInventoryHoldingCost();

        // Returns schedule cost
        return scheduleCost;
    }
}
