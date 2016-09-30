package alns.tour;

import alns.algo.Penalties;
import alns.data.Data;
import alns.data.Point;
import alns.data.Truck;
import alns.param.Parameters;
import alns.schedule.ContainerTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jgrapht.WeightedGraph;
import org.jgrapht.alg.BellmanFordShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

/**
 * Abstract class for the tour, representing the collection of points served by
 * a vehicle on a given day. Has implementations for the inventory routing
 * problem (IRP) and the benchmark inventory routing problem (IRPA/C), the
 * traveling salesman problem (TSP), and the vehicle routing problem (VRP).
 *
 * @author Markov
 * @version 2.0
 */
public abstract class Tour {

    // Day on which the tour is performed
    protected int day;

    // Origin and destination starting points
    Point originStartingPoint = null;
    Point destinationStartingPoint = null;

    // Number of inaccessible visits in the tour
    protected int numInaccVisits;

    // Number of containers and dump visits in the tour
    protected int numContainers;
    protected int numDumpVisits;

    // Data 
    protected final Data data;

    // Truck 
    protected final Truck truck;
    // Implicit number of trucks (Only used in TSP implementation)
    protected int implicitNumTrucks;

    // Container tracker
    protected final ContainerTracker cTracker;

    // Tour as ArrayList of Point objects (starting points, containers, dumps)
    protected final ArrayList<Point> tour;

    /**
     * Tour assignment constructor. Is overridden in the class implementations.
     *
     * @param data Data object
     * @param truck Truck object
     * @param cTracker ContainerTracker object
     * @param day day in the planning horizon on which the tour is performed
     */
    public Tour(Data data, Truck truck, ContainerTracker cTracker, int day) {

        // Assignments
        this.data = data;
        this.truck = truck;
        this.cTracker = cTracker;
        this.day = day;

        // Initialize number of containers and dump visits as 0s for a new tour
        this.numContainers = 0;
        this.numDumpVisits = 0;

        // An empty tour has 0 inaccessible visits
        this.numInaccVisits = 0;

        // Initialize tour as a new object
        this.tour = new ArrayList<>();
    }

    /**
     * Tour copy constructor. Is overridden in the class implementations.
     *
     * @param tour Tour object
     * @param cTracker ContainerTracker object
     */
    public Tour(Tour tour, ContainerTracker cTracker) {

        // Copy tour fields
        this.data = tour.data;
        this.truck = tour.truck;
        this.day = tour.day;

        // Assign new container tracker
        this.cTracker = cTracker;

        // Tour is rebuilt from scratch, therefore 0 containers and dump visits
        this.numContainers = 0;
        this.numDumpVisits = 0;

        // Tour is rebuilt from scratch, therefore 0 inaccessible visits
        this.numInaccVisits = 0;

        // Initialize tour as a new object
        this.tour = new ArrayList<>(tour.GetSize());
    }

    /**
     * Sets the tour's origin starting point.
     *
     * @param originStartingPoint starting point Point object
     */
    public void SetOriginStartingPoint(Point originStartingPoint) {
        this.originStartingPoint = originStartingPoint;
    }

    /**
     * Sets the tour's destination starting point.
     *
     * @param destinationStartingPoint starting point Point object
     */
    public void SetDestinationStartingPoint(Point destinationStartingPoint) {
        this.destinationStartingPoint = destinationStartingPoint;
    }

    /**
     * Reduces tour waiting times by eliminating forward slack times in the tour
     * start-of-service times. As a result total tour duration may be reduced.
     * This method preserve time window feasibility.
     */
    protected void reduceWaitingTimes() {

        // Loop over the tour in a backward direction and if point i has a non-zero waiting time
        // move the SST of point (i - 1) forward until waiting time at point i is eliminated
        // or until the SST of point (i - 1) reaches its upper time window bound
        for (int i = this.tour.size() - 1; i > 0; i--) {

            // Check waiting time at point i
            if (this.tour.get(i).GetWaitingTime() > 0.d) {

                // Keep track of old SST at point (i - 1)
                double oldSST_iminus1 = this.tour.get(i - 1).GetSST();
                // Update SST at point (i - 1) until waiting time at point i is eliminated
                // or until the SST of point (i - 1) reaches its upper time window bound
                this.tour.get(i - 1).SetSST(Math.min(oldSST_iminus1 + this.tour.get(i).GetWaitingTime(),
                        this.tour.get(i - 1).GetTWUpper()));

                // Update waiting time at i and (i - 1) by the amount SST at (i - 1) was shifted
                // For this purpose we use the old SST we retrieved above
                double shiftAmount = this.tour.get(i - 1).GetSST() - oldSST_iminus1;
                this.tour.get(i).SetWaitingTime(this.tour.get(i).GetWaitingTime() - shiftAmount);
                this.tour.get(i - 1).SetWaitingTime(this.tour.get(i - 1).GetWaitingTime() + shiftAmount);
            }
        }

        // Finally eliminate any waiting time that accrued at the origin
        this.tour.get(0).SetWaitingTime(0.d);
    }

    /**
     * Updates tour start-of-service times, enforcing lower time window bounds.
     *
     * @return true if tour has waiting time
     */
    protected boolean updateSSTs() {

        // Flag that denotes the existence or not of waiting times in the tour
        boolean tourHasWaitingTime = false;

        // Start tour at the default tour start time (earliest possible)
        // Introduce zero waiting time at origin
        this.tour.get(0).SetSST(Math.max(this.tour.get(0).GetTWLower(), this.data.GetTourStartTime()));
        this.tour.get(0).SetWaitingTime(0.d);

        // Successively calculate SST for each point in the tour. If SST violates
        // lower time window bound, shift SST to lower time window bound.
        for (int i = 1; i < this.tour.size(); i++) {

            // Set SST at point i based on SST at point (i - 1), service duration at point (i - 1) and
            // travel time between point (i - 1) and point i
            this.tour.get(i).SetSST(this.tour.get(i - 1).GetSST() + this.tour.get(i - 1).GetServiceDuration()
                    + this.data.GetTravelTime(this.tour.get(i - 1), this.tour.get(i), this.truck.GetSpeed()));

            // If start-of-service time is before lower time window bound, move it to the bound
            // and update waiting time. Otherwise set waiting time to zero
            if (this.tour.get(i).GetSST() < this.tour.get(i).GetTWLower()) {
                tourHasWaitingTime = true;
                this.tour.get(i).SetWaitingTime(this.tour.get(i).GetTWLower() - this.tour.get(i).GetSST());
                this.tour.get(i).SetSST(this.tour.get(i).GetTWLower());
            } else {
                this.tour.get(i).SetWaitingTime(0.d);
            }
        }

        // Returns true if there is waiting time in the tour
        return tourHasWaitingTime;
    }

    /**
     * Returns the violation of the truck's maximum volume as a sum of the
     * excess volume prior to each dump visit. Is overridden in the TSP
     * implementation.
     *
     * @return the violation of the truck's maximum volume as a sum of the
     * excess volume prior to each dump visit
     */
    public double GetVolViolation() {

        double currVolumeLoad = 0.d;
        double volumeViolation = 0.d;

        for (Point point : this.tour) {
            if (point.Is() == Parameters.pointIsContainer) {
                currVolumeLoad += this.cTracker.GetVolumeLoad(point, this.day);
            } else {
                volumeViolation += Math.max(0.d, currVolumeLoad - this.truck.GetMaxEffectiveVolume());
                currVolumeLoad = 0.d;
            }
        }

        // Return volume violation
        return volumeViolation;
    }

    /**
     * Returns the violation of the truck's maximum weight as a sum of the
     * excess weight prior to each dump visit. Is overridden in the TSP
     * implementation.
     *
     * @return the violation of the truck's maximum weight as a sum of the
     * excess weight prior to each dump visit
     */
    public double GetWeightViolation() {

        double currWeightLoad = 0.d;
        double weightViolation = 0.d;

        for (Point point : this.tour) {
            if (point.Is() == Parameters.pointIsContainer) {
                currWeightLoad += this.cTracker.GetWeightLoad(point, this.day);
            } else {
                weightViolation += Math.max(0.d, currWeightLoad - this.truck.GetMaxEffectiveWeight());
                currWeightLoad = 0.d;
            }
        }

        // Return weight violation
        return weightViolation;
    }

