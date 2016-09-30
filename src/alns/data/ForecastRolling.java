package alns.data;

import java.io.IOException;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * Implements only the probability functions for the rolling horizon approach.
 *
 * @author Markov
 */
public class ForecastRolling extends Forecast {

    /**
     * Initialization constructor. Here the error sigma is provided to the
     * constructor in order to avoid accessing the database.
     *
     * @param phLength planning horizon length
     * @param errorSigma error sigma
     * @throws IOException
     * @throws RserveException
     */
    public ForecastRolling(int phLength, double errorSigma) throws IOException, RserveException {

        // Call to superclass constructor
        super(phLength);

        // Assign error sigma, now coming from the 
        this.errorSigma = errorSigma;
    }

    /**
     * Returns an unconditional probability of the type Pr(X >= a), where X is
     * distributed as N(0, error.sigma^2). Here we call an alternative function
     * in R in order to avoid accessing the database.
     *
     * @param containerWid container wid
     * @param a value of a
     * @return an unconditional probability of the type Pr(X >= a), where X is
     * distributed as N(0, error.sigma^2)
     * @throws org.rosuda.REngine.Rserve.RserveException
     * @throws org.rosuda.REngine.REXPMismatchException
     */
    @Override
    public double GetUnconditionalProbability(int containerWid, double a) throws RserveException, REXPMismatchException {

        // Print message
        System.out.println(" - Extracting unconditional probability for container " + containerWid + "...");
        // Evaluate in R for the passed a
        double unconditionalProbability = this.conn.eval("get.uncond.probability.rolling(" + a + ", " + this.errorSigma + ")").asDouble();
        // Return
        return unconditionalProbability;
    }

    /**
     * Returns a conditional probability of the type Pr(X + Y >= a -
     * forecastVolumeDemand_h | X < a), where X is distributed as N(0, h_minus_k
     * error.sigma^2) and Y is distributed as N(0, error.sigma^2). Here we call
     * an alternative function in R in order to avoid accessing the database.
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
    @Override
    public double GetConditionalProbability(int containerWid, double a, double forecastVolumeDemand_h, double h_minus_k)
            throws RserveException, REXPMismatchException {

        // Print message
        System.out.println(" - Extracting conditional probability for container " + containerWid + "...");
        // Evaluate in R for the passed a, forecast volume demand on day = h, and value of day h minus day k
        double conditionalProbability = this.conn.eval("get.cond.probability.rolling(" + a + ", " + forecastVolumeDemand_h + ", " + h_minus_k + ", " + this.errorSigma + ")").asDouble();
        // Return 
        return conditionalProbability;
    }
}
