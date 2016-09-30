package alns.tour;

import alns.data.Data;
import alns.data.Point;
import alns.data.Truck;
import alns.param.Parameters;
import alns.schedule.ContainerTracker;

/**
 * Tour implementation for the traveling salesman problem (TSP).
 *
 * @author Markov
 * @version 1.0
 */
public class TourTSP extends Tour {

    /**
     * Implements the Tour assignment constructor.
     *
     * @param data Data object
     * @param truck Truck object
     * @param cTracker ContainerTracker object
     * @param day day in the planning horizon on which the tour is performed
     * @param implicitNumTrucks implicit number of trucks
     */
    public TourTSP(Data data, Truck truck, ContainerTracker cTracker, int day, int implicitNumTrucks) {

        // Call super constructor
        super(data, truck, cTracker, day);

        // Initialize implicit number of trucks
        this.implicitNumTrucks = implicitNumTrucks;

        // Assign origin and destination starting point as the truck's home depot
        this.originStartingPoint = this.truck.GetHomeStartingPoint();
        this.destinationStartingPoint = this.truck.GetHomeStartingPoint();

    }

    /**
     * Implements the Tour copy constructor.
     *
     * @param tour Tour object
     * @param cTracker ContainerTracker object
     */
    public TourTSP(Tour tour, ContainerTracker cTracker) {

        // Pass to super constructor
        super(tour, cTracker);

        // Copy implicit number of trucks
        this.implicitNumTrucks = tour.implicitNumTrucks;

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
     * Implements the Tour volume violation method. Returns 0 as the TSP
     * implementation considers no volume violation.
     *
     * @return 0
     */
    @Override
    public double GetVolViolation() {
        return 0.d;
    }

    /**
     * Implements the Tour weight violation method. Returns 0 as the TSP
     * implementation considers no weight violation.
     *
     * @return 0
     */
    @Override
    public double GetWeightViolation() {
        return 0.d;
    }

    /**
     * Implements the Tour time window violation method. Returns 0 as the TSP
     * implementation considers no time window violation.
     *
     * NB: Always call before GetDurViolation()
     *
     * @return 0
     */
    @Override
    public double GetTWViolation() {
        return 0.d;
    }

    /**
     * Implements the Tour duration violation method. The difference is that the
     * maximum tour duration is multiplied by the implicit number of trucks in
     * the tour, which is equal to the number of actually available trucks on
     * this day.
     *
     * @return tour duration violation
     */
    @Override
    public double GetDurViolation() {
        return Math.max(0, this.GetDuration() - this.implicitNumTrucks * this.data.GetTourMaxDur());
    }

    /**
     * Implements the Tour accessibility violation method. Returns 0 as the TSP
     * implementation considers no accessibility violation.
     *
     * @return 0
     */
    @Override
    public int GetAccessViolation() {
        return 0;
    }

    /**
     * Implements the Tour home depot violation method. Returns 0 as the TSP
     * implementation considers no home depot violation.
     *
     * @return 0
     */
    @Override
    public int GetHomeDepotViolation() {
        return 0;
    }

    /**
     * Implements the Tour spDumpFeasible method. But instead of checking for a
     * dump at the position before the last starting point, it checks for a
     * starting point there as well.
     *
     * @return true if the tour has a starting point at the beginning and two
     * starting points at the end, false otherwise
     */
    @Override
    protected boolean spDumpFeasible() {

        boolean spFeasible = true;

        if (this.tour.get(0).Is() != Parameters.pointIsSP
                || this.tour.get(this.tour.size() - 2).Is() != Parameters.pointIsSP
                || this.tour.get(this.tour.size() - 1).Is() != Parameters.pointIsSP) {
            spFeasible = false;
        }

        return spFeasible;
    }

    /**
     * Implements the Tour construct method. Construct a tour with a starting
     * point at the beginning and two starting points at the end. The reason for
     * the two starting points at the end is to avoid overriding the operators
     * from the abstract class which assume a dump and a starting point at the
     * end.
     */
    @Override
    public void Construct() {

        // Add an origin starting point, and twice the destination starting point
        // as in the TSP tour we consider no visits to dumps.
        this.InsertPoint(0, this.originStartingPoint);
        this.InsertPoint(1, this.destinationStartingPoint);
        this.InsertPoint(2, this.destinationStartingPoint);
    }

    /**
     * Implements the Tour duration method. Since the TSP considers no time
     * windows, this is just the travel time plus service time at all points.
     *
     * @return tour duration as travel time plus service time at all points
     */
    @Override
    public double GetDuration() {

        double tourDuration = this.GetLength() / this.truck.GetSpeed();
        for (Point point : this.tour) {
            tourDuration += point.GetServiceDuration();
        }
        return tourDuration;
    }
}
