package alns.rolling;

import alns.algo.SAALNS;
import alns.algo.SAALNSA;
import alns.data.Data;
import alns.data.ForecastRolling;
import alns.data.Point;
import alns.data.Truck;
import alns.param.Parameters;
import alns.schedule.Schedule;
import alns.tour.Tour;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * Implements a rolling horizon solution for the IRP.
 *
 * @author Markov
 */
public class Rolling {

    // Reported levels and associated estimated levels data object
    private final Data repData;
    private final Data estData;

    // Rolling horizon length
    private final int rhLength;
    // Number of rollovers
    private final int nbRollovers;
    // Number of data days
    private final int nbDays;

    // Daily routing costs 
    private final ArrayList<Double> dailyRoutingCosts;

    // Runtimes
    private final ArrayList<Long> runTimes;

    // Hash maps for linking containers to: (1) initial levels at the start of the
    // data days, (2) day of the most recent visit, (3) reported level demands
    // for each data day, (4) estimated level demands for each data day
    private final LinkedHashMap<Integer, Double> inits;
    private final LinkedHashMap<Integer, Integer> mostRecentVisits;
    private final LinkedHashMap<Integer, ArrayList<Double>> repLevelDemands;
    private final LinkedHashMap<Integer, ArrayList<Double>> estLevelDemands;

    // Hash maps for linking trucks to: (1) required returns to home on each of the 
    // data days, (2) availabilities on each of the data days
    private final LinkedHashMap<Integer, ArrayList<Boolean>> requiredReturnsToHome;
    private final LinkedHashMap<Integer, ArrayList<Boolean>> availabilities;

//    // Collections for debugging and verification purposes
//    private final LinkedHashMap<Integer, ArrayList<Double>> rhInits;
//    private final LinkedHashMap<Integer, ArrayList<Integer>> rhVisits;
//    private final ArrayList<LinkedHashMap<Integer, ArrayList<Double>>> rhEstLevelDemands;
//    private final ArrayList<LinkedHashMap<Integer, ArrayList<Double>>> rhProbabilities;
    
    /**
     * Assignment and initialization constructor.
     *
     * @param repData reported data object
     * @param estData estimated data object
     * @param rhLength rolling horizon length
     * @param nbRollovers number of roll overs
     * @throws java.io.IOException
     * @throws java.io.FileNotFoundException
     * @throws java.lang.ClassNotFoundException
     */
    public Rolling(Data repData, Data estData, int rhLength, int nbRollovers)
            throws IOException, FileNotFoundException, ClassNotFoundException {

        // Read in reported level and estimated level data
        this.repData = repData;
        this.estData = estData;

        // Assign rolling horizon length
        this.rhLength = rhLength;
        // Assign number of roll overs
        this.nbRollovers = nbRollovers;
        // Assign number of data days as the planning horizon of the original instance
        // which was generated for a period of 2 weeks
        this.nbDays = this.repData.GetPhLength();

        // Initialize the daily routing costs
        this.dailyRoutingCosts = new ArrayList<>();

        // Initialize runtimes
        this.runTimes = new ArrayList<>();

        // Initialize the hash maps for linking containers to: (1) initial levels 
        // at the start of the data days, (2) day of the most recent visit, 
        // (3) reported level demands for each data day, (4) estimated level 
        // demands for each data day
        this.inits = new LinkedHashMap<>();
        this.mostRecentVisits = new LinkedHashMap<>();
        this.repLevelDemands = new LinkedHashMap<>();
        this.estLevelDemands = new LinkedHashMap<>();

        // Initialize the hash maps for linking trucks to: (1) required returns to home
        // for each data day, (2) availabilities for each data day
        this.requiredReturnsToHome = new LinkedHashMap<>();
        this.availabilities = new LinkedHashMap<>();

//        // Initialize collections for debugging and verification purposes
//        this.rhInits = new LinkedHashMap<>();
//        this.rhVisits = new LinkedHashMap<>();
//        this.rhEstLevelDemands = new ArrayList<>();
//        this.rhProbabilities = new ArrayList<>();
        // Populate:
        // (1) initial levels at the start of the data days with reported values
        // (2) day of the most recent visit with a default value (-404)
        // (3) reported level demands for each data day with reported values
        for (Point container : this.repData.GetContainers()) {
            this.inits.put(container.GetContWid(), container.GetInitLevel());
            this.mostRecentVisits.put(container.GetContWid(), Parameters._404);
            this.repLevelDemands.put(container.GetContWid(), new ArrayList<Double>());
            for (int d = 0; d < this.nbDays; d++) {
                this.repLevelDemands.get(container.GetContWid()).add(container.GetForecastLevelDemand(d));
            }
        }
        // (4) estimated level demands for each day of the data days with estimated values
        for (Point container : this.estData.GetContainers()) {
            this.estLevelDemands.put(container.GetContWid(), new ArrayList<Double>());
            for (int d = 0; d < this.nbDays; d++) {
                this.estLevelDemands.get(container.GetContWid()).add(container.GetForecastLevelDemand(d));
            }
        }

        // Populate:
        // (1) truck required returns to home for each of the data days
        // (2) truck availabilities for each of the data days
        for (Truck truck : this.repData.GetTrucks()) {
            this.requiredReturnsToHome.put(truck.GetWid(), new ArrayList<Boolean>());
            this.availabilities.put(truck.GetWid(), new ArrayList<Boolean>());
            for (int d = 0; d < this.nbDays; d++) {
                this.requiredReturnsToHome.get(truck.GetWid()).add(truck.RequiredReturnToHome(d));
                this.availabilities.get(truck.GetWid()).add(truck.IsAvailable(d));
            }
        }

//        // Populate collections for debugging and verification purposes
//        for (Point container : this.repData.GetContainers()) {
//            this.rhInits.put(container.GetContWid(), new ArrayList<Double>());
//            this.rhVisits.put(container.GetContWid(), new ArrayList<Integer>());
//            for (int d = 0; d < this.nbDays; d++) {
//                this.rhVisits.get(container.GetContWid()).add(0);
//            }
//        }
    }

