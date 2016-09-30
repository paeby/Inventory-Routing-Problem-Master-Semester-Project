package alns.algo;

import alns.data.Data;
import alns.data.Point;
import alns.data.Truck;
import alns.param.Parameters;
import alns.schedule.*;
import alns.tour.Tour;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Abstract class for the adaptive large neighborhood search (ALNS) with
 * solution acceptance governed by simulated annealing (SA). Has two
 * implementations with different weight calculations for the destroy and repair
 * operators.
 *
 * @author Markov
 * @version 2.0
 */
public abstract class SAALNS {

    // Data object
    protected final Data data;

    // Solution mode - TSP, VRP, VRPC, VRPT, IRP, IRPA, IRPC
    protected final int mode;

    // Schedules
    protected Schedule newSchedule;
    protected Schedule currSchedule;
    protected Schedule bestSchedule;

    // Best schedule cost
    protected double bestScheduleCost;

    // Temperatures
    protected final double initTemp;
    protected final double finTemp;
    protected final double coolRate;
    protected double currTemp;

    // Reaction factor
    protected final double reactionFactor;

    // Phi segment length
    protected final int phiSegmentLength;

    // Total number of iterations
    protected int totIterations;

    // Awards
    protected final double awardS1;
    protected final double awardS2;
    protected final double awardS3;

    // Current award
    protected double award;

    // Operator performance
    protected double opPerformance;

    // Run time
    protected long runTime;

    // Holds solution hashcodes
    protected final HashSet<Integer> hashCodes;

    // For statistics collection
    protected int numNewBestSchedules;
    protected final ArrayList<String> saStats;
    protected final ArrayList<String> variableEvolutionStats;
    protected final ArrayList<String> penaltyStats;
    protected final ArrayList<String> violationStats;
    protected final ArrayList<String> bestSolutionStats;

    // Run number when a problem is being solved several times
    protected final int runNb;

    // Prefix of export files
    protected final String prefix;

    /**
     * Initialization constructor.
     *
     * @param data Data object
     * @param mode solution mode - TSP, VRP, VRPC, VRPT, IRP, IRPA, IRPC
     * @param runNb run number when several runs are executed in an experiment
     * @param testValueID test value ID when several parameters are tested
     */
    public SAALNS(Data data, int mode, int runNb, char testValueID) {

        // Initialize data
        this.data = data;

        // Initialize mode
        this.mode = mode;

        // Assign best schedule cost
        this.bestScheduleCost = Double.POSITIVE_INFINITY;

        // Initialize temperature parameters
        this.initTemp = Parameters.hInitTemp;
        this.finTemp = Parameters.hFinTemp;
        this.coolRate = Parameters.hCoolRate;
        this.currTemp = this.initTemp;

        // Initialize phi segment length (weight update)
        this.phiSegmentLength = Parameters.hPhiSegmentLength;

        // Initialize reaction factor
        this.reactionFactor = Parameters.hReactionFactor;

        // Total number of iterations
        this.totIterations = 0;

        // Initialize award values
        this.awardS1 = Parameters.hAwardS1;
        this.awardS2 = Parameters.hAwardS2;
        this.awardS3 = Parameters.hAwardS3;

        // Initialize solution hashcodes
        this.hashCodes = new HashSet<>();

        // For statistics collection
        this.numNewBestSchedules = 0;
        this.saStats = new ArrayList<>();
        this.variableEvolutionStats = new ArrayList<>();
        this.penaltyStats = new ArrayList<>();
        this.violationStats = new ArrayList<>();
        this.bestSolutionStats = new ArrayList<>();

        // Run number
        this.runNb = runNb;

        // Prefix of export files (we can change it to instance name to collect 
        // detailed statistics)
        this.prefix = "_";

    }

