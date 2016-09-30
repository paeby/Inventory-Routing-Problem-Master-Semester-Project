package alns.algo;

import alns.param.Parameters;
import java.io.Serializable;

/**
 * Holds the penalties used for penalizing the various constraint violations
 * during the search process. Extends methods for retrieving and changing the
 * penalties.
 *
 * @author Markov
 * @version 1.0
 */
public class Penalties implements Serializable {

    // Penalty for truck maximum volume violation
    private double volumeViolPenalty;
    // Penalty for truck maximum weight violation
    private double weightViolPenalty;
    // Penalty for time window violation
    private double twViolPenalty;
    // Penalty for maximum duration violation
    private double durViolPenalty;
    // Penalty for accessibility violation
    private double accessViolPenalty;
    // Penalty for backorder limit violation
    private double backorderViolPenalty;
    // Penalty for container capacity violation (overflow)
    private double contViolPenalty;
    // Penalty for home depot violation
    private double homeDepotViolPenalty;

    /**
     * Assignment constructor.
     */
    public Penalties() {
        this.volumeViolPenalty = Parameters.hInitPenalty;
        this.weightViolPenalty = Parameters.hInitPenalty;
        this.twViolPenalty = Parameters.hInitPenalty;
        this.durViolPenalty = Parameters.hInitPenalty;
        this.accessViolPenalty = Parameters.hInitPenalty;
        this.backorderViolPenalty = Parameters.hInitPenalty;
        this.contViolPenalty = Parameters.hInitPenalty;
        this.homeDepotViolPenalty = Parameters.hInitPenalty;
    }

    /**
     * Resets penalties to default values.
     */
    public void ResetPenalties() {
        this.volumeViolPenalty = Parameters.hInitPenalty;
        this.weightViolPenalty = Parameters.hInitPenalty;
        this.twViolPenalty = Parameters.hInitPenalty;
        this.durViolPenalty = Parameters.hInitPenalty;
        this.accessViolPenalty = Parameters.hInitPenalty;
        this.backorderViolPenalty = Parameters.hInitPenalty;
        this.contViolPenalty = Parameters.hInitPenalty;
        this.homeDepotViolPenalty = Parameters.hInitPenalty;
    }

    /**
     * Returns truck maximum volume violation penalty.
     *
     * @return truck maximum volume violation penalty
     */
    public double GetVolumeViolPenalty() {
        return this.volumeViolPenalty;
    }

    /**
     * Returns truck maximum weight violation penalty.
     *
     * @return truck maximum weight violation penalty
     */
    public double GetWeightViolPenalty() {
        return this.weightViolPenalty;
    }

    /**
     * Returns time window violation penalty.
     *
     * @return time window violation penalty
     */
    public double GetTWViolPenalty() {
        return this.twViolPenalty;
    }

    /**
     * Returns maximum duration violation penalty.
     *
     * @return maximum duration violation penalty
     */
    public double GetDurViolPenalty() {
        return this.durViolPenalty;
    }

    /**
     * Returns accessibility violation penalty.
     *
     * @return accessibility violation penalty
     */
    public double GetAccessViolPenalty() {
        return this.accessViolPenalty;
    }

    /**
     * Returns backorder limit violation penalty.
     *
     * @return backorder limit violation penalty
     */
    public double GetBackorderViolPenalty() {
        return this.backorderViolPenalty;
    }

    /**
     * Returns container violation (overflow) penalty.
     *
     * @return container violation (overflow) penalty
     */
    public double GetContViolPenalty() {
        return this.contViolPenalty;
    }

    /**
     * Returns home depot violation penalty.
     *
     * @return home depot violation penalty
     */
    public double GetHomeDepotViolPenalty() {
        return this.homeDepotViolPenalty;
    }

    /**
     * Increases the truck maximum volume violation penalty by the penalty rate
     * factor.
     */
    public void IncreaseVolumeViolPenalty() {
        this.volumeViolPenalty = Math.min(Parameters.hMaxPenalty, this.volumeViolPenalty * Parameters.hPenaltyRate);
    }

    /**
     * Increases the truck maximum weight violation penalty by the penalty rate
     * factor.
     */
    public void IncreaseWeightViolPenalty() {
        this.weightViolPenalty = Math.min(Parameters.hMaxPenalty, this.weightViolPenalty * Parameters.hPenaltyRate);
    }

