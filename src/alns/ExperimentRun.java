package alns;

import alns.algo.SAALNS;
import alns.algo.SAALNSA;
import alns.data.Data;
import alns.param.Parameters;
import alns.data.Simulation;
import alns.data.Truck;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.tuple.ImmutablePair;
/**
 * This class is for testing purposes only. It tests the algorithmic performance
 * on a testbed of instances.
 *
 * @author Markov
 * @version 1.0
 */
public class ExperimentRun {

    // Number of instances 
    private final int numInstances;

    // Thread pool size
    private final int threadPoolSize;

    // Solution mode
    private final int mode;

    // Reset values for probability related costs
    private final double resetOverflowCost;
    private final double resetEmergencyCost;
    private final double resetRouteFailureCostMultiplier;

    // List of data objects for the instances
    private final ArrayList<ImmutablePair<String, Data>> dataList;

    /**
     * Assignment, initialization and data population constructor.
     *
     * @param benchmarkType benchmark type
     * @param mode solution mode
     * @param threadPoolSize thread pool size
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.io.IOException
     * @throws java.io.FileNotFoundException
     * @throws java.lang.ClassNotFoundException
     */
    public ExperimentRun(int benchmarkType, int mode, int threadPoolSize)
            throws NoSuchAlgorithmException, IOException, FileNotFoundException, ClassNotFoundException {

        // Initialize solution mode and thread pool size
        this.mode = mode;
        this.threadPoolSize = threadPoolSize;

        // Reset values for probability related costs
        // Set to 0 as they are inapplicable to benchmark instances
        this.resetOverflowCost = 0.d;
        this.resetEmergencyCost = 0.d;
        this.resetRouteFailureCostMultiplier = 0.d;

        // Initialize data list
        this.dataList = new ArrayList<>();

        // Populate the data list with the instances found in the instance folder
        File[] importFiles = new File(Parameters.instanceFolder).listFiles(Parameters.filter);
        this.numInstances = importFiles.length;
        for (File importFile : importFiles) {
            if (importFile.isFile()) {
                for (int i = 0; i < Parameters.expNumberRuns; i++) {
                    // Read in data
                    Data data = new Data(Parameters.instanceFolder + importFile.getName(), benchmarkType);
                    // Add to data list
                    this.dataList.add(new ImmutablePair<>(importFile.getName(), data));
                }
            }
        }
    }
    
