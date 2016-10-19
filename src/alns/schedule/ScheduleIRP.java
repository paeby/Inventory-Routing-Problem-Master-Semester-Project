package alns.schedule;

import alns.data.Data;
import alns.data.FeedbackForm;
import alns.tour.Tour;
import alns.tour.TourIRP;
import alns.data.Truck;
import java.util.ArrayList;

/**
 * Schedule implementation for the inventory routing problem (IRP) over the
 * planning horizon.
 *
 * @author Markov
 * @version 1.0
 */
public class ScheduleIRP extends Schedule {

    /**
     * Implements the Schedule assignment constructor.
     *
     * @param data Data object
     */
    public ScheduleIRP(Data data) {

        // Call super constructor
        super(data);
        
        // Initialize container tracker
        this.cTracker = new ContainerTracker(this.data);
        this.cTracker.InitTrackingInfo();
    }

    /**
     * Implements the Schedule copy constructor.
     *
     * @param schedule Schedule object to copy
     */
    public ScheduleIRP(Schedule schedule) {

        // Pass schedule to super constructor
        super(schedule);
        
        // Initialize container tracker
        this.cTracker = new ContainerTracker(this.data);
        this.cTracker.InitTrackingInfo();

        // Copy each tour using Tour's copy constructor but passing the new container tracker
        for (Tour tour : schedule.GetTours()) {
            this.schedule.add(new TourIRP(tour, this.cTracker));
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
     * Implements the Schedule construction method. Constructs sequentially a
     * tour on each day and for each available truck on that day.
     */
    @Override
    public void Construct() {

        // For each day, open a tour for each available truck
        for (int d = 0; d < this.data.GetPhLength(); d++) {
            for (Truck truck : this.data.GetTrucks()) {
                if (truck.IsAvailable(d)) {
                    Tour tour = new TourIRP(this.data, truck, this.cTracker, d);
                    tour.Construct();
                    this.schedule.add(tour);
                }
            }
        }
    }

    /**
     * Implements the Schedule neighbor generation method with the operators
     * pertinent to the IRP implementation.
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
                nbTimesAppliedDestroyOp = this.emptyOneTruck();
                break;
            case 5:
                nbTimesAppliedDestroyOp = this.removeRandomDump();
                break;
            case 6:
                nbTimesAppliedDestroyOp = this.removeWorstDump();
                break;
            case 7:
                nbTimesAppliedDestroyOp = this.removeConsecutiveVisits();
                break;
            case 8:
                nbTimesAppliedDestroyOp = this.removeAllShawContainers();
                break;
            case 9:
                nbTimesAppliedDestroyOp = this.removeAllShawContainersRelatedness(0.3, 0.4, 0.1, 0.1, 0.4);
                break;
            case 10: 
                nbTimesAppliedDestroyOp = this.removeContainerCluster();
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
            case 4:
                nbTimesAppliedRepairOp = this.insertRandomDump();
                break;
            case 5:
                nbTimesAppliedRepairOp = this.insertBestDump();
                break;
            case 6:
                nbTimesAppliedRepairOp = this.swapAssgDumps();
                break;
            case 7:
                nbTimesAppliedRepairOp = this.replaceRandomDump();
                break;
            case 8:
                nbTimesAppliedRepairOp = this.reorderDumps();
                break;
            case 9:
                nbTimesAppliedRepairOp = this.replaceStartingPoint();
                break;
            case 10:
                nbTimesAppliedRepairOp = this.insertRhoContainersWithRegret(3);
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
}
