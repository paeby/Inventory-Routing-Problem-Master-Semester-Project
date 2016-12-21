package alns.schedule;

import alns.algo.Penalties;
import alns.data.Data;
import alns.data.FeedbackForm;
import alns.data.Point;
import alns.data.Simulation;
import alns.data.Truck;
import alns.param.Parameters;
import alns.tour.Tour;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * Abstract class for the tour schedule, representing the collection of tours
 * for all days in the planning horizon and all vehicles. Has implementations
 * for the inventory routing problem (IRP) and the benchmark inventory routing
 * problem (IRPA/C) over the planning horizon, the traveling salesman problem
 * (TSP) over the planning horizon, and the vehicle routing problem (VRP) for
 * day = 0.
 *
 * @author Markov
 * @version 2.0
 */
public abstract class Schedule {

    // Data
    protected Data data;

    // Container tracker
    protected ContainerTracker cTracker;

    // Schedule as an ArrayList of Tour objects
    protected ArrayList<Tour> schedule;

    /**
     * Schedule assignment constructor. Is overridden in the class
     * implementations.
     *
     * @param data Data object to assign
     */
    public Schedule(Data data) {

        // Assign data
        this.data = data;

        // Initialize schedule as an empty ArrayList 
        this.schedule = new ArrayList<>();
    }

    /**
     * Schedule copy constructor. Is overridden in the class implementations.
     *
     * @param schedule Schedule object to copy
     */
    public Schedule(Schedule schedule) {

        // Assign data
        this.data = schedule.data;

        // Initialize a new Schedule object
        this.schedule = new ArrayList<>(schedule.GetSize());
    }

    /**
     * An abstract method which adds a ready tour to the solution. It is only
     * used for testing purposes.
     *
     * @param day day in the planning horizon
     * @param truckWid trick wid
     * @param pointDwids point dwids
     */
    public abstract void AddTour(int day, int truckWid, ArrayList<Integer> pointDwids);

    /**
     * Abstract construction method for the schedule. Is overridden in the class
     * implementations.
     */
    public abstract void Construct();

    /**
     * Returns an integer from 1 to n, drawn from a semi-triangular distribution
     *
     * @param n upper bound
     * @return an integer from 1 to n, drawn from a semi-triangular distribution
     */
    protected int rho(int n) {
        return (int) Math.round(n + 0.5d - Math.sqrt(1 - this.data.GetRand().nextDouble()) * n);
    }

    /**
     * Abstract neighbor generation method that applies sequentially the
     * selected destroy and repair operators to the schedule to generate a
     * neighbor solution. Is overridden in the class implementations.
     *
     * @param destroyOpIndex index of the destroy operator to apply
     * @param repairOpIndex index of the repair operator to apply
     * @return
     */
    public abstract FeedbackForm GenerateNeighbor(int destroyOpIndex, int repairOpIndex);

    /**
     * Returns schedule tours exposed as Tour references.
     *
     * @return schedule tours exposed as Tour references
     */
    public ArrayList<Tour> GetTours() {
        return this.schedule;
    }

    /**
     * Returns schedule size in terms of number of tours.
     *
     * @return schedule size in terms of number of tours
     */
    public int GetSize() {
        return this.schedule.size();
    }

    /**
     * Returns schedule total number of points in all tours.
     *
     * @return schedule total number of points in all tours
     */
    public int GetNumPoints() {

        int numPoints = 0;

        for (Tour tour : this.schedule) {
            numPoints += tour.GetSize();
        }

        return numPoints;
    }

    /**
     * Returns schedule total number of containers in all tours.
     *
     * @return schedule total number of containers in all tours
     */
    public int GetNumContainers() {

        int numContainers = 0;

        for (Tour tour : this.schedule) {
            numContainers += tour.GetNumContainers();
        }

        return numContainers;
    }

    /**
     * Returns schedule number of containers served by all trucks on a given
     * day.
     *
     * @param d day in the planning horizon
     * @return schedule number of containers served by all trucks on a given
     * day.
     */
    protected int getNumContainersByDay(int d) {

        int numContainersByDay = 0;

        for (Tour tour : this.schedule) {
            if (tour.GetDay() == d) {
                numContainersByDay += tour.GetNumContainers();
            }
        }

        return numContainersByDay;
    }

    /**
     * Returns schedule number of containers served on all days by a given
     * truck.
     *
     * @param truck Truck object
     * @return schedule number of containers served on all days by a given
     * truck.
     */
    protected int getNumContainersByTruck(Truck truck) {

        int numContainersByTruck = 0;

        for (Tour tour : this.schedule) {
            if (tour.GetTruck().GetWid() == truck.GetWid()) {
                numContainersByTruck += tour.GetNumContainers();
            }
        }

        return numContainersByTruck;
    }

