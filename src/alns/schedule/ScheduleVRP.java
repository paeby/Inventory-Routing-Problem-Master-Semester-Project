package alns.schedule;

import alns.data.Data;
import alns.data.FeedbackForm;
import alns.data.Point;
import alns.tour.Tour;
import alns.tour.TourVRP;
import alns.data.Truck;
import java.util.ArrayList;

/**
 * Schedule implementation for the vehicle routing problem (VRP) for day = 0.
 *
 * @author Markov
 * @version 1.0
 */
public class ScheduleVRP extends Schedule {

    /**
     * Implements the Schedule assignment constructor.
     *
     * @param data Data object
     */
    public ScheduleVRP(Data data) {

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
    public ScheduleVRP(Schedule schedule) {

        // Pass schedule to super constructor
        super(schedule);

        // Initialize container tracker
        this.cTracker = new ContainerTracker(this.data);
        this.cTracker.InitTrackingInfo();

        // Copy each tour using Tour's copy constructor but passing the new container tracker
        for (Tour tour : schedule.GetTours()) {
            this.schedule.add(new TourVRP(tour, this.cTracker));
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

        // Combine all points within the same ArrayList
        ArrayList<Point> allPoints = new ArrayList<>();
        allPoints.addAll(this.data.GetStartingPoints());
        allPoints.addAll(this.data.GetContainers());
        allPoints.addAll(this.data.GetDumps());

        // Locate the appropriate tour in the schedule
        Tour tourToChange = this.schedule.get(0);
        for (Tour tour : this.schedule) {
            if (tour.GetDay() == day && tour.GetTruck().GetWid() == truckWid) {
                tourToChange = tour;
                break;
            }
        }
        // Delete all points in the tour
        for (int i = tourToChange.GetSize() - 1; i >= 0; i--) {
            tourToChange.RemovePoint(i);
        }
        // Add the points with the dwids as passed
        for (int i = 0; i < pointDwids.size(); i++) {
            Point pointToInsert = allPoints.get(0);
            for (Point point : allPoints) {
                if (point.GetDWid() == pointDwids.get(i)) {
                    pointToInsert = point;
                    break;
                }
            }
            tourToChange.InsertPoint(i, pointToInsert);
        }
    }

    /**
     * Implements the Schedule construction method. It constructs sequentially a
     * tour on day = 0 and for each available truck on that day.
     */
    @Override
    public void Construct() {

        // For day = 0, open a tour for each available truck
        for (Truck truck : this.data.GetTrucks()) {
            if (truck.IsAvailable(0)) {
                Tour tour = new TourVRP(this.data, truck, this.cTracker, 0);
                tour.Construct();
                this.schedule.add(tour);
            }
        }
    }

    /**
     * Implements the Schedule neighbor generation method with the operators
     * pertinent to the VRP implementation.
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
                nbTimesAppliedDestroyOp = this.emptyOneTruck();
                break;
            case 4:
                nbTimesAppliedDestroyOp = this.removeRandomDump();
                break;
            case 5:
                nbTimesAppliedDestroyOp = this.removeWorstDump();
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
     * Implements the Schedule total backorder limit violation method. For the
     * VRP, it the sum of volume loads on day 0 for containers that are not
     * served on day 0.
     *
     * @return the Schedule total backorder limit violation as the sum of volume
     * loads on day 0 for containers that are not served on day 0
     */
    @Override
    public double GetBackorderViolation() {

        double backorderViolation = 0.d;

        for (Point container : this.data.GetContainers()) {
            if (this.cTracker.GetVisit(container, 0) == false) {
                backorderViolation += this.cTracker.GetVolumeLoad(container, 0);
            }
        }

        return backorderViolation;
    }

    /**
     * Implements the Schedule container violation (overflow) method. Returns 0
     * as the VRP implementation considers no container violation (overflow).
     *
     * @return 0
     */
    @Override
    public double GetContViolation() {
        return 0.d;
    }

    /**
     * Implements the Schedule inventory holding cost method. Returns 0 as the
     * VRP implementation considers no inventory holding cost.
     *
     * @return 0
     */
    @Override
    public double GetInventoryHoldingCost() {
        return 0.d;
    }

    /**
     * Implements the Schedule overflow cost attribution method. Returns 0 as
     * the VRP implementation considers no overflow cost attribution.
     *
     * @return 0
     */
    @Override
    public double GetOverflowCostAttr() {
        return 0.d;
    }
}
