package alns.data;

import alns.param.Parameters;
import java.io.Serializable;
import java.util.ArrayList;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * Represents a point, ie starting point, container or dump and extends getter
 * methods for all and setter methods for some of its characteristics.
 *
 * @author Markov
 * @version 1.0
 */
public class Point implements Serializable {

    // POINT STATIC DATA
    // Artificial data for algorithmic purposes
    private int simpleDWid;                 // point simple distance matrix wid
    private int simpleContWid;              // point simple container wid

    // Descriptive data
    private final short isWhat;             // point type: starting point, container or dump
    private final int dWid;                 // point distance matrix wid 
    private final double lat;               // point latitude
    private final double lon;               // point longitude
    private final double servDur;           // point service duration in hours
    private final double twLower;           // point time window lower bound in hours since midnight
    private final double twUpper;           // point time window upper bound in hours since midnight
    private final int clientWid;            // point client wid
    private final int zoneWid;              // point zone wid
    private final int flowWid;              // point flow type wid
    private final double flowSpecWeight;    // point flow type specific weight in kg/m3
    private final int contWid;              // point container wid 
    private final int contTypeWid;          // point container type wid
    private final int ecologWid;            // point ecolog wid
    private final double volume;            // point volume in liters
    private final String location;          // point location description

    // Inventory holding and inventory shortage cost per day
    private final double inventoryHoldingCost;
    private final double inventoryShortageCost;

    // Closest dump and back and forth distance to it
    private Point closestDump = null;
    private double closestDumpBFDistance = 0.d;

    // Accessibity data: list of wids of trucks that can visit this point
    // If empty all trucks can visit it
    private final ArrayList<Integer> truckWidList;

    // Demand data
    private double initLevel = 0.d;                 // point initial level at the beginning of day = 0
    private double initVolumeLoad = 0.d;            // point initial volume load at the beginning of day = 0
    private double initWeightLoad = 0.d;            // point initial weight load at the beginning of day = 0
    private double[] forecastLevelDemands;          // point forecast levels demands starting on day = 0
    private double[] forecastVolumeDemands;         // point forecast volume demands (level * volume) starting on day = 0
    private double[] forecastWeightDemands;         // point forecast weight demands (level * volume * flow specific weight) starting on day = 0

    // Probability data
    private double unconditionalStartInv = 0.d;     // Unconditional probability with starting inventory
    private double[] unconditionalZeroInv;          // Array of unconditional probabilities with zero inventory
    private double[] conditionalStartInv;           // Array of conditional probabilities with starting inventory
    private double[][] conditionalZeroInv;          // Matrix of conditional probabilities with zero inventory

    // POINT DYNAMIC (DECISION) DATA
    private double sst = 0;     // Start-of-service time at the point in hours since midnight
    private double wTime = 0;   // Waiting time to service the point in hours

