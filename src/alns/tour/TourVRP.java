package alns.tour;

import alns.schedule.ContainerTracker;
import alns.data.Data;
import alns.data.Point;
import alns.data.Truck;

/**
 * Tour implementation for the vehicle routing problem (VRP).
 *
 * @author Markov
 * @version 1.0
 */
public class TourVRP extends Tour {

    /**
     * Implements the Tour assignment constructor.
     *
     * @param data Data object
     * @param truck Truck object
     * @param cTracker ContainerTracker object
     * @param day day in the planning horizon on which the tour is performed
     */
    public TourVRP(Data data, Truck truck, ContainerTracker cTracker, int day) {

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
    public TourVRP(Tour tour, ContainerTracker cTracker) {

        // Pass to super constructor
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
     * Implements the Tour overflow cost attribution method. Returns 0 as the
     * VRP implementation considers no overflow cost.
     *
     * @return 0
     */
    @Override
    protected double getOverflowCostAttr() {
        return 0.d;
    }

    /**
     * Implements the Tour total backorder limit violation method. For the VRP,
     * it the sum of volume loads on day 0 for containers that are not served on
     * day 0.
     *
     * @return the Tour total backorder limit violation as the sum of volume
     * loads on day 0 for containers that are not served on day 0
     */
    @Override
    protected double getBackorderViolation() {

        double backorderViolation = 0.d;

        for (Point container : this.data.GetContainers()) {
            if (this.cTracker.GetVisit(container, 0) == false) {
                backorderViolation += this.cTracker.GetVolumeLoad(container, 0);
            }
        }

        return backorderViolation;
    }

    /**
     * Implements the Tour container violation (overflow) method. Returns 0 as
     * the VRP implementation considers no container violation (overflow).
     *
     * @return 0
     */
    @Override
    protected double getContViolation() {
        return 0.d;
    }

    /**
     * Implements the Tour inventory holding cost method. Returns 0 as the VRP
     * implementation considers no inventory holding cost.
     *
     * @return 0
     */
    @Override
    protected double getInventoryHoldingCost() {
        return 0.d;
    }

    /**
     * Implements the Tour route failure cost method. Returns 0 as the VRP
     * implementation considers no route failure.
     *
     * @return 0
     */
    @Override
    public double GetRouteFailureCost() {
        return 0.d;
    }
}