    /**
     * Returns initial level for the passed container on the passed day,
     * depending on the container's most recent visit since the first rollover.
     *
     * @param container Point container reference
     * @param d day in the data days
     * @return initial level for the passed container on the passed day,
     * depending on the container's most recent visit since the first rollover
     */
    private double getContInitLevel(Point container, int d) {

        // Initial level
        double initLevel;

        // Find the most recent visit to this container
        int mostRecentVisit = this.mostRecentVisits.get(container.GetContWid());

        // If there is no visit since the first rollover, increment on top of initial level.
        // Otherwise, increment on top of 0 starting from the day of the most recent visit.
        if (mostRecentVisit == Parameters._404) {
            initLevel = this.inits.get(container.GetContWid());
            for (int i = 0; i < d; i++) {
                initLevel += this.repLevelDemands.get(container.GetContWid()).get(i);
            }
        } else {
            initLevel = 0.d;
            for (int i = mostRecentVisit; i < d; i++) {
                initLevel += this.repLevelDemands.get(container.GetContWid()).get(i);
            }
        }

        // Return initial level
        return initLevel;
    }

    /**
     * Returns the estimated level demands for the passed container and for the
     * rolling horizon length, starting on the passed day d.
     *
     * @param container Point container reference
     * @param d day in the planning horizon
     * @return the estimated level demands for the passed container and for the
     * rolling horizon length, starting on the passed day d
     */
    private double[] getContEstLevelDemands(Point container, int d) {

        // Determine the size of level demands array
        int sizeLevelDemands = Math.min(this.rhLength, this.nbDays - d);

        // Initialize an array of level demands
        double[] levelDemands = new double[sizeLevelDemands];

        // Populate the array of level demands for its determined size 
        // and starting on the passed day d
        for (int i = 0; i < sizeLevelDemands; i++) {
            levelDemands[i] = this.estLevelDemands.get(container.GetContWid()).get(d + i);
        }

        // Return level demands
        return levelDemands;
    }