    /**
     * Assignment constructor.
     *
     * @param isWhat point type: starting point, container or dump
     * @param dWid point distance matrix wid
     * @param lat point latitude
     * @param lon point longitude
     * @param servDur point service duration in hours
     * @param twLower point time window lower bound in hours since midnight
     * @param twUpper point time window upper bound in hours since midnight
     * @param clientWid point client wid
     * @param zoneWid point zone wid
     * @param flowWid point flow type wid
     * @param flowSpecWeight point flow type specific weight in kg/m3
     * @param contWid point container wid
     * @param contTypeWid point container type wid
     * @param ecologWid point ecolog wid
     * @param volume point volume in liters
     * @param location point location description
     * @param inventoryHoldingCost point inventory holding cost per day
     * @param inventoryShortageCost point inventory shortage cost per day
     * @param truckWidList ArrayList reference of wids of trucks that can visit
     * this point
     * @param forecast Forecast reference
     *
     * @throws org.rosuda.REngine.Rserve.RserveException
     * @throws org.rosuda.REngine.REXPMismatchException
     */
    public Point(short isWhat, int dWid, double lat, double lon, double servDur,
            double twLower, double twUpper, int clientWid, int zoneWid, int flowWid, double flowSpecWeight,
            int contWid, int contTypeWid, int ecologWid, double volume, String location,
            double inventoryHoldingCost, double inventoryShortageCost,
            ArrayList<Integer> truckWidList, Forecast forecast) throws RserveException, REXPMismatchException {

        // Initialize descriptive data
        this.isWhat = isWhat;
        this.dWid = dWid;
        this.lat = lat;
        this.lon = lon;
        this.servDur = servDur;
        this.twLower = twLower;
        this.twUpper = twUpper;
        this.clientWid = clientWid;
        this.zoneWid = zoneWid;
        this.flowWid = flowWid;
        this.flowSpecWeight = flowSpecWeight;
        this.contWid = contWid;
        this.contTypeWid = contTypeWid;
        this.ecologWid = ecologWid;
        this.volume = volume;
        this.location = location;
        this.inventoryHoldingCost = inventoryHoldingCost;
        this.inventoryShortageCost = inventoryShortageCost;

        // Accessibility data 
        this.truckWidList = truckWidList;

        // Initial level, initial volume load, and initial weight load 
        // need to be initialized and are later copied by value in the copy constructor
        this.extractInit(forecast);
        // Forecast level demands, volume demands, and weight demands need to be initialized 
        // as arrays but are later only copied by value of reference in the copy constructor
        this.extractForecast(forecast);
        // Unconditional and conditional probabilities need to be initialized 
        // as arrays but are later only copied by value of reference in the copy constructor
        this.extractProbabilities(forecast);
    }

    /**
     * Alternative assignment constructor for the benchmark instances.
     *
     * @param isWhat point type: starting point, container or dump
     * @param contWid point container wid
     * @param dWid point distance matrix wid
     * @param lat point latitude
     * @param lon point longitude
     * @param servDur point service duration in hours
     * @param twLower point time window lower bound in hours since midnight
     * @param twUpper point time window upper bound in hours since midnight
     * @param volume point volume in liters
     * @param inventoryHoldingCost point inventory holding cost per day
     * @param inventoryShortageCost point inventory shortage cost per day
     * @param initVolumeLoad point initial volume load
     * @param phLength planning horizon length
     * @param truckWidList ArrayList reference of wids of trucks that can visit
     * this point
     * @param volumeDemands point volume demands for the planning horizon
     */
    public Point(short isWhat, int contWid, int dWid, double lat, double lon,
            double servDur, double twLower, double twUpper, double volume,
            double inventoryHoldingCost, double inventoryShortageCost,
            double initVolumeLoad, int phLength, ArrayList<Integer> truckWidList, ArrayList<Double> volumeDemands) {

        // Initialize descriptive data
        this.isWhat = isWhat;
        this.contWid = contWid;
        this.dWid = dWid;
        this.lat = lat;
        this.lon = lon;
        this.servDur = servDur;
        this.twLower = twLower;
        this.twUpper = twUpper;
        this.clientWid = Parameters._404;
        this.zoneWid = Parameters._404;
        this.flowWid = Parameters._404;
        this.flowSpecWeight = 1.d;
        this.contTypeWid = Parameters._404;
        this.ecologWid = Parameters._404;
        this.volume = volume;
        this.location = Parameters.emptyString;
        this.inventoryHoldingCost = inventoryHoldingCost;
        this.inventoryShortageCost = inventoryShortageCost;

        // Accessibility data
        this.truckWidList = truckWidList;

        // The initial level is irrelevant
        // The initial volume load and weight load are equal
        this.initLevel = 0.d;
        this.initVolumeLoad = initVolumeLoad;
        this.initWeightLoad = initVolumeLoad;

        // The forecast level demands are irrelevant
        // The forecast volume and weight demands are equal and are initialized
        // only if a non-null volumeDemands object is passed to constructor
        this.forecastLevelDemands = null;
        if (volumeDemands != null) {
            this.forecastVolumeDemands = new double[volumeDemands.size()];
            this.forecastWeightDemands = new double[volumeDemands.size()];
            for (int i = 0; i < volumeDemands.size(); i++) {
                this.forecastVolumeDemands[i] = volumeDemands.get(i);
                this.forecastWeightDemands[i] = volumeDemands.get(i);
            }
        }

        // Probabilities are irrelevant
        this.unconditionalStartInv = 0.d;
        this.unconditionalZeroInv = new double[phLength + 1];
        this.conditionalStartInv = new double[phLength + 1];
        this.conditionalZeroInv = new double[phLength + 1][phLength + 1];
    }

