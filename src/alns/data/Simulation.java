package alns.data;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Holds and manages simulation results from the demand scenario generation over
 * the final solution.
 *
 * @author Markov
 * @version 1.0
 */
public class Simulation {

    // Collections should be sorted to retrieve correct results
    private boolean collectionsAreSorted;

    // Arrays for holding statistics
    private final ArrayList<Integer> backorderViolationCounts;
    private final ArrayList<Double> backorderViolationValues;
    private final ArrayList<Integer> containerViolationCounts;
    private final ArrayList<Double> containerViolationValues;
    private final ArrayList<Integer> routeFailureCounts;
    private final ArrayList<Double> routeFailureValues;

    /**
     * Initialization constructor.
     */
    public Simulation() {

        this.collectionsAreSorted = false;
        this.backorderViolationCounts = new ArrayList<>();
        this.backorderViolationValues = new ArrayList<>();
        this.containerViolationCounts = new ArrayList<>();
        this.containerViolationValues = new ArrayList<>();
        this.routeFailureCounts = new ArrayList<>();
        this.routeFailureValues = new ArrayList<>();
    }

    /**
     * Sorts collections in ascending order.
     */
    private void sortCollections() {
        
        this.collectionsAreSorted = true;
        Collections.sort(this.backorderViolationCounts);
        Collections.sort(this.backorderViolationValues);
        Collections.sort(this.containerViolationCounts);
        Collections.sort(this.containerViolationValues);
        Collections.sort(this.routeFailureCounts);
        Collections.sort(this.routeFailureValues);
    }

    /**
     * Adds a backorder violation count (total number of backorders) for a
     * simulated demand scenario.
     *
     * @param backorderViolationCount backorder violation count
     */
    public void AddBackorderViolationCount(int backorderViolationCount) {
        this.backorderViolationCounts.add(backorderViolationCount);
    }

    /**
     * Adds a backorder violation value (total backorder amount) for a simulated
     * demand scenario.
     *
     * @param backorderViolationValue backorder violation value
     */
    public void AddBackOrderViolationValue(double backorderViolationValue) {
        this.backorderViolationValues.add(backorderViolationValue);
    }

    /**
     * Adds a container violation count (total number of container violations)
     * for a simulated demand scenario.
     *
     * @param containerViolationCount container violation count
     */
    public void AddContainerViolationCount(int containerViolationCount) {
        this.containerViolationCounts.add(containerViolationCount);
    }

    /**
     * Adds a container violation value (total container violation amount) for a
     * simulated demand scenario.
     *
     * @param containerViolationValue container violation value
     */
    public void AddContainerViolationValue(double containerViolationValue) {
        this.containerViolationValues.add(containerViolationValue);
    }

    /**
     * Adds a route failure count (total number of route failures) for a
     * simulated demand scenario.
     *
     * @param routeFailureCount route failure count
     */
    public void AddRouteFailureCount(int routeFailureCount) {
        this.routeFailureCounts.add(routeFailureCount);
    }

    /**
     * Adds a route failure value (total route failure amount) for a given
     * simulated demand scenario.
     *
     * @param routeFailureValue route failure value
     */
    public void AddRouteFailureValue(double routeFailureValue) {
        this.routeFailureValues.add(routeFailureValue);
    }

    /**
     * Returns the specified percentile of backorder violation counts from all
     * simulated scenarios.
     *
     * @param percentile percentile (0 to 100)
     * @return the specified percentile of backorder violation counts from all
     * simulated scenarios
     */
    public int GetPercentileBackorderViolationCounts(double percentile) {
        // Sort collections if not sorted
        if (!this.collectionsAreSorted) {
            this.sortCollections();
        }
        // If exists, return required statistic, otherwise return 0
        if (this.backorderViolationCounts.size() > 0) {
            percentile = Math.max(0.d, Math.min(100.d, percentile));
            int pos = (int) ((percentile / 100.d) * (this.backorderViolationCounts.size() - 1));
            return this.backorderViolationCounts.get(pos);
        } else {
            return 0;
        }
    }

