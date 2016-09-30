package alns.algo;

import alns.data.Data;
import alns.data.FeedbackForm;
import alns.param.Parameters;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * Implements SAALNS considering destroy and repair operators separately and
 * calculating separately the weights, scores and usages of those.
 *
 * @author Markov
 * @version 1.0
 */
public class SAALNSA extends SAALNS {

    // Number of destroy and repair operators 
    private final int numDestroyOp;
    private final int numRepairOp;

    // Operator normalization factors 
    private final double[] destroyOpNormFactors;
    private final double[] repairOpNormFactors;

    // Operator weights 
    private final double[] destroyOpWeights;
    private final double[] repairOpWeights;

    // Operator scores 
    private final double[] destroyOpScores;
    private final double[] repairOpScores;

    // Operator usage 
    private final double[] destroyOpUsages;
    private final int[] destroyOpTotalUsages;
    private final double[] repairOpUsages;
    private final int[] repairOpTotalUsages;

    // Current destroy and repair operator indexes 
    private int destroyOpIndex;
    private int repairOpIndex;

    // For statistics collection
    private final ArrayList<String> destroyOpWeightStats;
    private final ArrayList<String> repairOpWeightStats;
    private final ArrayList<String> destroyOpUsageStats;
    private final ArrayList<String> repairOpUsageStats;
    private final int[][] destroyOpPerformanceStats;
    private final int[][] repairOpPerformanceStats;

    // Duration statistics
    protected long[] totalTimeDestroyOp;
    protected long[] totalTimeRepairOp;

    /**
     * Implementation of the initialization constructor.
     *
     * @param data Data object
     * @param mode solution mode - TSP, VRP, VRPC, VRPT, IRP, IRPA, IRPC
     * @param runNb run number when several runs are executed in an experiment
     * @param testValueID test value ID when several parameters are tested
     */
    public SAALNSA(Data data, int mode, int runNb, char testValueID) {

        // Call super constructor
        super(data, mode, runNb, testValueID);

        // Initialize number of destroy and repair operators
        this.numDestroyOp = Parameters.hNumDestroyOp[this.mode];
        this.numRepairOp = Parameters.hNumRepairOp[this.mode];

        // Initialize normalization factors to 1
        this.destroyOpNormFactors = new double[this.numDestroyOp];
        this.repairOpNormFactors = new double[this.numRepairOp];
        Arrays.fill(this.destroyOpNormFactors, 1.d);
        Arrays.fill(this.repairOpNormFactors, 1.d);
        // Reset the required normalization factors
        for (ImmutablePair<Integer, Double> resetPair : Parameters.hDestroyOpNormFactorResets) {
            this.destroyOpNormFactors[resetPair.getKey()] = resetPair.getValue();
        }
        for (ImmutablePair<Integer, Double> resetPair : Parameters.hRepairOpNormFactorResets) {
            this.repairOpNormFactors[resetPair.getKey()] = resetPair.getValue();
        }

        // Initialize operator weights to 1
        this.destroyOpWeights = new double[this.numDestroyOp];
        this.repairOpWeights = new double[this.numRepairOp];
        Arrays.fill(this.destroyOpWeights, 1.d);
        Arrays.fill(this.repairOpWeights, 1.d);

        // Initialize operator scores to zero
        this.destroyOpScores = new double[this.numDestroyOp];
        this.repairOpScores = new double[this.numRepairOp];

        // Initialize operator usages to zero
        this.destroyOpUsages = new double[this.numDestroyOp];
        this.destroyOpTotalUsages = new int[this.numDestroyOp];
        this.repairOpUsages = new double[this.numRepairOp];
        this.repairOpTotalUsages = new int[this.numRepairOp];

        // For statistics collection
        this.destroyOpWeightStats = new ArrayList<>();
        this.repairOpWeightStats = new ArrayList<>();
        this.destroyOpUsageStats = new ArrayList<>();
        this.repairOpUsageStats = new ArrayList<>();
        this.destroyOpPerformanceStats = new int[this.numDestroyOp][6];
        this.repairOpPerformanceStats = new int[this.numRepairOp][6];

        // Initialize duration statistic variables 
        this.totalTimeDestroyOp = new long[this.numDestroyOp];
        this.totalTimeRepairOp = new long[this.numRepairOp];
    }

