package alns.algo;

import alns.data.Data;
import alns.data.Point;
import alns.param.Parameters;
import alns.tour.Tour;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Decomposition framework for the waste collection IRP. This can be used as an
 * alternative to solving the IRP by directly initializing a SAALNS object and
 * calling its Run() method. This decomposition framework solves the cruder
 * multi-period TSP first for the original data and planning horizon and then
 * solves the IRP for a shorter planning horizon only for the containers that
 * were assigned by the TSP for this shorter planning horizon. Thus we avoid
 * solving the IRP for days that are relatively far in the future and thus have
 * a lot of uncertainly in terms of demand, and replace the assignments there by
 * the cruder measure given by the TSP assignments.
 *
 * @author Markov
 * @version 1.0
 */
public class Decomposition {

    // Data object
    private final Data data;

    // New planning horizon length
    private final int newPhLength;

    // Whether to collect statistics or not
    private final boolean collectStats;

    // SAALNS objects for the TSP and IRP
    private final SAALNS saalnsTSP;
    private final SAALNS saalnsIRP;

    /**
     * Assignment constructor.
     *
     * @param data Data object
     * @param newPhLength new planning horizon length
     * @param version whether to use SAALNSA or SAALNSB
     * @param collectStats whether to collect statistics or not
     */
    public Decomposition(Data data, int newPhLength, int version, boolean collectStats) {

        // Initialize data
        this.data = data;

        // Initialize new planning horizon length, which is the minimum
        // of the original and the new one
        this.newPhLength = Math.min(data.GetPhLength(), newPhLength);

        // Whether to collect statistics on the IRP or not
        this.collectStats = collectStats;

        // Initialize SAALNS objects for the TSP and IRP, based on the SAALNS version
        // that was passed to this constructor
        if (version == Parameters.hSAALNSA) {
            this.saalnsTSP = new SAALNSA(this.data, Parameters.hModeTSP, Parameters.defaultRunNb, Parameters.defaultTestValueID);
            this.saalnsIRP = new SAALNSA(this.data, Parameters.hModeIRP, Parameters.defaultRunNb, Parameters.defaultTestValueID);
        } else {
            this.saalnsTSP = new SAALNSB(this.data, Parameters.hModeTSP, Parameters.defaultRunNb, Parameters.defaultTestValueID);
            this.saalnsIRP = new SAALNSB(this.data, Parameters.hModeIRP, Parameters.defaultRunNb, Parameters.defaultTestValueID);
        }
    }

    /**
     * Removes from the data those containers that are not assigned by the TSP
     * to the days that fall within the new planning horizon length. Sets new
     * planning horizon length to the data object.
     */
    private void restrictData() {

        // HashSet holding the container wids that the TSP assigned on the days 
        // that fall within the new planning horizon length
        HashSet<Integer> containerWidsInNewPhLength = new HashSet<>();

        // Extract the container wids to populate the HashSet above
        for (Tour tour : this.saalnsTSP.GetBestSchedule().GetTours()) {
            if (tour.GetDay() < this.newPhLength) {
                containerWidsInNewPhLength.addAll(tour.GetContWids());
            }
        }

        // Remove from data those containers whose wids are not in the HashSet above
        for (Iterator<Point> it = this.data.GetContainers().iterator(); it.hasNext();) {
            Point container = it.next();
            if (!containerWidsInNewPhLength.contains(container.GetContWid())) {
                it.remove();
            }
        }

        // Set new planning horizon length to data object
        this.data.SetPhLength(this.newPhLength);
    }

    /**
     * Runs successively the ALNS on the TSP and IRP problem. The TSP problem is
     * solved for the original data and planning horizon length, while the IRP
     * problem is solved for the new planning horizon length and only for those
     * containers that the TSP assigned on the days that fall in the new
     * planning horizon length.
     *
     * @return true if the optimization was able to finish successfully and
     * false otherwise
     * @throws java.io.IOException
     */
    public boolean Run() throws IOException {

        // Flag indicating whether the run is successful or not
        boolean runSuccessful;

        // Decomposition framework start time
        long startTime = System.currentTimeMillis();

        // Solve TSP (we do not want to collect statistics on it)
        System.out.println("\nDecomposition framework: Step 1 TSP...");
        runSuccessful = this.saalnsTSP.Run(null,null);
        if (!runSuccessful) {
            System.err.println(Thread.currentThread().getStackTrace()[1].getClassName()
                    + "." + Thread.currentThread().getStackTrace()[1].getMethodName()
                    + "() says:");
            System.err.println(">> TSP optimization failed...");
            return false;
        }

        // Restrict data
        System.out.println("\nDecomposition framework: Step 2 Restricting data...");
        this.restrictData();
        System.out.println("After data restriction there are " + this.data.GetContainers().size() + " containers ");
        System.out.println("to collect over a planning horizon of " + this.data.GetPhLength() + " days.");

        // Solve IRP on restricted data
        // If the data object passes the verification test after restriction
        // run the SAALNS on the IRP, otherwise just run the construction method
        System.out.println("\nDecomposition framework: Step 3 IRP...");
        if (this.data.VerifyCompleteness() == true) {
            runSuccessful = this.saalnsIRP.Run(null, null);
            if (!runSuccessful) {
                System.err.println(Thread.currentThread().getStackTrace()[1].getClassName()
                        + "." + Thread.currentThread().getStackTrace()[1].getMethodName()
                        + "() says:");
                System.err.println(">> IRP optimization failed...");
                return false;
            }
        } else {
            runSuccessful = this.saalnsIRP.ConstructDummy();
            if (!runSuccessful) {
                System.err.println(Thread.currentThread().getStackTrace()[1].getClassName()
                        + "." + Thread.currentThread().getStackTrace()[1].getMethodName()
                        + "() says:");
                System.out.println(">> Construction of dummy IRP solution failed...");
                return false;
            }
        }

        // Decomposition framework end time
        long endTime = System.currentTimeMillis();

        // Print runtime message
        System.out.println("\nFinished decomposition framework in " + (endTime - startTime) / 1000.d + " seconds.");

        // If all the runs were successful, return true
        return true;
    }

    /**
     * Returns the SAALNS object holding the best TSP schedule. Can be used to
     * expose the schedule.
     *
     * @return the SAALNS object holding the best TSP schedule
     */
    public SAALNS GetSaalnsTSP() {
        return this.saalnsTSP;
    }

    /**
     * Returns the SAALNS object holding the best IRP schedule. Can be used to
     * expose the schedule.
     *
     * @return the SAALNS object holding the best IRP schedule
     */
    public SAALNS GetSaalnsIRP() {
        return this.saalnsIRP;
    }
}