    /**
     * Returns the required returns to home for the passed truck and for the
     * rolling horizon length, starting on the passed day d.
     *
     * @param truck Truck reference
     * @param d day in the planning horizon
     * @return the required returns to home for the passed truck and for the
     * rolling horizon length, starting on the passed day d
     */
    private ArrayList<Boolean> getTruckRequiredReturnsToHome(Truck truck, int d) {

        // Determine the size of required returns to home array
        int sizeRequiredReturnsToHome = Math.min(this.rhLength, this.nbDays - d);

        // Initialize an ArrayList of truck required returns to home
        ArrayList<Boolean> truckRequiredReturnsToHome = new ArrayList<>();

        // Populate the ArrayList of truck required returns to home for its determined 
        // size starting from the passed day d
        for (int i = 0; i < sizeRequiredReturnsToHome; i++) {
            truckRequiredReturnsToHome.add(this.requiredReturnsToHome.get(truck.GetWid()).get(d + i));
        }

        // Return truck required returns to home
        return truckRequiredReturnsToHome;

    }

    /**
     * Returns the availabilities for the passed truck and for the rolling
     * horizon length, starting on the passed day d.
     *
     * @param truck Truck reference
     * @param d day in the planning horizon
     * @return the availabilities for the passed truck and for the rolling
     * horizon length, starting on the passed day d
     */
    private ArrayList<Boolean> getTruckAvailabilities(Truck truck, int d) {

        // Determine the size of availabilities array
        int sizeAvailabilities = Math.min(this.rhLength, this.nbDays - d);

        // Initialize an ArrayList of truck availabilities
        ArrayList<Boolean> truckAvailabilities = new ArrayList<>();

        // Populate the ArrayList of truck availabilities for its determined 
        // size starting from the passed day d
        for (int i = 0; i < sizeAvailabilities; i++) {
            truckAvailabilities.add(this.availabilities.get(truck.GetWid()).get(d + i));
        }

        // Returns truck availabilities
        return truckAvailabilities;
    }