    /**
     * Updates the objective function penalties based on whether the new
     * schedule is feasible or not. The penalty for a particular constraint
     * violation is increased if the new schedule violates it, and decreased if
     * it does not violate it.
     */
    protected void updatePenalties() {

        // Shorthand for penalties
        Penalties penalties = this.data.GetPenalties();

        // Update max truck volume violation penalty
        if (this.newSchedule.GetVolumeViolation() > 0) {
            penalties.IncreaseVolumeViolPenalty();
        } else {
            penalties.ReduceVolumeViolPenalty();
        }

        // Update max truck weight violation penalty
        if (this.newSchedule.GetWeightViolation() > 0) {
            penalties.IncreaseWeightViolPenalty();
        } else {
            penalties.ReduceWeightViolPenalty();
        }

        // Update time window volume violation penalty
        if (this.newSchedule.GetTWViolation() > 0) {
            penalties.IncreaseTWViolPenalty();
        } else {
            penalties.ReduceTWViolPenalty();
        }

        // Update duration violation penalty
        if (this.newSchedule.GetDurViolation() > 0) {
            penalties.IncreaseDurViolPenalty();
        } else {
            penalties.ReduceDurViolPenalty();
        }

        // Update accessibility violation penalty
        if (this.newSchedule.GetAccessViolation() > 0) {
            penalties.IncreaseAccessViolPenalty();
        } else {
            penalties.ReduceAccessViolPenalty();
        }

        // Update backorder limit violation penalty
        if (this.newSchedule.GetBackorderViolation() > 0) {
            penalties.IncreaseBackorderViolPenalty();
        } else {
            penalties.ReduceBackorderViolPenalty();
        }

        // Update container capacity violation (overflow) penalty
        if (this.newSchedule.GetContViolation() > 0) {
            penalties.IncreaseContViolPenalty();
        } else {
            penalties.ReduceContViolPenalty();
        }

        // Update home starting point violation penalty
        if (this.newSchedule.GetHomeDepotViolation() > 0) {
            penalties.IncreaseHomeDepotViolPenalty();
        } else {
            penalties.ReduceHomeDepotViolPenalty();
        }
    }

    /**
     * Spins the roulette wheel for the provided operator weights and returns
     * the operator choice.
     *
     * @param opWeights
     * @return operator choice
     */
    protected int spinWheel(double[] opWeights) {

        // Get the maximum operator weight and the number of operators
        double maxOpWeight = NumberUtils.max(opWeights);
        int numOps = opWeights.length;

        // The operator index and whether it is accepted
        int opIndex = 0;
        boolean accepted = false;

        // Probabilistic operator index acceptance with O(1)
        while (!accepted) {
            opIndex = this.data.GetRand().nextInt(numOps);
            if (this.data.GetRand().nextDouble() < opWeights[opIndex] / maxOpWeight) {
                accepted = true;
            }
        }

        // Return the operator index selection
        return opIndex;
    }

    /**
     * Returns a new Schedule implementation based on the solution mode.
     *
     * @param data Data object
     * @return a new Schedule implementation based on the solution mode
     */
    protected Schedule newSchedule(Data data) {
        if (this.mode == Parameters.hModeTSP) {
            return new ScheduleTSP(data);
        } else if (this.mode == Parameters.hModeVRP) {
            return new ScheduleVRP(data);
        } else if (this.mode == Parameters.hModeVRPC) {
            return new ScheduleVRPC(data);
        } else if (this.mode == Parameters.hModeVRPT) {
            return new ScheduleVRPT(data);
        } else if (this.mode == Parameters.hModeIRP) {
            return new ScheduleIRP(data);
        } else if (this.mode == Parameters.hModeIRPA) {
            return new ScheduleIRPA(data);
        } else {
            return new ScheduleIRPC(data);
        }
    }

    /**
     * Returns a new Schedule implementation based on the solution mode.
     *
     * @param schedule Schedule object
     * @return a new Schedule implementation based on the solution mode
     */
    protected Schedule newSchedule(Schedule schedule) {
        if (this.mode == Parameters.hModeTSP) {
            return new ScheduleTSP(schedule);
        } else if (this.mode == Parameters.hModeVRP) {
            return new ScheduleVRP(schedule);
        } else if (this.mode == Parameters.hModeVRPC) {
            return new ScheduleVRPC(schedule);
        } else if (this.mode == Parameters.hModeVRPT) {
            return new ScheduleVRPT(schedule);
        } else if (this.mode == Parameters.hModeIRP) {
            return new ScheduleIRP(schedule);
        } else if (this.mode == Parameters.hModeIRPA) {
            return new ScheduleIRPA(schedule);
        } else {
            return new ScheduleIRPC(schedule);
        }
    }

    /**
     * Abstract function which generates a new schedule from the current
     * schedule. In particular, it spins the wheel for operator choice, assigns
     * a deep copy of this.currSchedule to this.newSchedule and generates a
     * neighbor of it with the operator choice.
     */
    protected abstract void generateNewSchedule();

