package alns.algo;

import alns.data.Data;
import alns.data.FeedbackForm;
import alns.param.Parameters;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * Implements SAALNS considering destroy and repair operator couples as single
 * operators and calculating weights, scores and usages of those.
 *
 * @author Markov
 * @version 1.0
 */
public class SAALNSB extends SAALNS {

    // Number of operators 
    private final int numOp;

    // Operator normalization factors 
    private final double[] opNormFactors;

    // Operator weights 
    private final double[] opWeights;

    // Operator scores 
    private final double[] opScores;

    // Operator usages
    private final double[] opUsages;

    // Current operator index
    private int opIndex;

    // For statistics collection
    private final ArrayList<String> opWeightStats;
    private final ArrayList<String> opUsageStats;
    private final int[][] opPerformanceStats;

    // Duration statistics
    protected long[] totalTimeOp;

    /**
     * Implementation of the initialization constructor.
     *
     * @param data Data object
     * @param mode solution mode - TSP, VRP, VRPC, VRPT, IRP, IRPA, IRPC
     * @param runNb run number when several runs are executed in an experiment
     * @param testValueID test value ID when several parameters are tested
     */
    public SAALNSB(Data data, int mode, int runNb, char testValueID) {

        // Call super constructor
        super(data, mode, runNb, testValueID);

        // Number of destroy and repair operators
        int numDestroyOps = Parameters.hNumDestroyOp[this.mode];
        int numRepairOps = Parameters.hNumRepairOp[this.mode];

        // Initialize number of operators, equal to the number of destroy times the number of repair operators
        this.numOp = numDestroyOps * numRepairOps;

        // Initialize normalization factors as 1
        double[] destroyOpNormFactors = new double[numDestroyOps];
        double[] repairOpNormFactors = new double[numRepairOps];
        Arrays.fill(destroyOpNormFactors, 1.d);
        Arrays.fill(repairOpNormFactors, 1.d);
        // Reset the required normalization factors
        for (ImmutablePair<Integer, Double> resetPair : Parameters.hDestroyOpNormFactorResets) {
            destroyOpNormFactors[resetPair.getKey()] = resetPair.getValue();
        }
        for (ImmutablePair<Integer, Double> resetPair : Parameters.hRepairOpNormFactorResets) {
            repairOpNormFactors[resetPair.getKey()] = resetPair.getValue();
        }
        // Initialize the normalization factors of the operators to be used in SAALNSB
        this.opNormFactors = new double[this.numOp];
        for (int i = 0; i < numDestroyOps; i++) {
            for (int j = 0; j < numRepairOps; j++) {
                this.opNormFactors[i * numRepairOps + j] = destroyOpNormFactors[i] + repairOpNormFactors[j];
            }
        }

        // Initialize operator weights to 1
        this.opWeights = new double[this.numOp];
        Arrays.fill(this.opWeights, 1.d);

        // Initialize operator scores to zero
        this.opScores = new double[this.numOp];

        // Initialize operator usages to zero
        this.opUsages = new double[this.numOp];

        // For statistics collection
        this.opWeightStats = new ArrayList<>();
        this.opUsageStats = new ArrayList<>();
        this.opPerformanceStats = new int[this.numOp][6];

        // Initialize duration statistic variables 
        this.totalTimeOp = new long[this.numOp];
    }