    /**
     * Returns the tour's time window violation as the sum of upper time window
     * violations for all points. Lower time window violations do not happen
     * because if the truck arrives earlier, it waits. Is overridden in the TSP
     * implementation.
     *
     * NB: Always call before GetDurViolation()
     *
     * @return the tour's time window violation as the sum of upper time window
     * violations for all points
     */
    public double GetTWViolation() {

        // Update start-of-service times, and return a boolean indicating the presence of waiting times
        boolean tourHasWaitingTime = this.updateSSTs();
        // Initialize time window violations
        double twViolation = 0.d;

        // Check violation for every point and sum up
        for (Point point : this.tour) {
            twViolation += Math.max(0.d, point.GetSST() - Math.min(point.GetTWUpper(), this.data.GetTourEndTime()));
        }

        // If tour has waiting time and no time window violations, reduce forward slack
        // Forward slack reduction preserves time window feasibility and potentially 
        // reduces duration. Therefore, duration violations should be checked after 
        // time window violations.
        if (tourHasWaitingTime && twViolation == 0.d) {
            this.reduceWaitingTimes();
        }

        // Return time window violation
        return twViolation;
    }

    /**
     * Returns the violation of the tour's maximum duration as the excess
     * duration. Is overridden in the TSP implementation.
     *
     * NB: Always call after GetTWViolation().
     *
     * @return the violation of the tour's maximum duration as the excess
     * duration
     */
    public double GetDurViolation() {
        return Math.max(0.d, this.GetDuration() - this.data.GetTourMaxDur());
    }

    /**
     * Returns the number of inaccessible visits in the tour. Is overridden in
     * the TSP implementation.
     *
     * @return the number of inaccessible visits in the tour
     */
    public int GetAccessViolation() {
        return this.numInaccVisits;
    }

    /**
     * Returns 1 if the tour does not finish at the truck's home starting point
     * when it is required to do so, 0 otherwise.
     *
     * @return 1 if the tour does not finish at the truck's home starting point
     * when it is required to do so, 0 otherwise
     */
    public int GetHomeDepotViolation() {

        if (this.destinationStartingPoint.GetDWid() != this.truck.GetHomeStartingPoint().GetDWid()
                && this.truck.RequiredReturnToHome(this.day) == true) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Returns true if the tour has a starting point at the beginning and the
     * end and a dump before the final starting point, false otherwise.
     *
     * @return true if the tour has a starting point at the beginning and the
     * end and a dump before the final starting point, false otherwise
     */
    protected boolean spDumpFeasible() {

        boolean spDumpFeasible = true;

        if (this.tour.get(0).Is() != Parameters.pointIsSP
                || this.tour.get(this.tour.size() - 2).Is() != Parameters.pointIsDump
                || this.tour.get(this.tour.size() - 1).Is() != Parameters.pointIsSP) {
            spDumpFeasible = false;
        }

        return spDumpFeasible;
    }

    /**
     * Returns tour cost in monetary units as truck fixed cost + truck running
     * cost * tour length + truck time cost * tour duration + route failure cost
     * + all tour-based penalties.
     *
     * @return tour cost in monetary units
     */
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

        // If there is at least one container in the tour, calculate cost in full, 
        double tourCost = 0.d;
        if (this.GetNumContainers() > 0) {
            // Include the penalties first, and then the actual costs. The reason is 
            // that for the correct duration, we need to update SST first, which is 
            // done in the function calculating time window violation.
            Penalties penalties = this.data.GetPenalties();
            tourCost = penalties.GetVolumeViolPenalty() * this.GetVolViolation();
            tourCost += penalties.GetWeightViolPenalty() * this.GetWeightViolation();
            tourCost += penalties.GetTWViolPenalty() * this.GetTWViolation();
            tourCost += penalties.GetDurViolPenalty() * this.GetDurViolation();
            tourCost += penalties.GetAccessViolPenalty() * this.GetAccessViolation();
            tourCost += penalties.GetHomeDepotViolPenalty() * this.GetHomeDepotViolation();
            tourCost += this.truck.GetFixedCost();
            tourCost += this.truck.GetDistanceCost() * this.GetLength();
            tourCost += this.truck.GetTimeCost() * this.GetDuration();
            tourCost += this.GetRouteFailureCost();

        } else if (this.originStartingPoint.GetDWid()
                != this.destinationStartingPoint.GetDWid()) {
            // If there are no containers in the tour, but the origin and destination
            // are different, we need to pay for the relocation. Otherwise, if the origin and destination 
            // are the same and there are no containers, we can consider the tour as non-executed with cost of 0.
            // Currently, we calculate the relocation cost by taking into account the fixed cost and the distance
            // and time costs of moving from the origin to the destination starting point. 
            Penalties penalties = this.data.GetPenalties();
            tourCost = penalties.GetTWViolPenalty() * this.GetTWViolation();
            tourCost += penalties.GetDurViolPenalty() * this.GetDurViolation();
            tourCost += penalties.GetHomeDepotViolPenalty() * this.GetHomeDepotViolation();
            tourCost += this.truck.GetFixedCost();
            tourCost += this.truck.GetDistanceCost() * this.data.GetDistance(this.originStartingPoint, this.destinationStartingPoint);
            tourCost += this.truck.GetTimeCost() * this.data.GetTravelTime(this.originStartingPoint, this.destinationStartingPoint, this.truck.GetSpeed());
        }

        // Return cost
        return tourCost;
    }

    /**
     * Returns tour final routing cost.
     *
     * @return tour final routing cost
     */
    public double GetTourFinalRoutingCost() {

        // If there is at least one container in the tour, calculate routing cost in full;
        // otherwise calculate relocation cost if origin and destination depots are not the same
        double tourFinalRoutingCost = 0.d;
        if (this.GetNumContainers() > 0) {
            tourFinalRoutingCost += this.truck.GetFixedCost();
            tourFinalRoutingCost += this.truck.GetDistanceCost() * this.GetLength();
            tourFinalRoutingCost += this.truck.GetTimeCost() * this.GetDuration();
        } else if (this.originStartingPoint.GetDWid()
                != this.destinationStartingPoint.GetDWid()) {
            tourFinalRoutingCost += this.truck.GetFixedCost();
            tourFinalRoutingCost += this.truck.GetDistanceCost() * this.data.GetDistance(this.originStartingPoint, this.destinationStartingPoint);
            tourFinalRoutingCost += this.truck.GetTimeCost() * this.data.GetTravelTime(this.originStartingPoint, this.destinationStartingPoint, this.truck.GetSpeed());
        }

        // Return cost
        return tourFinalRoutingCost;

    }

    /**
     * Returns tour final route failure cost.
     *
     * @return tour final route failure cost
     */
    public double GetTourFinalRouteFailureCost() {

        // If there is at least one container in the tour, calculate final route failure cost, otherwise do nothing
        double tourFinalRouteFailureCost = 0.d;
        if (this.GetNumContainers() > 0) {
            tourFinalRouteFailureCost += this.GetRouteFailureCost();
        }

        // Return cost
        return tourFinalRouteFailureCost;
    }

    /**
     * Returns a simple tour hashcode.
     *
     * @return a simple tour hashcode
     */
    public int GetHashCode() {
        return (this.GetDWids().hashCode());
    }

    /**
     * Returns the total backorder limit violation as the sum of volume capacity
     * violation on day 0 for containers that are not served on day 0.
     *
     * @return the total backorder limit violation as the sum of volume capacity
     * violation on day 0 for containers that are not served on day 0.
     */
    protected double getBackorderViolation() {

        double backorderViolation = 0.d;

        for (Point container : this.data.GetContainers()) {
            if (this.cTracker.GetContainerViolation(container, 0) > 0
                    && this.cTracker.GetVisit(container, 0) == false) {
                backorderViolation += this.cTracker.GetContainerViolation(container, 0);
            }
        }

        return backorderViolation;
    }

    /**
     * Returns the total violation (overflow) of the containers' volume
     * capacities on days 1 to phLength + 1.
     *
     * @return the total violation (overflow) of the containers' volume
     * capacities on days 1 to phLength + 1
     */
    protected double getContViolation() {

        double contViolation = 0.d;

        for (Point container : this.data.GetContainers()) {
            for (int d = 1; d < this.data.GetPhLength() + 1; d++) {
                contViolation += this.cTracker.GetContainerViolation(container, d);
            }
        }

        return contViolation;
    }