    /**
     * Implementation of the function which generates a new schedule from the
     * current schedule. In particular, it spins the wheel for operator choice,
     * assigns a deep copy of this.currSchedule to this.newSchedule and
     * generates a neighbor of it with the operator choice.
     */
    @Override
    protected void generateNewSchedule() {

        // Select destroy and repair operators
        this.destroyOpIndex = this.spinWheel(this.destroyOpWeights);
        this.repairOpIndex = this.spinWheel(this.repairOpWeights);
        // Generate new schedule
        this.newSchedule = this.newSchedule(this.currSchedule);
        // Make a sanity check of correct schedule copy. Since in a copied schedule everything
        // is recreated from scratch, we can detect incorrect behavior of operator or tree implementation 
        // if the new schedule is not the same as the current one.
        if (this.newSchedule.GetCost() < this.currSchedule.GetCost() - 1E-8
                || this.newSchedule.GetCost() > this.currSchedule.GetCost() + 1E-8) {
            System.err.println(Thread.currentThread().getStackTrace()[1].getClassName()
                    + "." + Thread.currentThread().getStackTrace()[1].getMethodName()
                    + "() says:");
            System.err.println(">> Incorrect schedule copy.");
            System.err.println(">> " + this.newSchedule.GetCost() + " vs. " + this.currSchedule.GetCost());
            System.err.println(">> Possible reason: Problems in probability updates in the Tree class.");
        }
        // Generate neighbor
        FeedbackForm feedback = this.newSchedule.GenerateNeighbor(this.destroyOpIndex, this.repairOpIndex);

        // Update duration statistic variables
        this.totalTimeDestroyOp[this.destroyOpIndex] += feedback.getDestroyOperatorDuration();
        this.totalTimeRepairOp[this.repairOpIndex] += feedback.getRepairOperatorDuration();

    }

    /**
     * Implements the function which updates operator scores based on the
     * quality of the generated new schedule.
     */
    @Override
    protected void updateOperatorScores() {

        // Update operator scores based on set award
        this.destroyOpScores[this.destroyOpIndex] += this.award;
        this.repairOpScores[this.repairOpIndex] += this.award;

        // Update operator usages multiplied by the normalization factor
        this.destroyOpUsages[this.destroyOpIndex] += this.destroyOpNormFactors[this.destroyOpIndex];
        this.repairOpUsages[this.repairOpIndex] += this.repairOpNormFactors[this.repairOpIndex];

        // Update operator total usages during the search
        this.destroyOpTotalUsages[this.destroyOpIndex]++;
        this.repairOpTotalUsages[this.repairOpIndex]++;
    }

    /**
     * Implements the function which updates the operator weights at every phi
     * iterations. It also resets operator scores and usages to 0.
     */
    @Override
    protected void updateOperatorWeightsScoresUsages() {

        // Destroy operators, update weights first, then reset scores and usages to 0
        for (int i = 0; i < this.destroyOpWeights.length; i++) {
            if (this.destroyOpUsages[i] != 0) {
                double newWeight = (1 - this.reactionFactor) * this.destroyOpWeights[i]
                        + (this.reactionFactor * this.destroyOpScores[i]) / this.destroyOpUsages[i];
                this.destroyOpWeights[i] = Math.max(Parameters.hMinWeight, newWeight);
            }
            this.destroyOpScores[i] = 0.d;
            this.destroyOpUsages[i] = 0.d;
        }

        // Repair operators, update weights first, then reset scores and usages to 0
        for (int i = 0; i < this.repairOpWeights.length; i++) {
            if (this.repairOpUsages[i] != 0) {
                double newWeight = (1 - this.reactionFactor) * this.repairOpWeights[i]
                        + (this.reactionFactor * this.repairOpScores[i]) / this.repairOpUsages[i];
                this.repairOpWeights[i] = Math.max(Parameters.hMinWeight, newWeight);
            }
            this.repairOpScores[i] = 0.d;
            this.repairOpUsages[i] = 0.d;
        }
    }

    /**
     * Implements the function which collects the operator weight and usage
     * statistics after every segment.
     *
     * @param collectStats true to collect statistics, false otherwise
     */
    @Override
    protected void collectOperatorStatistics(boolean collectStats) {

        if (collectStats) {
            this.destroyOpWeightStats.add(Arrays.toString(this.destroyOpWeights).substring(1, Arrays.toString(this.destroyOpWeights).length() - 1));
            this.repairOpWeightStats.add(Arrays.toString(this.repairOpWeights).substring(1, Arrays.toString(this.repairOpWeights).length() - 1));
            this.destroyOpUsageStats.add(Arrays.toString(this.destroyOpUsages).substring(1, Arrays.toString(this.destroyOpUsages).length() - 1));
            this.repairOpUsageStats.add(Arrays.toString(this.repairOpUsages).substring(1, Arrays.toString(this.repairOpUsages).length() - 1));
        }
    }