    /**
     * Copy constructor.
     *
     * @param point Point reference
     */
    public Point(Point point) {

        // Copy artificial data by values
        this.simpleDWid = point.simpleDWid;
        this.simpleContWid = point.simpleContWid;

        // Copy descriptive data by values
        this.isWhat = point.isWhat;
        this.dWid = point.dWid;
        this.lat = point.lat;
        this.lon = point.lon;
        this.servDur = point.servDur;
        this.twLower = point.twLower;
        this.twUpper = point.twUpper;
        this.clientWid = point.clientWid;
        this.zoneWid = point.zoneWid;
        this.flowWid = point.flowWid;
        this.flowSpecWeight = point.flowSpecWeight;
        this.contWid = point.contWid;
        this.contTypeWid = point.contTypeWid;
        this.ecologWid = point.ecologWid;
        this.volume = point.volume;
        this.location = point.location;
        this.inventoryHoldingCost = point.inventoryHoldingCost;
        this.inventoryShortageCost = point.inventoryShortageCost;

        // Copy closest dump and back and forth distance to it 
        this.closestDump = point.closestDump;
        this.closestDumpBFDistance = point.closestDumpBFDistance;

        // Copy accessibility data by value of reference
        this.truckWidList = point.truckWidList;

        // Copy initial level, initial volume demand and initial weight demand by value
        this.initLevel = point.initLevel;
        this.initVolumeLoad = point.initVolumeLoad;
        this.initWeightLoad = point.initWeightLoad;
        // Copy forecast level demands, volume demands, and weight demands by value of reference
        this.forecastLevelDemands = point.forecastLevelDemands;
        this.forecastVolumeDemands = point.forecastVolumeDemands;
        this.forecastWeightDemands = point.forecastWeightDemands;
        // Copy unconditional and conditional probabilities by value of reference
        this.unconditionalStartInv = point.unconditionalStartInv;
        this.unconditionalZeroInv = point.unconditionalZeroInv;
        this.conditionalStartInv = point.conditionalStartInv;
        this.conditionalZeroInv = point.conditionalZeroInv;

        // Copy dynamic (decision) data by value
        this.sst = point.sst;
        this.wTime = point.wTime;
    }

    /**
     * Extracts the initial point information, including initial level, initial
     * volume load and initial weight load at the beginning of day = 0.
     *
     * @param forecast Forecast reference
     * @throws RserveException
     * @throws REXPMismatchException
     */
    private void extractInit(Forecast forecast) throws RserveException, REXPMismatchException {

        // Extract only if the point is a container
        if (this.isWhat == Parameters.pointIsContainer) {
            // Extract initial level
            this.initLevel = forecast.GetInitLevel(this.contWid);

            // Extract initial volume load (level * volume)
            // The level needs to be divided by 100 before multiplying by volume
            this.initVolumeLoad = (this.initLevel / 100.d) * this.volume;

            // Extract initial weight load (level * volume * flow specific weight) 
            // The level needs to be divided by 100 before multiplying by volume
            // A conversion factor is used to synchronize the measurement units, 
            // as volume is measured in liters and flow specific weight in kg per m3.
            this.initWeightLoad = (this.initLevel / 100.d) * this.volume * this.flowSpecWeight / Parameters.flowSpecWeightCF;
        }
    }