    /**
     * Abstract function which updates operator scores based on the quality of
     * the generated new schedule.
     */
    protected abstract void updateOperatorScores();

    /**
     * Abstract function which updates the operator weights at every phi
     * iterations. It also resets operator scores and usages to 0.
     */
    protected abstract void updateOperatorWeightsScoresUsages();

    /**
     * Collects the simulated annealing statistics in terms of temperature,
     * non-accepted, accepted and best solution cost at each iteration.
     *
     * @param collectStats true to collect statistics, false otherwise
     * @param newScheduleCost the cost of the generated new schedule
     * @param startTime time at which the algorithm started running
     */
    private void collectSAStatistics(boolean collectStats, double newScheduleCost, long startTime) {

        if (collectStats) {
            long now = System.currentTimeMillis();
            double time_s = (now - startTime) / 1000.d;
            double nonAcceptedCost = (this.award == 0) ? newScheduleCost : Parameters._404;
            double acceptedCost = (this.award == 0) ? Parameters._404 : newScheduleCost;
            this.saStats.add(this.currTemp + "," + time_s + "," + nonAcceptedCost
                    + "," + acceptedCost + "," + this.bestScheduleCost);
        }
    }

    /**
     * Collects the feasibility violation penalties at each iteration.
     *
     * @param collectStats true to collect statistics, false otherwise
     */
    protected void collectPenaltyStatistics(boolean collectStats) {

        if (collectStats) {
            Penalties penalties = this.data.GetPenalties();
            this.penaltyStats.add(
                    penalties.GetVolumeViolPenalty() + ","
                    + penalties.GetWeightViolPenalty() + ","
                    + penalties.GetTWViolPenalty() + ","
                    + penalties.GetDurViolPenalty() + ","
                    + penalties.GetAccessViolPenalty() + ","
                    + penalties.GetBackorderViolPenalty() + ","
                    + penalties.GetContViolPenalty() + ","
                    + penalties.GetHomeDepotViolPenalty()
            );
        }
    }

    /**
     * Collects the feasibility violation existence at each iteration.
     *
     * @param collectStats true to collect statistics, false otherwise
     */
    protected void collectViolationStatistics(boolean collectStats) {

        if (collectStats) {

            int volumeViolation = this.currSchedule.GetVolumeViolation() > 0 ? 1 : 0;
            int weightViolation = this.currSchedule.GetWeightViolation() > 0 ? 1 : 0;
            int twViolation = this.currSchedule.GetTWViolation() > 0 ? 1 : 0;
            int durViolation = this.currSchedule.GetDurViolation() > 0 ? 1 : 0;
            int accessViolation = this.currSchedule.GetAccessViolation() > 0 ? 1 : 0;
            int backorderViolation = this.currSchedule.GetBackorderViolation() > 0 ? 1 : 0;
            int contViolation = this.currSchedule.GetContViolation() > 0 ? 1 : 0;
            int homeDepotViolation = this.currSchedule.GetHomeDepotViolation() > 0 ? 1 : 0;

            String feasible = volumeViolation + weightViolation
                    + twViolation + durViolation
                    + accessViolation + backorderViolation
                    + contViolation + homeDepotViolation
                    == 0 ? "yes" : "";

            this.violationStats.add(volumeViolation
                    + "," + weightViolation
                    + "," + twViolation
                    + "," + durViolation
                    + "," + accessViolation
                    + "," + backorderViolation
                    + "," + contViolation
                    + "," + homeDepotViolation
                    + "," + feasible);
        }
    }

    /**
     * Collects best solution statistics.
     *
     * @param iterationNumber iteration number
     * @param scheduleCost schedule cost
     * @param collectBestSolutionStatistics true for collecting solution
     * improvements, false otherwise
     */
    private void collectBestSolutionStatistics(int iterationNumber, double scheduleCost, boolean collectBestSolutionStatistics) {
        if (collectBestSolutionStatistics) {
            this.bestSolutionStats.add(String.valueOf(iterationNumber) + "," + String.valueOf(scheduleCost));
        }
    }

    /**
     * Abstract function which collects the operator weight and usage
     * statistics.
     *
     * @param collectStats true to collect statistics, false otherwise
     */
    protected abstract void collectOperatorStatistics(boolean collectStats);

    /**
     * Abstract function which collects operator performance statistics in terms
     * number of times operators led to new best solution, improved current
     * solution, accepted solution, and unaccepted solution.
     *
     * @param collectStats true to collect statistics, false otherwise
     */
    protected abstract void collectOperatorPerformance(boolean collectStats);

