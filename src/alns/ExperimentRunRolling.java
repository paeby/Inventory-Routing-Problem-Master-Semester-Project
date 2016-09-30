package alns;

import alns.algo.SAALNS;
import alns.algo.SAALNSA;
import alns.data.Data;
import alns.data.Truck;
import alns.param.Parameters;
import alns.rolling.Rolling;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * Implements the rolling horizon framework.
 *
 * @author Markov
 * @version 1.0
 */
public class ExperimentRunRolling {

    // Number of instances 
    private final int numInstances;
    
    // Rolling horizon length and number of rollovers
    private final int rhLength;
    private final int nbRollovers;

    // Thread pool size
    private final int threadPoolSize;

    // Reset values for probability related costs
    private final double resetEstOverflowCost;
    private final double resetEstEmergencyCost;
    private final double resetEstRouteFailureCostMultiplier;

    // List of estimated and reported data objects for the instances
    private final ArrayList<ImmutablePair<String, Data>> estDataList;
    private final ArrayList<ImmutablePair<String, Data>> repDataList;
    private final ArrayList<ImmutablePair<String, Data>> estRollingDataList;
    private final ArrayList<ImmutablePair<String, Data>> repRollingDataList;
    
    /**
     * Assignment, initialization and data population constructor.
     *
     * @param rhLength rolling horizon length
     * @param nbRollovers number of rollovers
     * @param threadPoolSize thread pool size
     * @param resetEstOverflowCost reset value for overflow cost
     * @param resetEstEmergencyCost reset value for emergency cost
     * @param resetEstRouteFailureCostMultiplier reset value for route failure
     * cost multiplier
     * @param resetAvailabilities reset truck availabilities
     * @param resetRequiredReturnsToHome reset truck required returns to home
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.io.IOException
     * @throws java.io.FileNotFoundException
     * @throws java.lang.ClassNotFoundException
     */
    public ExperimentRunRolling(int rhLength, int nbRollovers, int threadPoolSize,
            double resetEstOverflowCost, double resetEstEmergencyCost, double resetEstRouteFailureCostMultiplier,
            ArrayList<Boolean> resetAvailabilities, ArrayList<Boolean> resetRequiredReturnsToHome)
            throws NoSuchAlgorithmException, IOException, FileNotFoundException, ClassNotFoundException {

        // Initialize thread pool size
        this.threadPoolSize = threadPoolSize;
        
        // Initialize rolling horizon length and number of rollovers
        this.rhLength = rhLength;
        this.nbRollovers = nbRollovers;

        // Reset values for probability related costs
        this.resetEstOverflowCost = resetEstOverflowCost;
        this.resetEstEmergencyCost = resetEstEmergencyCost;
        this.resetEstRouteFailureCostMultiplier = resetEstRouteFailureCostMultiplier;

        // Initialize estimated and reported data lists
        this.estDataList = new ArrayList<>();
        this.repDataList = new ArrayList<>();
        this.estRollingDataList = new ArrayList<>();
        this.repRollingDataList = new ArrayList<>();

        // Populate the estimated data list with the instances found in the estimated instance folder
        // and add the respective reported instance to the reported instance list
        File[] importEstFiles = new File(Parameters.estimatedInstanceFolder).listFiles();
        
        //because of .DS_Store in OSX!
        this.numInstances = importEstFiles.length-1;
        for (File importEstFile : importEstFiles) {
            if (importEstFile.isFile() && !importEstFile.getName().equals(".DS_Store")) {
                for (int i = 0; i < Parameters.expNumberRuns; i++) {
                    // Build an estimated data object and add it to the estimated data list
                    Data estData = FileData.ReadData(Parameters.estimatedInstanceFolder, importEstFile.getName());
                    estData.SetOverflowCost(this.resetEstOverflowCost);
                    estData.SetEmergencyCost(this.resetEstEmergencyCost);
                    estData.SetRouteFailureCostMultiplier(this.resetEstRouteFailureCostMultiplier);
                    for (Truck truck : estData.GetTrucks()) {
                        truck.SetAvailabilities(resetAvailabilities);
                        truck.SetRequiredReturnsToHome(resetRequiredReturnsToHome);
                    }
                    this.estDataList.add(new ImmutablePair<>(importEstFile.getName(), estData));
                    // Build the same object for use with a rolling horizon and add it to the estimated data list for rolling horizon
                    Data estRollingData = FileData.ReadData(Parameters.estimatedInstanceFolder, importEstFile.getName());
                    estRollingData.SetOverflowCost(this.resetEstOverflowCost);
                    estRollingData.SetEmergencyCost(this.resetEstEmergencyCost);
                    estRollingData.SetRouteFailureCostMultiplier(this.resetEstRouteFailureCostMultiplier);
                    for (Truck truck : estRollingData.GetTrucks()) {
                        truck.SetAvailabilities(resetAvailabilities);
                        truck.SetRequiredReturnsToHome(resetRequiredReturnsToHome);
                    }
                    this.estRollingDataList.add(new ImmutablePair<>(importEstFile.getName(), estRollingData));
                    // Build the respective reported data object and add it to the reported data list 
                    // We look for the reported data instance in the reported instance folder, and the 
                    // the instance name is the same as that of the estimated instance with the only difference
                    // being that FT1 is replaced by FT2. We do not need to reset costs here because they should be 0
                    // as set for the reported instances.
                    String repFileName = importEstFile.getName().substring(0, importEstFile.getName().length() - 9)
                            + "2" + importEstFile.getName().substring(importEstFile.getName().length() - 8);
                    Data repData = FileData.ReadData(Parameters.reportedInstanceFolder, repFileName);
                    for (Truck truck : repData.GetTrucks()) {
                        truck.SetAvailabilities(resetAvailabilities);
                        truck.SetRequiredReturnsToHome(resetRequiredReturnsToHome);
                    }
                    this.repDataList.add(new ImmutablePair<>(repFileName, repData));
                    // Build the same object for use with a rolling horizon and add it to the reported data list for rolling horizon
                    Data repRollingData = FileData.ReadData(Parameters.reportedInstanceFolder, repFileName);
                    for (Truck truck : repRollingData.GetTrucks()) {
                        truck.SetAvailabilities(resetAvailabilities);
                        truck.SetRequiredReturnsToHome(resetRequiredReturnsToHome);
                    }
                    this.repRollingDataList.add(new ImmutablePair<>(repFileName, repRollingData));
                }
            }
        }
    }

