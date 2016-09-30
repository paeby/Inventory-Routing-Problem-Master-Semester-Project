package alns.schedule;

import alns.data.Data;
import alns.data.FeedbackForm;
import alns.data.Point;
import alns.tour.Tour;
import alns.tour.TourVRP;
import alns.data.Truck;
import alns.param.Parameters;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * Schedule implementation for the vehicle routing problem (VRP). Adapted for
 * benchmark instances of Crevier et al. (2007).
 *
 * @author Markov
 * @version 1.0
 */
public class ScheduleVRPC extends Schedule {
    
    /**
     * Implements the Schedule assignment constructor.
     *
     * @param data Data object
     */
    public ScheduleVRPC(Data data) {
        
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
    public ScheduleVRPC(Schedule schedule) {
        
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
     * tour on day = 0 and for each available truck on that day. All the
     * containers are assigned to one of the tours.
     */
    @Override
    public void Construct() {
        
        // Open a tour for each available truck
        for (Truck truck : this.data.GetTrucks()) {
            if (truck.IsAvailable(0)) {
                Tour tour = new TourVRP(this.data, truck, this.cTracker, 0);
                tour.Construct();
                this.schedule.add(tour);
            }
        }
        // Distribute randomly all available containers to the tours
        while (!this.cTracker.GetAvblContainers(0).isEmpty()) {
            for (Tour tour : this.schedule) {
                tour.InsertRandomContainer();
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
        int nbContainersRemoved = 0;
        int nbContainersInserted = 0;
        long destroyOperatorStartTime = System.nanoTime();
        
        // Here, the methodology has been changed in order to have a ballanced
        // removal and insertion of containers (a VRP needs every container to
        // be visited). The repair operators regroup are the operators capable
        // of adding new container to the schedule; the destroy operators regroup
        // all of the other operators (the ones which remove containers and the
        // ones which change the solution without changing the total number of
        // containers visited).
        switch (destroyOpIndex) {
            case 0:
                nbContainersRemoved = this.removeRandomRhoContainers();
                break;
            case 1:
                nbContainersRemoved = this.removeWorstRhoContainers();
                break;
            case 2:
                nbContainersRemoved = this.removeShawContainers();
                break;
            case 3:
                nbContainersRemoved = this.emptyOneTruck();
                break;
            case 4:
                nbContainersRemoved = this.removeRandomDump();
                break;
            case 5:
                nbContainersRemoved = this.removeWorstDump();
                break;
            case 6:
                nbContainersRemoved = this.swapAssgContainers();
                break;
            case 7:
                nbContainersRemoved = this.insertRandomDump();
                break;
            case 8:
                nbContainersRemoved = this.insertBestDump();
                break;
            case 9:
                nbContainersRemoved = this.swapAssgDumps();
                break;
            case 10:
                nbContainersRemoved = this.replaceRandomDump();
                break;
            case 11:
                nbContainersRemoved = this.reorderDumps();
                break;
            default:
                System.out.println("Destroy operator index out of range...");
                break;
        }
        
        long destroyOperatorEndTime = System.nanoTime();
        
        // Apply the selected repair operator
        switch (repairOpIndex) {
            case 0:
                this.insertRandomRhoContainers();
                break;
            case 1:
                this.insertBestRhoContainers();
                break;
            case 2:
                this.insertShawContainers();
                break;
            default:
                System.out.println("Repair operator index out of range...");
                break;
        }
        
        // Check if all removed containers have been reinserted
        if (!this.cTracker.GetAvblContainers(0).isEmpty()) {
            System.err.println(Thread.currentThread().getStackTrace()[1].getClassName()
                    + "." + Thread.currentThread().getStackTrace()[1].getMethodName()
                    + "() says:");
            System.err.println(">> Not all removed containers been reinserted!");
        }
        
        // Timing of the operators
        long repairOperatorEndTime = System.nanoTime();
        long destroyOperatorDuration = (destroyOperatorEndTime - destroyOperatorStartTime); // nanosecond
        long repairOperatorDuration = (repairOperatorEndTime - destroyOperatorEndTime); // nanosecond
        
        // Do local search before applying acceptance criterion
        // this.DoLocalSearch();
        // Save the data to return in a Feedback Form
        FeedbackForm feedback = new FeedbackForm();
        feedback.setDurationData(new long[]{destroyOperatorDuration, repairOperatorDuration});
        feedback.setNbTimesAppliedData(new int[]{nbContainersRemoved, nbContainersInserted});
        
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
    
    /**
     * Selects randomly a tour and inserts a random container in it using the
     * best insertion principle. It is repeated to insert the given number of
     * containers.
     *
     * @return 1
     */
    @Override
    protected int insertRandomRhoContainers() {
        
        // Insert rho random containers
        while (!this.cTracker.GetAvblContainers(0).isEmpty()) {
            // Select one random tour
            int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
            Tour tour = this.schedule.get(tourIndex);
            
            // Insert a random container in the tour
            tour.InsertRandomContainer();
        }
        return 1;
    }
    
    /**
     * Selects a random container and inserts it in the best position in the
     * schedule, considering the total cost. It is repeated until all containers
     * are inserted.
     *
     * @return 1
     */
    @Override
    protected int insertBestRhoContainers() {
        
        // Insert rho random containers
        while (!this.cTracker.GetAvblContainers(0).isEmpty()) {
            
            // Select a random container from the data
            int randIndex = this.data.GetRand().nextInt(this.cTracker.GetAvblContainers(0).size());
            Point randContainer = this.cTracker.GetAvblContainers(0).get(randIndex);
            
            // The tour index and position of the container that would
            // lead to the minimum increase in the total schedule cost when inserted
            int tourIndex = Parameters._404;
            int contIndex = Parameters._404;
            double minIncrease = Double.POSITIVE_INFINITY;
            
            // Loop over all tours in the schedule to evaluate the increase
            // obtainable from each of them if the container is inserted in the best position
            for (int j = 0; j < this.schedule.size(); j++) {
                ImmutablePair<Integer, Double> pair = this.schedule.get(j).FindBestContainerInsertion(randContainer);
                if (pair.getValue() < minIncrease) {
                    tourIndex = j;
                    contIndex = pair.getKey();
                    minIncrease = pair.getValue();
                }
            }
            // If feasible, insert container
            if (tourIndex != Parameters._404 && contIndex != Parameters._404) {
                this.schedule.get(tourIndex).InsertPoint(contIndex, randContainer);
            }
        }
        return 1;
    }
    
    /**
     * Inserts customers that are close to each other (Shaw, 1997). In
     * paricular, it randomly selects a tour and a container not served in it.
     * It calculates the distance dist_min to the nearest container. It inserts
     * in the tour all containers within 2 * dist_min that are not already
     * served in the tour using the best insertion principle. Repeat until all
     * available containers are inserted.
     *
     * @return 1
     */
    @Override
    protected int insertShawContainers() {
        
        while (!this.cTracker.GetAvblContainers(0).isEmpty()) {
            // Select one random tour
            int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
            Tour tour = this.schedule.get(tourIndex);
            // Perform Shaw insertion
            tour.InsertShawContainers();
        }
        
        return 1;
    }
}
