package alns;

import alns.algo.*;
import alns.data.Data;
import alns.data.Point;
import alns.data.Truck;
import alns.param.Parameters;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * Main class.
 *
 * @author Markov
 * @version 1.0
 */
public class Main {

    // The availabilities and required returns of the trucks for the rolling horizon
    private static final ArrayList<Boolean> availabilities = new ArrayList<>(Arrays.asList(true, true, true, true, true, false, false));
    private static final ArrayList<Boolean> requiredReturnsToHome = new ArrayList<>(Arrays.asList(true, true, true, true, true, true, true));

    // Emergency collection costs and route failure cost multipliers to be tested
    private static final double[] emergencyCosts = new double[]{100.d, 50.d, 25.d};
    private static final double[] routeFailureCostMultipliers = new double[]{1.00d, 0.50d, 0.25d};

    // Container and truck effective capacity fractions
    private static final double[] containerEffectiveFractions = new double[]{1.d, 0.9d, 0.75d};
    private static final double[] truckEffectiveFractions = new double[]{1.d, 0.9d, 0.75d};

    /**
     * Constructs an instance by extracting data directly from the database and
     * solves it.
     *
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws SQLException
     * @throws RserveException
     * @throws REXPMismatchException
     */
    public static void SolveDatabaseInstance()
            throws NoSuchAlgorithmException, IOException, SQLException, RserveException, REXPMismatchException {

        // Container wids of the containers to be served
        ArrayList<Integer> recContainerWids = new ArrayList<>(Arrays.asList(
                536871951, 536872190, 536871962, 536872035, 536871966, 536874063, 536873991, 536872091, 536872217, 536876757, 536874062, 536874061,
                536874086, 536873967, 536872114, 536872038, 536872001, 536871992, 536874016, 536872170, 536874060, 536872060, 536872156, 536872151,
                536872053, 536872127, 536872074, 536872261, 536872185, 536872195, 536873943, 536871996, 536872234, 536872012, 536874099, 536874558,
                536871980, 536872277, 536872006, 536872048, 536874105, 536874109, 536874033, 536873903, 536872004, 536871976, 536874004, 536874040,
                536874555, 536872245, 536874030, 536872230, 536872281, 536872198, 536871970, 536874034, 536871942, 536873999, 536872145, 536872255
        ));

        // Trucks and associated starting points
        // As explained in the Data class and the Truck class, the first starting point wid is the home starting point,
        // the second one is the current starting point, and any remaining ones can be used as flexible destination starting points. 
        LinkedHashMap<Integer, ArrayList<Integer>> truckSP = new LinkedHashMap<>();
        truckSP.put(536874174, new ArrayList<>(Arrays.asList(536876516, 536876516, 536874164)));
        truckSP.put(536874175, new ArrayList<>(Arrays.asList(536876516, 536876325, 536874164)));
        truckSP.put(536874176, new ArrayList<>(Arrays.asList(536876516, 536876325, 536874164)));

        // Data(truck to SP map, container wids, zone wid, client wid, flowtype wid, planning horizon length)
        Data data = new Data(truckSP, recContainerWids, 536873310, 268436583, 536871632, 7);
        data.SetRandSeed(Parameters.hRandSeed);

        // Verify data completeness
        boolean passVerification = data.VerifyCompleteness();

        // Solve if data passed verification test
        if (passVerification) {

            // Initialize a decomposition
            Decomposition decomposition = new Decomposition(data, 3, Parameters.hSAALNSA, Parameters.expCollectStats_default);
            // Run the ALNS on the decomposition
            boolean runSuccessful = decomposition.Run();
            if (runSuccessful) {
                // Retrieve the SAALNS holding the best IRP solution
                SAALNS saalnsIRP = decomposition.GetSaalnsIRP();
                // Print results
                saalnsIRP.PrintTours();
                saalnsIRP.PrintDataStatistics();
                // Export results
                saalnsIRP.ExportContainerTracker();
                saalnsIRP.ExportOperatorPerformance();
                saalnsIRP.ExportOperatorStatistics();
                saalnsIRP.ExportPenaltyStatistics();
                saalnsIRP.ExportSAStatistics();
                saalnsIRP.ExportViolationStatistics();
            }
        }
    }