    /**
     * Implementation of the function which generates a new schedule from the
     * current schedule. In particular, it spins the wheel for operator choice,
     * assigns a deep copy of this.currSchedule to this.newSchedule and
     * generates a neighbor of it with the operator choice.
     */
    @Override
    protected void generateNewSchedule() {

        // Select operator
        this.opIndex = this.spinWheel(this.opWeights);
        // Generate new schedule
        this.newSchedule = this.newSchedule(this.currSchedule);
        // Make a sanity check of correct schedule copy. Since in a copied schedule everything
        // is recreated from scratch, we can detect incorrect behavior of operator or tree implementation 
        // if the new schedule is not the same as the current one.
        if (this.newSchedule.GetCost() < this.currSchedule.GetCost() - 1E8
                || this.newSchedule.GetCost() > this.currSchedule.GetCost() + 1E8) {
            System.err.println(Thread.currentThread().getStackTrace()[1].getClassName()
                    + "." + Thread.currentThread().getStackTrace()[1].getMethodName()
                    + "() says:");
            System.err.println(">> Incorrect schedule copy.");
            System.err.println(">> " + this.newSchedule.GetCost() + " vs. " + this.currSchedule.GetCost());
            System.err.println(">> Possible reason: Problems in probability updates in the Tree class.");
        }
        // Generate neighbor, extracting the destroy and repair operator index from
        // the couples operator index
        FeedbackForm feedback = this.newSchedule.GenerateNeighbor(this.opIndex / Parameters.hNumRepairOp[this.mode],
                this.opIndex % Parameters.hNumRepairOp[this.mode]);

        // Update duration statistic variables
        this.totalTimeOp[this.opIndex] += feedback.getDestroyOperatorDuration()
                + feedback.getRepairOperatorDuration();
    }

    /**
     * Implements the function which updates operator scores based on the
     * quality of the generated new schedule.
     */
    @Override
    protected void updateOperatorScores() {

        // Update operator scores based on set award
        this.opScores[this.opIndex] += this.award;

        // Update operator usages multiplied by the normalization factor
        this.opUsages[this.opIndex] += this.opNormFactors[this.opIndex];
    }

    /**
     * Implements the function which updates the operator weights at every phi
     * iterations. It also resets operator scores and usages to 0.
     */
    @Override
    protected void updateOperatorWeightsScoresUsages() {

        // Update weights first, then reset scores and usages to 0
        for (int i = 0; i < this.opWeights.length; i++) {
            if (this.opUsages[i] != 0) {
                double newWeight = (1 - this.reactionFactor) * this.opWeights[i]
                        + (this.reactionFactor * this.opScores[i]) / this.opUsages[i];
                this.opWeights[i] = Math.max(Parameters.hMinWeight, newWeight);
            }
            this.opScores[i] = 0.d;
            this.opUsages[i] = 0.d;
        }
    }

    /**
     * Implements the function which collects the operator weight and usage
     * statistics.
     *
     * @param collectStats true to collect statistics, false otherwise
     */
    @Override
    protected void collectOperatorStatistics(boolean collectStats) {

        if (collectStats) {
            this.opWeightStats.add(Arrays.toString(this.opWeights).substring(1, Arrays.toString(this.opWeights).length() - 1));
            this.opUsageStats.add(Arrays.toString(this.opUsages).substring(1, Arrays.toString(this.opUsages).length() - 1));
        }
    }

    /**
     * Implements the function which collects operator performance statistics in
     * terms of number of times operators led to new best solution, improved
     * current solution, accepted solution, and unaccepted solution.
     *
     * @param collectStats true to collect statistics, false otherwise
     */
    @Override
    protected void collectOperatorPerformance(boolean collectStats) {

        if (collectStats) {
            if (this.opPerformance == Parameters.hAwardS1) {
                this.opPerformanceStats[this.opIndex][0]++;
            } else if (this.opPerformance == Parameters.hAwardS2) {
                this.opPerformanceStats[this.opIndex][1]++;
            } else if (this.opPerformance == -Parameters.hAwardS2) {
                this.opPerformanceStats[this.opIndex][2]++;
            } else if (this.opPerformance == Parameters.hAwardS3) {
                this.opPerformanceStats[this.opIndex][3]++;
            } else if (this.opPerformance == -Parameters.hAwardS3) {
                this.opPerformanceStats[this.opIndex][4]++;
            } else {
                this.opPerformanceStats[this.opIndex][5]++;
            }
        }
    }

