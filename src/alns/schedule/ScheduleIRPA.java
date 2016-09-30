package alns.schedule;

import alns.algo.Penalties;
import alns.data.Data;
import alns.data.FeedbackForm;
import alns.data.Point;
import alns.data.Truck;
import alns.tour.Tour;
import alns.tour.TourIRPA;
import java.util.ArrayList;

/**
 * Implements the Schedule class for a benchmark IRP instance of the type
 * Archetti et al. (2007) and Archetti et al. (2012).
 *
 * @author Markov
 * @version 1.0
 */
public class ScheduleIRPA extends Schedule {
    
    /**
     * Implements the Schedule assignment constructor.
     *
     * @param data Data object
     */
    public ScheduleIRPA(Data data) {
        
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
    public ScheduleIRPA(Schedule schedule) {
        
        // Pass schedule to super constructor
        super(schedule);
        
        // Initialize container tracker
        this.cTracker = new ContainerTrackerB(this.data);
        this.cTracker.InitTrackingInfo();
        
        // Copy each tour using Tour's copy constructor but passing the new container tracker
        for (Tour tour : schedule.GetTours()) {
            this.schedule.add(new TourIRPA(tour, this.cTracker));
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
                    Tour tour = new TourIRPA(this.data, truck, this.cTracker, d);
                    tour.Construct();
                    this.schedule.add(tour);
                }
            }
        }
    }
    
    /**
     * Implements the Schedule neighbor generation method with the operators
     * pertinent to the IRPA implementation.
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
     * IRP instance. Returns 0 as the IRPA implementation considers no overflow
     * cost attribution.
     *
     * @return 0
     */
    @Override
    public double GetOverflowCostAttr() {
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
    public double GetInventoryHoldingCost() {
        return this.cTracker.GetContainerInventoryHoldingCost();
    }
        
    /**
     * Implements the Schedule depot inventory holding cost method for a
     * benchmark IRP instance.
     *
     * @return the depot inventory holding cost from day 0 to day phLength + 1
     */
    @Override
    public double GetDepotInventoryHoldingCost() {
        return this.cTracker.GetDepotInventoryHoldingCost();
    }
    
    /**
     * Implements the Schedule container violation method for a benchmark IRP
     * instance. For the IRPA, container violations occur for negative
     * inventory.
     *
     * @return the schedule total violation (negative inventory) of the
     * containers on days 0 to phLength + 1
     */
    @Override
    public double GetContViolation() {
        
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
    public double GetBackorderViolation() {
        
        double depotViolation = 0.d;

        for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
            depotViolation += this.cTracker.GetDepotViolation(d);
        }

        return depotViolation;
    }
    
    /**
     * Implements the Schedule cost method for a benchmark IRP instance. The
     * schedule cost includes the Tour costs, the penalties for violating the
     * depot and container inventories, and the inventory holding costs at the
     * depot and the containers.
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
        
        // Add the penalties for violating the depot and container inventories
        Penalties penalties = this.data.GetPenalties();
        scheduleCost += penalties.GetBackorderViolPenalty() * this.GetBackorderViolation();
        scheduleCost += penalties.GetContViolPenalty() * this.GetContViolation();
        
        // Add inventory holding cost at the depot and containers
        scheduleCost += this.GetDepotInventoryHoldingCost();
        scheduleCost += this.GetInventoryHoldingCost();
        
        // Returns schedule cost
        return scheduleCost;
    }
}
