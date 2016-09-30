package alns.data;

import alns.param.Parameters;
import java.io.IOException;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * Sources the R code for forecasting using the Rserve TCP/IP server to interact
 * with R code. To set up Rserve, follow these steps: (1) Run
 * install.packages("Rserve") in R. (2) Add an environment variable R_HOME
 * pointing to the base directory of the R installation. (3) Add the location of
 * the x64 versions of Rscript.exe and Rserve.exe to the PATH variable. (4) Add
 * REngine.jar and Rserve.jar as compile time libraries. (5) Add the above
 * imports.
 *
 * @author Markov
 * @version 1.0
 */
public class Forecast {

    // Planning horizon length
    protected int phLength;

    // Rserve process
    protected Process RserveProc;

    // RConnection
    protected RConnection conn = null;

    // Error sigma is the standard deviation of the forecast residuals related to volume
    protected double errorSigma;

    /**
     * Initialization constructor.
     *
     * @param phLength planning horizon length
     * @throws RserveException
     * @throws java.io.IOException
     */
    public Forecast(int phLength) throws RserveException, IOException {

        // Initialize planning horizon length
        this.phLength = phLength;

        // Start RServe process
        System.out.println("Starting Rserve process...");
        Runtime runTime = Runtime.getRuntime();
        this.RserveProc = runTime.exec("Rserve");

        // Open RConnection
        System.out.println("Opening Rconnection...");
        this.conn = new RConnection();

        // Source the R script
        this.conn.eval("source('" + Parameters.forecastRFile + "')");
        // Open a database channel as a global variable in the R session
        this.conn.eval("open.connection()");
    }

    /**
     * Returns the standard deviation of the forecasting errors.
     *
     * @param flowWid flow type wid
     * @param clientWid client wid
     * @return the standard deviation of the forecasting errors.
     * @throws RserveException
     * @throws REXPMismatchException
     */
    public double GetErrorSigma(Integer flowWid, Integer clientWid) throws RserveException, REXPMismatchException {

        // Print message
        System.out.println("Extracting error sigma...");
        // Evaluate in R and assign to class field
        this.errorSigma = this.conn.eval("get.error.sigma(" + flowWid + ", " + clientWid + ")").asDouble();
        // Return class field
        return this.errorSigma;
    }

    /**
     * Returns the initial level (at the beginning of day = 0) of the passed
     * container wid.
     *
     * Note: See implementation in R file getInitLevel.R. Check if this is the
     * correct place where initial level is kept in the database.
     *
     * @param containerWid container wid
     * @return the initial level (at the beginning of day = 0) of the passed
     * container wid
     * @throws RserveException
     * @throws REXPMismatchException
     */
    public double GetInitLevel(int containerWid) throws RserveException, REXPMismatchException {

        // Print message
        System.out.println(" - Extracting initial level for container " + containerWid + "...");
        // Evaluate in R for container wid
        double initLevel = this.conn.eval("get.init.level(" + containerWid + ")").asDouble();
        // Return
        return initLevel;
    }

    /**
     * Returns forecast level demands for the passed container wid starting from
     * day = 0. If the R script returns -404, the code exits abruptly. This
     * should not happen under normal circumstances. If it happens, first check
     * if the weather and events tables are properly populated.
     *
     * @param containerWid container wid
     * @param ecologWid ecolog wid
     * @return forecast level demands for the passed container wid starting from
     * day = 0
     * @throws org.rosuda.REngine.Rserve.RserveException
     * @throws org.rosuda.REngine.REXPMismatchException
     */
    public double[] GetForecastLevelDemands(int containerWid, int ecologWid) throws RserveException, REXPMismatchException {

        // Print message
        System.out.println(" - Extracting forecast level demands for container " + containerWid + "...");
        // Evaluate in R for the planning horizon and container wid
        double[] forecastLevelDemands = this.conn.eval("get.forecast.level.demands(" + this.phLength + ", " + containerWid + ")").asDoubles();

        // If the forecast method in R returned -404, exit abruptly. This should never happen if the database
        // contains all information for the weather and events
        if (forecastLevelDemands[0] == Parameters._404) {
            System.err.println(">>>> Received -404 from R forecasting function. Don't know what to do...");
            System.exit(1);
        }

        // Return
        return forecastLevelDemands;
    }

    /**
     * Returns an unconditional probability of the type Pr(X >= a), where X is
     * distributed as N(0, error.sigma^2).
     *
     * @param containerWid container wid
     * @param a value of a
     * @return an unconditional probability of the type Pr(X >= a), where X is
     * distributed as N(0, error.sigma^2)
     * @throws org.rosuda.REngine.Rserve.RserveException
     * @throws org.rosuda.REngine.REXPMismatchException
     */
    public double GetUnconditionalProbability(int containerWid, double a) throws RserveException, REXPMismatchException {

        // Print message
        System.out.println(" - Extracting unconditional probability for container " + containerWid + "...");
        // Evaluate in R for the passed a
        double unconditionalProbability = this.conn.eval("get.uncond.probability(" + a + ", " + this.errorSigma + ")").asDouble();
        // Return
        return unconditionalProbability;
    }

    /**
     * Returns a conditional probability of the type Pr(X + Y >= a -
     * forecastVolumeDemand_h | X < a), where X is distributed as N(0, h_minus_k
     * error.sigma^2) and Y is distributed as N(0, error.sigma^2).
     *
     * @param containerWid container wid
     * @param a value of a
     * @param forecastVolumeDemand_h container forecast volume demand on day h
     * @param h_minus_k value of day h minus day k (see Point class)
     * @return a conditional probability of the type Pr(X + Y >= a -
     * forecastVolumeDemand_h | X < a), where X is distributed as N(0, h_minus_k
     * error.sigma^2) and Y is distributed as N(0, error.sigma^2)
     *
     * @throws org.rosuda.REngine.Rserve.RserveException
     * @throws org.rosuda.REngine.REXPMismatchException
     */
    public double GetConditionalProbability(int containerWid, double a, double forecastVolumeDemand_h, double h_minus_k)
            throws RserveException, REXPMismatchException {

        // Print message
        System.out.println(" - Extracting conditional probability for container " + containerWid + "...");
        // Evaluate in R for the passed a, forecast volume demand on day = h, and value of day h minus day k
        double conditionalProbability = this.conn.eval("get.cond.probability(" + a + ", " + forecastVolumeDemand_h + ", " + h_minus_k + ", " + this.errorSigma + ")").asDouble();
        // Return 
        return conditionalProbability;
    }

    /**
     * Returns the planning horizon length.
     *
     * @return the planning horizon length
     */
    public int GetPhLength() {
        return this.phLength;
    }

    /**
     * Performs cleanup by closing the RConnection and killing the Rserve
     * process.
     *
     * @throws org.rosuda.REngine.Rserve.RserveException
     */
    public void Close() throws RserveException {

        // Close the database channel in the R session
        this.conn.eval("close.connection()");

        // Close RConnection
        System.out.println("Closing RConnection...");
        this.conn.close();

        // Kill the RServe process
        System.out.println("Killing Rserve process...");
        this.RserveProc.destroy();
    }
}