    /**
     * Returns the average of the passed ArrayList of type Double.
     *
     * @param arrayList ArrayList of type Double
     * @return the average of the passed ArrayList of type Double
     */
    private double getArrayListAverage(ArrayList<Double> arrayList) {

        double arrayListSum = 0.d;
        for (double element : arrayList) {
            arrayListSum += element;
        }

        return (arrayListSum / arrayList.size());
    }

    /**
     * Runs the algorithm for a fixed number of times on each instance and
     * exports raw and average results.
     *
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.ExecutionException
     */
    public void Run() throws IOException, InterruptedException, ExecutionException {

        // Export parameter values
        final FileWriter parametersWriter = new FileWriter(Parameters.csvExportFolder + "parametersRolling"
                + "_O" + this.resetEstOverflowCost
                + "_E" + this.resetEstEmergencyCost
                + "_R" + this.resetEstRouteFailureCostMultiplier
                + "_LS" + Parameters.expLocalSearchActivated
                + "_T" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".csv");
        parametersWriter.write("hInitTemp, " + Parameters.hInitTemp + "\n");
        parametersWriter.write("hFinTemp, " + Parameters.hFinTemp + "\n");
        parametersWriter.write("hStartTempControlParam, " + Parameters.hStartTempControlParam + "\n");
        parametersWriter.write("hCoolRate, " + Parameters.hCoolRate + "\n");
        parametersWriter.write("hPhiSegmentLength, " + Parameters.hPhiSegmentLength + "\n");
        parametersWriter.write("hReactionFactor, " + Parameters.hReactionFactor + "\n");
        parametersWriter.write("hDestroyOpNormFactorResets, " + Parameters.hDestroyOpNormFactorResets + "\n");
        parametersWriter.write("hRepairOpNormFactorResets, " + Parameters.hRepairOpNormFactorResets + "\n");
        parametersWriter.write("hAwardS1, " + Parameters.hAwardS1 + "\n");
        parametersWriter.write("hAwardS2, " + Parameters.hAwardS2 + "\n");
        parametersWriter.write("hAwardS3, " + Parameters.hAwardS3 + "\n");
        parametersWriter.write("hInitPenalty, " + Parameters.hInitPenalty + "\n");
        parametersWriter.write("hPenaltyRate, " + Parameters.hPenaltyRate + "\n\n");
        // Local search
        parametersWriter.write("expLocalSearchActivated, " + Parameters.expLocalSearchActivated + "\n\n");
        // Reorder dumps DP operator
        parametersWriter.write("expUseHemmelmayrDPOperator, " + Parameters.expUseHemmelmayrDPOperator + "\n");
        parametersWriter.write("expDo2optLocalSearch, " + Parameters.expDo2optLocalSearch + "\n");
        parametersWriter.write("exp2optFirstImprovement, " + Parameters.exp2optFirstImprovement + "\n\n");
        // Reset values
        parametersWriter.write("overflowCost, " + this.resetEstOverflowCost + "\n");
        parametersWriter.write("hEmergencyCost, " + this.resetEstEmergencyCost + "\n");
        parametersWriter.write("hRouteFailureCostMultiplier, " + this.resetEstRouteFailureCostMultiplier + "\n");
        parametersWriter.close();

        // File writers for the raw and average results
        final FileWriter rawResultsWriter = new FileWriter(Parameters.csvExportFolder + "rawRunResultsRolling"
                + "_O" + this.resetEstOverflowCost
                + "_E" + this.resetEstEmergencyCost
                + "_R" + this.resetEstRouteFailureCostMultiplier
                + "_LS" + Parameters.expLocalSearchActivated
                + "_T" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".csv");
        final FileWriter avgResultsWriter = new FileWriter(Parameters.csvExportFolder + "avgRunResultsRolling"
                + "_O" + this.resetEstOverflowCost
                + "_E" + this.resetEstEmergencyCost
                + "_R" + this.resetEstRouteFailureCostMultiplier
                + "_LS" + Parameters.expLocalSearchActivated
                + "_T" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".csv");

        // Write file headers
        String rawHeader = "Instance, Data_NumSP, Data_NumDumps, Data_NumContainers, Data_NumTrucks, Data_Phlength, "
                + "Data_TourST, Data_TourET, Data_TourMaxDur, Data_EmergencyCost, Data_OverflowCost, Data_RouteFailureCostMultiplier, "
                + "ALNS_ReportedRuntime_ms, ALNS_RollingRuntime_ms, ALNS_EstimatedRuntime_ms, "
                + "Schedule_ReportedCost, Schedule_RollingCost, Schedule_EstimatedCost";
        rawResultsWriter.write(rawHeader + "\n");
        String avgHeader = "Instance, Data_NumSP, Data_NumDumps, Data_NumContainers, Data_NumTrucks, Data_Phlength, "
                + "Data_TourST, Data_TourET, Data_TourMaxDur, Data_EmergencyCost, Data_OverflowCost, Data_RouteFailureCostMultiplier, "
                + "ALNS_MinReportedRuntime_ms, ALNS_AvgReportedRuntime_ms, ALNS_MaxReportedRuntime_ms, "
                + "ALNS_MinRollingRuntime_ms, ALNS_AvgRollingRuntime_ms, ALNS_MaxRollingRuntime_ms, "
                + "ALNS_MinEstimatedRuntime_ms, ALNS_AvgEstimatedRuntime_ms, ALNS_MaxEstimatedRuntime_ms, "
                + "Schedule_MinReportedCost, Schedule_AvgReportedCost, Schedule_MaxReportedCost, "
                + "Schedule_MinRollingCost, Schedule_AvgRollingCost, Schedule_MaxRollingCost, "
                + "Schedule_MinEstimatedCost, Schedule_AvgEstimaedCost, Schedule_MaxEstimatedCost";
        avgResultsWriter.write(avgHeader + "\n");

        // Initialize executor service
        ExecutorService executor = Executors.newFixedThreadPool(this.threadPoolSize);

        // For each instance
        for (int i = 0; i < this.numInstances; i++) {

            // Print message
            System.out.println("\nSolving rolling, reported and estimated instance related to " 
                    + this.estDataList.get(i * Parameters.expNumberRuns).getKey()
                    + " " + Parameters.expNumberRuns + " times in parallel...");

            // ArrayList of future schedule costs (will be available when thread is complete)
            ArrayList<Future<ArrayList<Double>>> futureCallStatsArray = new ArrayList<>();

            // Run a fixed number of times
            for (int j = 0; j < Parameters.expNumberRuns; j++) {

                // Run number
                final int runNb = j;
                // Instance name and data reference as final variables, so we can pass to ad-hoc class below
                final String instanceName = estDataList.get(i * Parameters.expNumberRuns + j).getKey();
                final Data estData = estDataList.get(i * Parameters.expNumberRuns + j).getValue();
                final Data repData = repDataList.get(i * Parameters.expNumberRuns + j).getValue();
                final Data estRollingData = estRollingDataList.get(i * Parameters.expNumberRuns + j).getValue();
                final Data repRollingData = repRollingDataList.get(i * Parameters.expNumberRuns + j).getValue();

                // Submit new callable
                Future<ArrayList<Double>> futureCallStats = executor.submit(new Callable<ArrayList<Double>>() {
                    /**
                     * Callable run.
                     *
                     * @return ArrayList of callable stats
                     * @throws Exception
                     */
                    @Override
                    public ArrayList<Double> call() throws Exception {
                        
                        // Solve for rolling
                        repRollingData.GetPenalties().ResetPenalties();
                        estRollingData.GetPenalties().ResetPenalties();
                        Rolling rolling = new Rolling(repRollingData, estRollingData, rhLength, nbRollovers);
                        rolling.SolveRolling();
                        double rollingCost = rolling.GetRollingCost();
                        double rollingRuntime = rolling.GetRunTime();                        

                        // Solve for reported data
                        repData.SetPhLength(rhLength);
                        repData.GetPenalties().ResetPenalties();
                        SAALNS repSaalns = new SAALNSA(repData, Parameters.hModeIRP, runNb, Parameters.defaultTestValueID);
                        repSaalns.Run(null, null); 
                        double reportedCost = repSaalns.GetBestSchedule().GetCost();
                        double reportedRunTime = repSaalns.GetRunTime();
                        
                        // Solve for estimated data
                        estData.SetPhLength(rhLength);
                        estData.GetPenalties().ResetPenalties();
                        SAALNS estSaalns = new SAALNSA(estData, Parameters.hModeIRP, runNb, Parameters.defaultTestValueID);
                        estSaalns.Run(null, null);
                        double estimatedCost = estSaalns.GetBestSchedule().GetCost();
                        double estimatedRunTime = estSaalns.GetRunTime();

                        // Print message
                        System.out.printf(">> Thread: %-10d solution cost: reported %-15.3f, rolling %-15.3f, estimated %-15.3f %n",
                                Thread.currentThread().getId(), reportedCost, rollingCost, estimatedCost);

                        // Add a line to raw performance statistics
                        String rawResultsLine = instanceName + ","
                                + estData.GetStartingPoints().size() + "," + estData.GetDumps().size() + "," + estData.GetContainers().size() + ","
                                + estData.GetTrucks().size() + ","
                                + estData.GetPhLength() + ","
                                + estData.GetTourStartTime() + "," + estData.GetTourEndTime() + "," + estData.GetTourMaxDur() + ","
                                + estData.GetEmegencyCost() + "," + estData.GetOverflowCost() + "," + estData.GetRouteFailureCostMultiplier() + ","
                                + reportedRunTime + "," + rollingRuntime + "," + estimatedRunTime + ","
                                + reportedCost + "," + rollingCost + "," + estimatedCost;
                        rawResultsWriter.write(rawResultsLine + "\n");
                        rawResultsWriter.flush();

                        // Prepare and return an ArrayList of statistics from this call
                        ArrayList<Double> callStats = new ArrayList<>();
                        callStats.add(reportedRunTime);
                        callStats.add(rollingRuntime);
                        callStats.add(estimatedRunTime);
                        callStats.add(reportedCost);
                        callStats.add(rollingCost);
                        callStats.add(estimatedCost);
                        return callStats;
                    }
                });
                // Update future schedule costs
                futureCallStatsArray.add(futureCallStats);
            }

            // Extract call statistics from the ArrayList of Futures and transform so that
            // each embedded ArrayList represents a single collected statistic over the number or runs
            ArrayList<ArrayList<Double>> tempStatsArray = new ArrayList<>();
            ArrayList<ArrayList<Double>> callStatsArray = new ArrayList<>();
            for (Future<ArrayList<Double>> futureCallStats : futureCallStatsArray) {
                tempStatsArray.add(futureCallStats.get());
            }
            for (Double get : tempStatsArray.get(0)) {
                callStatsArray.add(new ArrayList<Double>());
            }
            for (ArrayList<Double> tempStats : tempStatsArray) {
                for (int statCounter = 0; statCounter < tempStats.size(); statCounter++) {
                    callStatsArray.get(statCounter).add(tempStats.get(statCounter));
                }
            }

            // Add line to average performance statistics
            String instanceName = estDataList.get(i * Parameters.expNumberRuns).getKey();
            Data estData = estDataList.get(i * Parameters.expNumberRuns).getValue();
            String avgResultsLine = instanceName + ","
                    + estData.GetStartingPoints().size() + "," + estData.GetDumps().size() + "," + estData.GetContainers().size() + ","
                    + estData.GetTrucks().size() + ","
                    + estData.GetPhLength() + ","
                    + estData.GetTourStartTime() + "," + estData.GetTourEndTime() + "," + estData.GetTourMaxDur() + ","
                    + estData.GetEmegencyCost() + "," + estData.GetOverflowCost() + "," + estData.GetRouteFailureCostMultiplier();
            for (ArrayList<Double> callStatsArrayJ : callStatsArray) {
                avgResultsLine += "," + Collections.min(callStatsArrayJ)
                        + "," + this.getArrayListAverage(callStatsArrayJ)
                        + "," + Collections.max(callStatsArrayJ);
            }
            avgResultsWriter.write(avgResultsLine + "\n");
            avgResultsWriter.flush();
        }

        // Shut down executor
        executor.shutdown();

        // Close both FileWriters
        rawResultsWriter.close();
        avgResultsWriter.close();
    }
}
