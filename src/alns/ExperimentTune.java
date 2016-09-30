package alns;

import alns.algo.SAALNS;
import alns.algo.SAALNSA;
import alns.data.Data;
import alns.param.Parameters;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * This class is for testing purposes only. It is used for tuning the
 * algorithmic parameters of the SA-ALNS
 *
 * @author Markov
 * @version 2.0
 */
public final class ExperimentTune {

    // Thread pool size
    private final int threadPoolSize;

    // Parameter to tune 
    private Field[] tuneParameter;

    // Instance file name
    private final String instanceFileName;

    // Benchmark type and mode
    private final int benchmarkType;
    private final int mode;

    // Raw and average performance statistics
    private final ArrayList<String> rawPerfStats;
    private final ArrayList<String> avgPerfStats;

    // List of data objects for the runs
    private final ArrayList<Data> dataList;

    /**
     * Assignment, initialization and data population constructor.
     *
     * @param instanceFile instance file name
     * @param benchmarkType benchmark type
     * @param mode solution mode
     * @param threadPoolSize thread pool size
     *
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.ExecutionException
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.lang.NoSuchFieldException
     */
    public ExperimentTune(String instanceFile, int benchmarkType, int mode, int threadPoolSize)
            throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException, NoSuchFieldException {

        // Assign instance file, benchmark type, mode, thread pool size and instance file name
        this.benchmarkType = benchmarkType;
        this.mode = mode;
        this.threadPoolSize = threadPoolSize;
        this.instanceFileName = instanceFile;

        // Initialize raw and average performance statistics and data list
        this.rawPerfStats = new ArrayList<>();
        this.avgPerfStats = new ArrayList<>();
        this.dataList = new ArrayList<>();

        // Initialize and populaze data list
        for (int i = 0; i < Parameters.expNumberRuns; i++) {
            Data data = new Data(Parameters.instanceFolder + instanceFile, this.benchmarkType);
            dataList.add(data);
        }

        // Report progress of the algorithm
        Parameters.hPrintIterations = true;
    }