    /**
     * Implements the function which collects operator performance statistics in
     * terms number of times operators led to new best solution, improved
     * current solution, accepted solution, and unaccepted solution.
     *
     * @param collectStats true to collect statistics, false otherwise
     */
    @Override
    protected void collectOperatorPerformance(boolean collectStats) {

        if (collectStats) {
            if (this.opPerformance == Parameters.hAwardS1) {
                this.destroyOpPerformanceStats[this.destroyOpIndex][0]++;
                this.repairOpPerformanceStats[this.repairOpIndex][0]++;
            } else if (this.opPerformance == Parameters.hAwardS2) {
                this.destroyOpPerformanceStats[this.destroyOpIndex][1]++;
                this.repairOpPerformanceStats[this.repairOpIndex][1]++;
            } else if (this.opPerformance == -Parameters.hAwardS2) {
                this.destroyOpPerformanceStats[this.destroyOpIndex][2]++;
                this.repairOpPerformanceStats[this.repairOpIndex][2]++;
            } else if (this.opPerformance == Parameters.hAwardS3) {
                this.destroyOpPerformanceStats[this.destroyOpIndex][3]++;
                this.repairOpPerformanceStats[this.repairOpIndex][3]++;
            } else if (this.opPerformance == -Parameters.hAwardS3) {
                this.destroyOpPerformanceStats[this.destroyOpIndex][4]++;
                this.repairOpPerformanceStats[this.repairOpIndex][4]++;
            } else {
                this.destroyOpPerformanceStats[this.destroyOpIndex][5]++;
                this.repairOpPerformanceStats[this.repairOpIndex][5]++;
            }
        }
    }

    /**
     * Returns an array of destroy operator total usages during the search.
     *
     * @return an array of destroy operator total usages during the search
     */
    @Override
    public int[] GetDestoryOpTotalUsages() {
        return this.destroyOpTotalUsages;
    }

    /**
     * Returns an array of repair operator total usages during the search.
     *
     * @return an array of repair operator total usages during the search
     */
    @Override
    public int[] GetRepairOpTotalUsages() {
        return this.repairOpTotalUsages;
    }

    /**
     * Implements the function which exports the evolution of the destroy and
     * repair operator weights and usages during the search.
     *
     * @throws java.io.IOException
     */
    @Override
    public void ExportOperatorStatistics() throws IOException {

        // Print message
        System.out.println("\nExporting operator statistics...");

        // Operator names to print
        String destroyOpNames;
        String repairOpNames;
        switch (this.mode) {
            case Parameters.hModeTSP:
                destroyOpNames = StringUtils.join(Parameters.hTspDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hTspRepairOps, ",");
                break;
            case Parameters.hModeVRP:
                destroyOpNames = StringUtils.join(Parameters.hVrpDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hVrpRepairOps, ",");
                break;
            case Parameters.hModeVRPC:
                destroyOpNames = StringUtils.join(Parameters.hVrpcDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hVrpcRepairOps, ",");
                break;
            case Parameters.hModeVRPT:
                destroyOpNames = StringUtils.join(Parameters.hVrptDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hVrptRepairOps, ",");
                break;
            case Parameters.hModeIRP:
                destroyOpNames = StringUtils.join(Parameters.hIrpDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hIrpRepairOps, ",");
                break;
            case Parameters.hModeIRPA:
                destroyOpNames = StringUtils.join(Parameters.hIrpaDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hIrpaRepairOps, ",");
                break;
            default:
                destroyOpNames = StringUtils.join(Parameters.hIrpcDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hIrpcRepairOps, ",");
                break;
        }

        // Export destroy operator weights
        FileWriter writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "destroyOpWeightStats.csv");
        writer.write(destroyOpNames + "\n");
        for (String line : this.destroyOpWeightStats) {
            writer.write(line + "\n");
        }
        writer.close();