    /**
     * Extracts forecast point information, including forecast level demands,
     * volume demands and weight demands for each day of the planning horizon,
     * starting from day = 0.
     *
     * @param forecast Forecast reference
     * @throws RserveException
     * @throws REXPMismatchException
     */
    private void extractForecast(Forecast forecast) throws RserveException, REXPMismatchException {

        // Extract only if the point is a container
        if (this.isWhat == Parameters.pointIsContainer) {
            // Call the level forecasting function
            this.forecastLevelDemands = forecast.GetForecastLevelDemands(this.contWid, this.ecologWid);

            // Initialize array
            this.forecastVolumeDemands = new double[this.forecastLevelDemands.length];
            // Calculate volume demands based on level demands and the container volume (capacity)
            for (int i = 0; i < this.forecastLevelDemands.length; i++) {
                // The level demand needs to be divided by 100 before multiplying by volume
                this.forecastVolumeDemands[i] = (this.forecastLevelDemands[i] / 100.d) * this.volume;
            }

            // Initialize array
            this.forecastWeightDemands = new double[this.forecastLevelDemands.length];
            // Calculate weight demands based on level demands, container volume (capacity) and the flow specific weight
            for (int i = 0; i < this.forecastLevelDemands.length; i++) {
                // The level demand needs to be divided by 100 before multiplying by volume
                this.forecastWeightDemands[i] = (this.forecastLevelDemands[i] / 100.d) * this.volume * this.flowSpecWeight / Parameters.flowSpecWeightCF;
            }
        }
    }

    /**
     * Extracts all unconditional and conditional probabilities that are used
     * for constructing the probability tree used for calculating the
     * probability of overflow on each day of the planning horizon (see class
     * Tree).
     *
     * @param forecast Forecast reference
     * @throws RserveException
     * @throws REXPMismatchException
     */
    private void extractProbabilities(Forecast forecast) throws RserveException, REXPMismatchException {

        // Extract only if the point is a container 
        if (this.isWhat == Parameters.pointIsContainer) {

            // Initialize the arrays for unconditional and conditional probabilities
            this.unconditionalZeroInv = new double[forecast.GetPhLength() + 1];
            this.conditionalStartInv = new double[forecast.GetPhLength() + 1];
            this.conditionalZeroInv = new double[forecast.GetPhLength() + 1][forecast.GetPhLength() + 1];

            // Populate unconditional probability of the type Pr(initVolumeLoad + forecastVolumeDemands[0] >= volume)
            double a1 = this.volume - this.initVolumeLoad - this.forecastVolumeDemands[0];
            this.unconditionalStartInv = forecast.GetUnconditionalProbability(this.contWid, a1);

            // Populate unconditional probabilities of the type Pr(0 + forecastVolumeDemand[h] >= volume) for all h
            for (int h = 0; h < forecast.GetPhLength(); h++) {
                double a2 = this.volume - this.forecastVolumeDemands[h];
                // The probability is assigned to h + 1, which is the day of the affected node
                this.unconditionalZeroInv[h + 1] = forecast.GetUnconditionalProbability(this.contWid, a2);
            }

            // Populate conditional probabilities of the type Pr(initVolumeLoad + sum_{i = 0}^h forecastVolumeDemands[i] >= volume 
            // | initVolumeLoad + sum_{i = 0}^{h - 1} forecastVolumeDemands[i] < volume) for all h
            double a3 = this.volume - this.initVolumeLoad;
            // The h loop starts from 1, as there are no conditional probabilities for h = 0
            for (int h = 1; h < forecast.GetPhLength(); h++) {
                a3 -= this.forecastVolumeDemands[h - 1];
                // The probability is assigned to h + 1, which is the day of the affected node
                this.conditionalStartInv[h + 1] = forecast.GetConditionalProbability(this.contWid, a3, this.forecastVolumeDemands[h], h);
            }

            // Populate probabilities of the type Pr(0 + sum_{i = k}^h forecastVolumeDemands[i] >= volume 
            // | 0 + sum_{i = k}^{h - 1} forecastVolumeDemands[i] < volume) for all h and k < h
            // The k loop starts from 0, and k is always smaller than h
            // The value of k is a day which corresponds to a state of overflow and so the conditionality is reset
            for (int k = 0; k < forecast.GetPhLength() - 1; k++) {
                double a4 = this.volume;
                // The h loop starts from 1, as there are no conditional probabilities for h = 0
                for (int h = k + 1; h < forecast.GetPhLength(); h++) {
                    a4 -= this.forecastVolumeDemands[h - 1];
                    // The probability is assigned to h + 1, which is the day of the affected node
                    this.conditionalZeroInv[h + 1][k] = forecast.GetConditionalProbability(this.contWid, a4, this.forecastVolumeDemands[h], h - k);
                }
            }
        }
    }