    /**
     * Tunes algorithmic parameters with a double parameter set.
     *
     * @param tuneParameterName name of the parameter as defined in the
     * Parameters class
     * @param valuesToTest array of the different values to test
     * @param typeOfParameter Type of the parameter: 'double':double,
     * 'int':integer, 'set':set of parameters
     * @throws IOException
     * @throws java.lang.InterruptedException
     * @throws java.util.concurrent.ExecutionException
     * @throws java.lang.IllegalAccessException
     * @throws java.lang.NoSuchFieldException
     */
    public void tune(String[] tuneParameterName, double[][] valuesToTest, String typeOfParameter) throws IOException, InterruptedException, ExecutionException,
            IllegalArgumentException, IllegalAccessException, NoSuchFieldException {

        // Print message
        System.out.println("\n\nTunning started with parameter " + Arrays.toString(tuneParameterName));

        // Initializing testValueID (to create an unique ID to name the output files)
        char testValueID = 'a';

        // Tune parameter
        // The parameters to tune can be integers or doubles. In the case of the 
        // awards, sets of three parameters are tunned together.
        this.tuneParameter = new Field[tuneParameterName.length];
        switch (typeOfParameter) {
            case "double":
                break;
            case "int":
                this.tuneParameter[0] = Parameters.class.getField(tuneParameterName[0]);
                break;
            case "set":
                this.tuneParameter[0] = Parameters.class.getField(tuneParameterName[0]);
                this.tuneParameter[1] = Parameters.class.getField(tuneParameterName[1]);
                this.tuneParameter[2] = Parameters.class.getField(tuneParameterName[2]);
                break;
            default:
                throw new IllegalArgumentException("typeOfParameter must be 'double', 'int' or 'set' ");
        }

        // Initialize executor serice
        ExecutorService executor = Executors.newFixedThreadPool(this.threadPoolSize);

        for (double[] testValue : valuesToTest) {
            switch (typeOfParameter) {
                case "double":
                    this.tuneParameter[0].set(null, testValue[0]);
                    break;
                case "int":
                    this.tuneParameter[0].set(null, (int) testValue[0]);
                    break;
                case "set":
                    this.tuneParameter[0].set(null, testValue[0]);
                    this.tuneParameter[1].set(null, testValue[1]);
                    this.tuneParameter[2].set(null, testValue[2]);
                    break;
                default:
                    break;
            }

            // Print message
            System.out.println("\nSolving " + Parameters.expNumberRuns + " times for parameter "
                    + Arrays.toString(tuneParameterName) + " = " + Arrays.toString(testValue));

            // Instance name and data reference as final variables, so we can
            // pass to ad-hoc class below
            // If the awards are being tunned, the name and value are arrays of 
            // 3 parameters. Commas seperating the elements are replaced by dashes
            // to avoid confusion with the csv file seperators.
            final String tuneParameterName_final = Arrays.toString(tuneParameterName).replace(",", "-");
            final String testValue_final = Arrays.toString(testValue).replace(",", "-");

            // ArrayList of future schedule costs (will be available when thread is complete)
            ArrayList<Future<ImmutablePair<Double, Long>>> futureScheduleCosts = new ArrayList<>();

            // Run a fixed number of times
            for (int i = 0; i < Parameters.expNumberRuns; i++) {

                final int runNb = i;
                final char testValueID_f = testValueID;

                // Data reference as final variable, so we can pass to ad-hoc class below
                final Data data = dataList.get(i);

                // Submit new callable
                Future<ImmutablePair<Double, Long>> future = executor.submit(new Callable<ImmutablePair<Double, Long>>() {
                    /**
                     * Callable run.
                     *
                     * @return schedule cost
                     * @throws Exception
                     */
                    @Override
                    public ImmutablePair<Double, Long> call() throws Exception {

                        // Create the SAALNS object and run without collecting statistics
                        SAALNS saalns = new SAALNSA(data, mode, runNb, testValueID_f);
                        saalns.Run(null, null);
                        // saalns.ExportBestSolutionLog();
                        // Retrieve schedule cost
                        double scheduleCost = saalns.GetBestSchedule().GetCost();
                        long runTime = saalns.GetRunTime();
                        // Print message
                        System.out.printf(">> Thread: %-10d solution cost: %-15.3f %n",
                                Thread.currentThread().getId(), scheduleCost);

                        // Add a line to raw performance statistics
                        rawPerfStats.add(tuneParameterName_final + "," + testValue_final + ","
                                + scheduleCost + "," + runTime);

                        // Return schedule cost
                        return new ImmutablePair<>(scheduleCost, runTime);
                    }
                });
                // Update future schedule costs
                futureScheduleCosts.add(future);
            }

            // Extract schedule costs from future schedule costs
            ArrayList<Double> scheduleCosts = new ArrayList<>();
            ArrayList<Long> runTimes = new ArrayList<>();
            for (Future<ImmutablePair<Double, Long>> futures : futureScheduleCosts) {
                scheduleCosts.add(futures.get().getLeft());
                runTimes.add(futures.get().getRight());
            }

            // Calculate minimum, average and maximum cost
            double minCost = Collections.min(scheduleCosts);
            double maxCost = Collections.max(scheduleCosts);
            double avgCost = 0.d;
            double avgRunTime = 0.d;
            int nbRuns = Parameters.expNumberRuns;
            for (int m = 0; m < nbRuns; m++) {
                avgCost += (scheduleCosts.get(m) - avgCost) / (m + 1);
                avgRunTime += (runTimes.get(m) - avgRunTime) / (m + 1);

            }
            // Add line to average performance statistics
            avgRunTime = avgRunTime / 1000.0; // ms to seconds
            avgPerfStats.add(Arrays.toString(tuneParameterName).replace(",", "-")
                    + "," + Arrays.toString(testValue).replace(",", "-") + ","
                    + nbRuns + "," + minCost + "," + avgCost + "," + maxCost
                    + "," + avgRunTime);

            // Invcrement testValueID
            testValueID++;
        }

        // Shut down the executor service
        executor.shutdown();

        // Export statistics
        dumpRawStatisticsInCsvFile();
        dumpAverageStatisticsInCsvFile();
    }

    /**
     * Dumps raw tuning statistics to file.
     *
     * @throws IOException
     */
    private void dumpRawStatisticsInCsvFile() throws IOException {
        // Dump the raw statistics in a csvFile
        String fileName = this.instanceFileName.substring(0, this.instanceFileName.length() - 4);
        String csvFileName = Parameters.csvExportFolder + fileName + "_rawTuneResults.csv";
        FileWriter writer = new FileWriter(csvFileName);
        writer.write("Parameter, Value, Cost \n");
        for (String line : this.rawPerfStats) {
            line = line.replace("[", "");
            line = line.replace("]", "");
            writer.write(line + "\n");
        }
        writer.close();
    }

    /**
     * Dumps average tuning statistics to file.
     *
     * @throws IOException
     */
    private void dumpAverageStatisticsInCsvFile() throws IOException {
        // Dump the average statistics in a csvFile
        String fileName = this.instanceFileName.substring(0, this.instanceFileName.length() - 4);
        String csvFileName = Parameters.csvExportFolder + fileName + "_avgTuneResults.csv";
        FileWriter writer = new FileWriter(csvFileName);
        writer.write("Parameter, Value, Nb runs, Min cost, Avg cost, Max cost, Avg runtime \n");
        for (String line : this.avgPerfStats) {
            // "[]" which are added because arrays are used are removed.
            line = line.replace("[", "");
            line = line.replace("]", "");
            writer.write(line + "\n");
        }
        writer.close();
    }
}