    /**
     * Returns the specified percentile of backorder violation values from all
     * simulated scenarios.
     *
     * @param percentile percentile
     * @return the specified percentile of backorder violation values from all
     * simulated scenarios
     */
    public double GetPercentileBackorderViolationValues(double percentile) {
        // Sort collections if not sorted
        if (!this.collectionsAreSorted) {
            this.sortCollections();
        }
        // If exists, return required statistic, otherwise return 0
        if (this.backorderViolationValues.size() > 0) {
            percentile = Math.max(0.d, Math.min(100.d, percentile));
            int pos = (int) ((percentile / 100.d) * (this.backorderViolationValues.size() - 1));
            return this.backorderViolationValues.get(pos);
        } else {
            return 0.d;
        }
    }

    /**
     * Returns the specified percentile of container violation counts from all
     * simulated scenarios.
     *
     * @param percentile percentile
     * @return the specified percentile of container violation counts from all
     * simulated scenarios
     */
    public int GetPercentileContainerViolationCounts(double percentile) {
        // Sort collections if not sorted
        if (!this.collectionsAreSorted) {
            this.sortCollections();
        }
        // If exists, return required statistic, otherwise return 0
        if (this.containerViolationCounts.size() > 0) {
            percentile = Math.max(0.d, Math.min(100.d, percentile));
            int pos = (int) ((percentile / 100.d) * (this.containerViolationCounts.size() - 1));
            return this.containerViolationCounts.get(pos);
        } else {
            return 0;
        }
    }

    /**
     * Returns the specified percentile of container violation values from all
     * simulated scenarios.
     *
     * @param percentile percentile
     * @return the specified percentile of container violation values from all
     * simulated scenarios
     */
    public double GetPercentileContainerViolationValues(double percentile) {
        // Sort collections if not sorted
        if (!this.collectionsAreSorted) {
            this.sortCollections();
        }
        // If exists, return required statistic, otherwise return 0
        if (this.containerViolationValues.size() > 0) {
            percentile = Math.max(0.d, Math.min(100.d, percentile));
            int pos = (int) ((percentile / 100.d) * (this.containerViolationValues.size() - 1));
            return this.containerViolationValues.get(pos);
        } else {
            return 0.d;
        }
    }

    /**
     * Returns the specified percentile of route failure counts from all
     * simulated scenarios.
     *
     * @param percentile percentile
     * @return the specified percentile of route failure counts from all
     * simulated scenarios
     */
    public int GetPercentileRouteFailureCounts(double percentile) {
        // Sort collections if not sorted
        if (!this.collectionsAreSorted) {
            this.sortCollections();
        }
        // If exists, return required statistic, otherwise return 0
        if (this.routeFailureCounts.size() > 0) {
            percentile = Math.max(0.d, Math.min(100.d, percentile));
            int pos = (int) ((percentile / 100.d) * (this.routeFailureCounts.size() - 1));
            return this.routeFailureCounts.get(pos);
        } else {
            return 0;
        }
    }

    /**
     * Returns the specified percentile of route failure values from all
     * simulated scenarios.
     *
     * @param percentile percentile
     * @return the specified percentile of route failure values from all
     * simulated scenarios
     */
    public double GetPercentileRouteFailureValues(double percentile) {
        // Sort collections if not sorted
        if (!this.collectionsAreSorted) {
            this.sortCollections();
        }
        // If exists, return required statistic, otherwise return 0
        if (this.routeFailureValues.size() > 0) {
            percentile = Math.max(0.d, Math.min(100.d, percentile));
            int pos = (int) ((percentile / 100.d) * (this.routeFailureValues.size() - 1));
            return this.routeFailureValues.get(pos);
        } else {
            return 0.d;
        }
    }
}