    /**
     * Sets point initial level, initial volume load and initial weight load.
     *
     * @param initLevel initial level in percent
     */
    public void SetInit(double initLevel) {

        // If the point is a container, set initial level, volume load and weight load
        if (this.isWhat == Parameters.pointIsContainer) {
            this.initLevel = initLevel;
            this.initVolumeLoad = (this.initLevel / 100.d) * this.volume;
            this.initWeightLoad = (this.initLevel / 100.d) * this.volume * this.flowSpecWeight / Parameters.flowSpecWeightCF;
        }
    }

    /**
     * Sets point forecast level demands, forecast volume demands and forecast
     * weight demands.
     *
     * @param levelDemands forecast level demands in percent over the planning
     * horizon
     */
    public void SetLevelDemands(double[] levelDemands) {

        // If the point is a container, set forecast level demands, volume demands and weight demands
        if (this.isWhat == Parameters.pointIsContainer) {
            this.forecastLevelDemands = new double[levelDemands.length];
            for (int i = 0; i < levelDemands.length; i++) {
                this.forecastLevelDemands[i] = levelDemands[i];
            }
            this.forecastVolumeDemands = new double[this.forecastLevelDemands.length];
            for (int i = 0; i < this.forecastLevelDemands.length; i++) {
                this.forecastVolumeDemands[i] = (this.forecastLevelDemands[i] / 100.d) * this.volume;
            }
            this.forecastWeightDemands = new double[this.forecastLevelDemands.length];
            for (int i = 0; i < this.forecastLevelDemands.length; i++) {
                this.forecastWeightDemands[i] = (this.forecastLevelDemands[i] / 100.d) * this.volume * this.flowSpecWeight / Parameters.flowSpecWeightCF;
            }
        }
    }

    /**
     * Sets all unconditional and conditional probabilities that are used for
     * constructing the probability tree used for calculating the probability of
     * overflow on each day of the planning horizon (see class Tree).
     *
     * @param forecast Forecast reference
     * @throws org.rosuda.REngine.Rserve.RserveException
     * @throws org.rosuda.REngine.REXPMismatchException
     */
    public void SetProbabilities(Forecast forecast) throws RserveException, REXPMismatchException {

        // Extract probabilities
        this.extractProbabilities(forecast);
    }

    /**
     * Returns point simple dWid used for more efficient implementation of Java
     * distance matrix representation and access.
     *
     * @return point simple dWid
     */
    public int GetSimpleDWid() {
        return this.simpleDWid;
    }

    /**
     * Sets the point simple dWid used for more efficient implementation of Java
     * distance matrix representation and access.
     *
     * @param simpleDWid the point simple dWid
     */
    void SetSimpleDWid(int simpleDWid) {
        this.simpleDWid = simpleDWid;
    }

    /**
     * Returns point simple container wid used for more efficient representation
     * and tracking of container visits
     *
     * @return point simple container wid
     */
    public int GetSimpleContWid() {
        return this.simpleContWid;
    }

    /**
     * Sets point simple container wid used for more efficient representation
     * and tracking of container visits
     *
     * @param simpleContWid point simple container wid
     */
    void SetSimpleContWid(int simpleContWid) {
        this.simpleContWid = simpleContWid;
    }