        // Export repair operator weights
        writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "repairOpWeightStats.csv");
        writer.write(repairOpNames + "\n");
        for (String line : this.repairOpWeightStats) {
            writer.write(line + "\n");
        }
        writer.close();

        // Export destroy operator usage
        writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "destroyOpUsageStats.csv");
        writer.write(destroyOpNames + "\n");
        for (String line : this.destroyOpUsageStats) {
            writer.write(line + "\n");
        }
        writer.close();

        // Export repair operator usage
        writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "repairOpUsageStats.csv");
        writer.write(repairOpNames + "\n");
        for (String line : this.repairOpUsageStats) {
            writer.write(line + "\n");
        }
        writer.close();
    }

    /**
     * Implements the function which exports operator performance in terms of
     * number of times operators led to new best solution, improved current
     * solution, accepted solution, and unaccepted solution.
     *
     * @throws java.io.IOException
     */
    @Override
    public void ExportOperatorPerformance() throws IOException {

        // Print message
        System.out.println("\nExporting operator performance...");

        // Operator names to print
        String destroyOpNames;
        String repairOpNames;
        switch (this.mode) {
            case Parameters.hModeTSP:
                destroyOpNames = StringUtils.join(Parameters.hTspDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hTspRepairOps, ",");
                break;
            case Parameters.hModeVRP:
                destroyOpNames = StringUtils.join(Parameters.hVrpDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hVrpRepairOps, ",");
                break;
            case Parameters.hModeVRPC:
                destroyOpNames = StringUtils.join(Parameters.hVrpcDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hVrpcRepairOps, ",");
                break;
            case Parameters.hModeVRPT:
                destroyOpNames = StringUtils.join(Parameters.hVrptDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hVrptRepairOps, ",");
                break;
            case Parameters.hModeIRP:
                destroyOpNames = StringUtils.join(Parameters.hIrpDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hIrpRepairOps, ",");
                break;
            case Parameters.hModeIRPA:
                destroyOpNames = StringUtils.join(Parameters.hIrpaDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hIrpaRepairOps, ",");
                break;
            default:
                destroyOpNames = StringUtils.join(Parameters.hIrpcDestroyOps, ",");
                repairOpNames = StringUtils.join(Parameters.hIrpcRepairOps, ",");
                break;
        }

        // Operator performance statuses
        ArrayList<String> opPerformanceStatuses = new ArrayList<>(Arrays.asList(
                "new best",
                "improved current",
                "improved current (visited)",
                "accepted",
                "accepted (visited)",
                "unaccepted"
        ));

        // Export DESTROY operator performance
        FileWriter writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "destroyOpPerformance.csv");
        writer.write("," + destroyOpNames + "\n");
        for (int i = 0; i < 6; i++) {
            writer.write(opPerformanceStatuses.get(i));
            for (int j = 0; j < this.numDestroyOp; j++) {
                writer.write("," + this.destroyOpPerformanceStats[j][i]);
            }
            writer.write("\n");
        }

        // Log total number of times the operator was used
        int sum;
        writer.write("sum");
        for (int j = 0; j < this.numDestroyOp; j++) {
            sum = 0;
            for (int i = 0; i < 6; i++) {
                sum += this.destroyOpPerformanceStats[j][i];
            }
            writer.write("," + sum);
        }
        writer.write("\n");

        // Log total operator running time to the file
        writer.write("total time [s]");
        for (int j = 0; j < this.numDestroyOp; j++) {
            writer.write("," + this.totalTimeDestroyOp[j] * 1E-9);
        }
        writer.write("\n");

        writer.close();

        // Export REPAIR operator performance
        writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "repairOpPerformance.csv");
        writer.write("," + repairOpNames + "\n");
        for (int i = 0; i < 6; i++) {
            writer.write(opPerformanceStatuses.get(i));
            for (int j = 0; j < this.numRepairOp; j++) {
                writer.write("," + this.repairOpPerformanceStats[j][i]);
            }
            writer.write("\n");
        }

        // Log total number of times the operator was used
        writer.write("sum");
        for (int j = 0; j < this.numRepairOp; j++) {
            sum = 0;
            for (int i = 0; i < 6; i++) {
                sum += this.repairOpPerformanceStats[j][i];
            }
            writer.write("," + sum);
        }
        writer.write("\n");

        // Log total operator running time to the file
        writer.write("total time [s]");
        for (int j = 0; j < this.numRepairOp; j++) {
            writer.write("," + this.totalTimeRepairOp[j] * 1E-9);
        }
        writer.write("\n");

        writer.close();
    }
}