    /**
     * Solve for a rolling horizon.
     *
     * @throws java.io.IOException
     * @throws java.io.FileNotFoundException
     * @throws java.lang.ClassNotFoundException
     * @throws org.rosuda.REngine.Rserve.RserveException
     * @throws org.rosuda.REngine.REXPMismatchException
     */
    public void SolveRolling() throws IOException, FileNotFoundException,
            ClassNotFoundException, RserveException, REXPMismatchException {

        // Initialize a ForecastRolling and pass the length of the rolling horizon
        // and the error sigma from the estimated data objected
        ForecastRolling forecast = new ForecastRolling(this.rhLength, this.estData.GetErrorSigma());

        // Start solving in a rolling horizon fashion
        for (int d = 0; d < this.nbRollovers; d++) {

            // Print message
            System.out.println("\n\n\n Rollover " + d + "\n\n");

//            // For rolling horizon estimated demands and probabilities, add
//            // a new linked hash map for each rollover
//            this.rhEstLevelDemands.add(new LinkedHashMap<Integer, ArrayList<Double>>());
//            this.rhProbabilities.add(new LinkedHashMap<Integer, ArrayList<Double>>());
            
            // Set rolling horizon length to estimated data object
            this.estData.SetPhLength(this.rhLength);
            // Reset penalties for each new "instance"
            this.estData.GetPenalties().ResetPenalties();
            
            // Set required returns to home and availabilities to each truck
            for (Truck truck : this.estData.GetTrucks()) {
                truck.SetRequiredReturnsToHome(this.getTruckRequiredReturnsToHome(truck, d));
                truck.SetAvailabilities(this.getTruckAvailabilities(truck, d));
            }
            
            // Set initial level, estimated level demands and probabilities to each container
            for (Point container : this.estData.GetContainers()) {

                // Container initial level and estimated level demands
                double contInitLevel = this.getContInitLevel(container, d);
                double[] contEstLevelDemands = this.getContEstLevelDemands(container, d);

                // Update container initial level, level demands and probabilities
                container.SetInit(contInitLevel);
                container.SetLevelDemands(contEstLevelDemands);
                container.SetProbabilities(forecast);

//                // Update collections for debugging and verification
//                this.rhInits.get(container.GetContWid()).add(contInitLevel);
//                ArrayList<Double> contEstLevelDemandsArrayList = new ArrayList<>();
//                for (double contEstLevelDemand : contEstLevelDemands) {
//                    contEstLevelDemandsArrayList.add(contEstLevelDemand);
//                }
//                this.rhEstLevelDemands.get(d).put(container.GetContWid(), contEstLevelDemandsArrayList);
//                ArrayList<Double> contRhProbabilitiesArrayList = new ArrayList<>();
//                for (int r = 0; r < this.rhLength + 1; r++) {
//                    contRhProbabilitiesArrayList.add(container.GetConditionalStartInv(r));
//                }
//                this.rhProbabilities.get(d).put(container.GetContWid(), contRhProbabilitiesArrayList);
            }

            // Solve and get the best schedule
            SAALNS saalns = new SAALNSA(this.estData, Parameters.hModeIRP, 1, Parameters.defaultTestValueID);
            saalns.Run(false, false);
            Schedule bestSchedule = saalns.GetBestSchedule();

            // Given the solution obtained above:
            // (1). Log the cost of routing and overflows on day 0 of each rollover. 
            // (2). Log the container visits on day 0
            // (3). Set the trucks' current starting point as the destination starting point
            // of the tour executed by the trucks
            double rhDayZeroCost = 0.d;
            for (Tour tour : bestSchedule.GetTours()) {
                if (tour.GetDay() == 0) {
                    // Add routing cost on day 0 of each rolling horizon.
                    rhDayZeroCost += tour.GetCost();
                    // Add the overflow cost for overflow on day 0 of each rolling horizon.
                    for (Point container : tour.GetContainers()) {
                        if (container.GetInitLevel() > 100.d) {
                            rhDayZeroCost += this.estData.GetOverflowCost();
                        }
                    }
                    // Update the container visits for the next rollover
                    for (Point container : tour.GetContainers()) {
                        this.mostRecentVisits.put(container.GetContWid(), d);
//                        this.rhVisits.get(container.GetContWid()).set(d, 1);
                    }
                    // Update the current starting points of the trucks executing the tours
                    // for the next rollover. 
                    for (Truck truck : this.estData.GetTrucks()) {
                        if (truck.GetWid() == tour.GetTruck().GetWid()) {
                            truck.SetCurrentStartingPoint(tour.GetDestinationStartingPoint());
                        }
                    }                    
                }
            }
            // Add daily routing costs (plus overflow) to ArrayList
            this.dailyRoutingCosts.add(d, rhDayZeroCost);

            // Add runtime to ArrayList
            this.runTimes.add(saalns.GetRunTime());
        }

        // Close forecast
        forecast.Close();

//        // Export initial levels
//        FileWriter initLevelWriter = new FileWriter(Parameters.csvExportFolder + "initLevels.csv");
//        initLevelWriter.write(",initLevel\n");
//        for (Integer contWid : this.inits.keySet()) {
//            initLevelWriter.write(contWid + "," + this.inits.get(contWid) + "\n");
//        }
//        initLevelWriter.close();
//
//        // Export rolling horizon initial levels
//        FileWriter rhInitLevelWriter = new FileWriter(Parameters.csvExportFolder + "rhInitLevels.csv");
//        rhInitLevelWriter.write(",mon,tue,wed,thu,fri,sat,sun,mon,tue,wed,thu,fri,sat,sun\n");
//        for (Integer contWid : this.rhInits.keySet()) {
//            rhInitLevelWriter.write(contWid + ",");
//            for (double contRhInit : this.rhInits.get(contWid)) {
//                rhInitLevelWriter.write(contRhInit + ",");
//            }
//            rhInitLevelWriter.write("\n");
//        }
//        rhInitLevelWriter.close();
//
//        // Export reported level demands for each container over the data days
//        FileWriter repLevelDemandsWriter = new FileWriter(Parameters.csvExportFolder + "repLevelDemands.csv");
//        repLevelDemandsWriter.write(",mon,tue,wed,thu,fri,sat,sun,mon,tue,wed,thu,fri,sat,sun\n");
//        for (Integer contWid : this.repLevelDemands.keySet()) {
//            repLevelDemandsWriter.write(contWid + ",");
//            for (double contRepLevelDemand : this.repLevelDemands.get(contWid)) {
//                repLevelDemandsWriter.write(contRepLevelDemand + ",");
//            }
//            repLevelDemandsWriter.write("\n");
//        }
//        repLevelDemandsWriter.close();
//
//        // Export estimated level demands for each container over the data days
//        FileWriter estLevelDemandsWriter = new FileWriter(Parameters.csvExportFolder + "estLevelDemands.csv");
//        estLevelDemandsWriter.write(",mon,tue,wed,thu,fri,sat,sun,mon,tue,wed,thu,fri,sat,sun\n");
//        for (Integer contWid : this.estLevelDemands.keySet()) {
//            estLevelDemandsWriter.write(contWid + ",");
//            for (double contEstLevelDemand : this.estLevelDemands.get(contWid)) {
//                estLevelDemandsWriter.write(contEstLevelDemand + ",");
//            }
//            estLevelDemandsWriter.write("\n");
//        }
//        estLevelDemandsWriter.close();
//
//        // Export visits
//        FileWriter visitsWriter = new FileWriter(Parameters.csvExportFolder + "visits.csv");
//        visitsWriter.write(",mon,tue,wed,thu,fri,sat,sun,mon,tue,wed,thu,fri,sat,sun\n");
//        for (Integer contWid : this.rhVisits.keySet()) {
//            visitsWriter.write(contWid + ",");
//            for (int contVisit : this.rhVisits.get(contWid)) {
//                visitsWriter.write(contVisit + ",");
//            }
//            visitsWriter.write("\n");
//        }
//        visitsWriter.close();
//
//        // Export rolling horizon estimated demands
//        FileWriter rhEstLevelDemandsWriter = new FileWriter(Parameters.csvExportFolder + "rhEstLevelDemands.csv");
//        String[] weekdays = new String[]{"mon", "tue", "wed", "thu", "fri", "sat", "sun", "mon", "tue", "wed", "thu", "fri", "sat", "sun", "mon"};
//        for (int d = 0; d < this.nbRollovers; d++) {
//            String rhDays = ",";
//            for (int r = d; r < d + this.rhLength; r++) {
//                rhDays += (weekdays[r] + ",");
//            }
//            rhEstLevelDemandsWriter.write(rhDays + ",");
//        }
//        rhEstLevelDemandsWriter.write("\n");
//        for (Integer contWid : this.repLevelDemands.keySet()) {
//            for (int d = 0; d < this.nbRollovers; d++) {
//                rhEstLevelDemandsWriter.write(contWid + ",");
//                for (int r = 0; r < this.rhLength; r++) {
//                    rhEstLevelDemandsWriter.write(this.rhEstLevelDemands.get(d).get(contWid).get(r) + ",");
//                }
//                rhEstLevelDemandsWriter.write(",");
//            }
//            rhEstLevelDemandsWriter.write("\n");
//        }
//        rhEstLevelDemandsWriter.close();
//
//        // Export rolling horizon probabilities
//        FileWriter rhProbabilitiesWriter = new FileWriter(Parameters.csvExportFolder + "rhProbabilities.csv");
//        for (int d = 0; d < this.nbRollovers; d++) {
//            String rhDays = ",";
//            for (int r = d; r < d + this.rhLength + 1; r++) {
//                rhDays += (weekdays[r] + ",");
//            }
//            rhProbabilitiesWriter.write(rhDays + ",");
//        }
//        rhProbabilitiesWriter.write("\n");
//        for (Integer contWid : this.repLevelDemands.keySet()) {
//            for (int d = 0; d < this.nbRollovers; d++) {
//                rhProbabilitiesWriter.write(contWid + ",");
//                for (int r = 0; r < this.rhLength + 1; r++) {
//                    rhProbabilitiesWriter.write(this.rhProbabilities.get(d).get(contWid).get(r) + ",");
//                }
//                rhProbabilitiesWriter.write(",");
//            }
//            rhProbabilitiesWriter.write("\n");
//        }
//        rhProbabilitiesWriter.close();
    }

    /**
     * Returns the total routing cost as the sum of the routing costs and
     * overflow costs on the first day of each solved rollover problem.
     *
     * @return the total routing cost as the sum of the routing costs and
     * overflow costs on the first day of each solved rollover problem
     */
    public double GetRollingCost() {

        double totalRoutingCost = 0.d;

        for (double dailyRoutingCost : this.dailyRoutingCosts) {
            totalRoutingCost += dailyRoutingCost;
        }

        return totalRoutingCost;
    }

    /**
     * Returns the total runtime of the rolling horizon solution.
     *
     * @return the total runtime of the rolling horizon solution
     */
    public double GetRunTime() {

        double totalRunTime = 0.d;

        for (long runTime : this.runTimes) {
            totalRunTime += runTime;
        }

        return totalRunTime;
    }
}