    /**
     * Returns point type: starting point, container or dump
     *
     * @return point type: starting point, container or dump
     */
    public short Is() {
        return this.isWhat;
    }

    /**
     * Returns point wid as used in the database distance matrix, in the case of
     * container this is the ecopoint wid
     *
     * @return point wid as used in the database distance matrix
     */
    public int GetDWid() {
        return this.dWid;
    }

    /**
     * Returns point latitude
     *
     * @return point latitude
     */
    public double GetLat() {
        return this.lat;
    }

    /**
     * Returns point longitude
     *
     * @return point longitude
     */
    public double GetLon() {
        return this.lon;
    }

    /**
     * Returns point service duration in hours
     *
     * @return point service duration in hours
     */
    public double GetServiceDuration() {
        return this.servDur;
    }

    /**
     * Returns point time window lower bound in hours since midnight
     *
     * @return point time window lower bound in hours since midnight
     */
    public double GetTWLower() {
        return this.twLower;
    }

    /**
     * Returns point time window upper bound in hours since midnight
     *
     * @return point time window upper bound in hours since midnight
     */
    public double GetTWUpper() {
        return this.twUpper;
    }

    /**
     * Returns point client wid
     *
     * @return point client wid
     */
    public int GetClientWid() {
        return this.clientWid;
    }

    /**
     * Returns point zone wid
     *
     * @return point zone wid
     */
    public int GetZoneWid() {
        return this.zoneWid;
    }

    /**
     * Returns point flow type wid
     *
     * @return point flow type wid
     */
    public int GetFlowWid() {
        return this.flowWid;
    }

    /**
     * Returns point flow type specific weight in kg/m3 in case the point is a
     * container; otherwise this s a default value
     *
     * @return point flow type specific weight in kg/m3
     */
    public double GetFlowSpecWeight() {
        return this.flowSpecWeight;
    }

    /**
     * Returns point container wid in case the point is a container; otherwise
     * this is a default value
     *
     * @return point container wid
     */
    public int GetContWid() {
        return this.contWid;
    }

    /**
     * Returns point container type wid in case the point is a container;
     * otherwise this is a default value
     *
     * @return point container type wid
     */
    public int GetContTypeWid() {
        return this.contTypeWid;
    }

    /**
     * Returns point ecolog wid in case the point is a container; otherwise this
     * is a default value
     *
     * @return point ecolog wid
     */
    public int GetEcologWid() {
        return this.ecologWid;
    }

    /**
     * Returns point effective volume in liters in case the point is a
     * container; otherwise this is a default value
     *
     * @return point effective volume in liters
     */
    public double GetEffectiveVolume() {
        return this.volume * Parameters.policyContainerEffectiveVolumeFraction;
    }

    /**
     * Returns point volume in liters in case the point is a container;
     * otherwise this is a default value
     *
     * @return point volume in liters
     */
    public double GetVolume() {
        return this.volume;
    }

    /**
     * Returns point location description
     *
     * @return point location description
     */
    public String GetLocation() {
        return this.location;
    }

    /**
     * Return point inventory holding cost per day
     *
     * @return point inventory holding cost per day
     */
    public double GetInventoryHoldingCost() {
        return this.inventoryHoldingCost;
    }

    /**
     * Return point inventory shortage cost per day
     *
     * @return point inventory shortage cost per day
     */
    public double GetInventoryShortageCost() {
        return this.inventoryShortageCost;
    }

    /**
     * Sets the closest dump and the back and forth distance to the closest dump
     * in km.
     *
     * @param closestDump closest dump
     * @param closestDumpBFDistance back and forth distance to the closest dump
     */
    public void SetContainerClosestDumpAndBFDistance(Point closestDump, double closestDumpBFDistance) {
        this.closestDump = closestDump;
        this.closestDumpBFDistance = closestDumpBFDistance;
    }