    /**
     * Assignment, initialization and data population constructor.
     *
     * @param threadPoolSize thread pool size
     * @param resetOverflowCost reset value for overflow cost
     * @param resetEmergencyCost reset value for emergency cost
     * @param resetRouteFailureCostMultiplier reset value for route failure cost
     * multiplier
     * @param resetAvailabilities reset truck availabilities
     * @param resetRequiredReturnsToHome reset truck required returns to home
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.io.IOException
     * @throws java.io.FileNotFoundException
     * @throws java.lang.ClassNotFoundException
     */
    public ExperimentRun(int threadPoolSize,
            double resetOverflowCost, double resetEmergencyCost, double resetRouteFailureCostMultiplier,
            ArrayList<Boolean> resetAvailabilities, ArrayList<Boolean> resetRequiredReturnsToHome)
            throws NoSuchAlgorithmException, IOException, FileNotFoundException, ClassNotFoundException {

        // Initialize solution mode and thread pool size
        this.mode = Parameters.hModeIRP;
        this.threadPoolSize = threadPoolSize;

        // Reset values for probability related costs
        this.resetOverflowCost = resetOverflowCost;
        this.resetEmergencyCost = resetEmergencyCost;
        this.resetRouteFailureCostMultiplier = resetRouteFailureCostMultiplier;

        // Initialize data list
        this.dataList = new ArrayList<>();

        // Populate the data list with the instances found in the instance folder
        File[] importFiles = new File(Parameters.instanceFolder).listFiles(Parameters.filter);
        this.numInstances = importFiles.length;
        for (File importFile : importFiles) {
            if (importFile.isFile() && !importFile.getName().equals(".DS_Store")) {
                for (int i = 0; i < Parameters.expNumberRuns; i++) {
                    // Read in data
                    Data data = FileData.ReadData(Parameters.instanceFolder, importFile.getName());
                    // Reset costs
                    data.SetOverflowCost(this.resetOverflowCost);
                    data.SetEmergencyCost(this.resetEmergencyCost);
                    data.SetRouteFailureCostMultiplier(this.resetRouteFailureCostMultiplier);
                    // For each truck, reset availabilities and required returns to home
                    for (Truck truck : data.GetTrucks()) {
                        truck.SetAvailabilities(resetAvailabilities);
                        truck.SetRequiredReturnsToHome(resetRequiredReturnsToHome);
                    }
                    // Add to data list
                    this.dataList.add(new ImmutablePair<>(importFile.getName(), data));
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

        // Planning horizon length (assume uniform over all instances)
        int phLength = this.dataList.get(0).getRight().GetPhLength();

        // Export parameter values
        final FileWriter parametersWriter = new FileWriter(Parameters.csvExportFolder + "parameters"
                + "_O" + this.resetOverflowCost
                + "_E" + this.resetEmergencyCost
                + "_R" + this.resetRouteFailureCostMultiplier
                + "_ECF" + Parameters.policyContainerEffectiveVolumeFraction
                + "_ETF" + Parameters.policyTruckEffectiveVolumeFraction
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
        parametersWriter.write("overflowCost, " + this.resetOverflowCost + "\n");
        parametersWriter.write("hEmergencyCost, " + this.resetEmergencyCost + "\n");
        parametersWriter.write("hRouteFailureCostMultiplier, " + this.resetRouteFailureCostMultiplier + "\n");
        // Effective fractions
        parametersWriter.write("policyContainerEffectiveVolumeFraction, " + Parameters.policyContainerEffectiveVolumeFraction + "\n");
        parametersWriter.write("policyTruckEffectiveVolumeFraction, " + Parameters.policyTruckEffectiveVolumeFraction + "\n");
        parametersWriter.write("policyTruckEffectiveWeightFraction, " + Parameters.policyTruckEffectiveWeightFraction + "\n");
        parametersWriter.close();

        // File writers for the raw and average results
        final FileWriter rawResultsWriter = new FileWriter(Parameters.csvExportFolder + "rawRunResults"
                + "_O" + this.resetOverflowCost
                + "_E" + this.resetEmergencyCost
                + "_R" + this.resetRouteFailureCostMultiplier
                + "_ECF" + Parameters.policyContainerEffectiveVolumeFraction
                + "_ETF" + Parameters.policyTruckEffectiveVolumeFraction
                + "_LS" + Parameters.expLocalSearchActivated
                + "_T" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".csv");
        final FileWriter avgResultsWriter = new FileWriter(Parameters.csvExportFolder + "avgRunResults"
                + "_O" + this.resetOverflowCost
                + "_E" + this.resetEmergencyCost
                + "_R" + this.resetRouteFailureCostMultiplier
                + "_ECF" + Parameters.policyContainerEffectiveVolumeFraction
                + "_ETF" + Parameters.policyTruckEffectiveVolumeFraction
                + "_LS" + Parameters.expLocalSearchActivated
                + "_T" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".csv");

        // Write file headers
        String rawHeader = "Instance, Data_NumSP, Data_NumDumps, Data_NumContainers, Data_NumTrucks, Data_Phlength, "
                + "Data_TourST, Data_TourET, Data_TourMaxDur, Data_EmergencyCost, Data_OverflowCost, Data_RouteFailureCostMultiplier, "
                + "Parameters_EffectiveContainerFraction, Parameters_EffectiveTruckFraction, "
                + "ALNS_Runtime_ms, "
                + "Schedule_NumTours, Schedule_NumContainers, Schedule_NumDumpVisits, "
                + "Schedule_Cost, Schedule_Length, Schedule_Duration, Schedule_RoutingCost, "
                + "Schedule_RouteFailureCost, Schedule_InventoryCost, Schedule_OverflowAttr, "
                + "Schedule_CollectedVolumeLoad";
        for (int d = 0; d < phLength; d++) {
            rawHeader += (", Schedule_AvgLevelAtCollectionOnDay_" + d);
        }
        for (int d = 0; d < phLength + 1; d++) {
            rawHeader += (", Schedule_AvgLevelOnDay_" + d);
        }
        for (double percentile : Parameters.expSimululationPercentiles) {
            rawHeader += ", Sim_BackorderViolationCounts" + percentile + "p, Sim_BackorderViolationValues" + percentile + "p, "
                    + "Sim_ContainerViolationCounts" + percentile + "p, Sim_ContainerViolationValues" + percentile + "p, "
                    + "Sim_RouteFailureCounts" + percentile + "p, Sim_RouteFailureValues" + percentile + "p";
        }
        rawHeader += ", ALNS_OperatorUsage_tomatch";
        rawResultsWriter.write(rawHeader + "\n");
        String avgHeader = "Instance, Data_NumSP, Data_NumDumps, Data_NumContainers, Data_NumTrucks, Data_Phlength, "
                + "Data_TourST, Data_TourET, Data_TourMaxDur, Data_EmergencyCost, Data_OverflowCost, Data_RouteFailureCostMultiplier, "
                + "Parameters_EffectiveContainerFraction, Parameters_EffectiveTruckFraction, "
                + "ALNS_MinRuntime_ms, ALNS_AvgRuntime_ms, ALNS_MaxRuntime_ms, "
                + "Schedule_MinNumTours, Schedule_AvgNumTours, Schedule_MaxNumTours,"
                + "Schedule_MinNumContainers, Schedule_AvgNumContainers, Schedule_MaxNumContainers, "
                + "Schedule_MinNumDumpVisits, Schedule_AvgNumDumpVisits, Schedule_MaxNumDumpVisits, "
                + "Schedule_MinCost, Schedule_AvgCost, Schedule_MaxCost, "
                + "Schedule_MinLength, Schedule_AvgLength, Schedule_MaxLength, "
                + "Schedule_MinDuration, Schedule_AvgDuration, Schedule_MaxDuration, "
                + "Schedule_MinRoutingCost, Schedule_AvgRoutingCost, Schedule_MaxRoutingCost, "
                + "Schedule_MinRouteFailureCost, Schedule_AvgRouteFailureCost, Schedule_MaxRouteFailureCost, "
                + "Schedule_MinInventoryCost, Schedule_AvgInventoryCost, Schedule_MaxInventoryCost, "
                + "Schedule_MinOverflowAttr, Schedule_AvgOverflowAttr, Schedule_MaxOverflowAttr, "
                + "Schedule_MinCollectedVolumeLoad, Schedule_AvgCollectedVolumeLoad, Schedule_MaxCollectedVolumeLoad";
        for (int d = 0; d < phLength; d++) {
            avgHeader += (", Schedule_MinAvgLevelAtCollectionOnDay_" + d
                    + ", Schedule_AvgAvgLevelAtCollectionOnDay_" + d
                    + ", Schedule_MaxAvgLevelAtCollectionOnDay_" + d);
        }
        for (int d = 0; d < phLength + 1; d++) {
            avgHeader += (", Schedule_MinAvgLevelOnDay_" + d
                    + ", Schedule_AvgAvgLevelOnDay_" + d
                    + ", Schedule_MaxAvgLevelOnDay_" + d);
        }
        for (double percentile : Parameters.expSimululationPercentiles) {
            avgHeader += ", Sim_AvgBackorderViolationCounts" + percentile + "p, Sim_AvgBackorderViolationValues" + percentile + "p, "
                    + "Sim_AvgContainerViolationCounts" + percentile + "p, Sim_AvgContainerViolationValues" + percentile + "p, "
                    + "Sim_AvgRouteFailureCounts" + percentile + "p, Sim_AvgRouteFailureValues" + percentile + "p";
        }
        avgHeader += ", ALNS_OperatorUsage_tomatch";
        avgResultsWriter.write(avgHeader + "\n");

        // Initialize executor service
        ExecutorService executor = Executors.newFixedThreadPool(this.threadPoolSize);

        // For each instance
        for (int i = 0; i < this.numInstances; i++) {

            // Print message
            System.out.println("\nSolving instance " + this.dataList.get(i * Parameters.expNumberRuns).getKey()
                    + " " + Parameters.expNumberRuns + " times in parallel...");

            // ArrayList of future schedule costs (will be available when thread is complete)
            ArrayList<Future<ArrayList<Double>>> futureCallStatsArray = new ArrayList<>();

            // Run a fixed number of times
            for (int j = 0; j < Parameters.expNumberRuns; j++) {

                // Run number
                final int runNb = j;
                // Instance name and data reference as final variables, so we can
                // pass to ad-hoc class below
                final String instanceName = dataList.get(i * Parameters.expNumberRuns + j).getKey();
                final Data data = dataList.get(i * Parameters.expNumberRuns + j).getValue();

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

                        // Create the SAALNS object and run without collecting statistics
                        SAALNS saalns = new SAALNSA(data, mode, runNb, Parameters.defaultTestValueID);
                        saalns.Run(null, null); // with null, the default values are used
                        if (Parameters.expCollectStats_default) {
                            saalns.ExportOperatorStatistics();
                            saalns.ExportPenaltyStatistics();
                            saalns.ExportViolationStatistics();
                            saalns.ExportSAStatistics();
                            saalns.ExportOperatorPerformance();
                        }
                        if (Parameters.expCollectSolutionImprovements_default) {
                            saalns.ExportBestSolutionLog();
                        }

                        // Retrieve best schedule statistics 
                        double runTime = saalns.GetRunTime();
                        double scheduleNumTours = saalns.GetBestSchedule().GetScheduleFinalNumPerformedTours();
                        double scheduleNumContainers = saalns.GetBestSchedule().GetNumContainers();
                        double scheduleNumDumpVisits = saalns.GetBestSchedule().GetScheduleFinalNumDumpVisits();
                        double scheduleCost = saalns.GetBestSchedule().GetCost();
                        double scheduleLength = saalns.GetBestSchedule().GetScheduleFinalLength();
                        double scheduleDuration = saalns.GetBestSchedule().GetScheduleFinalDuration();
                        double scheduleRoutingCost = saalns.GetBestSchedule().GetScheduleFinalRoutingCost();
                        double scheduleRouteFailureCost = saalns.GetBestSchedule().GetScheduleFinalRouteFailureCost();
                        double scheduleInventoryHoldingCost = saalns.GetBestSchedule().GetScheduleFinalInventoryHoldingCost();
                        double scheduleOverflowCostAttr = saalns.GetBestSchedule().GetScheduleFinalOverflowCostAttr();
                        double scheduleCollectedVolumeLoad = saalns.GetBestSchedule().GetScheduleFinalCollectedVolumeLoad();
                        ArrayList<Double> scheduleAvgLevelsAtCollectionOnDays = saalns.GetBestSchedule().GetScheduleFinalAvgLevelsAtCollectionOnDays();
                        ArrayList<Double> scheduleAvgLevelsOnDays = saalns.GetBestSchedule().GetScheduleFinalAvgLevelsOnDays();
                        Simulation simulation = saalns.GetBestSchedule().SimulateAndLogDemandScenarios();
                        int[] tempDestroyOpTotalUsages = saalns.GetDestoryOpTotalUsages();
                        int[] tempRepairOpTotalUsages = saalns.GetRepairOpTotalUsages();
                        ArrayList<Double> destroyOpTotalUsages = new ArrayList<>();
                        ArrayList<Double> repairOpTotalUsages = new ArrayList<>();
                        for (int destroyOpTotalUsage : tempDestroyOpTotalUsages) {
                            destroyOpTotalUsages.add((double) destroyOpTotalUsage);
                        }
                        for (int repairOpTotalUsage : tempRepairOpTotalUsages) {
                            repairOpTotalUsages.add((double) repairOpTotalUsage);
                        }

                        // Print message
                        System.out.printf(">> Thread: %-10d solution cost: %-15.3f %n",
                                Thread.currentThread().getId(), scheduleCost);

                        // Add a line to raw performance statistics
                        String rawResultsLine = instanceName + ","
                                + data.GetStartingPoints().size() + "," + data.GetDumps().size() + "," + data.GetContainers().size() + ","
                                + data.GetTrucks().size() + ","
                                + data.GetPhLength() + ","
                                + data.GetTourStartTime() + "," + data.GetTourEndTime() + "," + data.GetTourMaxDur() + ","
                                + data.GetEmegencyCost() + "," + data.GetOverflowCost() + "," + data.GetRouteFailureCostMultiplier() + ","
                                + Parameters.policyContainerEffectiveVolumeFraction + "," + Parameters.policyTruckEffectiveVolumeFraction + ","
                                + runTime + ","
                                + scheduleNumTours + "," + scheduleNumContainers + "," + scheduleNumDumpVisits + ","
                                + scheduleCost + "," + scheduleLength + "," + scheduleDuration + ","
                                + scheduleRoutingCost + "," + scheduleRouteFailureCost + ","
                                + scheduleInventoryHoldingCost + "," + scheduleOverflowCostAttr + ","
                                + scheduleCollectedVolumeLoad;
                        for (double scheduleAvgLevelAtCollectionOnDay : scheduleAvgLevelsAtCollectionOnDays) {
                            rawResultsLine += "," + scheduleAvgLevelAtCollectionOnDay;
                        }
                        for (double scheduleAvgLevelOnDay : scheduleAvgLevelsOnDays) {
                            rawResultsLine += "," + scheduleAvgLevelOnDay;
                        }
                        for (double percentile : Parameters.expSimululationPercentiles) {
                            rawResultsLine += "," + simulation.GetPercentileBackorderViolationCounts(percentile) + "," + simulation.GetPercentileBackorderViolationValues(percentile) + ","
                                    + simulation.GetPercentileContainerViolationCounts(percentile) + "," + simulation.GetPercentileContainerViolationValues(percentile) + ","
                                    + simulation.GetPercentileRouteFailureCounts(percentile) + "," + simulation.GetPercentileRouteFailureValues(percentile);
                        }
                        for (double destroyOpUsage : destroyOpTotalUsages) {
                            rawResultsLine += "," + destroyOpUsage;
                        }
                        for (double repairOpUsage : repairOpTotalUsages) {
                            rawResultsLine += "," + repairOpUsage;
                        }
                        rawResultsWriter.write(rawResultsLine + "\n");
                        rawResultsWriter.flush();

                        // Prepare and return an ArrayList of statistics from this call
                        ArrayList<Double> callStats = new ArrayList<>();
                        callStats.add(runTime);
                        callStats.add(scheduleNumTours);
                        callStats.add(scheduleNumContainers);
                        callStats.add(scheduleNumDumpVisits);
                        callStats.add(scheduleCost);
                        callStats.add(scheduleLength);
                        callStats.add(scheduleDuration);
                        callStats.add(scheduleRoutingCost);
                        callStats.add(scheduleRouteFailureCost);
                        callStats.add(scheduleInventoryHoldingCost);
                        callStats.add(scheduleOverflowCostAttr);
                        callStats.add(scheduleCollectedVolumeLoad);
                        for (double scheduleAvgLevelAtCollectionOnDay : scheduleAvgLevelsAtCollectionOnDays) {
                            callStats.add(scheduleAvgLevelAtCollectionOnDay);
                        }
                        for (double scheduleAvgLevelOnDay : scheduleAvgLevelsOnDays) {
                            callStats.add(scheduleAvgLevelOnDay);
                        }
                        for (double percentile : Parameters.expSimululationPercentiles) {
                            callStats.add((double) simulation.GetPercentileBackorderViolationCounts(percentile));
                            callStats.add(simulation.GetPercentileBackorderViolationValues(percentile));
                            callStats.add((double) simulation.GetPercentileContainerViolationCounts(percentile));
                            callStats.add(simulation.GetPercentileContainerViolationValues(percentile));
                            callStats.add((double) simulation.GetPercentileRouteFailureCounts(percentile));
                            callStats.add(simulation.GetPercentileRouteFailureValues(percentile));
                        }
                        callStats.addAll(destroyOpTotalUsages);
                        callStats.addAll(repairOpTotalUsages);
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
            String instanceName = dataList.get(i * Parameters.expNumberRuns).getKey();
            Data data = dataList.get(i * Parameters.expNumberRuns).getValue();
            String avgResultsLine = instanceName + ","
                    + data.GetStartingPoints().size() + "," + data.GetDumps().size() + "," + data.GetContainers().size() + ","
                    + data.GetTrucks().size() + ","
                    + data.GetPhLength() + ","
                    + data.GetTourStartTime() + "," + data.GetTourEndTime() + "," + data.GetTourMaxDur() + ","
                    + data.GetEmegencyCost() + "," + data.GetOverflowCost() + "," + data.GetRouteFailureCostMultiplier() + ","
                    + Parameters.policyContainerEffectiveVolumeFraction + "," + Parameters.policyTruckEffectiveVolumeFraction;
            int delimiter = 12 + 2 * phLength + 1;
            for (int j = 0; j < delimiter; j++) {
                ArrayList<Double> callStatsArrayJ = callStatsArray.get(j);
                for (int r = callStatsArrayJ.size() - 1; r >= 0; r--) {
                    if (callStatsArrayJ.get(r) == (double) Parameters._404) {
                        callStatsArrayJ.remove((int) r);
                    }
                }
                if (!callStatsArrayJ.isEmpty()) {
                    avgResultsLine += "," + Collections.min(callStatsArrayJ)
                            + "," + this.getArrayListAverage(callStatsArrayJ)
                            + "," + Collections.max(callStatsArrayJ);
                } else {
                    avgResultsLine += ",NA, NA, NA";
                }
            }
            for (int j = delimiter; j < callStatsArray.size(); j++) {
                avgResultsLine += "," + this.getArrayListAverage(callStatsArray.get(j));
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