    /**
     * Constructs an empty dummy schedule and deep copies it to the best
     * schedule.
     *
     * @return true if the construction of the dummy schedule was successful,
     * false otherwise
     */
    public boolean ConstructDummy() {

        // Reset the penalties
        this.data.GetPenalties().ResetPenalties();

        // Create and construct the current schedule
        this.currSchedule = this.newSchedule(this.data);
        this.currSchedule.Construct();
        // If the size of the schedule (number of tours) after construction is 0,
        // then there are either no trucks provided, or none of the provided trucks is
        // available during the planning horizon
        if (this.currSchedule.GetSize() == 0) {
            System.err.println(Thread.currentThread().getStackTrace()[1].getClassName()
                    + "." + Thread.currentThread().getStackTrace()[1].getMethodName()
                    + "() says:");
            System.err.println(">> Initial schedule is empty.");
            System.err.println(">> Possible reasons: No truck availabilities for the planning horizon.");
            System.err.println(">> I don't know what to do. Terminating heuristic...");
            return false;
        }
        // Assign a deep copy of the current schedule to the best schedule
        this.bestSchedule = this.newSchedule(this.currSchedule);

        // If the construction of the dummy schedule was successful, return true
        return true;
    }

    /**
     * Runs the adaptive large neighborhood search (ALNS) with solution
     * acceptance by simulated annealing (SA).
     *
     * @param collectStats true to collect statistics, false otherwise
     * @param collectBestSolutionStatistics true to collect at which iteration
     * the solution is improved, false otherwise
     * @return true if the optimization was successful, false otherwise
     * @throws java.io.IOException
     */
    public boolean Run(Boolean collectStats, Boolean collectBestSolutionStatistics) throws IOException {
        // Set defaults if argument values are "null"
        if (collectStats == null) {
            collectStats = Parameters.expCollectStats_default;
        }
        if (collectBestSolutionStatistics == null) {
            collectBestSolutionStatistics = Parameters.expCollectSolutionImprovements_default;
        }

        // It is imperative that we reset the penalties
        this.data.GetPenalties().ResetPenalties();

        // Create and construct the current schedule
        this.currSchedule = this.newSchedule(this.data);
        this.currSchedule.Construct();
        // If the size of the schedule (number of tours) after construction is 0,
        // then there are either no trucks provided, or none of the provided trucks is
        // available during the planning horizon
        if (this.currSchedule.GetSize() == 0) {
            System.err.println(Thread.currentThread().getStackTrace()[1].getClassName()
                    + "." + Thread.currentThread().getStackTrace()[1].getMethodName()
                    + "() says:");
            System.err.println(">> Initial schedule is empty.");
            System.err.println(">> Possible reasons: No truck availabilities for the planning horizon.");
            System.err.println(">> I don't know what to do. Terminating heuristic...");
            return false;
        }
        // Assign a deep copy of the current schedule to the best schedule
        this.bestSchedule = this.newSchedule(this.currSchedule);

        // SA loop
        long startTime = System.currentTimeMillis();
        int iterCounter = 0;
        if (Parameters.hPrintIterations) {
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            System.out.println("\nStarting SA-ALNS. Time: " + sdf.format(cal.getTime()));
        }
        while (this.currTemp > this.finTemp) {

            // Award to be added to the operators' score
            this.award = 0.d;

            // Default operator performance
            this.opPerformance = 0.d;

            // Generate a new schedule from the current schedule
            this.generateNewSchedule();
            // Update penalties
            // this.updatePenalties();

            // Current schedule cost and new schedule cost after penalties have been updated
            double currScheduleCost = this.currSchedule.GetCost();
            double newScheduleCost = this.newSchedule.GetCost();

            // Print statistics
            if (Parameters.hPrintIterations && iterCounter % Parameters.hPrintFreq == 0) {
                int progress;
                if (!searchingFirstFeasibleSolution()) {
                    progress = (int) Math.floor((double) iterCounter / (double) this.totIterations * 100);
                } else {
                    progress = 0;
                }
                System.out.printf("Thread %-2d - TIter: %-12d (%3d%%)   Temp %-17.3f Assignments: %-12d Best cost: %-18.3f Current cost: %-20.3f %n",
                        Thread.currentThread().getId(), iterCounter, progress, this.currTemp,
                        this.bestSchedule.GetNumContainers(), this.bestScheduleCost, currScheduleCost);
            }

            // Compute delta
            double delta = newScheduleCost - currScheduleCost;

            // If new schedule is better, accept automatically,
            // otherwise accept with a gradually decreasing probability
            if (delta < 0) {
                // Set current schedule
                this.updatePenalties();
                this.currSchedule = this.newSchedule;
                currScheduleCost = newScheduleCost;
                // If solution has not been visited, set award and performance
                if (!this.hashCodes.contains(this.currSchedule.GetHashCode())) {
                    this.award = this.awardS2;
                    this.opPerformance = this.awardS2;
                } else {
                    this.opPerformance = -this.awardS2;
                }
            } else {
                if (this.data.GetRand().nextDouble() < Math.exp(-delta / this.currTemp)) {
                    // Set current schedule
                    this.updatePenalties();
                    this.currSchedule = this.newSchedule;
                    currScheduleCost = newScheduleCost;
                    // If solution has not been visited, set award and performance
                    if (!this.hashCodes.contains(this.currSchedule.GetHashCode())) {
                        this.award = this.awardS3;
                        this.opPerformance = this.awardS3;
                    } else {
                        this.opPerformance = -this.awardS3;
                    }
                }
            }

            // If current schedule improves best schedule and is feasible, best schedule is
            // updated to be equal to current schedule
            if (currScheduleCost < this.bestScheduleCost
                    && this.currSchedule.IsFeasible()) {

                // Update number of new best schedules
                this.numNewBestSchedules++;

                // Set best schedule and its cost
                this.bestSchedule = this.newSchedule(this.currSchedule);
                this.bestScheduleCost = currScheduleCost;
                // Set award and performance
                this.award = this.awardS1;
                this.opPerformance = this.awardS1;
                // Log the new best solution
                this.collectBestSolutionStatistics(iterCounter, currScheduleCost, collectBestSolutionStatistics);
            }

            // Add new schedule to hashcodes
            this.hashCodes.add(this.newSchedule.GetHashCode());

            // Log data every hStatsCollectionRate number of iterations
            if (iterCounter % Parameters.hStatsCollectionRate == 0) {
                // Collect SA statistics
                this.collectSAStatistics(collectStats, newScheduleCost, startTime);
                // Collect violation and penalty statistics
                this.collectViolationStatistics(collectStats);
                this.collectPenaltyStatistics(collectStats);
                // Collect operator performance
                this.collectOperatorPerformance(collectStats);
            }

            // Update operator scores based on set award
            this.updateOperatorScores();
            // The first time a feasible solution is found, compute the initial 
            // temperature based on w (the start temperature control parameter)
            if (searchingFirstFeasibleSolution() && firstFeasibleSolutionFound()) {
                // A solution w% worst than the 1st feasible solution has 50%
                // probability of being accepted
                this.currTemp = currScheduleCost * Parameters.hStartTempControlParam / Math.log(2);
                // Compute the total number of Iterations which will be needed to reach the final temperature
                double iterations = (Math.log(this.finTemp) - Math.log(this.currTemp)) / Math.log(this.coolRate);
                this.totIterations = (int) Math.round(iterations);
            }

            // Once a feasible solution has bean reached, update current temperature at every iteration
            if (firstFeasibleSolutionFound()) {
                this.currTemp *= this.coolRate;
            }

            // Update iteration counter
            iterCounter++;

            // At the phi segment length, update operator weights, reset scores and usages
            // and perform a local search if activated 
            if (iterCounter % this.phiSegmentLength == 0) {
                // Collect operator statistics
                this.collectOperatorStatistics(collectStats);
                // Update operator weights, scores and usages
                this.updateOperatorWeightsScoresUsages();
                // If local search activated
                if (Parameters.expLocalSearchActivated) {
                    // Do local search on current schedule and update cost
                    double oldScheduleCost = currScheduleCost;
                    this.currSchedule.DoLocalSearch();
                    currScheduleCost = this.currSchedule.GetCost();
//                    if (currScheduleCost < oldScheduleCost) {
//                        System.out.println("Improved with LOCAL SEARCH!");
//                    }

                    // If the solution is better than the current best solution
                    if (currScheduleCost < this.bestScheduleCost
                            && this.currSchedule.IsFeasible()) {
                        this.bestSchedule = this.newSchedule(this.currSchedule);
                        this.bestScheduleCost = currScheduleCost;
                    }
                }
            }

            // Break loop if instance is probably infeasible, that is after a maximum number 
            // of iterations without finding a first feasible solutions
            if (iterCounter > Parameters.hMaxIterNoFeasible && bestScheduleCost == Double.POSITIVE_INFINITY) {
                break;
            }
        }
        // Log one final entry with the final number of iterations and the final best cost
        this.collectBestSolutionStatistics(iterCounter, this.bestScheduleCost, collectBestSolutionStatistics);

        // Comupte and print the computation time and some statistics
        long endTime = System.currentTimeMillis();
        this.runTime = (endTime - startTime);
        if (Parameters.hPrintIterations) {
            System.out.println("\nFinished SA-ALNS in " + (endTime - startTime) / 1000.d + " seconds.");
            System.out.println("Improved " + this.numNewBestSchedules + " times the best feasible schedule.");
        }

        // If the optimization was successful, return true
        return true;
    }