    /**
     * Generates real data instances using data from the database.
     *
     * @param nbDays number of days in the planning horizon.
     * @param flowTypeWid flow type wid
     * @param forecastType forecast type indicates whether we return (1)
     * forecast demands and their related error sigmas or (2) reported demands
     * and zero error sigmas. In the second case the conditional and
     * unconditional probabilities of overflow are 0 since there is no
     * uncertainty.
     * @param modelType model type refers to whether we are using (1) a
     * per-container Poisson model or a (2) container fixed effects mixture
     * model
     * @param tourStartTime tour start time
     * @param tourEndTime tour end time
     * @param tourMaxDur tour max duration
     * @param overflowCost overflow cost
     * @param emergencyCost emergency collection cost
     * @param routeFailureCostMultiplier route failure cost multiplier
     * @throws SQLException
     * @throws RserveException
     * @throws REXPMismatchException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws ParseException
     * @throws FileNotFoundException
     * @throws ClassNotFoundException
     */
    public static void GenerateRealDataInstances(int nbDays, int flowTypeWid,
            int forecastType, int modelType,
            double tourStartTime, double tourEndTime, double tourMaxDur,
            double overflowCost, double emergencyCost, double routeFailureCostMultiplier)
            throws SQLException, RserveException, REXPMismatchException, IOException,
            NoSuchAlgorithmException, ParseException, FileNotFoundException, ClassNotFoundException {

        // Zone wid, client wid and tour type wid. The client wid and tour type wid define collection tours
        // for Serbeco, which is what we use by default. The zone wid is the only zone wid for Serbeco.
        int zoneWid = 536873310;
        int clientWid = 19;
        int tourTypeWid = 536872291;

        // Init GR object, passing all necessary identifiers as well as the parameters that are
        // held in the Data class. The latter can be reset for each Data object in any case.
        GR gr = new GR(nbDays, zoneWid, clientWid, flowTypeWid, tourTypeWid,
                forecastType, modelType,
                tourStartTime, tourEndTime, tourMaxDur,
                overflowCost, emergencyCost, routeFailureCostMultiplier);
        // Run instance generation for all Mondays in the collection period
        gr.GenerateAndExportInstances();
        // Clean up and close
        gr.Close();
    }

