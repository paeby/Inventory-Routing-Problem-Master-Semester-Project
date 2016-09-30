package alns.schedule;

import alns.data.Data;
import alns.data.FeedbackForm;
import alns.data.Point;
import alns.tour.Tour;
import alns.tour.TourTSP;
import alns.data.Truck;
import alns.param.Parameters;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Schedule implementation for the traveling salesman problem (TSP) over the
 * planning horizon.
 *
 * @author Markov
 * @version 1.0
 */
public class ScheduleTSP extends Schedule {

    /**
     * Implements the Schedule assignment constructor.
     *
     * @param data Data object
     */
    public ScheduleTSP(Data data) {

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
    public ScheduleTSP(Schedule schedule) {

        // Pass schedule to super constructor
        super(schedule);

        // Initialize container tracker
        this.cTracker = new ContainerTracker(this.data);
        this.cTracker.InitTrackingInfo();

        // Copy each tour using Tour's copy constructor but passing the new container tracker
        for (Tour tour : schedule.GetTours()) {
            this.schedule.add(new TourTSP(tour, this.cTracker));
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
     * single tour on each day using a representative truck.
     */
    @Override
    public void Construct() {

        // For each day, open a single tour with a representative truck
        for (int d = 0; d < this.data.GetPhLength(); d++) {

            // Representative (mean) truck characteristics
            double repSpeed = 0.d;
            double repFixedCost = 0.d;
            double repDistanceCost = 0.d;
            double repTimeCost = 0.d;

            // Loop over trucks and update the representative truck characteristics
            // based on the number of available trucks on this day
            int numAvailableTrucks = 0;
            for (int i = 0; i < this.data.GetTrucks().size(); i++) {
                Truck truck = this.data.GetTrucks().get(i);
                if (truck.IsAvailable(d)) {
                    repSpeed += (truck.GetSpeed() - repSpeed) / (i + 1);
                    repFixedCost += (truck.GetFixedCost() - repFixedCost) / (i + 1);
                    repDistanceCost += (truck.GetDistanceCost() - repDistanceCost) / (i + 1);
                    repTimeCost += (truck.GetTimeCost() - repTimeCost) / (i + 1);
                    numAvailableTrucks++;
                }
            }
            // If there are available truks on this day, create a representative Truck object
            if (numAvailableTrucks > 0) {
                Truck repTruck = new Truck(
                        (10000 + d), // dummy truck wid
                        Parameters._404, // dummy truck type
                        String.valueOf(10000 + d), // dummy truck identifier
                        String.valueOf(10000 + d), // dummy truck name
                        Parameters._404, // dummy truck client wid
                        Parameters._404, // dummy truck zone wid
                        0, // dummy truck flow wid
                        Double.POSITIVE_INFINITY, // dummy truck max volume
                        Double.POSITIVE_INFINITY, // dummy truck max weight
                        repSpeed, // representative truck speed
                        repFixedCost, // representative truck fixed cost
                        repDistanceCost, // representative truck distance cost
                        repTimeCost, // representative truck time cost
                        // representative truck home starting point is the home starting point
                        // of the first truck in the list of trucks
                        this.data.GetTrucks().get(0).GetHomeStartingPoint(),
                        // representative truck current starting point is the home starting point
                        // of the first truck in the list of trucks
                        this.data.GetTrucks().get(0).GetHomeStartingPoint(),
                        // representative truck flexible starting points is empty
                        new ArrayList<Point>(),
                        // Representative truck required returns to home - always. 
                        // The TSP implementation does not consider flexible final starting point choice anyway.
                        // The list below is just passed for consistency.
                        new ArrayList<>(Collections.nCopies(this.data.GetPhLength(), true)),
                        new ArrayList<Boolean>() // rep truck availabilities (unnecessary here)
                );

                // Initialize a tour with it, construct it, and add it to the schedule
                // The actual number of available trucks is passed to the constructor and 
                // treated as an implicit number of trucks in the TourTSP object. This implicit number 
                // of trucks is used for calculating the duration violation of the TSP tour. The maximum
                // TSP tour duration is equal to the implicit number of trucks times the actual max tour duration.
                Tour tour = new TourTSP(this.data, repTruck, this.cTracker, d, numAvailableTrucks);
                tour.Construct();
                this.schedule.add(tour);
            }
        }
    }

    /**
     * Implements the Schedule neighbor generation method with the operators
     * pertinent to the TSP implementation.
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
}