    /**
     * Returns true if the first feasible solution has been found, false
     * otherwise.
     *
     * @return true if the first feasible solution has been found, false
     * otherwise
     */
    private boolean firstFeasibleSolutionFound() {
        return (this.bestScheduleCost < Double.POSITIVE_INFINITY);
    }

    /**
     * Returns true if the algorithm is still searching for the first feasible
     * solution, false otherwise.
     *
     * @return true if the algorithm is still searching for the first feasible
     * solution, false otherwise
     */
    private boolean searchingFirstFeasibleSolution() {
        return (this.currTemp == Parameters.hInitTemp);
    }

    /**
     * Returns the runtime of the SA-ALNS.
     *
     * @return the runtime of the SA-ALNS
     */
    public long GetRunTime() {
        return this.runTime;
    }

    /**
     * Returns the best schedule found by SA-ALNS.
     *
     * @return the best schedule found by SA-ALNS
     */
    public Schedule GetBestSchedule() {
        return this.bestSchedule;
    }

    /**
     * Returns the destroy operators total usages during the search.
     *
     * @return the destroy operators total usages during the search
     */
    public int[] GetDestoryOpTotalUsages() {
        return null;
    }

    /**
     * Returns the repair operators total usages during the search.
     *
     * @return the repair operators total usages during the search
     */
    public int[] GetRepairOpTotalUsages() {
        return null;
    }