    /**
     * Returns the inventory holding cost for periods 1 to phLength + 1. It is
     * currently set to 0 and not relevant. However, the code is kept for
     * testing purposes and for possible future extensions.
     *
     * @return 0
     */
    protected double getInventoryHoldingCost() {
        return 0.d;
    }

    /**
     * Returns the overflow cost attribution as a sum of container overflow cost
     * attributions for day 0 to phLength + 1.
     *
     * @return the overflow cost attribution as a sum of container overflow cost
     * attributions for day 0 to phLength + 1
     */
    protected double getOverflowCostAttr() {

        double scheduleOverflowCostAttr = 0.d;

        for (Point container : this.data.GetContainers()) {
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                scheduleOverflowCostAttr += this.cTracker.GetOverflowCostAttr(container, d);
            }
        }

        return scheduleOverflowCostAttr;
    }

    /**
     * Returns tour cost plus backorder violation penalty, container violation
     * (overflow) penalty, inventory holding cost, and overflow penalty
     * attribution. This is used to measure the effect of tour changes to the
     * total solution cost.
     *
     * @return tour cost plus backorder violation penalty, container violation
     * (overflow) penalty, inventory holding cost, and overflow penalty
     * attribution
     */
    protected double getEffectiveCost() {

        // Tour cost
        double tourCost = this.GetCost();
        // Cost components not related to tour: backorder violation penalty, 
        // container violation (overflow) penalty, inventory holding cost  
        // and overflow cost attribution
        Penalties penalties = this.data.GetPenalties();
        tourCost += penalties.GetBackorderViolPenalty() * this.getBackorderViolation();
        tourCost += penalties.GetContViolPenalty() * this.getContViolation();
        tourCost += this.getInventoryHoldingCost();
        tourCost += this.getOverflowCostAttr();

        return tourCost;
    }

    /**
     * Construct a tour with a starting point at the beginning and the end, and
     * one dump visit of the cheapest dump. Is overridden in the TSP
     * implementation.
     */
    public void Construct() {

        // Insert origin and destination starting points
        this.InsertPoint(0, this.originStartingPoint);
        this.InsertPoint(1, this.destinationStartingPoint);

        // Add the best dump in between
        Point bestDump = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (Point dump : this.data.GetDumps()) {
            this.insertTemp(1, dump);
            double newCost = this.getEffectiveCost();
            this.RemovePoint(1);
            if (newCost < bestCost) {
                bestCost = newCost;
                bestDump = dump;
            }
        }
        this.InsertPoint(1, bestDump);
    }

    /**
     * Returns the containers served by this tour as Point objects.
     *
     * @return the containers served by this tour as Point objects
     */
    public ArrayList<Point> GetContainers() {

        ArrayList<Point> containers = new ArrayList<>();

        for (Point point : this.tour) {
            if (point.Is() == Parameters.pointIsContainer) {
                containers.add(new Point(point));
            }
        }

        return containers;
    }

    /**
     * Returns tour point types, ie whether the point is a starting point,
     * container or dump.
     *
     * @return tour point types
     */
    public ArrayList<Short> GetAreWhat() {

        ArrayList<Short> tourAreWhat = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourAreWhat.add(point.Is());
        }

        return tourAreWhat;
    }

    /**
     * Returns tour point distance matrix wids (dwids), ie starting point and
     * dump wids and container ecopoint wids.
     *
     * @return tour point distance matrix wids (dwids)
     */
    public ArrayList<Integer> GetDWids() {

        ArrayList<Integer> tourDWids = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourDWids.add(point.GetDWid());
        }

        return tourDWids;
    }

    /**
     * Returns the tour's origin starting point.
     *
     * @return the tour's origin starting point
     */
    public Point GetOriginStartingPoint() {
        return this.originStartingPoint;
    }

    /**
     * Returns the tour's destination starting point.
     *
     * @return the tour's destination starting point
     */
    public Point GetDestinationStartingPoint() {
        return this.destinationStartingPoint;
    }

    /**
     * Returns tour point latitudes.
     *
     * @return tour point latitudes
     */
    public ArrayList<Double> GetLats() {

        ArrayList<Double> tourLats = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourLats.add(point.GetLat());
        }

        return tourLats;
    }

    /**
     * Returns tour point longitudes.
     *
     * @return tour point longitudes
     */
    public ArrayList<Double> GetLons() {

        ArrayList<Double> tourLons = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourLons.add(point.GetLon());
        }

        return tourLons;
    }

    /**
     * Returns tour point service durations in hours.
     *
     * @return tour point service durations in hours
     */
    public ArrayList<Double> GetServiceDurations() {

        ArrayList<Double> tourServiceDurations = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourServiceDurations.add(point.GetServiceDuration());
        }

        return tourServiceDurations;
    }

    /**
     * Returns tour point time window lower bounds in hours since midnight.
     *
     * @return tour point time window lower bounds in hours since midnight
     */
    public ArrayList<Double> GetTWLowers() {

        ArrayList<Double> tourTWLowers = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourTWLowers.add(point.GetTWLower());
        }

        return tourTWLowers;
    }

    /**
     * Returns tour point time window upper bounds in hours since midnight.
     *
     * @return tour point time window upper bounds in hours since midnight
     */
    public ArrayList<Double> GetTWUppers() {

        ArrayList<Double> tourTWUppers = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourTWUppers.add(point.GetTWUpper());
        }

        return tourTWUppers;
    }

    /**
     * Returns tour point zone wids.
     *
     * @return tour point zone wids
     */
    public ArrayList<Integer> GetZoneWids() {

        ArrayList<Integer> tourZoneWids = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourZoneWids.add(point.GetZoneWid());
        }

        return tourZoneWids;
    }

    /**
     * Returns tour point flow type wids.
     *
     * @return tour point flow type wids
     */
    public ArrayList<Integer> GetFlowWids() {

        ArrayList<Integer> tourFlowWids = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourFlowWids.add(point.GetFlowWid());
        }

        return tourFlowWids;
    }

    /**
     * Returns tour point container wids; and a default value in case of
     * starting points and dumps.
     *
     * @return tour point container wids
     */
    public ArrayList<Integer> GetContWids() {

        ArrayList<Integer> tourContWids = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourContWids.add(point.GetContWid());
        }

        return tourContWids;
    }

    /**
     * Returns tour point container type wids; and a default value in case of
     * starting points and dumps.
     *
     * @return tour point container type wids
     */
    public ArrayList<Integer> GetContTypeWids() {

        ArrayList<Integer> tourContTypeWids = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourContTypeWids.add(point.GetContTypeWid());
        }

        return tourContTypeWids;
    }

    /**
     * Returns tour point ecolog wids; and a default value in case of starting
     * points and dumps.
     *
     * @return tour point ecolog wids
     */
    public ArrayList<Integer> GetEcologWids() {

        ArrayList<Integer> tourEcologWids = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourEcologWids.add(point.GetEcologWid());
        }

        return tourEcologWids;
    }

    /**
     * Returns tour point volumes in liters in case of containers; and a default
     * value in case of starting points and dumps.
     *
     * @return tour point volumes in liters
     */
    public ArrayList<Double> GetVolumes() {

        ArrayList<Double> tourVolumes = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourVolumes.add(point.GetVolume());
        }

        return tourVolumes;
    }

    /**
     * Returns tour point locations.
     *
     * @return tour point locations
     */
    public ArrayList<String> GetLocations() {

        ArrayList<String> tourLocations = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourLocations.add(point.GetLocation());
        }

        return tourLocations;
    }

    /**
     * Returns tour point cumulative volume loads in liters in the vehicle
     *
     * @return tour point cumulative volume loads in liters in the vehicle
     */
    public ArrayList<Double> GetCumuVolumeLoads() {

        ArrayList<Double> cumuVolumeLoads = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            if (point.Is() == Parameters.pointIsContainer) {
                cumuVolumeLoads.add(cumuVolumeLoads.get(cumuVolumeLoads.size() - 1)
                        + this.cTracker.GetVolumeLoad(point, this.day));
            } else {
                cumuVolumeLoads.add(0.d);
            }
        }

        return cumuVolumeLoads;
    }

    /**
     * Returns tour point cumulative weight loads in kg in the vehicle
     *
     * @return tour point cumulative weight loads in kg in the vehicle
     */
    public ArrayList<Double> GetCumuWeightLoads() {

        ArrayList<Double> cumuWeightLoads = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            if (point.Is() == Parameters.pointIsContainer) {
                cumuWeightLoads.add(cumuWeightLoads.get(cumuWeightLoads.size() - 1)
                        + this.cTracker.GetWeightLoad(point, this.day));
            } else {
                cumuWeightLoads.add(0.d);
            }
        }

        return cumuWeightLoads;
    }

    /**
     * Returns tour point start-of-service times as hours since midnight.
     *
     * @return tour point start-of-service times as hours since midnight
     */
    public ArrayList<Double> GetSSTs() {

        ArrayList<Double> tourSSTs = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourSSTs.add(point.GetSST());
        }

        return tourSSTs;
    }

    /**
     * Returns tour waiting times at each point in hours.
     *
     * @return tour waiting times at each point in hours
     */
    public ArrayList<Double> GetWaitingTimes() {

        ArrayList<Double> tourWaitingTimes = new ArrayList<>(this.tour.size());

        for (Point point : this.tour) {
            tourWaitingTimes.add(point.GetWaitingTime());
        }

        return tourWaitingTimes;
    }

    /**
     * Returns the truck reference of the truck servicing this tour.
     *
     * @return the truck reference of the truck servicing this tour
     */
    public Truck GetTruck() {
        return this.truck;
    }

    /**
     * Returns the day in the planning horizon for which the tour is performed.
     *
     * @return the day in the planning horizon for which the tour is performed
     */
    public int GetDay() {
        return this.day;
    }

    /**
     * Returns tour length in km.
     *
     * @return tour length in km
     */
    public double GetLength() {

        double tourLength = 0;

        for (int i = 0; i < this.tour.size() - 1; i++) {
            tourLength += this.data.GetDistance(this.tour.get(i), this.tour.get(i + 1));
        }

        return tourLength;
    }

    /**
     * Returns tour duration in hours. Is overridden in the VRP implementation.
     *
     * @return tour duration in hours
     */
    public double GetDuration() {

        // Tour duration is the SST at the last point plus the service duration at the
        // last point minus the SST at the first point. Even thourgh we assume service
        // duration at starting points to be 0, we still add it to the formula to
        // stay general
        return this.tour.get(this.tour.size() - 1).GetSST()
                + this.tour.get(this.tour.size() - 1).GetServiceDuration()
                - this.tour.get(0).GetSST();
    }

    /**
     * Returns the cumulative normal probability of X < x.
     *
     * @param x real number
     * @return the cumulative normal probability of X < x
     */
    protected double CNDF(double x) {

        int neg = (x < 0.d) ? 1 : 0;
        if (neg == 1) {
            x *= -1.d;
        }

        double k = 1.d / (1.d + 0.2316419d * x);
        double y = ((((1.330274429d * k - 1.821255978d) * k + 1.781477937d)
                * k - 0.356563782d) * k + 0.319381530d) * k;
        y = 1.d - 0.398942280401d * Math.exp(-0.5d * x * x) * y;

        return (1.d - neg) * y + neg * (1.d - y);
    }

    /**
     * Returns the tour's route failure cost. Route failure cost is 0 for day =
     * 0 because all the information is fully known.
     *
     * @return the tour's route failure cost
     */
    public double GetRouteFailureCost() {

        // Initialize route failure as 0
        double routeFailureCost = 0.d;

        // Compute only if the tour's day is greater than 0
        if (this.day > 0) {

            // Since we rely only on error sigma which is based on volume,
            // we consider the most restrictive of the truck's volume and weight capacity,
            // in the last case transformed into volume using the flow's specific weight
            double truckVolume = Math.min(this.truck.GetMaxEffectiveVolume(),
                    this.truck.GetMaxEffectiveWeight() * Parameters.flowSpecWeightCF / this.data.GetFlowSpecWeight());

            // Number of random demand days and number of containers
            int numRandomDemandDays = 0;
            int numCont = 0;

            // Cumulative volume load on the vehicle
            double cumuVolumeLoad = 0.d;
            // Average dump visit cost
            double avgDumpCost = 0.d;

            // Loop over all points in the tour
            for (Point point : this.tour) {

                // If the point is a container
                if (point.Is() == Parameters.pointIsContainer) {

                    // Increment the number of containers
                    numCont++;
                    // Increase the comulative volume load on the vehicle by the container volume load
                    cumuVolumeLoad += this.cTracker.GetVolumeLoad(point, this.day);
                    // Increase the number of random demand days by the number of random demand days for this container
                    numRandomDemandDays += this.cTracker.GetNumRandomDemandDays(point, this.day);

                    // Calculate the cost of a back and forth trip from the point to the nearest dump
                    double pointDumpDistance = point.GetClosestDumpBFDistance();
                    double pointDumpCost = this.truck.GetDistanceCost() * pointDumpDistance
                            + this.truck.GetTimeCost() * (pointDumpDistance / this.truck.GetSpeed());

                    // Update the average dump cost 
                    avgDumpCost += (pointDumpCost - avgDumpCost) / numCont;
                } else {
                    // If there was at least one container before this non-container point, calculate the 
                    // probability of the cumulative volume load on the vehicle exceeding its capacity. 
                    // The variance of the forecasting errors is mulitplied by the total number of random demand days.
                    if (numCont > 0) {
                        routeFailureCost += avgDumpCost * (1 - this.CNDF((truckVolume - cumuVolumeLoad)
                                / (Math.sqrt(numRandomDemandDays) * this.data.GetErrorSigma())));
                    }

                    // Reset number of random demand days and number of containers
                    numRandomDemandDays = 0;
                    numCont = 0;

                    // Reset cumulative volume load on the vehicle
                    cumuVolumeLoad = 0.d;
                    // Reset average dump visit cost
                    avgDumpCost = 0.d;
                }
            }
        }

        // Multiply route failure cost by its multiplication factor
        routeFailureCost *= this.data.GetRouteFailureCostMultiplier();

        // Return route failure cost
        return routeFailureCost;
    }

    /**
     * Returns tour size as total number of points.
     *
     * @return tour size as total number of points
     */
    public int GetSize() {
        return tour.size();
    }

    /**
     * Returns number of containers in tour.
     *
     * @return number of containers in tour
     */
    public int GetNumContainers() {
        return this.numContainers;
    }

    /**
     * Returns number of dump visits during the tour.
     *
     * @return number of dump visits during the tour
     */
    public int GetNumDumpVisits() {
        return this.numDumpVisits;
    }

    /**
     * Implements a swap-based local search on this tour.
     */
    public void DoLocalSearch() {

        // Only perform if there is more than one container in the tour
        if (this.GetNumContainers() > 1) {

            // Start an infinite local search loop
            while (true) {

                // Best cost, best i position, and best j position
                double bestCost = Double.POSITIVE_INFINITY;
                int bestI = Parameters._404;
                int bestJ = Parameters._404;

                // Inspect all possible swap positions
                for (int i = 1; i < this.tour.size() - 3; i++) {
                    for (int j = i + 1; j < this.tour.size() - 2; j++) {
                        // Evaluate only if both positions are containers
                        if (this.tour.get(i).Is() == Parameters.pointIsContainer
                                && this.tour.get(j).Is() == Parameters.pointIsContainer) {

                            // Swap points, evaluate, and swap back to original
                            this.SwapPoints(i, j);
                            double newCost = this.getEffectiveCost();
                            this.SwapPoints(i, j);

                            // If new cost is better than best cost,
                            // update best cost, best i position, and best j position
                            if (newCost < bestCost) {
                                bestCost = newCost;
                                bestI = i;
                                bestJ = j;
                            }
                        }
                    }
                }

                // If the best cost improves the current cost, implement the swap,
                // otherwise break loop
                if (bestCost < this.getEffectiveCost()) {
                    this.SwapPoints(bestI, bestJ);
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Updates the number of inaccessible points, the number of containers and
     * dump visits.
     *
     * @param point point that is being changed
     * @param inOut true if the point is being added to the tour, false if it is
     * being removed
     */
    protected void updateNumbers(Point point, boolean inOut) {

        if (inOut == true) {
            if (!point.IsAccessibleBy(this.truck)) {
                this.numInaccVisits++;
            }
            if (point.Is() == Parameters.pointIsContainer) {
                this.numContainers++;
            }
            if (point.Is() == Parameters.pointIsDump) {
                this.numDumpVisits++;
            }
        } else {
            if (!point.IsAccessibleBy(this.truck)) {
                this.numInaccVisits--;
            }
            if (point.Is() == Parameters.pointIsContainer) {
                this.numContainers--;
            }
            if (point.Is() == Parameters.pointIsDump) {
                this.numDumpVisits--;
            }
        }
    }

    /**
     * Returns a random container index from this tour if there are containers,
     * otherwise returns a default value.
     *
     * @return a random container index from this tour if there are containers,
     * otherwise returns a default value
     */
    public int GetRandContainerIndex() {

        // Retrieve container indexes in this tour
        ArrayList<Integer> containerIndexes = new ArrayList<>();
        for (int i = 1; i < this.tour.size() - 2; i++) {
            if (this.tour.get(i).Is() == Parameters.pointIsContainer) {
                containerIndexes.add(i);
            }
        }

        // If there are containers in the tour, return a random index; otherwise a default value
        if (!containerIndexes.isEmpty()) {
            return containerIndexes.get(this.data.GetRand().nextInt(containerIndexes.size()));
        } else {
            return Parameters._404;
        }
    }

    /**
     * Returns a random dump index from this tour, including or excluding the
     * last dump index.
     *
     * @param includeLast a boolean specifying whether the index of the last
     * dump should be included
     * @return a random dump index from this tour, including or excluding the
     * last dump index
     */
    protected int getRandDumpIndex(boolean includeLast) {

        // Specify the shift depending on whether the last dump
        // index is included or not
        int shift;
        if (includeLast) {
            shift = -1;
        } else {
            shift = -2;
        }

        // Retrieve dump indexes in the tour
        ArrayList<Integer> dumpIndexes = new ArrayList<>();
        for (int i = 1; i < this.tour.size() + shift; i++) {
            if (this.tour.get(i).Is() == Parameters.pointIsDump) {
                dumpIndexes.add(i);
            }
        }
        // Return the dump index or a default value
        if (!dumpIndexes.isEmpty()) {
            return dumpIndexes.get(this.data.GetRand().nextInt(dumpIndexes.size()));
        } else {
            return Parameters._404;
        }
    }

    /**
     * Returns a random dump index, including the index of the last dump
     *
     * @return a random dump index, including the index of the last dump
     */
    public int GetRandomDumpIndex() {
        return this.getRandDumpIndex(true);
    }

    /**
     * Returns a random number between min and max, inclusive of both.
     *
     * @param min lower bound
     * @param max upper bound
     * @return a random number between min and max, inclusive of both
     */
    protected int getRandomBetween(int min, int max) {
        return this.data.GetRand().nextInt((max - min) + 1) + min;
    }

    /**
     * Removes the point at the specified position from the tour, updates
     * numbers and tracking info.
     *
     * @param pos position of point
     * @return removed point
     */
    public Point RemovePoint(int pos) {
        // Update numbers 
        this.updateNumbers(this.tour.get(pos), false);
        // Update tracker
        this.cTracker.Update(this.tour.get(pos), this.day, false);
        // Remove and return point
        return this.tour.remove(pos);
    }

    /**
     * Removes the passed point if it is included in the tour, and if it is a
     * container.
     *
     * @param point Point object
     * @return the removed point if it is included in the tour, null otherwise
     */
    public Point RemovePoint(Point point) {

        // Check whether the point is a container, exists in the tour and at which position
        int pointPos = Parameters._404;
        if (point.Is() == Parameters.pointIsContainer) {
            for (int pos = 0; pos < this.tour.size(); pos++) {
                if (this.tour.get(pos).GetSimpleContWid() == point.GetSimpleContWid()) {
                    pointPos = pos;
                    break;
                }
            }
        }

        // Remove and return if it exists, otherwise return null
        if (pointPos != Parameters._404) {
            return this.RemovePoint(pointPos);
        } else {
            return null;
        }
    }

    /**
     * Swaps the positions of two points in the tour.
     *
     * @param pos1 position of first point
     * @param pos2 position of second point
     */
    public void SwapPoints(int pos1, int pos2) {
        Point pos1Point = this.tour.get(pos1);
        this.tour.set(pos1, this.tour.get(pos2));
        this.tour.set(pos2, pos1Point);
    }

    /**
     * Insert a deep copy of the passed point at the passed position in the
     * tour, updates numbers and tracking info.
     *
     * @param pos position in tour
     * @param point point to insert
     */
    public void InsertPoint(int pos, Point point) {
        // Add a deep copy of the point to the tour
        this.tour.add(pos, new Point(point));
        // Update numbers 
        this.updateNumbers(point, true);
        // Update tracker
        this.cTracker.Update(point, this.day, true);
    }

    /**
     * Insert a shallow copy of the passed point at the passed position in the
     * tour, updates numbers and tracking info.
     *
     * @param pos position in tour
     * @param point point to insert
     */
    protected void insertTemp(int pos, Point point) {
        // Add a shallow copy of the point to the tour
        this.tour.add(pos, point);
        // Update numbers 
        this.updateNumbers(point, true);
        // Update tracker
        this.cTracker.Update(point, this.day, true);
    }

    /**
     * Set a deep copy of the passed point to the passed position in the tour,
     * updates numbers and tracking info.
     *
     * @param pos position in tour
     * @param point point to set
     */
    public void SetPoint(int pos, Point point) {
        // Update numbers 
        this.updateNumbers(this.tour.get(pos), false);
        // Update tracker
        this.cTracker.Update(this.tour.get(pos), this.day, false);
        // Set a deep copy of new point to tour
        this.tour.set(pos, new Point(point));
        // Update numbers 
        this.updateNumbers(point, true);
        // Update tracker
        this.cTracker.Update(point, this.day, true);
    }

    /**
     * Set a shallow copy of the passed point to the passed position in the
     * tour, updates numbers and tracking info.
     *
     * @param pos position in tour
     * @param point point to set
     */
    protected void setTemp(int pos, Point point) {
        // Update numbers 
        this.updateNumbers(this.tour.get(pos), false);
        // Update tracker
        this.cTracker.Update(this.tour.get(pos), this.day, false);
        // Set a shalow copy of new point to tour
        this.tour.set(pos, point);
        // Update numbers 
        this.updateNumbers(point, true);
        // Update tracker
        this.cTracker.Update(point, this.day, true);
    }

    /**
     * Removes a random container from the tour and returns it; returns null if
     * there are no containers in the tour
     *
     * @return a random container from the tour
     */
    public Point RemoveRandomContainer() {

        int contIndex = this.GetRandContainerIndex();
        if (contIndex != Parameters._404) {
            return this.RemovePoint(contIndex);
        } else {
            return null;
        }
    }

    /**
     * Inserts a random available container in the best position in the tour.
     */
    public void InsertRandomContainer() {

        // If there are available containers
        ArrayList<Point> avblContainers = this.cTracker.GetAvblContainers(this.day);
        if (avblContainers.size() > 0) {

            // Select a random container from the list of available containers
            Point insContainer = avblContainers.get(this.data.GetRand().nextInt(avblContainers.size()));

            // Best index and best cost
            int bestIndex = Parameters._404;
            double bestCost = Double.POSITIVE_INFINITY;

            // Insert container at position 0
            this.InsertPoint(0, insContainer);

            // Loop to find the best position in the tour to insert the container
            // making sure we leave a starting point at the beginning, and a dump
            // and a starting point at the end
            for (int i = 1; i < this.tour.size() - 2; i++) {
                // Move container gradually forward
                this.SwapPoints(i - 1, i);
                // Estimate new cost
                double newCost = this.getEffectiveCost();
                // If new cost improves best cost, update best index and best cost
                if (newCost < bestCost) {
                    bestIndex = i;
                    bestCost = newCost;
                }
            }
            // Remove container from its last position
            this.RemovePoint(this.tour.size() - 3);
            // Insert container at best position in the tour
            this.InsertPoint(bestIndex, insContainer);
        }
    }

    /**
     * Finds the worst container in the tour, ie the one that will bring the
     * largest savings when removed, and returns its index and savings.
     *
     * @return an ImmutablePair of the index and the savings to be obtained when
     * the worst container is removed from the tour
     */
    public ImmutablePair<Integer, Double> FindWorstContainerRemoval() {

        // Initial tour cost, the index and the savings of the worst removal
        double initCost = this.getEffectiveCost();
        int worstIndex = Parameters._404;
        double worstSavings = Double.NEGATIVE_INFINITY;

        // Loop over tour containers
        for (int i = 1; i < this.tour.size() - 2; i++) {
            if (this.tour.get(i).Is() == Parameters.pointIsContainer) {
                // Remove the current point and evaluate the savings
                Point currContainer = this.RemovePoint(i);
                double newSavings = initCost - this.getEffectiveCost();
                // Then add the point back at position i
                this.insertTemp(i, currContainer);
                // If worstSavins are improved, update worstIndex and worstSavings
                if (newSavings > worstSavings) {
                    worstIndex = i;
                    worstSavings = newSavings;
                }
            }
        }

        // Return a Pair of worstIndex and worstSavings
        return new ImmutablePair<>(worstIndex, worstSavings);
    }

    /**
     * Finds the best insertion position of the passed container in the tour, ie
     * the one that will bring the least cost increase, and returns the
     * insertion position and the cost increase.
     *
     * @param insContainer the container to be inserted
     * @return an ImmutablePair of the index and increase obtainable from the
     * best insertion position
     */
    public ImmutablePair<Integer, Double> FindBestContainerInsertion(Point insContainer) {

        // Best index and best increase
        int bestIndex = Parameters._404;
        double bestIncrease = Double.POSITIVE_INFINITY;

        // If the passed container is in the list of available containers
        if (this.cTracker.GetVisit(insContainer, this.day) == false) {

            // Initial tour cost
            double initCost = this.getEffectiveCost();
            // Insert container at position 0
            this.InsertPoint(0, insContainer);

            // Loop to find the best position in the tour to insert the container
            // making sure we leave a starting point at the beginning, and a dump
            // and a starting point at the end
            for (int i = 1; i < this.tour.size() - 2; i++) {

                // Move container gradually forward
                this.SwapPoints(i - 1, i);
                // Estimate new cost increase
                double newIncrease = this.getEffectiveCost() - initCost;
                // If bestIncrease is improved, update bestIncrease and bestIndex
                if (newIncrease < bestIncrease) {
                    bestIndex = i;
                    bestIncrease = newIncrease;
                }
            }
            // Remove container from its last position  
            this.RemovePoint(this.tour.size() - 3);
        }

        // Return an ImmutablePair of bestIndex and bestIncrease
        return new ImmutablePair<>(bestIndex, bestIncrease);
    }
    
    /**
     * Finds k-th best insertion position of the passed container in the tour, 
     * and returns the insertion position and the cost increase
     * difference in cost between the k-th cheapest route and the cheapest route
     *
     * @param insContainer the container to be inserted
     * @return an ImmutablePair of the index and increase obtainable from the
     * best insertion position
     */
    public ImmutablePair<Integer, Double> FindContainerInsertionRegret(Point insContainer, int k) {

        // List of positions with respective cost 
        ArrayList<ImmutablePair<Integer, Double>> insertions = new ArrayList<>();

        // If the passed container is in the list of available containers
        if (this.cTracker.GetVisit(insContainer, this.day) == false) {

            // Insert container at position 0
            this.InsertPoint(0, insContainer);

            // Loop to add all the possible positions and their respective cost in the Array
            // making sure we leave a starting point at the beginning, and a dump
            // and a starting point at the end
            for (int i = 1; i < this.tour.size() - 2; i++) {

                // Move container gradually forward
                this.SwapPoints(i - 1, i);
                // Estimate new cost
                double newCost = this.getEffectiveCost();
                // If bestIncrease is improved, update bestIncrease and bestIndex
                insertions.add(new ImmutablePair<>(i, newCost));
            }
            // Remove container from its last position  
            this.RemovePoint(this.tour.size() - 3);
        }
        
        // Sort the insert positions in ascending cost value
        Collections.sort(insertions, new Comparator<ImmutablePair<Integer, Double>>() {
            @Override
            public int compare(final ImmutablePair<Integer, Double> o1, final ImmutablePair<Integer, Double> o2) {
                return o1.right.compareTo(o2.right);
            }
        });
        
        // Return an ImmutablePair of the index of the k-th position and the difference in cost 
        // between the k-th cheapest route and the cheapest route
        return new ImmutablePair<>(insertions.get(k-1).left, insertions.get(0).right-insertions.get(k-1).right);
    }
    
    /**
     * Selects a random container in the tour, and removes all containers within
     * 2 * dist_min from it, where dist_min is the distance from the selected
     * container to its nearest neighbor in the tour.
     */
    public void RemoveShawContainers() {

        // Select a random container from the tour
        int randContainerIndex = this.GetRandContainerIndex();
        // If there are containers in the tour, perform Shaw removal
        if (randContainerIndex != Parameters._404) {
            Point randContainer = this.tour.get(randContainerIndex);

            // Find the minimum distance from randContainer to any other container
            double distMin = Double.POSITIVE_INFINITY;
            for (int i = 1; i < this.tour.size() - 2; i++) {
                if (this.tour.get(i).Is() == Parameters.pointIsContainer // point should be container
                        && i != randContainerIndex // point should not be randContainer
                        && this.data.GetDistance(this.tour.get(i), randContainer) < distMin) {
                    distMin = this.data.GetDistance(this.tour.get(i), randContainer);
                }
            }

            // Find and remove all containers within 2 * distMin of randContainer
            for (int i = this.tour.size() - 3; i >= 1; i--) {
                if (this.tour.get(i).Is() == Parameters.pointIsContainer // point should be container
                        && this.data.GetDistance(this.tour.get(i), randContainer) <= 2 * distMin) {
                    this.RemovePoint(i);
                }
            }
        }
    }

    /**
     * Selects a random customer not served on this day, calculates the distance
     * dist_min to the nearest customer, finds all customers within 2 * distMin
     * not served on this day, and inserts them into this tour using best
     * insertion.
     */
    public void InsertShawContainers() {

        // If there are available containers
        ArrayList<Point> avblContainers = this.cTracker.GetAvblContainers(this.day);
        if (avblContainers.size() > 1) {

            // Select a random container from the list of available containers
            int randIndex = this.data.GetRand().nextInt(avblContainers.size());
            Point randContainer = avblContainers.get(randIndex);

            // Find the minimum distance from randContainer to any other available container
            double distMin = Double.POSITIVE_INFINITY;
            for (int i = 0; i < avblContainers.size(); i++) {
                if (i != randIndex
                        && this.data.GetDistance(avblContainers.get(i), randContainer) < distMin) {
                    distMin = this.data.GetDistance(avblContainers.get(i), randContainer);
                }
            }

            // Insert all available containers that are within 2 * dist_min and that are not 
            // already served on this day using cheapest insertion
            for (Point container : avblContainers) {
                if (this.data.GetDistance(container, randContainer) < 2 * distMin) {

                    // Best cost and best insertion index
                    int bestIndex = Parameters._404;
                    double bestCost = Double.POSITIVE_INFINITY;

                    // Insert container at position 0
                    this.InsertPoint(0, container);

                    // Loop to find the best position in the tour to insert the container
                    for (int i = 1; i < this.tour.size() - 2; i++) {
                        // Move container gradually forward
                        this.SwapPoints(i - 1, i);
                        // Estimate new cost
                        double newCost = this.getEffectiveCost();
                        // If new cost improves best cost, update best cost and best index
                        if (newCost < bestCost) {
                            bestCost = newCost;
                            bestIndex = i;
                        }
                    }
                    // Remove container from its last position
                    this.RemovePoint(this.tour.size() - 3);

                    // Insert the container at the best position in the tour
                    this.InsertPoint(bestIndex, container);
                }
            }
        }
    }

    /**
     * Removes a random dump from the tour, excluding the last dump.
     */
    public void RemoveRandomDump() {
        int randDumpIndex = this.getRandDumpIndex(false);
        if (randDumpIndex != Parameters._404) {
            this.RemovePoint(randDumpIndex);
        }
    }

    /**
     * Inserts a random dump using best insertion in the tour if it has more
     * than two containers.
     */
    public void InsertRandomDump() {

        // If the tour has more than 2 containers
        if (this.GetNumContainers() > 2) {

            // List of available dumps from the data
            ArrayList<Point> avblDumps = this.data.GetDumps();
            Point randDump = avblDumps.get(this.data.GetRand().nextInt(avblDumps.size()));

            // Best index and best cost
            int bestIndex = Parameters._404;
            double bestCost = Double.POSITIVE_INFINITY;

            // Insert dump at position 1
            this.InsertPoint(1, randDump);

            // Loop to find the best position in the tour to insert the dump
            // making sure we leave a starting point at the beginning, and a dump
            // and a starting point at the end. Moreover, the dump should not neighbor
            // another dump.
            for (int i = 2; i < this.tour.size() - 3; i++) {
                // Move dump gradually forward
                this.SwapPoints(i - 1, i);
                // Estimate new cost 
                double newCost = this.getEffectiveCost();
                // If new cost improves best cost and the dump does not neighbor another dump, 
                // update best index and best cost
                if (newCost < bestCost
                        && this.tour.get(i - 1).Is() != Parameters.pointIsDump
                        && this.tour.get(i + 1).Is() != Parameters.pointIsDump) {
                    bestIndex = i;
                    bestCost = newCost;
                }
            }
            // Remove dump from its last position
            this.RemovePoint(this.tour.size() - 4);
            // Insert dump at best position in the tour
            if (bestIndex != Parameters._404) {
                this.InsertPoint(bestIndex, randDump);
            }
        }
    }

    /**
     * Finds the worst dump in the tour, ie the one that will bring the largest
     * savings when removed, excluding the last one, and returns its index and
     * savings.
     *
     * @return an ImmutablePair of the index and the savings to be obtained when
     * the worst dump, excluding the last one, is removed from the tour
     */
    public ImmutablePair<Integer, Double> FindWorstDumpRemoval() {

        // Initial tour cost, the index and the savings of the worst removal
        double initCost = this.getEffectiveCost();
        int worstIndex = Parameters._404;
        double worstSavings = Double.NEGATIVE_INFINITY;

        // Loop over tour dumps
        for (int i = 1; i < this.tour.size() - 2; i++) {
            if (this.tour.get(i).Is() == Parameters.pointIsDump) {
                // Remove the current point and evaluate the savings
                Point currDump = this.RemovePoint(i);
                double newSavings = initCost - this.getEffectiveCost();
                // Then add the point back at position i
                this.insertTemp(i, currDump);
                // If worstSavins are improved, update worstIndex and worstSavings
                if (newSavings > worstSavings) {
                    worstIndex = i;
                    worstSavings = newSavings;
                }
            }
        }

        // Return a Pair of worstIndex and worstSavings
        return new ImmutablePair<>(worstIndex, worstSavings);
    }

    /**
     * Finds the best insertion position of the passed dump in the tour, ie the
     * one that will bring the least cost increase, and returns the insertion
     * position and the cost increase.
     *
     * @param insDump the dump to be inserted
     * @return an ImmutablePair of the index and increase obtainable from the
     * best insertion position
     */
    public ImmutablePair<Integer, Double> FindBestDumpInsertion(Point insDump) {

        // Best index and best increase
        int bestIndex = Parameters._404;
        double bestIncrease = Double.POSITIVE_INFINITY;

        // If there are more than two containers in the tour
        if (this.GetNumContainers() > 2) {

            // Initial tour cost
            double initCost = this.getEffectiveCost();
            // Insert dump at position 1
            this.InsertPoint(1, insDump);

            // Loop to find the best position in the tour to insert the dump
            // making sure we leave a starting point at the beginning, and a dump
            // and a starting point at the end. Moreover, the dump should not
            // neighbor another dump
            for (int i = 2; i < this.tour.size() - 3; i++) {
                // Move dump gradually forward
                this.SwapPoints(i - 1, i);
                // Estimate new cost increase
                double newIncrease = this.getEffectiveCost() - initCost;
                // If bestIncrease is improved and the dump does not neighbor another dump,
                // update bestIncrease and bestIndex
                if (newIncrease < bestIncrease
                        && this.tour.get(i - 1).Is() != Parameters.pointIsDump
                        && this.tour.get(i + 1).Is() != Parameters.pointIsDump) {
                    bestIndex = i;
                    bestIncrease = newIncrease;
                }
            }
            // Remove the dump from its last position  
            this.RemovePoint(this.tour.size() - 4);
        }
        // Return an ImmutablePair of bestIndex and bestIncrease
        return new ImmutablePair<>(bestIndex, bestIncrease);
    }

    /**
     * Replaces a random dump in the tour with the best different dump from the
     * list of available dumps.
     */
    public void ReplaceRandomDump() {

        // List of available dumps from the data
        ArrayList<Point> avblDumps = this.data.GetDumps();

        // If the list of available dumps is larger than 1
        if (avblDumps.size() > 1) {
            // Select a random dump from the tour, including the last one
            int randDumpIndex = this.getRandDumpIndex(true);
            Point randDump = this.tour.get(randDumpIndex);

            // Best dump from the list and best cost
            Point bestDump = null;
            double bestCost = Double.POSITIVE_INFINITY;
            // Loop the list of available dumps
            for (Point dump : avblDumps) {
                // Set new dump and evaluate
                this.setTemp(randDumpIndex, dump);
                double newCost = this.getEffectiveCost();
                // If new cost improves best cost for a different dump,
                // impove best dump and best cost
                if (newCost < bestCost && dump.GetDWid() != randDump.GetDWid()) {
                    bestDump = dump;
                    bestCost = newCost;
                }
            }
            // Set new dump
            this.SetPoint(randDumpIndex, bestDump);
        }
    }

    /**
     * Returns true if the path between p1index and p2index is feasible
     * regarding truck capacity, and false otherwise.
     *
     * @param p1index path starting point
     * @param p2index path ending point
     * @return true if the path between p1index and p2index is feasible
     * regarding truck capacity, and false otherwise
     */
    private Boolean isPathTruckCapacityFeasible(int p1index, int p2index) {

        // Cumulative volume and weight 
        double cumuVolumeLoad = 0.d;
        double cumuWeightLoad = 0.d;

        // Calculate the cumulative volume and weight form p1index inclusive
        // to p2index exclusive
        for (int i = p1index; i < p2index; i++) {
            cumuVolumeLoad += this.cTracker.GetVolumeLoad(this.tour.get(i), this.day);
            cumuWeightLoad += this.cTracker.GetWeightLoad(this.tour.get(i), this.day);
        }

        // Check if the path is feasible wrt cumulative volume and weight
        Boolean isPathTruckCapacityFeasible = false;
        if (cumuVolumeLoad <= this.truck.GetMaxEffectiveVolume()
                && cumuWeightLoad <= this.truck.GetMaxEffectiveWeight()) {
            isPathTruckCapacityFeasible = true;
        }

        // Return feasibility 
        return isPathTruckCapacityFeasible;
    }

    /**
     * Performs all the individual point swaps to result in the 2-opt swap
     * transformation.
     *
     * @param p1index path starting point
     * @param p2index path ending point
     */
    private void twoOptSwap(int p1index, int p2index) {
        int nbSwaps = (int) Math.ceil((p2index - p1index) / 2.d);
        for (int i = 0; i < nbSwaps; i++) {
            Collections.swap(this.tour, p1index + i, p2index - i);
        }
    }

    /**
     * Reorders dump visits in the tour by deleting all dump visits and placing
     * them in a locally optimal way using the algorithm of Hemmelmayr et al.
     * (2013).
     *
     * NOTE: THE CONSTRUCTION OF THE GRAPH CONSIDERS DISTANCE ONLY (WHICH MAY BE
     * PROBLEMATIC IN THE PRESENCE OF TIGHT TIME WINDOWS, BUT OK FOR THE
     * MOMENT). THE 2-OPT SEARCH THAT FOLLOWS CONSIDERS THE COMPLETE COST.
     */
    public void ReorderDumps() {

        // Whether to use Hemmelmayr's DP operator or greedy insertion
        Boolean useHemmeExpUseHemmelmayrDPOperator = Parameters.expUseHemmelmayrDPOperator;
        if (useHemmeExpUseHemmelmayrDPOperator) {

            // If there is one or more containers
            if (this.numContainers >= 1) {

                // Remove all dumps from the tour (starting at the end)
                for (int i = this.tour.size() - 1; i >= 0; i--) {
                    if (this.tour.get(i).Is() == Parameters.pointIsDump) {
                        this.RemovePoint(i);
                    }
                }

                // Go through the containers of the tour and find the closest dump to insert at each 
                // step. The 1st point is a starting point and therfore is not considered.
                double[] shortestDetourDistances = new double[this.tour.size() - 2];
                Point[] bestDumps = new Point[this.tour.size() - 2];
                for (int i = 1; i < this.tour.size() - 1; i++) {
                    // Find the closest dump between point i and the next and the distance of the detour
                    shortestDetourDistances[i - 1] = Double.POSITIVE_INFINITY;
                    for (Point dump : this.data.GetDumps()) {
                        double detourDistance = this.data.GetDistance(this.tour.get(i), dump)
                                + this.data.GetDistance(dump, this.tour.get(i + 1));
                        if (detourDistance < shortestDetourDistances[i - 1]) {
                            shortestDetourDistances[i - 1] = detourDistance;
                            bestDumps[i - 1] = dump;
                        }
                    }
                }

                // Create a graph of truck capacity feasible dump insertions
                WeightedGraph<Integer, DefaultWeightedEdge> graphOfFeasibleDumpInsertions
                        = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

                // Populate the graph with all containers and the end depot as vertices
                for (int i = 1; i < this.tour.size(); i++) {
                    graphOfFeasibleDumpInsertions.addVertex(i);
                }

                // Loop through the tour, identify the feasible dump insertions and create the directed weighted edges                  
                for (int p1index = 1; p1index < this.tour.size() - 1; p1index++) {
                    // Holder of path length excluding the final dump detour
                    double length_pathToPenultimatePoint = 0.d;
                    // If we start from the first container, add here the distance from the depot to it
                    if (p1index == 1) {
                        length_pathToPenultimatePoint += this.data.GetDistance(this.tour.get(0), this.tour.get(p1index));
                    }

                    // Check all the paths form p1index to the rest of the points of the tour
                    for (int p2index = p1index + 1; p2index < this.tour.size(); p2index++) {
                        // If the path with a dump visit just before p2index is truck capacity feasible, 
                        // create the corresponding edge in the graph
                        Boolean isPathTruckCapacityFeasible = this.isPathTruckCapacityFeasible(p1index, p2index);
                        if (isPathTruckCapacityFeasible) {
                            // If p2index > p1index + 1, add the distances from p1index to p2index - 1
                            if (p2index > p1index + 1) {
                                length_pathToPenultimatePoint += this.data.GetDistance(this.tour.get(p2index - 2), this.tour.get(p2index - 1));
                            }

                            // Compute total path length by adding the final detour
                            double length_detourThroughFinalDump = shortestDetourDistances[p2index - 2];
                            double totalPathLength = length_pathToPenultimatePoint + length_detourThroughFinalDump;

                            // Create an new edge in the graph
                            DefaultWeightedEdge edge = graphOfFeasibleDumpInsertions.addEdge(p1index, p2index);
                            graphOfFeasibleDumpInsertions.setEdgeWeight(edge, totalPathLength);
                        } else {
                            // Start the search with a new starting point
                            break;
                        }
                    }
                }

                // Find the sortest path from the first vertex of the graph to 
                // the final one with the Bellman-Ford algorithm
                Integer startVertex = 1; // the index of the first container
                @SuppressWarnings("unchecked") // Just for this one statement
                BellmanFordShortestPath bellmanFordShortestPath
                        = new BellmanFordShortestPath(graphOfFeasibleDumpInsertions, startVertex);
                @SuppressWarnings("unchecked") // Just for this one statement
                List<DefaultWeightedEdge> bestPath = bellmanFordShortestPath.getPathEdgeList(this.tour.size() - 1);

                // Insert the optimal dumps in the tour in reverse order (to keep
                // track of the point numbering while inserting new points)
                for (int i = bestPath.size() - 1; i >= 0; i--) {
                    int targetPoint = graphOfFeasibleDumpInsertions.getEdgeTarget(bestPath.get(i));
                    this.InsertPoint(targetPoint, bestDumps[targetPoint - 2]);
                }
            }

            // Perform a two-opt local search that preserves truck capacity feasibility (volume and weight)
            if (Parameters.expDo2optLocalSearch) {

                // Best cost and 2-opt positions that lead to it.
                // There is no problem to consider cost instead of effective cost, because 
                // we are only shuffling points within the tour, rather than among tours.
                double bestCost = this.GetCost();
                int[] best2opt = new int[2];
                // True if improvement found, false otherwise
                boolean hasFoundImprovement = true;

                // Loop while there are improvements
                while (hasFoundImprovement) {
                    // Set improvement to false
                    hasFoundImprovement = false;

                    // Try all possible 2-opt exept for the origin and final points (depots)
                    // and the last dump which must always be visited!
                    outerloop:
                    for (int p1index = 1; p1index < this.tour.size() - 3; p1index++) {
                        for (int p2index = p1index + 1; p2index < this.tour.size() - 2; p2index++) {
                            this.twoOptSwap(p1index, p2index);
                            double newCost = this.GetCost();
                            // If new best swap found, save it
                            if ((newCost < bestCost) && (this.GetVolViolation() == 0) && (this.GetWeightViolation() == 0)) {
                                bestCost = newCost;
                                best2opt[0] = p1index;
                                best2opt[1] = p2index;
                                hasFoundImprovement = true;
                            }

                            // Reverse swap and continue search
                            this.twoOptSwap(p1index, p2index);

                            // If using the first improvement rule and a first
                            // improvement has been found, STOP the search here
                            if (Parameters.exp2optFirstImprovement && hasFoundImprovement) {
                                break outerloop;
                            }
                        }
                    }

                    // Perform the best swap found during the search
                    if (hasFoundImprovement) {
                        this.twoOptSwap(best2opt[0], best2opt[1]);
                    } 
                }
            }
        } else { // not NEW_VERSION

            if (this.numContainers >= 1) {

                // Remove all dumps from the tour ! remove from the end
                for (int i = this.tour.size() - 1; i >= 0; i--) {
                    if (this.tour.get(i).Is() == Parameters.pointIsDump) {
                        this.RemovePoint(i);
                    }
                }

                // Start inspecting the tour for violated truck volume and weight capacity
                double cumuVolumeLoad = 0.d;
                double cumuWeightLoad = 0.d;
                for (int i = 0;; i++) {
                    if (this.tour.get(i).Is() == Parameters.pointIsContainer) {
                        // Update the cumulated volume and weight load
                        cumuVolumeLoad += this.cTracker.GetVolumeLoad(this.tour.get(i), this.day);
                        cumuWeightLoad += this.cTracker.GetWeightLoad(this.tour.get(i), this.day);
                        // If at this point there is a violation, insert the closest dump to the preceding container
                        // and reset the cumulative values
                        if ((cumuVolumeLoad > this.truck.GetMaxEffectiveVolume() || cumuWeightLoad > this.truck.GetMaxEffectiveWeight())
                                && this.tour.get(i - 1).Is() == Parameters.pointIsContainer) {
                            this.InsertPoint(i, this.tour.get(i - 1).GetClosestDump());
                            cumuVolumeLoad = 0.d;
                            cumuWeightLoad = 0.d;
                        }
                    }

                    // Break the loop if we reached end of tour
                    if (this.tour.get(i).Is() == Parameters.pointIsSP) {
                        break;
                    }
                }

                // If the penultimate tour point is a container, insert the closest dump to it in the tour
                if (this.tour.get(this.tour.size() - 2).Is() == Parameters.pointIsContainer) {
                    this.InsertPoint(this.tour.size() - 1, this.tour.get(this.tour.size() - 2).GetClosestDump());
                }
            }
        }
    }

    /**
     * Empties all containers and dumps from the tour and reconstructs it with a
     * single dump.
     */
    public void EmptyTour() {
        for (int i = this.tour.size() - 1; i >= 0; i--) {
            this.RemovePoint(i);
        }
        this.Construct();
    }
}