    /**
     * Increases the time window violation penalty by the penalty rate factor.
     */
    public void IncreaseTWViolPenalty() {
        this.twViolPenalty = Math.min(Parameters.hMaxPenalty, this.twViolPenalty * Parameters.hPenaltyRate);
    }

    /**
     * Increases the maximum duration violation penalty by the penalty rate
     * factor.
     */
    public void IncreaseDurViolPenalty() {
        this.durViolPenalty = Math.min(Parameters.hMaxPenalty, this.durViolPenalty * Parameters.hPenaltyRate);
    }

    /**
     * Increases the accessibility violation penalty by the penalty rate factor.
     */
    public void IncreaseAccessViolPenalty() {
        this.accessViolPenalty = Math.min(Parameters.hMaxPenalty, this.accessViolPenalty * Parameters.hPenaltyRate);
    }

    /**
     * Increases the backorder limit violation penalty by the penalty rate
     * factor.
     */
    public void IncreaseBackorderViolPenalty() {
        this.backorderViolPenalty = Math.min(Parameters.hMaxPenalty, this.backorderViolPenalty * Parameters.hPenaltyRate);
    }

    /**
     * Increases the container violation (overflow) penalty by the penalty rate
     * factor.
     */
    public void IncreaseContViolPenalty() {
        this.contViolPenalty = Math.min(Parameters.hMaxPenalty, this.contViolPenalty * Parameters.hPenaltyRate);
    }

    /**
     * Increases the home depot violation penalty by the penalty rate factor.
     */
    public void IncreaseHomeDepotViolPenalty() {
        this.homeDepotViolPenalty = Math.min(Parameters.hMaxPenalty, this.homeDepotViolPenalty * Parameters.hPenaltyRate);
    }

    /**
     * Reduces the truck maximum volume violation penalty by the penalty rate
     * factor.
     */
    public void ReduceVolumeViolPenalty() {
        this.volumeViolPenalty = Math.max(Parameters.hMinPenalty, this.volumeViolPenalty / Parameters.hPenaltyRate);
    }

    /**
     * Reduces the truck maximum weight violation penalty by the penalty rate
     * factor.
     */
    public void ReduceWeightViolPenalty() {
        this.weightViolPenalty = Math.max(Parameters.hMinPenalty, this.weightViolPenalty / Parameters.hPenaltyRate);
    }

    /**
     * Reduces the time window violation penalty by the penalty rate factor.
     */
    public void ReduceTWViolPenalty() {
        this.twViolPenalty = Math.max(Parameters.hMinPenalty, this.twViolPenalty / Parameters.hPenaltyRate);
    }

    /**
     * Reduces the maximum duration violation penalty by the penalty rate
     * factor.
     */
    public void ReduceDurViolPenalty() {
        this.durViolPenalty = Math.max(Parameters.hMinPenalty, this.durViolPenalty / Parameters.hPenaltyRate);
    }

    /**
     * Reduces the accessibility violation penalty by the penalty rate factor.
     */
    public void ReduceAccessViolPenalty() {
        this.accessViolPenalty = Math.max(Parameters.hMinPenalty, this.accessViolPenalty / Parameters.hPenaltyRate);
    }

    /**
     * Reduces the backorder limit penalty by the penalty rate factor.
     */
    public void ReduceBackorderViolPenalty() {
        this.backorderViolPenalty = Math.max(Parameters.hMinPenalty, this.backorderViolPenalty / Parameters.hPenaltyRate);
    }

    /**
     * Reduces the container violation (overflow) penalty by the penalty rate
     * factor.
     */
    public void ReduceContViolPenalty() {
        this.contViolPenalty = Math.max(Parameters.hMinPenalty, this.contViolPenalty / Parameters.hPenaltyRate);
    }

    /**
     * Reduces the home depot violation penalty by the penalty rate factor.
     */
    public void ReduceHomeDepotViolPenalty() {
        this.homeDepotViolPenalty = Math.max(Parameters.hMinPenalty, this.homeDepotViolPenalty / Parameters.hPenaltyRate);
    }
}