    /**
     * Implements the function that exports the evolution of the operator
     * weights and usages during the search.
     *
     * @throws java.io.IOException
     */
    @Override
    public void ExportOperatorStatistics() throws IOException {

        // Print message
        System.out.println("\nExporting operator statistics...");

        // Operator names
        String opNames = "";
        if (this.mode == Parameters.hModeTSP) {
            for (String destoyOp : Parameters.hTspDestroyOps) {
                for (String repairOp : Parameters.hTspRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else if (this.mode == Parameters.hModeVRP) {
            for (String destoyOp : Parameters.hVrpDestroyOps) {
                for (String repairOp : Parameters.hVrpRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else if (this.mode == Parameters.hModeVRPC) {
            for (String destoyOp : Parameters.hVrpcDestroyOps) {
                for (String repairOp : Parameters.hVrpcRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else if (this.mode == Parameters.hModeVRPT) {
            for (String destoyOp : Parameters.hVrptDestroyOps) {
                for (String repairOp : Parameters.hVrptRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else if (this.mode == Parameters.hModeIRP) {
            for (String destoyOp : Parameters.hIrpDestroyOps) {
                for (String repairOp : Parameters.hIrpRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else if (this.mode == Parameters.hModeIRPA) {
            for (String destoyOp : Parameters.hIrpaDestroyOps) {
                for (String repairOp : Parameters.hIrpaRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else {
            for (String destoyOp : Parameters.hIrpcDestroyOps) {
                for (String repairOp : Parameters.hIrpcRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        }

        // Export operator weights
        FileWriter writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "opWeightStats.csv");
        writer.write(opNames + "\n");
        for (String line : this.opWeightStats) {
            writer.write(line + "\n");
        }
        writer.close();

        // Export operator usage
        writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "opUsageStats.csv");
        writer.write(opNames + "\n");
        for (String line : this.opUsageStats) {
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

        // Operator names
        String opNames = "";
        if (this.mode == Parameters.hModeTSP) {
            for (String destoyOp : Parameters.hTspDestroyOps) {
                for (String repairOp : Parameters.hTspRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else if (this.mode == Parameters.hModeVRP) {
            for (String destoyOp : Parameters.hVrpDestroyOps) {
                for (String repairOp : Parameters.hVrpRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else if (this.mode == Parameters.hModeVRPC) {
            for (String destoyOp : Parameters.hVrpcDestroyOps) {
                for (String repairOp : Parameters.hVrpcRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else if (this.mode == Parameters.hModeVRPT) {
            for (String destoyOp : Parameters.hVrptDestroyOps) {
                for (String repairOp : Parameters.hVrptRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else if (this.mode == Parameters.hModeIRP) {
            for (String destoyOp : Parameters.hIrpDestroyOps) {
                for (String repairOp : Parameters.hIrpRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else if (this.mode == Parameters.hModeIRPA) {
            for (String destoyOp : Parameters.hIrpaDestroyOps) {
                for (String repairOp : Parameters.hIrpaRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
        } else {
            for (String destoyOp : Parameters.hIrpcDestroyOps) {
                for (String repairOp : Parameters.hIrpcRepairOps) {
                    opNames += destoyOp + "-" + repairOp + ",";
                }
            }
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

        // Export operator performance
        FileWriter writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "opPerformance.csv");
        writer.write("," + opNames + "\n");
        for (int i = 0; i < 6; i++) {
            writer.write(opPerformanceStatuses.get(i));
            for (int j = 0; j < this.numOp; j++) {
                writer.write("," + this.opPerformanceStats[j][i]);
            }
            writer.write("\n");
        }

        // Log total operator running time to the file
        writer.write("total time [s]");
        for (int j = 0; j < this.numOp; j++) {
            writer.write("," + this.totalTimeOp[j] * 1E-9);
        }
        writer.write("\n");

        writer.close();
    }
}