    /**
     * Export the evolution of the non-accepted, accepted and best solution
     * during the SA search.
     *
     * @throws java.io.IOException
     */
    public void ExportSAStatistics() throws IOException {

        // Print message
        System.out.println("\nExporting SA statistics...");

        // Export SA statistics
        FileWriter writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "saStats.csv");
        writer.write("temperature, time_s, non-accepted cost, accepted cost, best cost\n");
        for (String line : this.saStats) {
            writer.write(line + "\n");
        }
        writer.close();
    }

    /**
     * Exports the evolution of the feasibility violation penalties during the
     * search.
     *
     * @throws java.io.IOException
     */
    public void ExportPenaltyStatistics() throws IOException {

        // Print message
        System.out.println("\nExporting penalty statistics...");

        // Export penalty statistics
        FileWriter writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "penaltyStats.csv");
        writer.write("volume violation, weight violation, tw violation, duration violation, "
                + "accessibility violation, backorder limit violation, container violation, "
                + "home starting point violation \n");
        for (String line : this.penaltyStats) {
            writer.write(line + "\n");
        }
        writer.close();
    }

    /**
     * Exports the evolution of the feasibility violation existence during the
     * search.
     *
     * @throws IOException
     */
    public void ExportViolationStatistics() throws IOException {

        // Print message
        System.out.println("\nExporting violation statistics...");

        // Export violation statistics
        FileWriter writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "violationStats.csv");
        writer.write("volume violation, weight violation, tw violation, duration violation, "
                + "accessibility violation, backorder limit violation, container violation, "
                + "home starting point violation, feasible \n");
        for (String line : this.violationStats) {
            writer.write(line + "\n");
        }
        writer.close();
    }

    /**
     * Abstract function which exports the evolution of the operator weights and
     * usages during the search.
     *
     * @throws java.io.IOException
     */
    public abstract void ExportOperatorStatistics() throws IOException;

    /**
     * Abstract function which exports operator performance in terms of number
     * of times operators led to new best solution, improved current solution,
     * accepted solution, and unaccepted solution.
     *
     * @throws java.io.IOException
     */
    public abstract void ExportOperatorPerformance() throws IOException;

    /**
     * Exports the data in the ContainerTracker instance of the best schedule.
     *
     * @throws java.io.IOException
     */
    public void ExportContainerTracker() throws IOException {

        // Print message
        System.out.println("\nExporting container tracker...");

        // Initialize file writer
        FileWriter writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "containerTracker.csv");

        // Commas
        String threeCommas = ",,,";
        String xCommas = StringUtils.repeat(",", 3 + this.data.GetPhLength());

        // Export titles
        writer.write("container wids" + threeCommas);
        writer.write("container visits" + xCommas);
        writer.write("container number of visits" + threeCommas);
        writer.write("container violations" + xCommas);
        writer.write("container overflow cost attributions" + xCommas);
        writer.write("container volume loads" + xCommas);
        writer.write("container weight loads" + xCommas + "\n");

        // Export days
        String days = "";
        for (int i = 0; i < this.data.GetPhLength(); i++) {
            days += i + ",";
        }
        days += (this.data.GetPhLength() - 1) + "+";
        writer.write(threeCommas);
        writer.write(days + threeCommas);
        writer.write(threeCommas);
        writer.write(days + threeCommas);
        writer.write(days + threeCommas);
        writer.write(days + threeCommas);
        writer.write(days + threeCommas + "\n\n");

        // Export values
        for (Point container : this.data.GetContainers()) {
            writer.write(container.GetContWid() + threeCommas);
            writer.write(this.bestSchedule.GetContainerVisits(container).toString()
                    .substring(1, this.bestSchedule.GetContainerVisits(container).toString().length() - 1) + threeCommas);
            writer.write(this.bestSchedule.GetContainerNumVisits(container) + threeCommas);
            writer.write(this.bestSchedule.GetContainerViolations(container).toString()
                    .substring(1, this.bestSchedule.GetContainerViolations(container).toString().length() - 1) + threeCommas);
            writer.write(this.bestSchedule.GetOverflowCostAttr(container).toString()
                    .substring(1, this.bestSchedule.GetOverflowCostAttr(container).toString().length() - 1) + threeCommas);
            writer.write(this.bestSchedule.GetContainerVolumeLoads(container).toString()
                    .substring(1, this.bestSchedule.GetContainerVolumeLoads(container).toString().length() - 1) + threeCommas);
            writer.write(this.bestSchedule.GetContainerWeightLoads(container).toString()
                    .substring(1, this.bestSchedule.GetContainerWeightLoads(container).toString().length() - 1) + threeCommas + "\n");
        }

        // Close file writer
        writer.close();

    }

    /**
     * Export the evolution of the non-accepted, accepted and best solution
     * during the SA search.
     *
     * @throws java.io.IOException
     */
    public void ExportBestSolutionLog() throws IOException {

        // Print message
        System.out.println("\nExporting best solution log...");

        // Export
        FileWriter writer = new FileWriter(Parameters.csvExportFolder + this.prefix + "bestSolutionLog.csv");
        writer.write("iteration, best cost\n");
        for (String line : this.bestSolutionStats) {
            writer.write(line + "\n");
        }
        writer.close();
    }

    /**
     * Prints the point sequences for all tours in the solution.
     */
    public void PrintTours() {

        System.out.println("\nBest solution: ");
        for (int i = 0; i < this.bestSchedule.GetSize(); i++) {
            Tour tour = this.bestSchedule.GetTours().get(i);
            int day = tour.GetDay();
            int truckWid = tour.GetTruck().GetWid();
            int originWid = tour.GetOriginStartingPoint().GetDWid();
            int destinationWid = tour.GetDestinationStartingPoint().GetDWid();
            int numCont = tour.GetNumContainers();
            System.out.printf("%-6s Day: %-6s Truck: %-15s Origin: %-15s Destination: %-15s Num cont: %-7s %-6s %n",
                    i, day, truckWid, originWid, destinationWid, numCont, tour.GetAreWhat());
            if (i < this.bestSchedule.GetSize() - 1 && day != this.bestSchedule.GetTours().get(i + 1).GetDay()) {
                System.out.println("-------------");
            }
        }
    }

    /**
     * Exports the tour to a file.
     *
     * @throws java.io.IOException
     */
    public void ExportTours() throws IOException {

        // Print message
        System.out.println("\nExporting tours...");

        PrintWriter writer = new PrintWriter(new FileWriter(Parameters.csvExportFolder
                + this.prefix + Thread.currentThread().getId() + "_tours.txt"), true);
        for (int i = 0; i < this.bestSchedule.GetSize(); i++) {
            Tour tour = this.bestSchedule.GetTours().get(i);
            int day = tour.GetDay();
            int truckWid = tour.GetTruck().GetWid();
            int originWid = tour.GetOriginStartingPoint().GetDWid();
            int destinationWid = tour.GetDestinationStartingPoint().GetDWid();
            int numCont = tour.GetNumContainers();
            writer.printf("%-6s Day: %-6s Truck: %-15s Origin: %-15s Destination: %-15s Num cont: %-7s %-6s %n",
                    i, day, truckWid, originWid, destinationWid, numCont, tour.GetAreWhat());
            if (i < this.bestSchedule.GetSize() - 1 && day != this.bestSchedule.GetTours().get(i + 1).GetDay()) {
                writer.println("-------------");
            }
        }
    }

    /**
     * Exports plotting information to files.
     *
     * @throws IOException
     */
    public void ExportPlotInfo() throws IOException {

        // Print message
        System.out.println("\nExporting plot info...");

        // Write starting points, dumps and containers to file
        PrintWriter writer = new PrintWriter(new FileWriter(Parameters.plotExportFolder + this.prefix + "current.txt"), true);
        for (Point startingPoint : this.data.GetStartingPoints()) {
            writer.write("<" + startingPoint.GetLat() + "," + startingPoint.GetLon()
                    + ";SP " + startingPoint.GetDWid() + ":>\n");
        }
        for (Point dump : this.data.GetDumps()) {
            writer.write("<" + dump.GetLat() + "," + dump.GetLon()
                    + ";Dump " + dump.GetDWid() + ":>\n");
        }
        for (Point container : this.data.GetContainers()) {
            writer.write("<" + container.GetLat() + "," + container.GetLon()
                    + ";Container " + container.GetContWid() + ":Capacity " + container.GetVolume() + ">\n");
        }
        writer.close();

        // Write solution sequence to file
        writer = new PrintWriter(new FileWriter(Parameters.plotExportFolder + this.prefix + "solution.txt"), true);
        for (Tour tour : this.GetBestSchedule().GetTours()) {
            writer.write(">>>>");
            for (int i = 0; i < tour.GetSize(); i++) {
                if (i == tour.GetSize() - 1) {
                    writer.write(tour.GetDWids().get(i) + "");
                } else {
                    writer.write(tour.GetDWids().get(i) + "-");
                }
            }
            writer.write("\n");
        }
        writer.close();

        // Write simple sequence to file
        writer = new PrintWriter(new FileWriter(Parameters.plotExportFolder + this.prefix + "simpleseq.txt"), true);
        for (Tour tour : this.GetBestSchedule().GetTours()) {
            for (int i = 0; i < tour.GetSize(); i++) {
                writer.write(tour.GetDWids().get(i) + " ");
            }
            writer.write("\n");
        }
        writer.close();
    }

    /**
     * Prints simple descriptive statistics for the data.
     */
    public void PrintDataStatistics() {

        System.out.println("\nData statistics");
        System.out.printf("%-10s %-17s %-17s %-17s %-17s %n", "Flow", "Starting points", "Containers", "Dumps", "Trucks");
        int flowWid = this.data.GetFlowWid();
        int numStartingPoints = 0;
        int numContainers = 0;
        int numDumps = 0;
        int numTrucks = 0;
        for (Point startingPoint : this.data.GetStartingPoints()) {
            if (startingPoint.GetFlowWid() == flowWid) {
                numStartingPoints++;
            }
        }
        for (Point container : this.data.GetContainers()) {
            if (container.GetFlowWid() == flowWid) {
                numContainers++;
            }
        }
        for (Point dump : this.data.GetDumps()) {
            if (dump.GetFlowWid() == flowWid) {
                numDumps++;
            }
        }
        for (Truck truck : this.data.GetTrucks()) {
            if (truck.GetFlowWid() == flowWid) {
                numTrucks++;
            }
        }
        System.out.printf("%-10s %-17s %-17s %-17s %-17s %n", flowWid, numStartingPoints, numContainers, numDumps, numTrucks);
    }
}