    /**
     * Inspects all generated instances and deletes those that have a container
     * demand on any day that is higher than the maximum demand.
     *
     * @param nbDays number of days in the planning horizon
     * @param maxDemand maximum demand for container on a day
     * @throws IOException
     * @throws FileNotFoundException
     * @throws ClassNotFoundException
     */
    public static void InspectRealDataInstances(int nbDays, double maxDemand)
            throws IOException, FileNotFoundException, ClassNotFoundException {

        // ArrayList of ImmutablePair of file name and container wid with excess demand
        ArrayList<ImmutablePair<String, Integer>> excessDemands = new ArrayList<>();
        // List of real data instance files
        File[] importFiles = new File(Parameters.dataObjectFolder).listFiles(Parameters.filter);
        
        // Loop over files
        for (File importFile : importFiles) {
            if (importFile.isFile() && importFile.getName().substring(importFile.getName().lastIndexOf(".")).equals(".ser")  && !importFile.getName().equals(".DS_Store")) {
                // Build a data object and add it to the dataList 
                Data data = FileData.ReadData(Parameters.dataObjectFolder, importFile.getName());
                // Print data statistics
                System.out.printf("%-18s %-6d %n", "Flow type: ", data.GetFlowWid());
                System.out.printf("%-18s %-6.2f %n", "Error sigma: ", data.GetErrorSigma());
                System.out.printf("%-18s %-6s %n", "Num containers: ", data.GetContainers().size());
                System.out.printf("%-18s %-6s %n", "Num dumps: ", data.GetDumps().size());
                System.out.printf("%-18s %-6s %n", "Num trucks: ", data.GetTrucks().size());
                // Print truck statistics
                for (Truck truck : data.GetTrucks()) {
                    System.out.printf("%-18s %-6s %n", "Truck: ", truck.GetWid());
                    System.out.printf("%-18s %-6s %n", " > Home: ", truck.GetHomeStartingPoint().GetDWid());
                    System.out.printf("%-18s %-6s %n", " > Curr: ", truck.GetCurrentStartingPoint().GetDWid());
                    System.out.printf("%-18s ", " > Flex: ");
                    for (Point flexStartingPoint : truck.GetFlexStartingPoints()) {
                        System.out.printf("%-12s ", flexStartingPoint.GetDWid());
                    }
                    System.out.printf("%n%-18s ", " > Avail: ");
                    for (int d = 0; d < nbDays; d++) {
                        System.out.printf("%-8b ", truck.IsAvailable(d));
                    }
                    System.out.printf("%n%-18s ", " > Req ret: ");
                    for (int d = 0; d < nbDays; d++) {
                        System.out.printf("%-8b ", truck.RequiredReturnToHome(d));
                    }
                    System.out.println("");
                }
                // Print container statistics and if container has excess demands, add to ArrayList
                for (Point container : data.GetContainers()) {
                    System.out.printf("Container %12s : [%6.2f] ", container.GetContWid(), 100 * container.GetInitVolumeLoad() / container.GetVolume());
                    for (int d = 0; d < nbDays; d++) {
                        double demand = 100 * container.GetForecastVolumeDemand(d) / container.GetVolume();
                        if (demand > maxDemand) {
                            excessDemands.add(new ImmutablePair<>(importFile.getName(), container.GetContWid()));
                        }
                        System.out.printf("%6.2f ", demand);
                    }
                    System.out.println();
                }
            }
            System.out.println("\n\n");
        }
        // Delete the instance files that have containers with excess demands
        for (ImmutablePair<String, Integer> excessDemand : excessDemands) {
            System.out.println(excessDemand);
            String fileStart = excessDemand.getKey().substring(0, excessDemand.getKey().length() - 12);
            File[] instanceFiles = new File(Parameters.instanceFolder).listFiles();
            for (File file : instanceFiles) {
                if (file.isFile() && file.getName().startsWith(fileStart)) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Tests algorithmic parameters values on a select set of instances for the
     * purpose of tuning.
     *
     * @param paramId parameter to tune
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchFieldException
     * @throws Exception
     */
    public static void TuneParameters(int paramId)
            throws IOException, InterruptedException, ExecutionException,
            NoSuchAlgorithmException, NoSuchFieldException, Exception {

        // List of instances to do the tunning with
        ArrayList<String> namesOfTuningInstances = new ArrayList<>();
        namesOfTuningInstances.add("abs4n45_arc07LoH3.dat");
        namesOfTuningInstances.add("abs4n40_arc07LoH3.dat");
        namesOfTuningInstances.add("abs1n50_arc07LoH3.dat");
        namesOfTuningInstances.add("abs3n30_arc07LoH3.dat");
        namesOfTuningInstances.add("abs1n40_arc07LoH3.dat");
        namesOfTuningInstances.add("abs1n30_arc07LoH6.dat");
        namesOfTuningInstances.add("abs3n40_arc07LoH3.dat");
        namesOfTuningInstances.add("abs1n25_arc07LoH6.dat");
        namesOfTuningInstances.add("abs2n50_arc07LoH3.dat");
        namesOfTuningInstances.add("abs4n30_arc07LoH6.dat");

        // Loop over all instances
        int nbTuningInstances = namesOfTuningInstances.size();
        for (int i = 0; i < nbTuningInstances; i++) {
            ExperimentTune experimentTune = new ExperimentTune(namesOfTuningInstances.get(i),
                    Parameters.benchmarkTypeArchetti2007, Parameters.hModeIRPA, 10);

            switch (paramId) {
                default:
                    // Variable declarations
                    String[] parameterName;
                    double[][] valuesToTest;
                    // Throw exception if incorrect ParamID
                    throw new Exception("ParamID has to be set to an accepted value!");
                case 1:
                    parameterName = new String[]{"hStartTempControlParam"};
                    valuesToTest = new double[][]{{0.2}, {0.4}, {0.5}, {0.6}, {0.8}};
                    experimentTune.tune(parameterName, valuesToTest, "double");
                    break;
                case 2:
                    parameterName = new String[]{"hCoolRate"};
                    valuesToTest = new double[][]{{0.99960}, {0.99977}, {0.99988}, {0.99994}, {0.99996}};
                    experimentTune.tune(parameterName, valuesToTest, "double");
                    break;
                case 3: // no tunning
                    parameterName = new String[]{"hFinTemp"};
                    valuesToTest = new double[][]{{0.01}, {0.1}, {1}};
                    experimentTune.tune(parameterName, valuesToTest, "double");
                    break;
                case 4:
                    parameterName = new String[]{"hPenaltyRate"};
                    //valuesToTest = new double[][] {{1.001}, {1.005}, {1.010}, {1.015}, {1.020}, {1.025}};
                    valuesToTest = new double[][]{{1.0001}, {1.0005}, {1.001}, {1.0015}, {1.002}, {1.003}};
                    experimentTune.tune(parameterName, valuesToTest, "double");
                    break;
                case 5:
                    parameterName = new String[]{"hPhiSegmentLength"};
                    valuesToTest = new double[][]{{500}, {1500}, {2000}, {3000}, {4000}, {5000}};
                    experimentTune.tune(parameterName, valuesToTest, "int");
                    break;
                case 6:
                    parameterName = new String[]{"hReactionFactor"};
                    valuesToTest = new double[][]{{0.2}, {0.4}, {0.5}, {0.6}, {0.8}};
                    experimentTune.tune(parameterName, valuesToTest, "double");
                    break;
                case 7:
                    parameterName = new String[]{"hAwardS1", "hAwardS2", "hAwardS3"};
                    valuesToTest = new double[][]{{30, 15, 10}, {30, 10, 5}, {30, 20, 5}};
                    experimentTune.tune(parameterName, valuesToTest, "set");
                    break;
            }
        }
    }

    /**
     * Run an experiment batch for real data instances analyzing the
     * probabilistic policies.
     *
     * @param threadPoolSize thread pool size
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws NoSuchAlgorithmException
     * @throws FileNotFoundException
     * @throws ClassNotFoundException
     */
    public static void RunRealDataExperimentBatchProbabilistic(int threadPoolSize)
            throws IOException, InterruptedException, ExecutionException,
            NoSuchAlgorithmException, FileNotFoundException, ClassNotFoundException {

        // Loop for emergency collection costs and route failure cost multipliers
        for (int i = 0; i < emergencyCosts.length; i++) {
            for (int j = 0; j < routeFailureCostMultipliers.length; j++) {

                // Benchmark type, solution mode, thread pool size, 
                // reset values for overflow cost, emergency collection cost, route failure cost multiplier,
                // availabilities, required returns to home
                ExperimentRun experimentRun = new ExperimentRun(threadPoolSize,
                        100.d, emergencyCosts[i], routeFailureCostMultipliers[j],
                        availabilities, requiredReturnsToHome);
                experimentRun.Run();
            }
        }
    }

    /**
     * Run an experiment batch for real data instances analyzing the alternative
     * policies.
     *
     * @param threadPoolSize thread pool size
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws FileNotFoundException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public static void RunRealDataExperimentBatchAlternative(int threadPoolSize)
            throws NoSuchAlgorithmException, IOException, FileNotFoundException,
            ClassNotFoundException, InterruptedException, ExecutionException {

        // Loop for container and truck capacity effective fractions
        for (int i = 0; i < containerEffectiveFractions.length; i++) {
            for (int j = 0; j < truckEffectiveFractions.length; j++) {

                // Fix effective capacity parameters
                Parameters.policyContainerEffectiveVolumeFraction = containerEffectiveFractions[i];
                Parameters.policyTruckEffectiveVolumeFraction = truckEffectiveFractions[j];
                Parameters.policyTruckEffectiveWeightFraction = truckEffectiveFractions[j];

                // Benchmark type, solution mode, thread pool size, 
                // reset values for overflow cost, emergency collection cost, route failure cost multiplier,
                // availabilities, required returns to home
                ExperimentRun experimentRun = new ExperimentRun(threadPoolSize,
                        0.d, 0.d, 0.d,
                        availabilities, requiredReturnsToHome);
                experimentRun.Run();
            }
        }
    }
    
    /**
     * Run an experiment batch for benchmark instances.
     * 
     * @param benchmarkType benchmark type
     * @param solutionMode solution mode
     * @param threadPoolSize thread pool size
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws FileNotFoundException
     * @throws ClassNotFoundException 
     * @throws java.lang.InterruptedException 
     * @throws java.util.concurrent.ExecutionException 
     */
    public static void RunBenchmarkExperimentBatch(int benchmarkType, int solutionMode, int threadPoolSize) 
            throws NoSuchAlgorithmException, IOException, FileNotFoundException, ClassNotFoundException, 
            InterruptedException, ExecutionException {
        
        // Run a benchmark experiment batch      
        ExperimentRun experimentRun = new ExperimentRun(benchmarkType, solutionMode, threadPoolSize);
        experimentRun.Run();
    }

    /**
     * Solve a single benchmark instance once and print results on console.
     *
     * @param instanceName instance name
     * @param benchmarkType benchmark type
     * @param solutionMode solution mode
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public static void SolveBenchmarkInstance(String instanceName, int benchmarkType, int solutionMode)
            throws IOException, NoSuchAlgorithmException {

        // Read in data
        Data data = new Data(Parameters.instanceFolder + instanceName, benchmarkType);

        // Construct SAALNS object
        SAALNS saalns = new SAALNSA(data, solutionMode, 1, Parameters.defaultTestValueID);
        // Run ALNS
        saalns.Run(null, null);
        // Print tours
        saalns.PrintTours();
        // Export results
        saalns.ExportContainerTracker();
        saalns.ExportOperatorPerformance();
        saalns.ExportOperatorStatistics();
        saalns.ExportPenaltyStatistics();
        saalns.ExportSAStatistics();
        saalns.ExportViolationStatistics();
    }

    /**
     * Run an experiment batch for real instances on a rolling horizon basis.
     *
     * @param resetOverflowCost overflow cost reset value
     * @param resetEmergencyCost emergency collection cost reset value
     * @param resetRouteFailureCostMultiplier route failure cost multiplier
     * reset value
     * @param threadPoolSize thread pool size
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws FileNotFoundException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public static void RunExperimentBatchRollingHorizon(double resetOverflowCost, double resetEmergencyCost,
            double resetRouteFailureCostMultiplier, int threadPoolSize)
            throws NoSuchAlgorithmException, IOException, FileNotFoundException,
            ClassNotFoundException, InterruptedException, ExecutionException {

        // Run the experiment batch for the rolling horizon
        // Rolling horizon length, number of rollovers, thread pool size,
        // reset values for overflow cost, emergency collection cost, route failure cost multiplier,
        // truck availabilities, truck required returns to home
        ExperimentRunRolling experimentRunRolling = new ExperimentRunRolling(7, 7, threadPoolSize,
                resetOverflowCost, resetEmergencyCost, resetRouteFailureCostMultiplier,
                availabilities, requiredReturnsToHome);
        experimentRunRolling.Run();
    }

    /**
     * Constructs main objects, executes and print results.
     *
     * @param args the command line arguments
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.ExecutionException
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.io.IOException
     * @throws java.lang.NoSuchFieldException
     * @throws java.lang.IllegalAccessException
     */
    public static void main(String[] args) throws InterruptedException, ExecutionException, NoSuchAlgorithmException,
            IOException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, Exception {

        // Run a regular probabilistic batch
         RunRealDataExperimentBatchProbabilistic(1);
        //RunBenchmarkExperimentBatch(Parameters.benchmarkTypeCrevier2007, Parameters.hModeVRPC, 1);
    }
}
