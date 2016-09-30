package alns.tour;

import alns.data.Data;
import alns.data.Truck;
import alns.schedule.ContainerTracker;

/**
 * Tour implementation for the inventory routing problem (IRP).
 *
 * @author Markov
 * @version 1.0
 */
public class TourIRP extends Tour {

    /**
     * Implements the Tour assignment constructor.
     *
     * @param data Data object
     * @param truck Truck object
     * @param cTracker ContainerTracker object
     * @param day day in the planning horizon on which the tour is performed
     */
    public TourIRP(Data data, Truck truck, ContainerTracker cTracker, int day) {

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
    public TourIRP(Tour tour, ContainerTracker cTracker) {

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
}