    /**
     * Returns the visits of the passed point if it is a container, null
     * otherwise.
     *
     * @param point point to return visits for
     * @return the visits of the passed point if it is a container, null
     * otherwise
     */
    public ArrayList<Boolean> GetContainerVisits(Point point) {

        if (point.Is() == Parameters.pointIsContainer) {
            ArrayList<Boolean> containerVisits = new ArrayList<>(this.data.GetPhLength() + 1);
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                containerVisits.add(this.cTracker.GetVisit(point, d));
            }
            return containerVisits;
        } else {
            return null;
        }
    }

    /**
     * Returns the number of visits to the passed point if it is a container, a
     * default value otherwise.
     *
     * @param point point to return number of visits for;
     * @return the number of visits to the passed point if it is a container, a
     * default value otherwise
     */
    public int GetContainerNumVisits(Point point) {

        if (point.Is() == Parameters.pointIsContainer) {
            int containerNumVisits = 0;
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                if (this.cTracker.GetVisit(point, d) == true) {
                    containerNumVisits++;
                }
            }
            return containerNumVisits;
        } else {
            return Parameters._404;
        }
    }

    /**
     * Returns the volume loads of the passed point if it is a container, null
     * otherwise.
     *
     * @param point point to return volume loads for
     * @return the volume loads of the passed point if it is a container, null
     * otherwise
     */
    public ArrayList<Double> GetContainerVolumeLoads(Point point) {

        if (point.Is() == Parameters.pointIsContainer) {
            ArrayList<Double> containerVolumeLoads = new ArrayList<>(this.data.GetPhLength() + 1);
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                containerVolumeLoads.add(this.cTracker.GetVolumeLoad(point, d));
            }
            return containerVolumeLoads;
        } else {
            return null;
        }
    }

    /**
     * Returns the weight loads of the passed point if it is a container, null
     * otherwise.
     *
     * @param point point to return weight loads for
     * @return the weight loads of the passed point if it is a container, null
     * otherwise
     */
    public ArrayList<Double> GetContainerWeightLoads(Point point) {

        if (point.Is() == Parameters.pointIsContainer) {
            ArrayList<Double> containerWeightLoads = new ArrayList<>(this.data.GetPhLength() + 1);
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                containerWeightLoads.add(this.cTracker.GetWeightLoad(point, d));
            }
            return containerWeightLoads;
        } else {
            return null;
        }
    }

    /**
     * Returns the overflow cost attributions of the passed point if it is a
     * container, null otherwise.
     *
     * @param point point to return overflow cost attributions for
     * @return the overflow cost attributions of the passed point if it is a
     * container, null otherwise
     */
    public ArrayList<Double> GetOverflowCostAttr(Point point) {

        if (point.Is() == Parameters.pointIsContainer) {
            ArrayList<Double> overflowCostAttr = new ArrayList<>(this.data.GetPhLength() + 1);
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                overflowCostAttr.add(this.cTracker.GetOverflowCostAttr(point, d));
            }
            return overflowCostAttr;
        } else {
            return null;
        }
    }

    /**
     * Returns the overflow probabilities of the passed point if it is a
     * container, null otherwise.
     *
     * @param point point to return overflow probabilities for
     * @return the overflow probabilities of the passed point if it is a
     * container, null otherwise
     */
    public ArrayList<Double> GetOverflowProbabilities(Point point) {

        if (point.Is() == Parameters.pointIsContainer) {
            ArrayList<Double> overflowProbabilities = new ArrayList<>(this.data.GetPhLength() + 1);
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                overflowProbabilities.add(this.cTracker.GetOverflowProbability(point, d));
            }
            return overflowProbabilities;
        } else {
            return null;
        }
    }

    /**
     * Returns container violations (overflow) of the passed point if it is a
     * container, null otherwise.
     *
     * @param point point to return container violations for
     * @return container violations (overflow) of the passed point if it is a
     * container, null otherwise
     */
    public ArrayList<Double> GetContainerViolations(Point point) {

        if (point.Is() == Parameters.pointIsContainer) {
            ArrayList<Double> containerViolations = new ArrayList<>(this.data.GetPhLength() + 1);
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                containerViolations.add(this.cTracker.GetContainerViolation(point, d));
            }
            return containerViolations;
        } else {
            return null;
        }
    }

    /**
     * Returns the schedule total violation of the trucks' maximum volumes.
     *
     * @return the schedule total violation of the trucks' maximum volumes
     */
    public double GetVolumeViolation() {

        double volumeViolation = 0.d;

        for (Tour tour : this.schedule) {
            volumeViolation += tour.GetVolViolation();
        }

        return volumeViolation;
    }

    /**
     * Returns the schedule total violation of the trucks' maximum weights.
     *
     * @return the schedule total violation of the trucks' maximum weights
     */
    public double GetWeightViolation() {

        double weightViolation = 0.d;

        for (Tour tour : this.schedule) {
            weightViolation += tour.GetWeightViolation();
        }

        return weightViolation;
    }

    /**
     * Returns the schedule total time window violation.
     *
     * @return the schedule total time window violation
     */
    public double GetTWViolation() {

        double twViolation = 0.d;

        for (Tour tour : this.schedule) {
            twViolation += tour.GetTWViolation();
        }

        return twViolation;
    }

    /**
     * Returns the schedule total violation of the tours' maximum durations.
     *
     * @return the schedule total violation of the tours' maximum durations
     */
    public double GetDurViolation() {

        double durationViolation = 0.d;

        for (Tour tour : this.schedule) {
            durationViolation += tour.GetDurViolation();
        }

        return durationViolation;
    }

    /**
     * Returns the schedule total violation of the tours' point accessibilities.
     *
     * @return the schedule total violation of the tours' point accessibilities
     */
    public int GetAccessViolation() {

        int accessViolation = 0;

        for (Tour tour : this.schedule) {
            accessViolation += tour.GetAccessViolation();
        }

        return accessViolation;
    }

    /**
     * Returns the schedule total violation of the tours' home depots.
     *
     * @return the schedule total violation of the tours' home depots
     */
    public int GetHomeDepotViolation() {

        int homeDepotViolation = 0;

        for (Tour tour : this.schedule) {
            homeDepotViolation += tour.GetHomeDepotViolation();
        }

        return homeDepotViolation;
    }

    /**
     * Returns the schedule total backorder limit violation as the sum of volume
     * capacity violation on day 0 for containers that are not served on day 0.
     *
     * @return the schedule total backorder limit violation as the sum of volume
     * capacity violation on day 0 for containers that are not served on day 0
     */
    public double GetBackorderViolation() {

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
     * Returns the schedule total violation (overflow) of the containers' volume
     * capacities on days 1 to phLength + 1.
     *
     * @return the schedule total violation (overflow) of the containers' volume
     * capacities on days 1 to phLength + 1
     */
    public double GetContViolation() {

        double contViolation = 0.d;

        for (Point container : this.data.GetContainers()) {
            for (int d = 1; d < this.data.GetPhLength() + 1; d++) {
                contViolation += this.cTracker.GetContainerViolation(container, d);
            }
        }

        return contViolation;
    }

    /**
     * Returns true if the schedule has no feasibility violations, false
     * otherwise.
     *
     * @return true if the schedule has no feasibility violations, false
     * otherwise
     */
    public boolean IsFeasible() {

        return (this.GetVolumeViolation() == 0.d && this.GetWeightViolation() == 0.d
                && this.GetTWViolation() == 0.d && this.GetDurViolation() == 0.d
                && this.GetAccessViolation() == 0 && this.GetHomeDepotViolation() == 0
                && this.GetBackorderViolation() == 0.d && this.GetContViolation() == 0.d);
    }

    /**
     * Returns the depot inventory holding cost. It is currently set to 0 and
     * not relevant. However, the code is kept for testing purposes and for
     * possible future extensions. It is overridden in the IRPA/C
     * implementation.
     *
     * @return depot inventory holding cost.
     */
    public double GetDepotInventoryHoldingCost() {
        return 0.d;
    }

    /**
     * Returns schedule inventory holding cost. It is currently set to 0 and not
     * relevant. However, the code is kept for testing purposes and for possible
     * future extensions. Is overridden in the VRP and IRPA/C implementation.
     *
     * @return schedule inventory holding cost
     */
    public double GetInventoryHoldingCost() {
        return 0.d;
    }

    /**
     * Returns schedule overflow cost attribution as a sum of container overflow
     * cost attributions for day 0 to phLength + 1.
     *
     * @return schedule overflow cost attribution as a sum of container overflow
     * cost attributions for day 0 to phLength + 1
     */
    public double GetOverflowCostAttr() {

        double scheduleOverflowCostAttr = 0.d;

        for (Point container : this.data.GetContainers()) {
            for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
                scheduleOverflowCostAttr += this.cTracker.GetOverflowCostAttr(container, d);
            }
        }

        return scheduleOverflowCostAttr;
    }

    /**
     * Returns schedule cost as a sum of tour costs + inventory holding cost +
     * overflow cost attribution + backorder limit violation penalty + container
     * violation penalty.
     *
     * @return schedule cost as a sum of tour costs + inventory holding cost +
     * overflow cost attribution + backorder limit violation penalty + container
     * violation penalty
     */
    public double GetCost() {

        // Calculate schedule cost as the sum of tours costs
        double scheduleCost = 0.d;
        for (Tour tour : this.schedule) {
            scheduleCost += tour.GetCost();
        }
        // Because container violation penalty and backorder penalty are not calculated 
        // on a tour basis we add them to the schedule cost
        Penalties penalties = this.data.GetPenalties();
        scheduleCost += penalties.GetBackorderViolPenalty() * this.GetBackorderViolation();
        scheduleCost += penalties.GetContViolPenalty() * this.GetContViolation();

        // Add inventory holding cost
        scheduleCost += this.GetInventoryHoldingCost();

        // Add overflow cost attribution
        scheduleCost += this.GetOverflowCostAttr();

        // Returns schedule cost
        return scheduleCost;
    }

    /**
     * Returns schedule final number of dump visits in tours serving containers.
     *
     * @return schedule final number of dump visits in tours serving containers
     */
    public int GetScheduleFinalNumDumpVisits() {

        int scheduleFinalNumDumpVisits = 0;
        for (Tour tour : this.schedule) {
            if (tour.GetNumContainers() > 0) {
                scheduleFinalNumDumpVisits += tour.GetNumDumpVisits();
            }
        }
        return scheduleFinalNumDumpVisits;
    }

    /**
     * Returns schedule final number of performed tours, i.e. tours serving
     * containers or repositioning tours.
     *
     * @return schedule final number of performed tours, i.e. tours serving
     * containers or repositioning tours
     */
    public int GetScheduleFinalNumPerformedTours() {

        int scheduleFinalNumPerformedTours = 0;
        for (Tour tour : this.schedule) {
            if (tour.GetNumContainers() > 0
                    || tour.GetOriginStartingPoint().GetDWid() != tour.GetDestinationStartingPoint().GetDWid()) {
                scheduleFinalNumPerformedTours++;
            }
        }
        return scheduleFinalNumPerformedTours;
    }

    /**
     * Returns the schedule final length, i.e. for tours serving containers or
     * repositioning tours.
     *
     * @return the schedule final length, i.e. for tours serving containers or
     * repositioning tours
     */
    public double GetScheduleFinalLength() {

        double scheduleFinalLength = 0.d;
        for (Tour tour : this.schedule) {
            if (tour.GetNumContainers() > 0
                    || tour.GetOriginStartingPoint().GetDWid() != tour.GetDestinationStartingPoint().GetDWid()) {
                scheduleFinalLength += tour.GetLength();
            }
        }
        return scheduleFinalLength;
    }

    /**
     * Returns the schedule final duration, i.e. for tours serving containers or
     * repositioning tours.
     *
     * @return the schedule final duration, i.e. for tours serving containers or
     * repositioning tours
     */
    public double GetScheduleFinalDuration() {

        double scheduleFinalDuration = 0.d;
        for (Tour tour : this.schedule) {
            if (tour.GetNumContainers() > 0
                    || tour.GetOriginStartingPoint().GetDWid() != tour.GetDestinationStartingPoint().GetDWid()) {
                scheduleFinalDuration += tour.GetDuration();
            }
        }
        return scheduleFinalDuration;
    }

    /**
     * Returns schedule final routing cost, i.e. for tours serving containers or
     * repositioning tours.
     *
     * @return schedule final routing cost, i.e. for tours serving containers or
     * repositioning tours
     */
    public double GetScheduleFinalRoutingCost() {

        double scheduleFinalRoutingCost = 0.d;
        for (Tour tour : this.schedule) {
            scheduleFinalRoutingCost += tour.GetTourFinalRoutingCost();
        }
        return scheduleFinalRoutingCost;
    }

    /**
     * Returns schedule final route failure cost, i.e. for tours serving
     * containers on day > 0.
     *
     * @return schedule final route failure cost, i.e. for tours serving
     * containers on day > 0
     */
    public double GetScheduleFinalRouteFailureCost() {

        double scheduleFinalRouteFailureCost = 0.d;
        for (Tour tour : this.schedule) {
            scheduleFinalRouteFailureCost += tour.GetTourFinalRouteFailureCost();
        }
        return scheduleFinalRouteFailureCost;
    }

    /**
     * Returns schedule final collected volume load.
     *
     * @return schedule final collected volume load
     */
    public double GetScheduleFinalCollectedVolumeLoad() {

        double scheduleFinalCollectedVolumeLoad = 0.d;
        for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
            for (Point container : this.data.GetContainers()) {
                if (this.cTracker.GetVisit(container, d) == true) {
                    scheduleFinalCollectedVolumeLoad += this.cTracker.GetVolumeLoad(container, d);
                }
            }
        }
        return scheduleFinalCollectedVolumeLoad;
    }

    /**
     * Returns the schedule final inventory holding cost.
     *
     * @return the schedule final inventory holding cost
     */
    public double GetScheduleFinalInventoryHoldingCost() {
        return this.GetInventoryHoldingCost();
    }

    /**
     * Returns the schedule final average container levels at collection.
     *
     * @return the schedule final average container levels at collection
     */
    public ArrayList<Double> GetScheduleFinalAvgLevelsAtCollectionOnDays() {

        ArrayList<Double> avgLevelsAtCollectionOnDays
                = new ArrayList<>(Collections.nCopies(this.data.GetPhLength(), (double) Parameters._404));

        for (int d = 0; d < this.data.GetPhLength(); d++) {
            int numVisitsOnDayD = 0;
            double sumLevelsAtCollectionOnDayD = 0.d;
            for (Point container : this.data.GetContainers()) {
                if (this.cTracker.GetVisit(container, d) == true) {
                    numVisitsOnDayD++;
                    sumLevelsAtCollectionOnDayD += (this.cTracker.GetVolumeLoad(container, d) / container.GetVolume());
                }
            }
            if (numVisitsOnDayD > 0) {
                double avgLevelAtCollectionOnDayD = sumLevelsAtCollectionOnDayD / numVisitsOnDayD;
                avgLevelsAtCollectionOnDays.set(d, avgLevelAtCollectionOnDayD);
            }
        }

        return avgLevelsAtCollectionOnDays;
    }

    /**
     * Returns the schedule final average levels on days of the planning horizon
     * (including phLength + 1).
     *
     * @return the schedule final average levels on days of the planning horizon
     * (including phLength + 1)
     */
    public ArrayList<Double> GetScheduleFinalAvgLevelsOnDays() {

        ArrayList<Double> avgLevelsOnDays
                = new ArrayList<>(Collections.nCopies(this.data.GetPhLength() + 1, (double) Parameters._404));

        for (int d = 0; d < this.data.GetPhLength() + 1; d++) {
            int numContainersOnDayD = 0;
            double sumLevelsOnDayD = 0.d;
            for (Point container : this.data.GetContainers()) {
                numContainersOnDayD++;
                sumLevelsOnDayD += (this.cTracker.GetVolumeLoad(container, d) / container.GetVolume());
            }
            if (numContainersOnDayD > 0) {
                double avgLevelOnDayD = sumLevelsOnDayD / numContainersOnDayD;
                avgLevelsOnDays.set(d, avgLevelOnDayD);
            }
        }

        return avgLevelsOnDays;
    }

    /**
     * Returns the schedule final overflow cost attributions.
     *
     * @return the schedule final overflow cost attributions
     */
    public double GetScheduleFinalOverflowCostAttr() {
        return this.GetOverflowCostAttr();
    }

    /**
     * Returns a simple schedule hashcode.
     *
     * @return a simple schedule hashcode
     */
    public int GetHashCode() {

        int hashCode = 0;

        for (Tour tour : this.schedule) {
            hashCode += tour.GetHashCode();
        }

        return hashCode;
    }

    /**
     * Runs a swap-based local search on each tour in sequence.
     */
    public void DoLocalSearch() {
        for (Tour tour : this.schedule) {
            tour.DoLocalSearch();
        }
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
     * Selects randomly a tour and removes a random container from it. It is
     * repeated rho times.
     *
     * @return rho - number of times operator is applied
     */
    protected int removeRandomRhoContainers() {

        // Select a rho with an upper bound on the number of containers in the data
        int rho = this.rho(this.data.GetContainers().size());

        // Remove rho random containers
        for (int i = 0; i < rho; i++) {
            // Select one random tour
            int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
            Tour tour = this.schedule.get(tourIndex);
            // Remove a random container from it
            tour.RemoveRandomContainer();
        }

        // Return rho
        return rho;
    }

    /**
     * Removes the container from the schedule that will save the most,
     * considering the total schedule cost. It is repeated rho times.
     *
     * @return rho - number of times operator is applied
     */
    protected int removeWorstRhoContainers() {

        // Select a rho with an upper bound on the number of containers in the data
        int rho = this.rho(this.data.GetContainers().size());

        // Remove rho worst containers
        for (int i = 0; i < rho; i++) {

            // The tour index and container index of the container that would
            // lead to the maximum savings in the total schedule cost when removed
            int tourIndex = Parameters._404;
            int contIndex = Parameters._404;
            double maxSavings = Double.NEGATIVE_INFINITY;

            // Loop over all tours in the schedule to evaluate the savings 
            // obtainable from each of them if their worst container is removed
            for (int j = 0; j < this.schedule.size(); j++) {
                ImmutablePair<Integer, Double> pair = this.schedule.get(j).FindWorstContainerRemoval();
                if (pair.getValue() > maxSavings) {
                    tourIndex = j;
                    contIndex = pair.getKey();
                    maxSavings = pair.getValue();
                }
            }
            // If exists, remove container
            if (tourIndex != Parameters._404 && contIndex != Parameters._404) {
                this.schedule.get(tourIndex).RemovePoint(contIndex);
            }
        }

        // Return rho
        return rho;
    }

    /**
     * Removes customers that are close to each other (Shaw, 1997). In
     * particular, it randomly selects a tour and a container in it. It
     * calculates the distance dist_min to the nearest container also served in
     * this tour. It removes all containers within 2 * dist_min in this tour.
     *
     * @return 1 - number of times operator is applied
     */
    protected int removeShawContainers() {

        // Select one random tour
        int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
        Tour tour = this.schedule.get(tourIndex);
        // Perform Shaw removal
        tour.RemoveShawContainers();

        // Return 1
        return 1;
    }
   
    /**
     * Removes customers that are close to each other (Shaw, 1997). In
     * particular, it randomly selects a tour and a container in it. It
     * calculates the distance dist_min to the nearest container also served in
     * this tour. It removes all containers within 2 * dist_min in this tour
     * and all the tours in the schedule with the same day as the random selected tour
     *
     * @return 1 - number of times operator is applied
     */
    protected int removeAllShawContainers() {
        // Select one random tour
        int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
        Tour tour = this.schedule.get(tourIndex);
        // Perform Shaw removal on this tour
        ImmutablePair<Point, Double> pointDistance = tour.RemoveShawContainers();
        
        // Perform Shaw removal on the other tours of the schedule
        if(pointDistance.left != null){
            for(int i = 0; i < this.schedule.size(); i++) {
                if(i != tourIndex) {
                    Tour t = this.schedule.get(i);
                    //check that it is same day
                    if(t.GetDay() == tour.GetDay()){
                        t.RemoveShawContainers(pointDistance.left, pointDistance.right);
                    }
                }
            }
        }
        // Return 1
        return 1;
    }
   
    /**
     * Removes customers that are close to each other (Shaw, 1997). In
     * particular, it randomly selects a tour and a container in it. It
     * calculates the relatedness of the container with all others containers
     * scheduled the same day. The relatedness is the following weighted sum:
     * a*distance + b*time_diff + c*overflow_diff.
     * The distance, time_diff and overflow_diff are normalized between 0 and 1.
     * The coefficients a,b, and c must sum to 1.
     * It removes all containers under the given threshold in all the tours
     * scheduled the same day.
     * 
     * @param threshold removes the points with relatedness under this threshold
     * @param a weight factor for distance
     * @param b weight factor for time_diff
     * @param c weight factor for overflow_diff
     * @param d weight factor for volume_diff
     * 
     * @return 1 - number of times operator is applied
     */
    protected int removeAllShawContainersRelatedness(double threshold, double a, double b, double c) {
        // Select one random tour
        int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
        Tour tour = this.schedule.get(tourIndex);
        // Select a random container from the random tour
        int randContainerIndex = tour.GetRandContainerIndex();
        
        // If there are containers in the tour, perform Shaw removal
        if (randContainerIndex != Parameters._404) {
            Point randContainer = tour.GetTour().get(randContainerIndex);
            
            ArrayList<Double> distances = new ArrayList<>();
            ArrayList<Double> time_diff = new ArrayList<>();
            ArrayList<Double> overflow_diff = new ArrayList<>();
            
            // this list is used to keep the tour and point (container)
            // In the pair, the Integer is the index of the tour and the Point the container in the tour
            ArrayList<ImmutablePair<Integer, Point>> containers = new ArrayList<>(); 
            
            // Now we iterate through all the tours and compute the three types of relatedness
            // They will be normalized at the end
            for(int i = 0; i < this.schedule.size(); i++) {
                Tour t = this.schedule.get(i);
                //check that it is same day
                if(t.GetDay() == tour.GetDay()){
                    // We compute the three relatedness measures and store them in their respective list
                    for(Point cont: t.GetContainers()){
                        if (cont.GetSimpleDWid()!= randContainer.GetSimpleDWid()) { // if it is not the random container
                            //distance difference
                            distances.add(this.data.GetDistance(cont, randContainer));
                            //time difference
                            double time = Math.abs(cont.GetTWLower()-randContainer.GetTWLower()) + Math.abs(cont.GetTWUpper()-randContainer.GetTWUpper());
                            time_diff.add(time);
                            //overflow probability difference
                            double overflow = t.GetOverflowProbability(cont) - tour.GetOverflowProbability(randContainer);
                            overflow_diff.add(Math.abs(overflow));
                            //store the tour and point
                            containers.add(new ImmutablePair<>(i, cont));
                        }
                    }
                } 
            }
            
            if(!distances.isEmpty()){
                // We normalize the three relatedness measures
                normalize(distances);
                normalize(time_diff);
                normalize(overflow_diff);
                
                ArrayList<Double> relatedness = new ArrayList<>();
                
                // We compute the relatedness of each point
                for(int i = 0; i<distances.size(); i++){
                    double weightedRelatedness = a*distances.get(i) + b*time_diff.get(i) + c*overflow_diff.get(i); //+ d*volume_diff.get(i);
                    relatedness.add(weightedRelatedness);
                }
                // The measure is normalized
                normalize(relatedness);
                
                // We remove the points under the relatedness threshold
                for(int i = 0; i<relatedness.size(); i++){
                    if(relatedness.get(i) <= threshold){
                        Tour t = this.schedule.get(containers.get(i).left);
                        t.RemovePoint(containers.get(i).right);
                    }
                }
                tour.RemovePoint(randContainer);
            }
        }
        return 1;
    }

    /**
     * Removes one cluster of container. It selects a random day and then divides the containers 
     * visited during this day into k clusters = number of tours scheduled this day. 
     * If there is only one tour, we divide the tour into 2 clusters.
     * Then a random cluster is selected and all containers from this cluster are removed.
     * The cluster cannot be larger than half of the containers visited during the day.
     * The clusters are computed using Kruskal's algorithm for the minimum spanning tree problem,
     * it stops when k connected components are left.
     * 
     * @return 1 - number of times operator is applied
     */
    protected int removeContainerCluster(){
        // Get a random day in the schedule
        int randDay = this.data.GetRand().nextInt(this.data.GetPhLength());
        // Compute the number of routes happening this day
        int k = 0;
        // We store to which route belongs each container
        HashMap<Point, Tour> containerTour = new HashMap<>();
        // Containers 
        ArrayList<Point> containers = new ArrayList<>();
        // Iterate through the routes, add all containers of the route to the list of containers
        for(Tour t: this.schedule){
            if(t.GetDay() == randDay && !t.GetContainers().isEmpty()) {
                k++;
                for(Point cont: t.GetContainers()){
                    containers.add(cont);
                    // Add the reference to the tour containing the container
                    containerTour.put(cont, t);
                }
            }
        }
        
        // If there is only one route, we compute two clusters
        if(k == 1){
            k++;
        }
        
        // Clustering 
        if(containers.size() > 0){
            // Computes the k clusters for all containers scheduled the same random day
            KruskalClustering clustering = new KruskalClustering(containers, k, this.data);
            
            // Keeps only clusters smaller than half the total number of containers
            for (Iterator<Integer> iterator = clustering.clusters.keySet().iterator(); iterator.hasNext(); ) {
                int cluster = iterator.next();
                if(clustering.clusters.get(cluster).size() > containers.size()/2){
                    iterator.remove();
                }
            }
            
            if(clustering.clusters.size() > 0){
                // Chooses a random cluster to remove 
                int clusterToRemove = this.data.GetRand().nextInt(clustering.clusters.size());
                int c = 0;
                int i = 0;
                // clusterToRemove is between 0 and the number of clusters, but we removed some
                // clusters (not all indices are accessible), so we get the indice of the random position clusterToRemove
                for(Integer clust : clustering.clusters.keySet()) {
                    if (i == clusterToRemove){
                        c =  clust;
                    }
                    i++;
                }
                // We remove the points of the cluster, accessing the reference to which route each container belongs
                for(Point p : clustering.getCluster(c)){
                    containerTour.get(p).RemovePoint(p);
                }
            }
        }
        return 1;
    }
    
    /**
     * Selects randomly a day and empties all tours performed on this day.
     *
     * @return 1 - number of times operator is applied
     */
    protected int emptyOneDay() {

        // Select one random day
        int randDay = this.data.GetRand().nextInt(this.data.GetPhLength());

        // Empty all tours performed on this day
        for (Tour tour : this.schedule) {
            if (tour.GetDay() == randDay) {
                tour.EmptyTour();
            }
        }

        // Return 1 
        return 1;
    }

    /**
     * Selects randomly a truck and empties all tours performed by this truck.
     *
     * @return 1 - number of times operator is applied
     */
    protected int emptyOneTruck() {

        // Select one random truck
        int truckIndex = this.data.GetRand().nextInt(this.data.GetTrucks().size());
        Truck randTruck = this.data.GetTrucks().get(truckIndex);

        // Empty all tour performed by this truck
        for (Tour tour : this.schedule) {
            if (tour.GetTruck().GetWid() == randTruck.GetWid()) {
                tour.EmptyTour();
            }
        }

        // Return 1
        return 1;
    }

    /**
     * Selects randomly a tour and removes a random dump from it, except the
     * last one.
     *
     * @return 1 - number of times operator is applied
     */
    protected int removeRandomDump() {

        // Select one random tour
        int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
        Tour tour = this.schedule.get(tourIndex);
        // Remove a random dump from the tour
        tour.RemoveRandomDump();

        // Return 1
        return 1;
    }

    /**
     * Removes the worst dump from the schedule considering the total schedule
     * cost, ie the dump that would lead to the largest savings if removed.
     *
     * @return 1 - number of times operator is applied
     */
    protected int removeWorstDump() {

        // The tour index and dump index of the dump that would
        // lead to the maximum savings in the total schedule cost when removed
        int tourIndex = Parameters._404;
        int dumpIndex = Parameters._404;
        double maxSavings = Double.NEGATIVE_INFINITY;

        // Loop over all tours in the schedule to evaluate the savings 
        // obtainable from each of them if their worst dump is removed
        for (int i = 0; i < this.schedule.size(); i++) {
            ImmutablePair<Integer, Double> pair = this.schedule.get(i).FindWorstDumpRemoval();
            if (pair.getValue() > maxSavings) {
                tourIndex = i;
                dumpIndex = pair.getKey();
                maxSavings = pair.getValue();
            }
        }
        // If exists, remove container
        if (tourIndex != Parameters._404 && dumpIndex != Parameters._404) {
            this.schedule.get(tourIndex).RemovePoint(dumpIndex);
        }

        // Return 1
        return 1;
    }

    /**
     * Removes consecutive container visits.
     *
     * @return 1 - number of times operator is applied
     */
    protected int removeConsecutiveVisits() {

        // For each container
        for (Point container : this.data.GetContainers()) {

            // For each day in the planning horizon - 1, check if container 
            // is also visited the following day
            for (int d = 0; d < this.data.GetPhLength() - 1; d++) {
                if (this.cTracker.GetVisit(container, d) == true
                        && this.cTracker.GetVisit(container, d + 1) == true) {

                    // Check all tours executed on day d+1 and remove container 
                    for (Tour tour : this.schedule) {
                        if (tour.GetDay() == d + 1) {
                            tour.RemovePoint(container);
                        }
                    }
                }
            }
        }

        // Return 1
        return 1;
    }

    /**
     * Selects randomly a tour and inserts a random container in it using the
     * best insertion principle. It is repeated rho times.
     *
     * @return rho - number of times operator is applied
     */
    protected int insertRandomRhoContainers() {

        // Select a rho with an upper bound on the number of containers in the data
        int rho = this.rho(this.data.GetContainers().size());

        // Insert rho random containers
        for (int i = 0; i < rho; i++) {

            // Select one random tour
            int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
            Tour tour = this.schedule.get(tourIndex);

            // Insert a random container in the tour
            tour.InsertRandomContainer();
        }

        // Return rho
        return rho;
    }
    
//    /**
//     * Selects a random container and inserts it in the best position in the
//     * schedule, considering the total cost. It is repeated rho times.
//     *
//     * @return rho - number of times operator is applied
//     */
//    protected int insertBestRhoContainers() {
//        // Select a rho with an upper bound on the number of containers in the data
//        int rho = this.rho(this.data.GetContainers().size());
//        for (int i = 0; i < rho; i++) {
//            // Select a random container from the data
//            int randIndex = this.data.GetRand().nextInt(this.data.GetContainers().size());
//            Point randContainer = this.data.GetContainers().get(randIndex);
//            insertBestContainer(randContainer);
//        }
//        return rho;
//    }
    
    /**
     * It inserts the container in the best position in the
     * schedule, considering the total cost at the given day
     *
     * @param container the container to insert
     * @param day the day it has to be inserted
     *
     */
    protected void insertBestContainer(Point container, int day) {
        // The tour index and position of the container that would
        // lead to the minimum increase in the total schedule cost when inserted
        int tourIndex = Parameters._404;
        int contIndex = Parameters._404;
        double minIncrease = Double.POSITIVE_INFINITY;

        // Loop over all tours in the schedule to evaluate the increase 
        // obtainable from each of them if the container is inserted in the best position
        for (int j = 0; j < this.schedule.size(); j++) {
            if(this.schedule.get(j).GetDay() == day){
                ImmutablePair<Integer, Double> pair = this.schedule.get(j).FindBestContainerInsertion(container);
                if (pair.getValue() < minIncrease) {
                    tourIndex = j;
                    contIndex = pair.getKey();
                    minIncrease = pair.getValue();
                }
            }
        }
        // If feasible, insert container
        if (tourIndex != Parameters._404 && contIndex != Parameters._404) {
            this.schedule.get(tourIndex).InsertPoint(contIndex, container);
        }
    }
    
    /**
     * It inserts the rho containers that would lead to the minimum increase of the cost
     * function. 
     * 
     * @return 1 - number of times operator is applied
     */
    protected int insertBestRhoContainers() {
        // Select a rho with an upper bound on the number of containers in the data
        int rho = this.rho(this.data.GetContainers().size());
        // List of containers with their respective minimum cost increase value
        ArrayList<ImmutablePair<Point, Double>> costValues = new ArrayList<>();
        // We store the best position of each point
        HashMap<Point, ImmutablePair<Tour, Integer>> bestPosition = new HashMap<>();
        
        // Loop over the containers to choose one
        for(Point container : this.data.GetContainers()){
            // check if a position has been found
            boolean found = false;
            double bestCost = Double.MAX_VALUE;
            //Loop over the possible routes
            for(Tour tour : this.schedule){
                ImmutablePair<Integer, Double> best = tour.FindBestContainerInsertion(container);
                if(best.left != Parameters._404){
                    // Add the cost increase if we insert the container in its best position in this tour
                    if(best.right < bestCost){
                        found = true;
                        bestCost = best.right;
                        // Add the best position if it can be inserted in the tour
                        bestPosition.put(container, new ImmutablePair<>(tour, best.left));
                    }
                }
            }
            // The container can be inserted
            if(found){
                costValues.add(new ImmutablePair<>(container, bestCost));
            }
        }
        // We sort the cost values in increasing order
        if(!costValues.isEmpty()){
            Collections.sort(costValues, new Comparator<ImmutablePair<Point, Double>>() {
                @Override
                public int compare(final ImmutablePair<Point, Double> o1, final ImmutablePair<Point, Double> o2) {
                    return o1.right.compareTo(o2.right);
                }
            });
            // insert rho containers
            for (int i = 0; i < rho && i < costValues.size(); i++){
                Point containerToAdd = costValues.get(i).left;
                // insert it at the best position
                ImmutablePair<Tour, Integer> best = bestPosition.get(containerToAdd);
                best.left.InsertPoint(best.right, containerToAdd);
            }
        }
        // Return rho
        return rho;
    }
    
    /**
     * We choose to insert the container which maximizes the difference in cost between inserting
     * it in its best position and k-th best position. Then we insert it at its minimum cost position.
     * It is repeated rho times.
     * If we can't compute the regret value (k is smaller than the number of possible routes),
     * we compute the largest k. The containers with the smallest k are privileged.
     *
     * @param k the k-th best position to compute the regret 
     * @return rho - number of times operator is applied
     */
    protected int insertRhoContainersWithRegret(int k) {

        // Select a rho with an upper bound on the number of containers in the data
        int rho = this.rho(this.data.GetContainers().size());
        
        // List of containers with their respective regret-k value, and the largest k found if k is too large
        // <Point, <Regret, largest_k>>
        ArrayList<ImmutablePair<Point, ImmutablePair<Double, Integer>>> regretValues = new ArrayList<>();
        // We store the best position of each point
        HashMap<Point, ImmutablePair<Tour, Integer>> bestPosition = new HashMap<>();
        
        // Loop over the containers to choose one
        for(Point container : this.data.GetContainers()){
            //One entry for the tour and the cost increase when best insertion
            ArrayList<Double> costs = new ArrayList<>();
            double bestCost = Double.MAX_VALUE;
            //Loop over the possible routes
            for(Tour tour : this.schedule){
                ImmutablePair<Integer, Double> best = tour.FindBestContainerInsertion(container);
                if(best.left != Parameters._404){
                    // Add the cost increase if we insert the container in its best position in this tour
                    costs.add(best.right);
                    if(best.right < bestCost){
                        bestCost = best.right;
                        // Add the best position if it can be inserted in the tour
                        bestPosition.put(container, new ImmutablePair<>(tour, best.left));
                    }
                }
            }
            if(!costs.isEmpty()){
                Collections.sort(costs);
                //We can compute the regret value (only if k is smaller than the number of routes), otherwise we take the largest possible k
                if(k <= costs.size()){
                    double regret = costs.get(k-1)-costs.get(0);
                    regretValues.add(new ImmutablePair<>(container, new ImmutablePair<>(regret, k)));
                }
                else{
                    int largest_k = costs.size();
                    double regret = costs.get(largest_k-1)-costs.get(0);
                    regretValues.add(new ImmutablePair<>(container, new ImmutablePair<>(regret, largest_k)));
                }
            }
        }
        // We need to sort the regretValues in reversed order and get the container with the highest regret
        // There is a priority for the ones with lowest k
        if(!regretValues.isEmpty()){
            Collections.sort(regretValues, new Comparator<ImmutablePair<Point, ImmutablePair<Double, Integer>>>() {
                @Override
                public int compare(final ImmutablePair<Point, ImmutablePair<Double, Integer>> o1, final ImmutablePair<Point, ImmutablePair<Double, Integer>> o2) {
                    // if o1 has smallest k
                    if(o1.right.right < o2.right.right){
                        return -1;
                    }
                    else if(o1.right.right > o2.right.right){
                        return 1;
                    }
                    // we compare the cost if k is the same, priority to higher cost
                    else{
                        return o2.right.left.compareTo(o1.right.left);
                    }
                }
            });
            // insert rho containers
            for (int i = 0; i < rho && i < regretValues.size(); i++){
                Point containerToAdd = regretValues.get(i).left;
                // insert it at the best position
                ImmutablePair<Tour, Integer> best = bestPosition.get(containerToAdd);
                best.left.InsertPoint(best.right, containerToAdd);
            }
        }
        // Return rho
        return rho;
    }

    /**
     * Inserts customers that are close to each other (Shaw, 1997). In
     * paricular, it randomly selects a tour and a container not served in it.
     * It calculates the distance dist_min to the nearest container. It inserts
     * in the tour all containers within 2 * dist_min that are not already
     * served in the tour using the best insertion principle.
     *
     * @return 1 - number of times operator is applied
     */
    protected int insertShawContainers() {
        
        // Select one random tour
        int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
        Tour tour = this.schedule.get(tourIndex);
        // Perform Shaw insertion
        tour.InsertShawContainers();

        // Return 1;
        return 1;
    }
    
    /**
     * Inserts customers that are close to each other (Shaw, 1997). 
     * It selects a random container from the list of containers and a random day in the schedule,
     * find all containers that are below the relatedness measure threashold,
     * the relatedness being a*distance + b*time_diff + c*overflow_diff, 
     * and insert the containers in the best position considering all tours scheduled the random day
     * The distance, time_diff and overflow_diff are normalized between 0 and 1.
     * The coefficients a,b, and c must sum to 1.
     * 
     * @param threshold insert the containers with relatedness under this threshold
     * @param a weight factor for distance
     * @param b weight factor for time_diff
     * @param c weight factor for overflow_diff
     * @param d weight factor for volume_diff
     * 
     * @return 1 - number of times operator is applied
     */
 
    protected int insertShawContainersRelatedness(double threshold, double a, double b, double c) {
        //select a random day
        int randDay = this.data.GetRand().nextInt(this.data.GetPhLength());
        // if there is at least one container
        if(this.cTracker.GetAvblContainers(randDay).size() > 0){

            // select random container
            int contIndex = this.data.GetRand().nextInt(this.cTracker.GetAvblContainers(randDay).size());
            Point container = this.cTracker.GetAvblContainers(randDay).get(contIndex);
            
            // List storing containers under threshold with "container"
            ArrayList<Point> contToInsert = new ArrayList();

            ArrayList<Double> distances = new ArrayList<>();
            ArrayList<Double> time_diff = new ArrayList<>();
            ArrayList<Double> overflow_diff = new ArrayList<>();

            for(Point con : this.cTracker.GetAvblContainers(randDay)){
                //distance difference
                distances.add(this.data.GetDistance(container, con));
                //time differencebva
                double time = Math.abs(container.GetTWLower()-con.GetTWLower()) + Math.abs(container.GetTWUpper()-con.GetTWUpper());
                time_diff.add(time);
                //overflow probability difference
                double overflow = this.cTracker.GetOverflowProbability(container,randDay) - this.cTracker.GetOverflowProbability(con,randDay);
                overflow_diff.add(Math.abs(overflow));
                contToInsert.add(con);
            }
            // We normalize the three relatedness measures
            normalize(distances);          
            normalize(time_diff); 
            normalize(overflow_diff);
            ArrayList<Double> relatedness = new ArrayList<>();

            // We compute the relatedness of each point
            for(int i = 0; i<distances.size(); i++){
                double weightedRelatedness = a*distances.get(i) + b*time_diff.get(i) + c*overflow_diff.get(i); //+ d*volume_diff.get(i);
                relatedness.add(weightedRelatedness);
            }
            
            // The measure is normalized
            normalize(relatedness);
            // We add the points under the relatedness threshold
            for(int i = 0; i < contToInsert.size(); i++){
                if(relatedness.get(i) <= threshold){
                    insertBestContainer(contToInsert.get(i), randDay);
                }
            }
        }
     
        return 1;
    }
    
    
    /**
     * Swaps the assignments of two random containers from two random tours,
     * using best insertion.
     *
     * @return rho - number of times operator is applied
     */
    protected int swapAssgContainers() {

        // Select a rho with an upper bound on the number of containers in the data
        int rho = this.rho(this.data.GetContainers().size());

        // Find all tours with one or more containers
        ArrayList<Integer> tourIndexes = new ArrayList<>();
        for (int i = 0; i < this.schedule.size(); i++) {
            if (this.schedule.get(i).GetNumContainers() >= 1) {
                tourIndexes.add(i);
            }
        }

        // If there is more than one tour with more than one point
        if (tourIndexes.size() > 1) {

            // Perform rho swaps
            for (int i = 0; i < rho; i++) {

                // Select two different random tours
                int t1Index = tourIndexes.get(this.data.GetRand().nextInt(tourIndexes.size()));
                int t2Index = tourIndexes.get(this.data.GetRand().nextInt(tourIndexes.size()));
                while (t2Index == t1Index) {
                    t2Index = tourIndexes.get(this.data.GetRand().nextInt(tourIndexes.size()));
                }
                Tour t1 = this.schedule.get(t1Index);
                Tour t2 = this.schedule.get(t2Index);

                // Get a random container index from each of the tours
                int cont1Index = t1.GetRandContainerIndex();
                int cont2Index = t2.GetRandContainerIndex();

                // Remove the containers
                Point cont1 = t1.RemovePoint(cont1Index);
                Point cont2 = t2.RemovePoint(cont2Index);

                // If possible to swap, do it, otherwise reinsert as original
                if (this.cTracker.GetVisit(cont1, t2.GetDay()) == false
                        && this.cTracker.GetVisit(cont2, t1.GetDay()) == false) {

                    // Swap the container assignments using best insertion
                    ImmutablePair<Integer, Double> pair = t2.FindBestContainerInsertion(cont1);
                    t2.InsertPoint(pair.getKey(), cont1);
                    pair = t1.FindBestContainerInsertion(cont2);
                    t1.InsertPoint(pair.getKey(), cont2);

                } else {
                    t1.InsertPoint(cont1Index, cont1);
                    t2.InsertPoint(cont2Index, cont2);
                }
            }
        }

        // Return rho
        return rho;
    }

    /**
     * Selects randomly a tour and inserts a random dump at a random position in
     * it
     *
     * @return 1 - number of times operator is applied
     */
    protected int insertRandomDump() {
        // Select one random tour
        int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
        Tour tour = this.schedule.get(tourIndex);
        // Insert a random dump in the tour
        tour.InsertRandomDump();

        // Return 1
        return 1;
    }

    /**
     * Inserts a random dump in the tour and the position in the tour that would
     * lead to the least cost increase considering the total schedule cost.
     *
     * @return 1 - number of times operator is applied
     */
    protected int insertBestDump() {
        // Select a random dump from the data
        int randIndex = this.data.GetRand().nextInt(this.data.GetDumps().size());
        Point randDump = this.data.GetDumps().get(randIndex);   
        insertBestDump(randDump);
        
        return 1;
    }
    
    /**
     * Inserts a dump in the tour and the position in the tour that would
     * lead to the least cost increase considering the total schedule cost.
     *
     * @param dump the dump to insert at the best position
     * 
     * @return 1 - number of times operator is applied
     */
    protected int insertBestDump(Point dump) {

        // The tour index and position of the dump that would
        // lead to the minimum increase in the total schedule cost when inserted
        int tourIndex = Parameters._404;
        int dumpIndex = Parameters._404;
        double minIncrease = Double.POSITIVE_INFINITY;

        // Loop over all tours in the schedule to evaluate the increase 
        // obtainable from each of them if the dump is inserted in the best position
        for (int j = 0; j < this.schedule.size(); j++) {
            ImmutablePair<Integer, Double> pair = this.schedule.get(j).FindBestDumpInsertion(dump);
            if (pair.getValue() < minIncrease) {
                tourIndex = j;
                dumpIndex = pair.getKey();
                minIncrease = pair.getValue();
            }
        }
        // If feasible, insert container
        if (tourIndex != Parameters._404 && dumpIndex != Parameters._404) {
            this.schedule.get(tourIndex).InsertPoint(dumpIndex, dump);
        }

        // Return 1
        return 1;
    }
    
    /**
     * Selects randomly two tours and swaps random dumps.
     *
     * @return 1 - number of times operator is applied
     */
    protected int swapAssgDumps() {

        // Select two random tour indexes
        int t1Index = this.data.GetRand().nextInt(this.schedule.size());
        int t2Index = this.data.GetRand().nextInt(this.schedule.size());

        // If the tour indexes are different
        if (t1Index != t2Index) {
            // Random dumps from the two tours
            int t1DumpIndex = this.schedule.get(t1Index).GetRandomDumpIndex();
            int t2DumpIndex = this.schedule.get(t2Index).GetRandomDumpIndex();
            Point t1Dump = this.schedule.get(t1Index).RemovePoint(t1DumpIndex);
            Point t2Dump = this.schedule.get(t2Index).RemovePoint(t2DumpIndex);

            // Swap the dumps
            this.schedule.get(t1Index).InsertPoint(t1DumpIndex, t2Dump);
            this.schedule.get(t2Index).InsertPoint(t2DumpIndex, t1Dump);
        }

        // If the tour indexes are the same and the tour has more than one dump visit
        if (t1Index == t2Index && this.schedule.get(t1Index).GetNumDumpVisits() > 1) {
            // Random dumps from the two tours
            int t1DumpIndex = this.schedule.get(t1Index).GetRandomDumpIndex();
            int t2DumpIndex = this.schedule.get(t2Index).GetRandomDumpIndex();
            while (t1DumpIndex == t2DumpIndex) {
                t2DumpIndex = this.schedule.get(t2Index).GetRandomDumpIndex();
            }
            // Swap the dumps
            this.schedule.get(t1Index).SwapPoints(t1DumpIndex, t2DumpIndex);
        }

        // Return 1
        return 1;
    }

    /**
     * Selects randomly a tour and a dump in it and replaces it with a random
     * dump from the available dumps in the data.
     *
     * @return 1 - number of times operator is applied
     */
    protected int replaceRandomDump() {

        // Select one random tour
        int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
        Tour tour = this.schedule.get(tourIndex);
        // Replace dump
        tour.ReplaceRandomDump();

        // Return 1
        return 1;
    }

    /**
     * Selects a random tour and reorders the dump visits in the tour.
     *
     * @return 1 - number of times operator is applied
     */
    protected int reorderDumps() {

        // Select one random tour
        int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
        Tour tour = this.schedule.get(tourIndex);
        // Reorder dumps
        tour.ReorderDumps();

        // Return 1
        return 1;
    }

    /**
     * Selects a random tour and replaces its destination starting point. Then,
     * it looks for the next tour executed by the same truck, and if such exists
     * in the planning horizon, changes its origin starting point.
     *
     * @return 1 - number of times operator is applied
     */
    protected int replaceStartingPoint() {

        // Select one random tour of which the destination starting point is to be changed
        int tourIndex = this.data.GetRand().nextInt(this.schedule.size());
        Tour tourChangeDestSP = this.schedule.get(tourIndex);

        // Get the truck's flexible starting points
        ArrayList<Point> flexStartingPoints = tourChangeDestSP.GetTruck().GetFlexStartingPoints();

        // If there are flexible starting points, execute the starting point replacement
        if (!flexStartingPoints.isEmpty()) {

            // Select a new starting point from the truck's list of flexible starting points
            int newStartingPointIndex = this.data.GetRand().nextInt(flexStartingPoints.size());
            Point newStartingPoint = flexStartingPoints.get(newStartingPointIndex);

            // Replace the tour's destination starting point and set it as the new 
            // destination starting point of the Tour object
            tourChangeDestSP.SetPoint(tourChangeDestSP.GetSize() - 1, newStartingPoint);
            tourChangeDestSP.SetDestinationStartingPoint(newStartingPoint);

            // Now check if this truck is available and executing another tour on its next available day
            // in the planning horizon, and if yes, change that tour's origin starting point
            // and set it as the new origin starting point of the Tour object
            int truckNextAvblDay = Integer.MAX_VALUE;
            Tour tourChangeOrigSP = null;
            for (Tour tour : this.schedule) {
                if (tour.GetDay() > tourChangeDestSP.GetDay()
                        && tour.GetDay() < truckNextAvblDay
                        && tour.GetTruck().GetWid() == tourChangeDestSP.GetTruck().GetWid()) {
                    tourChangeOrigSP = tour;
                    truckNextAvblDay = tour.GetDay();
                }
            }
            if (tourChangeOrigSP != null) {
                tourChangeOrigSP.SetPoint(0, newStartingPoint);
                tourChangeOrigSP.SetOriginStartingPoint(newStartingPoint);
            }
        }

        // Return 1
        return 1;
    }

    /**
     * Simulates demand scenarios over the final solution and logs data of
     * interest.
     *
     * @return a Simulation object holding logged data of interest
     */
    public Simulation SimulateAndLogDemandScenarios() {

        // Initialize a Simulation object to log data of interest
        Simulation simulation = new Simulation();

        // Simulate a number of scenarios to evaluate container violations and route failures
        for (int i = 0; i < Parameters.expNumberSimulationScenarios; i++) {

            // Simulate demand scenario
            this.cTracker.SimulateDemandScenario(true);

            // Log backorder violations
            int backorderViolationCount = 0;
            double backorderViolationValue = 0.d;
            for (Point container : this.data.GetContainers()) {
                if (this.cTracker.GetContainerViolation(container, 0) > 0
                        && this.cTracker.GetVisit(container, 0) == false) {
                    backorderViolationCount++;
                    backorderViolationValue += this.cTracker.GetContainerViolation(container, 0);
                }
            }
            // Add to simulation data
            simulation.AddBackorderViolationCount(backorderViolationCount);
            simulation.AddBackOrderViolationValue(backorderViolationValue);

            // Log container violations 
            int containerViolationCount = 0;
            double containerViolationValue = 0.d;
            for (Point container : this.data.GetContainers()) {
                for (int d = 1; d < this.data.GetPhLength() + 1; d++) {
                    if (this.cTracker.GetContainerViolation(container, d) > 0) {
                        containerViolationCount++;
                        containerViolationValue += this.cTracker.GetContainerViolation(container, d);
                    }
                }
            }
            // Add to simulation data
            simulation.AddContainerViolationCount(containerViolationCount);
            simulation.AddContainerViolationValue(containerViolationValue);

            // Log route failure
            int routeFailureCount = 0;
            double routeFailureValue = 0.d;
            for (Tour tour : this.schedule) {
                // Retrieve tour cumulative volume loads from the Tour class
                ArrayList<Double> tourCumuVolumeLoads = tour.GetCumuVolumeLoads();
                // Adapt truck max volume to max weight if it is more restrictive
                double truckAdaptedVolume = Math.min(tour.GetTruck().GetMaxVolume(),
                        tour.GetTruck().GetMaxWeight() * Parameters.flowSpecWeightCF / this.data.GetFlowSpecWeight());
                // Count route failures and their value considering the point immediately before going to the dump
                for (int p = 0; p < tourCumuVolumeLoads.size() - 1; p++) {
                    if (tourCumuVolumeLoads.get(p) > truckAdaptedVolume
                            && tourCumuVolumeLoads.get(p) > tourCumuVolumeLoads.get(p + 1)) {
                        routeFailureCount++;
                        routeFailureValue += (tourCumuVolumeLoads.get(p) - truckAdaptedVolume);
                    }
                }
            }
            // Add to simulation data
            simulation.AddRouteFailureCount(routeFailureCount);
            simulation.AddRouteFailureValue(routeFailureValue);
        }

        // Return Simulation object
        return simulation;
    }
    
    /**
    * Normalize the given list by feature scaling (x-min)/(max-min)
    * 
    * @param l list to be normalized
    */
    
    public void normalize(ArrayList<Double> l) {
        if(l.size() > 1){
            Double min = Double.MAX_VALUE;
            Double max = -Double.MAX_VALUE;
            // Look for maximum and minimum values in the list 
            for(Double e: l){
                if(e<min) {
                    min = e;
                }
                if(e>max) {
                    max = e;
                }
            }
            
            for(int i = 0; i < l.size(); i++) {
                double normalized_value = 1.0;
                // Avoid division by 0
                if(min != max){
                    normalized_value = (l.get(i)-min)/(max-min);
                }
                // If min = max, noramlized value is 1
                l.set(i, normalized_value); 
            }
        }
        // Only one element in the list
        else{
            l.set(0,1.0);
        }
    }
}