    /**
     * Returns the closest dump if the point is a container, null otherwise.
     *
     * @return the closest dump if the point is a container, null otherwise
     */
    public Point GetClosestDump() {
        return this.closestDump;
    }

    /**
     * Returns the back and forth distance to the closest dump in km.
     *
     * @return the back and forth distance to the closest dump in km
     */
    public double GetClosestDumpBFDistance() {
        return this.closestDumpBFDistance;
    }

    /**
     * Sets point start-of-service time in hours since midnight
     *
     * @param sst point start-of-service time in hours since midnight
     */
    public void SetSST(double sst) {
        this.sst = sst;
    }

    /**
     * Returns point start-of-service time in hours since midnight
     *
     * @return point start of service time in hours since midnight
     */
    public double GetSST() {
        return this.sst;
    }

    /**
     * Sets waiting time to service the point in hours
     *
     * @param wTime waiting time to service the point in hours
     */
    public void SetWaitingTime(double wTime) {
        this.wTime = wTime;
    }

    /**
     * Returns waiting time to service the point in hours
     *
     * @return waiting time to service the point in hours
     */
    public double GetWaitingTime() {
        return this.wTime;
    }

    /**
     * Returns true if the point is accessible by the passed truck, false
     * otherwise.
     *
     * @param truck truck to check accessibility for
     * @return true if the point is accessible by the passed truck, false
     * otherwise
     */
    public boolean IsAccessibleBy(Truck truck) {
        if (this.truckWidList == null || this.truckWidList.isEmpty()) {
            return true;
        } else {
            return this.truckWidList.contains(truck.GetWid());
        }
    }

    /**
     * Returns point initial level in percent.
     *
     * @return point initial level in percent
     */
    public double GetInitLevel() {
        return this.initLevel;
    }

    /**
     * Returns point initial volume load in liters
     *
     * @return point initial volume load in liters
     */
    public double GetInitVolumeLoad() {
        return this.initVolumeLoad;
    }

    /**
     * Returns point initial weight load in kg
     *
     * @return point initial weight load in kg
     */
    public double GetInitWeightLoad() {
        return this.initWeightLoad;
    }

    /**
     * Returns point forecast level demand in percent on day d.
     *
     * @param d day for which the point level demand is returned
     * @return point level demand in liters
     */
    public double GetForecastLevelDemand(int d) {
        return this.forecastLevelDemands[d];
    }

    /**
     * Returns point forecast volume demand in liters on day d
     *
     * @param d day for which the point volume demand is returned
     * @return point volume demand in liters
     */
    public double GetForecastVolumeDemand(int d) {
        return this.forecastVolumeDemands[d];
    }

    /**
     * Returns point forecast weight demand in kg on day d
     *
     * @param d day for which the point weight demand is returned
     * @return point weight demand in kg
     */
    public double GetForecastWeightDemand(int d) {
        return this.forecastWeightDemands[d];
    }

    /**
     * Returns the unconditional probability with a starting inventory.
     *
     * @return the unconditional probability with a starting inventory
     */
    public double GetUnconditionalStartInv() {
        return this.unconditionalStartInv;
    }

    /**
     * Returns the unconditional probability with zero inventory for day h.
     *
     * @param h day in the planning horizon
     * @return the unconditional probability with zero inventory for day h
     */
    public double GetUnconditionalZeroInv(int h) {
        return this.unconditionalZeroInv[h];
    }

    /**
     * Returns the conditional probability with starting inventory on day h.
     *
     * @param h day in the planning horizon
     * @return the conditional probability with starting inventory on day h
     */
    public double GetConditionalStartInv(int h) {
        return this.conditionalStartInv[h];
    }

    /**
     * Returns the conditional probability with zero inventory on day h with
     * inventory reset on day k.
     *
     * @param h day in the planning horizon
     * @param k day in the planning horizon
     * @return the conditional probability with zero inventory on day h with
     * inventory reset on day k
     */
    public double GetConditionalZeroInv(int h, int k) {
        return this.conditionalZeroInv[h][k];
    }
}
