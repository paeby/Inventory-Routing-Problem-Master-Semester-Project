package alns.data;

import alns.algo.Penalties;
import alns.param.Parameters;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * Database access, extracts problem specific data and extends data access
 * methods
 *
 * @author Markov
 * @version 1.0
 */
public class Data implements Serializable {

    // Random number generator
    private final SecureRandom rand;

    // Because penalties should be accessible from SAALNS, Schedule and Tour we keep them here
    private final Penalties penalties;

    // DB connection
    private Connection conn = null;

    // Number of days in the planning horizon
    private int phLength;

    // Forecast class instance
    private Forecast forecast = null;

    // Standard deviation of the forecasting errors in terms of volume (liters)
    private double errorSigma;

    // Information received from the interface
    private final Map<Integer, ArrayList<Integer>> recTruckSP;
    private final ArrayList<Integer> recContainerWids;
    private final Integer recZoneWid;
    private final Integer recClientWid;
    private final Integer recFlowWid;

    // Flow specific weight in kg/m3
    private double flowSpecWeight;

    // Starting points, containers, dumps and trucks
    // corresponding to the information received from the interface
    private ArrayList<Point> startingPoints;
    private ArrayList<Point> containers;
    private ArrayList<Point> dumps;
    private ArrayList<Truck> trucks;

    // Original number of points before any data restrictions
    // in the Decomposition class
    private int originalNumPoints;

    // Tour characteristics
    private double tourStartTime;         // earliest tour start
    private double tourEndTime;           // latest tour finish
    private double tourMaxDur;            // maximum tour duration

    // Other problem characteristics
    private double overflowCost;                  // overflow cost
    private double emergencyCost;                 // emergency collection cost
    private double routeFailureCostMultiplier;    // route failure cost multiplication factor

    // dWid and contWid matchers (only used for algorithmic implementation)
    private final HashMap<Integer, Integer> dWidMatcher;
    private final HashMap<Integer, Integer> contWidMatcher;

    // Distance matrix corresponding to the information received from the interface
    private double[][] distanceMatrix;

    /**
     * Constructor for database access, calls all data extraction methods.
     *
     * @param recTruckSP Received truck to starting points map. This is a
     * LinkedHashMap which maps a trick wid to an ArrayList of starting point
     * wids. The ArrayList should contain at least 2 starting point wids - the
     * home starting point wid and the current starting point wid in that order.
     * Any additional starting point wids define flexible starting points. For
     * the definition of home, current and flexible starting points, see the
     * Truck class. Also, see documentation for further explanation.
     * @param recContainerWids list of received container wids
     * @param recZoneWid received zone wid
     * @param recClientWid received client wid
     * @param recFlowWid received flowtype wid
     * @param phLength planning horizon length
     * @throws java.sql.SQLException
     * @throws org.rosuda.REngine.Rserve.RserveException
     * @throws org.rosuda.REngine.REXPMismatchException
     * @throws java.io.IOException
     * @throws java.security.NoSuchAlgorithmException
     */
    public Data(LinkedHashMap<Integer, ArrayList<Integer>> recTruckSP, ArrayList<Integer> recContainerWids,
            Integer recZoneWid, Integer recClientWid, Integer recFlowWid, int phLength)
            throws SQLException, RserveException, REXPMismatchException, IOException, NoSuchAlgorithmException {

        // Initialize random number generator
        this.rand = SecureRandom.getInstance("SHA1PRNG");

        // Initialize penalties
        this.penalties = new Penalties();

        // Establish DB connection
        System.out.println("Opening DB connection...");
        this.conn = DriverManager.getConnection("jdbc:postgresql://localhost/" + Parameters.dbName
                + "?user=" + Parameters.dbUser
                + "&password=" + Parameters.dbPassword);

        // Set planning horizon length
        // It should be at least 1 and at most as specified by hMaxHorizon for 
        // exponential computational complexity reasons
        this.phLength = Math.max(phLength, 1);
        this.phLength = Math.min(phLength, Parameters.hMaxHorizon);

        // Initialize forecast 
        this.forecast = new Forecast(this.phLength);

        // Initialize information received from the interface
        this.recTruckSP = new LinkedHashMap<>();
        this.recTruckSP.putAll(new LinkedHashMap<>(recTruckSP));
        this.recContainerWids = new ArrayList<>();
        this.recContainerWids.addAll(new ArrayList<>(recContainerWids));
        this.recZoneWid = recZoneWid;
        this.recClientWid = recClientWid;
        this.recFlowWid = recFlowWid;

        // Extract flow specific weight in kg/m3
        this.extractFlowSpecWeight();

        // Initialize dWid and contWid matchers
        this.dWidMatcher = new HashMap<>();
        this.contWidMatcher = new HashMap<>();

        // Set tour characteristics
        this.tourStartTime = Parameters.tourStartTime;
        this.tourEndTime = Parameters.tourEndTime;
        this.tourMaxDur = Parameters.tourMaxDur;

        // Set other problem characteristics
        this.overflowCost = Parameters.overflowCost;
        this.emergencyCost = Parameters.hEmergencyCost;
        this.routeFailureCostMultiplier = Parameters.hRouteFailureCostMultiplier;

        // Extract standard deviation of the forecasting errors
        // This is and should be done before points are extracted
        // because points rely on having this information
        this.extractErrorSigma();

        // Initialize data fields and extract data
        System.out.println("Extracting data from DB...");
        this.startingPoints = new ArrayList<>();
        this.containers = new ArrayList<>();
        this.dumps = new ArrayList<>();
        this.trucks = new ArrayList<>();
        // Extract points and trucks
        this.extractContainers();
        this.extractDumps();
        this.extractTrucks();

        // Set original number of points before any data restrictions in the Decomposition class
        this.originalNumPoints = this.startingPoints.size() + this.containers.size() + this.dumps.size();

        // Calculate distance matrix size and initialize distance matrix
        int distanceMatrixSize = this.startingPoints.size() + this.containers.size() + this.dumps.size();
        this.distanceMatrix = new double[distanceMatrixSize][distanceMatrixSize];
        // Update matchers and extract distances
        this.updateMatchers();
        this.extractDistanceMatrix();
        // Set for each container the closest dump and the back and forth distance to it
        this.setContainerClosestDumpsAndBFDistances();

        // Close forecast
        this.forecast.Close();

        // Close connection
        System.out.println("Closing DB connection...");
        this.conn.close();

        // We set forecast and conn to null for the purposes of serialization.
        // The Data, Point and Truck classes implement the Serializable interface 
        // only for the purposes of serializing a data object to a file for test purposes.
        this.forecast = null;
        this.conn = null;
    }

    /**
     * Alternative constructor applicable for benchmark instances to be read
     * from files.
     *
     * @param fileName instance file name
     * @param instanceType instance type
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.io.FileNotFoundException
     */
    public Data(String fileName, int instanceType) throws NoSuchAlgorithmException, FileNotFoundException, IOException {

        // Print message
        System.out.println("Loading instance " + fileName);

        // Initialize random number generator
        this.rand = SecureRandom.getInstance("SHA1PRNG");

        // Initialize penalties
        this.penalties = new Penalties();

        // There is no information received from the interface
        // Therefore initialize as null or -404
        this.recTruckSP = null;
        this.recContainerWids = null;
        this.recZoneWid = Parameters._404;
        this.recClientWid = Parameters._404;
        this.recFlowWid = Parameters._404;

        // Initialize dWid and contWid matchers
        this.dWidMatcher = new HashMap<>();
        this.contWidMatcher = new HashMap<>();

        // Read in instance depending on its type
        if (instanceType == Parameters.benchmarkTypeCrevier2007) {
            this.readCrevier2007Instance(fileName);
        }
        if (instanceType == Parameters.benchmarkTypeArchetti2007
                || instanceType == Parameters.benchmarkTypeArchetti2012) {
            this.readArchetti2007Instance(fileName, instanceType);
        }
        if (instanceType == Parameters.benchmarkTypeTaillard1999) {
            this.readTaillard1999Instance(fileName);
        }
        if (instanceType == Parameters.benchmarkTypeCoelho2012b) {
            this.readCoelho2012bInstance(fileName);
        }

        // Set for each container the closest dump and the back and forth distance to it
        this.setContainerClosestDumpsAndBFDistances();
    }

    /**
     * Populates the dWid and contWid matchers and updates the points'
     * simpleDWid and simpleContWid fields.
     */
    private void updateMatchers() {

        // Clear dWid and contWid matchers
        this.dWidMatcher.clear();
        this.contWidMatcher.clear();

        // Combine all points into a single ArrayList
        ArrayList<Point> allPoints = new ArrayList<>();
        for (Point startingPoint : this.startingPoints) {
            if (startingPoint != null) {
                allPoints.add(startingPoint);
            }
        }
        for (Point container : this.containers) {
            if (container != null) {
                allPoints.add(container);
            }
        }
        for (Point dump : this.dumps) {
            if (dump != null) {
                allPoints.add(dump);
            }
        }

        // Populate dWid and contWid matchers
        for (int i = 0; i < allPoints.size(); i++) {
            this.dWidMatcher.put(allPoints.get(i).GetDWid(), i);
            this.contWidMatcher.put(allPoints.get(i).GetContWid(), i);
        }

        // Set point simpleDWid and simpleContWid
        for (Point point : allPoints) {
            point.SetSimpleDWid(this.dWidMatcher.get(point.GetDWid()));
            point.SetSimpleContWid(this.contWidMatcher.get(point.GetContWid()));
        }
    }

    /**
     * Generates default accessibilities. If a container can be visited by all
     * trucks, this is an empty ArrayList. Otherwise it includes the truck wids
     * of the trucks that can visit the container.
     *
     * @return default accessibilities
     */
    private ArrayList<Integer> getDefaultAccessibilities() {
        ArrayList<Integer> truckWidList = new ArrayList<>();
        return truckWidList;
    }

    /**
     * Generates default truck availabilities for the planning horizon. This is
     * an ArrayList with true if a truck is available on a given day of the
     * planning horizon, and false otherwise.
     *
     * @param phLength planning horizon length
     * @return default truck availabilities
     */
    private ArrayList<Boolean> getDefaultAvailabilities(int phLength) {

        ArrayList<Boolean> truckAvailabilities = new ArrayList<>();
        for (int i = 0; i < phLength; i++) {
            truckAvailabilities.add(true);
        }

        return truckAvailabilities;
    }

    /**
     * Generates default required returns to the home starting point. This is a
     * boolean ArrayList specifying on which days of the planning horizon the
     * truck is required to return to its home starting point. For these days, a
     * flexible choice of final starting point will not be applied. It has to be
     * remembered that if a truck is not available on a given day, the algorithm
     * assumes that this truck does not move at all. So if it needs to be at the
     * home starting point for such a day, it has to be specified that it needs
     * to finish its tour there on its most recent available day.
     *
     * @param phLength planning horizon length
     * @return a boolean ArrayList specifying with true that the truck needs to
     * finish its tour at the home starting point on a given day of the planning
     * horizon, and false if not.
     */
    private ArrayList<Boolean> getDefaultRequiredReturns(int phLength) {

        ArrayList<Boolean> truckRequiredReturns = new ArrayList<>();
        for (int i = 0; i < phLength; i++) {
            truckRequiredReturns.add(true);
        }

        return truckRequiredReturns;
    }

    /**
     * Extract the flow specific weight in kg/m3.
     *
     * @throws SQLException
     */
    private void extractFlowSpecWeight() throws SQLException {

        // Print message
        System.out.println("Extracting flow specific weight for flow type " + this.recFlowWid + "...");

        // MySQL Query
        PreparedStatement statement = this.conn.prepareStatement("SELECT specificweight FROM "
                + Parameters.tableFlowTypes
                + " WHERE wid = " + this.recFlowWid);

        // Execute query
        ResultSet result = statement.executeQuery();

        // Assign specific weight
        while (result.next()) {
            this.flowSpecWeight = result.getDouble("specificweight");
        }

        // Clean up environment
        result.close();
        statement.close();
    }

    /**
     * Extracts standard deviation of the forecasting errors.
     *
     * @throws RserveException
     * @throws REXPMismatchException
     */
    private void extractErrorSigma() throws RserveException, REXPMismatchException {
        this.errorSigma = this.forecast.GetErrorSigma(this.recFlowWid, this.recClientWid);
    }

    /**
     * Extracts the starting point as a Point object corresponding to the
     * starting point wid passed as a parameter.
     *
     * @param startingPointWid starting point wid
     * @throws SQLException
     */
    private Point extractStartingPoint(int startingPointWid) throws SQLException, RserveException, REXPMismatchException {

        // Print message
        System.out.println("Extracting starting point " + startingPointWid + "...");

        // SQL query to select the starting point corresponding to the starting point wid passed as a parameter
        PreparedStatement statement;
        statement = this.conn.prepareStatement("SELECT wid, center_y, center_x,"
                + " tw_lower, tw_upper, client_wid, zone_wid, flowtype_wid, location FROM " + Parameters.tableStartingPoints
                + " WHERE wid = " + startingPointWid);

        // Execute query
        ResultSet result = statement.executeQuery();

        // Declare starting point as null
        Point startingPoint = null;
        // Extract from result set
        while (result.next()) {
            startingPoint = new Point(Parameters.pointIsSP, // point type
                    result.getInt("wid"), // point dwid (distance matrix wid)
                    result.getDouble("center_y"), // point lat
                    result.getDouble("center_x"), // point lon
                    Parameters.serviceDurSP, // point service duration in hours
                    result.getDouble("tw_lower"), // point time window lower bound in hours since midnight
                    result.getDouble("tw_upper"), // point time window upper bound in hours since midnight
                    result.getInt("client_wid"), // point client wid
                    result.getInt("zone_wid"), // point zone wid
                    result.getInt("flowtype_wid"), // point flow type wid
                    Parameters._404, // point flow type specific weight in kg/m3
                    Parameters._404, // point container wid
                    Parameters._404, // point container type wid
                    Parameters._404, // point ecolog wid
                    Parameters._404, // point volume in liters
                    result.getString("location"), // ponit location description
                    0.d, // point inventory holding cost per day
                    0.d, // point inventory shortage cost per day
                    null, // Truck wid list
                    null); // Forecast reference                            
        }

        // Clean up environment
        result.close();
        statement.close();

        // Add the extracted starting point to the ArrayList of starting points and return it
        this.startingPoints.add(startingPoint);
        return startingPoint;
    }

    /**
     * Extracts containers as Point objects corresponding to the container wids
     * received from the interface
     *
     * @throws SQLException
     */
    private void extractContainers() throws SQLException, RserveException, REXPMismatchException {

        // Print message
        System.out.println("Extracting containers...");

        // Populate the string to be used in POSITION in the SQL query below
        String recContainerWidsString = this.recContainerWids.toString().substring(1, this.recContainerWids.toString().length() - 1);

        // SQL query to select the containers corresponding to the container wids received from the interface
        PreparedStatement statement;
        statement = this.conn.prepareStatement("SELECT " + Parameters.tableContainers + ".ecopoint_wid, "
                + Parameters.tableEcopoints + ".center_y, " + Parameters.tableEcopoints + ".center_x, "
                + Parameters.tableEcopoints + ".tw_lower, " + Parameters.tableEcopoints + ".tw_upper, "
                + Parameters.tableContainers + ".client_wid, " + Parameters.tableEcopoints + ".zone_wid, "
                + Parameters.tableContainers + ".flowtype_wid, " + Parameters.tableFlowTypes + ".specificweight, "
                + Parameters.tableContainers + ".wid, " + Parameters.tableContainers + ".containertype_wid, "
                + Parameters.tableContainers + ".ecolog_wid, " + Parameters.tableContainerTypes + ".volume, "
                + Parameters.tableContainerTypes + ".collectiontime, " + Parameters.tableEcopoints + ".location FROM "
                + Parameters.tableContainers + " INNER JOIN " + Parameters.tableEcopoints + " ON "
                + Parameters.tableContainers + ".ecopoint_wid = " + Parameters.tableEcopoints + ".wid"
                + " INNER JOIN " + Parameters.tableFlowTypes + " ON "
                + Parameters.tableContainers + ".flowtype_wid = " + Parameters.tableFlowTypes + ".wid"
                + " INNER JOIN " + Parameters.tableContainerTypes + " ON "
                + Parameters.tableContainers + ".containertype_wid = " + Parameters.tableContainerTypes + ".wid"
                + " WHERE POSITION(CAST(" + Parameters.tableContainers + ".wid AS TEXT) IN '" + recContainerWidsString + "') > 0");

        // Execute query
        ResultSet result = statement.executeQuery();

        // Populate containers
        while (result.next()) {
            // Due to no database implementation, generate default accessibilities
            ArrayList<Integer> truckWidList = this.getDefaultAccessibilities();
            this.containers.add(
                    new Point(Parameters.pointIsContainer, // point type
                            result.getInt("ecopoint_wid"), // point dwid (distance matrix wid)
                            result.getDouble("center_y"), // point lat
                            result.getDouble("center_x"), // point lon
                            result.getDouble("collectiontime") / 60.d, // point service duration in hours
                            result.getDouble("tw_lower"), // point time window lower bound in hours since midnight
                            result.getDouble("tw_upper"), // point time window upper bound in hours since midnight
                            result.getInt("client_wid"), // point client wid
                            result.getInt("zone_wid"), // point zone wid
                            result.getInt("flowtype_wid"), // point flow type wid
                            result.getDouble("specificweight"), // point flow type specific weiht in kg/m3
                            result.getInt("wid"), // point container wid
                            result.getInt("containertype_wid"), // point container type wid
                            result.getInt("ecolog_wid"), // point ecolog wid
                            result.getDouble("volume"), // point volume in liters
                            result.getString("location"), // point location description
                            0.d, // point inventory holding cost per day
                            0.d, // point inventory shortage cost per day
                            truckWidList, // Truck wid list
                            this.forecast) // Forecast reference
            );
        }

        // Clean up environment
        result.close();
        statement.close();
    }

    /**
     * Extracts dumps as Point objects corresponding to the client wid, zone wid
     * and flow type wid received from the interface
     *
     * @throws SQLException
     */
    private void extractDumps() throws SQLException, RserveException, REXPMismatchException {

        // Print message
        System.out.println("Extracting dumps...");

        // SQL query to select dumps corresponding to the client wid, zone wid and flow type wid received 
        // from the interface (including NULL for multi-zone and NULL for multi-flow). 
        PreparedStatement statement = this.conn.prepareStatement("SELECT wid, center_y, center_x, waittime,"
                + " tw_lower, tw_upper, zone_wid, flowtype_wid, name FROM " + Parameters.tableDumps
                + " WHERE client_wid = " + this.recClientWid
                + " AND (zone_wid IS NULL OR zone_wid = " + this.recZoneWid + ")"
                + " AND (flowtype_wid IS NULL OR flowtype_wid = " + this.recFlowWid + ")");

        // Execute query
        ResultSet result = statement.executeQuery();

        // Populate dumps
        while (result.next()) {
            this.dumps.add(
                    new Point(Parameters.pointIsDump, // point type
                            result.getInt("wid"), // point dwid (distance matrix wid)
                            result.getDouble("center_y"), // point lat
                            result.getDouble("center_x"), // point lon
                            result.getDouble("waittime") / 60.d, // point service duration in hours
                            result.getDouble("tw_lower"), // point time window lower bound in hours since midnight
                            result.getDouble("tw_upper"), // point time window upper bound in hours since midnight
                            this.recClientWid, // point client wid
                            result.getInt("zone_wid"), // point zone wid
                            result.getInt("flowtype_wid"), // point flow type wid
                            Parameters._404, // point flow type specific weight in kg/m3
                            Parameters._404, // point container wid
                            Parameters._404, // point container type wid
                            Parameters._404, // point ecolog wid
                            Parameters._404, // point volume in liters
                            result.getString("name"), // point location description
                            0.d, // point inventory holding cost per day
                            0.d, // point inventory shortage cost per day
                            null, // Truck wid list
                            null) // Forecast reference               
            );
        }

        // Clean up environment
        result.close();
        statement.close();
    }

    /**
     * Extracts trucks as Truck objects corresponding to the client wid, zone
     * wid and flow type wid received from the interface
     *
     * @throws SQLException
     */
    private void extractTrucks() throws SQLException, RserveException, REXPMismatchException {

        // Print message
        System.out.println("Extracting trucks...");

        // Extract individually each truck passed with the LinkedHashMap
        for (int truckWid : recTruckSP.keySet()) {

            // There should be at least two starting points associated with each truck
            if (this.recTruckSP.get(truckWid).size() >= 2) {

                System.out.println("Extracting truck " + truckWid + " and its associated starting points...");

                // Extract starting points associated with this truck. 
                ArrayList<Point> truckStartingPoints = new ArrayList<>();
                for (int startingPointWid : this.recTruckSP.get(truckWid)) {
                    truckStartingPoints.add(this.extractStartingPoint(startingPointWid));
                }

                // The first one is the home starting point (where the truck is normally assigned),
                // the second one is the current starting point (where the truck is physically located at the launch
                // of the algorithm). 
                Point homeStartingPoint = truckStartingPoints.get(0);
                Point currStartungPoint = truckStartingPoints.get(1);

                // The rest of the starting points, if any, can be used as a final tour starting point at the end of the day.
                // We use a new name (flex for flexible starting points) for clarity 
                ArrayList<Point> flexStartingPoints = truckStartingPoints;
                // We remove the current starting point from this list
                flexStartingPoints.remove(1);

                // SQL query to select the truck with the current truck wid
                PreparedStatement statement;
                statement = this.conn.prepareStatement("SELECT wid, identifier, name, client_wid, zone_wid, flowtype_wid,"
                        + " volumemax, weightmax, speed, fixed_cost, distance_cost, time_cost FROM " + Parameters.tableTrucks
                        + " WHERE wid = " + truckWid);

                // Execute query
                ResultSet result = statement.executeQuery();

                // Declare truck object as null
                Truck truck = null;
                // Populate trucks
                while (result.next()) {
                    // Due to no database implementation, generate default required returns to the home starting point
                    ArrayList<Boolean> truckRequiredReturns = this.getDefaultRequiredReturns(this.phLength);
                    // Due to no database implementation, generate default truck availabilities
                    ArrayList<Boolean> truckAvailabilities = this.getDefaultAvailabilities(this.phLength);
                    truck = new Truck(result.getInt("wid"), // truck wid
                            Parameters._404, // truck type
                            result.getString("identifier"), // truck identifier
                            result.getString("name"), // truck name
                            result.getInt("client_wid"), // truck client wid
                            result.getInt("zone_wid"), // truck zone wid
                            result.getInt("flowtype_wid"), // truck flow type wid
                            result.getDouble("volumemax"), // truck max volume
                            result.getDouble("weightmax"), // truck max weight
                            result.getDouble("speed"), // truck speed in km/h
                            result.getDouble("fixed_cost"), // truck fixed cost
                            result.getDouble("distance_cost"), // truck running cost per km
                            result.getDouble("time_cost"), // truck driver wage per hour
                            homeStartingPoint, // truck home starting point
                            currStartungPoint, // truck current starting point
                            flexStartingPoints, // truck flexible starting points
                            truckRequiredReturns, // truck required returns to the home starting point (starting on day = 0)
                            truckAvailabilities); // truck availabilities for the planning horizon (starting on day = 0)
                }

                // Clean up environment
                result.close();
                statement.close();

                // Add truck to list of trucks
                this.trucks.add(truck);
            } else {
                System.err.println("Truck with wid " + truckWid
                        + " was not added because it has less than two associated starting points.");
            }
        }
    }

    /**
     * Builds a distance matrix of the points corresponding to the information
     * received from the interface.
     *
     * @throws SQLException
     */
    private void extractDistanceMatrix() throws SQLException {

        // Print message
        System.out.println("Extracting distance matrix...");

        // Extract the distance matrix wids of the starting points, dumps and 
        // containers corresponding to the data received from the interface.
        ArrayList<Point> matrixPoints = new ArrayList<>();
        for (Point startingPoint : this.startingPoints) {
            if (startingPoint != null) {
                matrixPoints.add(startingPoint);
            }
        }
        for (Point container : this.containers) {
            if (container != null) {
                matrixPoints.add(container);
            }
        }
        for (Point dump : this.dumps) {
            if (dump != null) {
                matrixPoints.add(dump);
            }
        }
        ArrayList<Integer> matrixPointDWids = new ArrayList<>(matrixPoints.size());
        for (Point point : matrixPoints) {
            matrixPointDWids.add(point.GetDWid());
        }
        String matrixPointsString = matrixPointDWids.toString().substring(1, matrixPointDWids.toString().length() - 1);

        // Compose distance extraction query
        String distanceQuery = "SELECT * FROM " + Parameters.tableDistances + " WHERE POSITION(CAST(wid_start AS TEXT) IN '"
                + matrixPointsString + "') > 0 AND POSITION(CAST(wid_end AS TEXT) IN '" + matrixPointsString + "') > 0";

        // SQL query
        PreparedStatement statement = this.conn.prepareStatement(distanceQuery);

        // Execute query
        ResultSet result = statement.executeQuery();

        // Populate distance matrix in km
        while (result.next()) {
            this.distanceMatrix[this.dWidMatcher.get(result.getInt("wid_start"))][this.dWidMatcher.get(result.getInt("wid_end"))]
                    = result.getDouble("distance") / 1000.d;
        }

        // Clean up environment
        result.close();
        statement.close();
    }

    /**
     * Reads in an instance from a Crevier (2007) type file and populates
     * required fields.
     *
     * @param fileName instance file name
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void readCrevier2007Instance(String fileName) throws FileNotFoundException, IOException {

        // The Crevier (2007) instance represents a VRP with interdepot routes. Therefore,
        // we assign a dummy planning horizon of 1 (as 0 is impossible). Moreover,
        // we disregard overflow cost, emergency cost and route failure cost.
        this.phLength = 1;
        this.overflowCost = 0.d;
        this.emergencyCost = 0.d;
        this.routeFailureCostMultiplier = 0.d;

        // To avoid numeric errors, assign a dummy error sigma of 200. It will not be used anyway.
        this.errorSigma = 200;

        // Number of trucks, containers and dumps
        int numTrucks;
        int numContainers;
        int numDumps;
        // Point latitudes, longitudes, service durations, and volume loads 
        ArrayList<Double> lats = new ArrayList<>();
        ArrayList<Double> lons = new ArrayList<>();
        ArrayList<Double> servDurs = new ArrayList<>();
        ArrayList<Double> volumeLoads = new ArrayList<>();

        // Open input stream
        BufferedReader inputFile = new BufferedReader(new FileReader(fileName));

        // File line string
        String line;

        // Read first line in file
        line = inputFile.readLine();
        String[] lineInfo = line.split("\\s+");
        numTrucks = Integer.parseInt(lineInfo[0]);
        numContainers = Integer.parseInt(lineInfo[1]);
        numDumps = Integer.parseInt(lineInfo[2]);

        // Read second line in file
        line = inputFile.readLine();
        lineInfo = line.split("\\s+");
        double maxDur = Double.parseDouble(lineInfo[0]);
        double maxVolume = Double.parseDouble(lineInfo[1]);
        double dumpServDur = Double.parseDouble(lineInfo[2]);
        double servDurMultiplier = Double.parseDouble(lineInfo[3]);

        // While there are lines to read, read the line correponding to one point
        while ((line = inputFile.readLine()) != null) {
            // Add space in front due to file structure
            line = " " + line;
            lineInfo = line.split("\\s+");
            lats.add(Double.parseDouble(lineInfo[2]));
            lons.add(Double.parseDouble(lineInfo[3]));
            volumeLoads.add(Double.parseDouble(lineInfo[5]));
            servDurs.add(Integer.parseInt(lineInfo[4]) + (servDurMultiplier * Double.parseDouble(lineInfo[5])));
        }

        // Initialize arrays for points and trucks
        this.startingPoints = new ArrayList<>();
        this.containers = new ArrayList<>();
        this.dumps = new ArrayList<>();
        this.trucks = new ArrayList<>();

        // Fill in arrays with the data that was read from the file
        // Containers
        for (int i = 0; i < numContainers; i++) {
            this.containers.add(
                    new Point(
                            Parameters.pointIsContainer, // point type
                            i + 1, // container wid
                            i + 1, // dwid
                            lats.get(i), // latitude
                            lons.get(i), // longitude
                            servDurs.get(i), // service duration
                            0.d, // lower time window bound
                            100000.d, // upper time window bound
                            Double.POSITIVE_INFINITY, // maxumum volume
                            0.d, // inventory holding cost per day
                            0.d, // inventory shortage cost per day
                            volumeLoads.get(i), // initial volume load
                            this.phLength, // planning horizon length
                            new ArrayList<Integer>(), // dummy truck wid list
                            new ArrayList<>(Collections.nCopies(this.phLength, 0.d)) // dummy volumes demands
                    )
            );
        }

        // Starting point
        for (int i = numContainers; i < numContainers + 1; i++) {
            this.startingPoints.add(
                    new Point(
                            Parameters.pointIsSP, // point type
                            Parameters._404, // container wid
                            0, // dwid
                            lats.get(i), // latitude 
                            lons.get(i), // longitude
                            0.d, // service duration
                            0.d, // lower time window bound
                            100000.d, // upper time window bound
                            Parameters._404, // maximum volume
                            0.d, // inventory holding cost per day
                            0.d, // inventory shortage cost per day
                            0.d, // initial volume load
                            this.phLength, // planning horizon length
                            new ArrayList<Integer>(), // truck wid list
                            null // null volume demands
                    )
            );
        }

        // Dumps
        for (int i = numContainers; i < numContainers + numDumps; i++) {
            this.dumps.add(
                    new Point(
                            Parameters.pointIsDump, // point type
                            Parameters._404, // container wid
                            i + 1, // dwid
                            lats.get(i), // latitude
                            lons.get(i), // longitude
                            dumpServDur, // service duration
                            0.d, // lower time window bound
                            100000.d, // upper time window bound
                            Parameters._404, // maximum volume
                            0.d, // inventory holding cost per day
                            0.d, // inventory shortage cost per day
                            0.d, // initial volume load
                            this.phLength, // planning horizon length
                            new ArrayList<Integer>(), // truck wid list
                            null // null volume demands
                    )
            );
        }

        // Trucks
        for (int i = 0; i < numTrucks; i++) {
            this.trucks.add(
                    new Truck(
                            i + 1, // wid
                            maxVolume, // max volume
                            1.d, // speed
                            0.d, // fixed cost
                            1.d, // distance cost
                            0.d, // time cost
                            this.startingPoints.get(0), // home starting point
                            this.startingPoints.get(0), // current starting point
                            new ArrayList<>(Arrays.asList(this.startingPoints.get(0))), // list of flexible starting points
                            new ArrayList<>(Collections.nCopies(this.phLength, true)), // list of required returns to home
                            new ArrayList<>(Collections.nCopies(this.phLength, true)) // list of truck availabilities
                    )
            );
        }

        // Set original number of points before any data restrictions
        // in the Decomposition class
        this.originalNumPoints = this.startingPoints.size() + this.containers.size() + this.dumps.size();

        // Create an array that holds all points, for ease of distance matrix calculation
        ArrayList<Point> allPoints = new ArrayList<>();
        allPoints.addAll(this.containers);
        allPoints.addAll(this.startingPoints);
        allPoints.addAll(this.dumps);

        // Calculate distance matrix size and initialize distance matrix
        int distanceMatrixSize = this.startingPoints.size() + this.containers.size() + this.dumps.size();
        this.distanceMatrix = new double[distanceMatrixSize][distanceMatrixSize];
        // Update matchers
        this.updateMatchers();
        // Extract distance matrix as Euclidean distances
        for (int i = 0; i < allPoints.size(); i++) {
            for (int j = 0; j < allPoints.size(); j++) {
                this.distanceMatrix[allPoints.get(i).GetSimpleDWid()][allPoints.get(j).GetSimpleDWid()]
                        = (double) Math.sqrt(Math.pow(allPoints.get(i).GetLat() - allPoints.get(j).GetLat(), 2)
                                + Math.pow(allPoints.get(i).GetLon() - allPoints.get(j).GetLon(), 2));
            }
        }

        // In order to have a one-to-one conversion between volume and weight,
        // we specify the flow specific weight and its conversion factor as 1
        Parameters.flowSpecWeightCF = 1.d;
        this.flowSpecWeight = 1.d;

        // Change tour start time, end time, and max duration to correspond to file data
        this.tourStartTime = 0.d;
        this.tourEndTime = maxDur;
        this.tourMaxDur = maxDur;

        // Close input stream
        inputFile.close();
    }

    /**
     * Reads in an instance from a Archetti (2007) type file and populates
     * required fields.
     *
     * @param fileName instance file name
     * @param instanceType instance type
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void readArchetti2007Instance(String fileName, int instanceType) throws FileNotFoundException, IOException {

        // The Archetti (2007) instance represents an IRP with a fully deterministic structure. 
        // The planning horizon length is extracted from the first line in the file below.
        // We disregard overflow cost, emergency cost and route failure cost.
        this.overflowCost = 0.d;
        this.emergencyCost = 0.d;
        this.routeFailureCostMultiplier = 0.d;

        // To avoid numeric errors, assign a dummy error sigma of 200. It will not be used anyway.
        this.errorSigma = 200;

        // Number of points and maximum volume
        int numPoints;
        int maxVolume;
        // Point latitudes, longitudes, initial volume loads, maximum volumes, 
        // volume demands, inventory holding costs
        ArrayList<Double> lats = new ArrayList<>();
        ArrayList<Double> lons = new ArrayList<>();
        ArrayList<Double> initVolumeLoads = new ArrayList<>();
        ArrayList<Double> maxVolumes = new ArrayList<>();
        ArrayList<Double> volumeDemands = new ArrayList<>();
        ArrayList<Double> inventoryHoldingCosts = new ArrayList<>();

        // Open input stream
        BufferedReader inputFile = new BufferedReader(new FileReader(fileName));

        // File line string
        String line;

        // Read first line in file
        line = inputFile.readLine();
        line = " " + line;
        String[] lineInfo = line.split("\\s+");
        numPoints = Integer.parseInt(lineInfo[1]);
        this.phLength = Integer.parseInt(lineInfo[2]);
        maxVolume = Integer.parseInt(lineInfo[3]);

        // Read second line in file
        line = inputFile.readLine();
        line = " " + line;
        lineInfo = line.split("\\s+");
        lats.add(Double.parseDouble(lineInfo[2]));
        lons.add(Double.parseDouble(lineInfo[3]));
        initVolumeLoads.add(Double.parseDouble(lineInfo[4]));
        maxVolumes.add(Double.POSITIVE_INFINITY);
        volumeDemands.add(Double.parseDouble(lineInfo[5]));
        inventoryHoldingCosts.add(Double.parseDouble(lineInfo[6]));

        // While there are lines to read, read the line correponding to one point
        while ((line = inputFile.readLine()) != null) {
            // Add space in front due to file structure
            line = " " + line;
            lineInfo = line.split("\\s+");
            lats.add(Double.parseDouble(lineInfo[2]));
            lons.add(Double.parseDouble(lineInfo[3]));
            initVolumeLoads.add(Double.parseDouble(lineInfo[4]));
            maxVolumes.add(Double.parseDouble(lineInfo[5]));
            volumeDemands.add(Double.parseDouble(lineInfo[7]));
            inventoryHoldingCosts.add(Double.parseDouble(lineInfo[8]));
        }

        // Initialize arrays for points and trucks
        this.startingPoints = new ArrayList<>();
        this.containers = new ArrayList<>();
        this.dumps = new ArrayList<>();
        this.trucks = new ArrayList<>();

        // Fill in arrays with the data that was read from the file
        // Starting point
        this.startingPoints.add(
                new Point(
                        Parameters.pointIsSP, // point type
                        Parameters._404, // container wid
                        1, // dwid
                        lats.get(0), // latitude 
                        lons.get(0), // longitude
                        0.d, // service duration
                        0.d, // lower time window bound
                        100000.d, // upper time window bound
                        Parameters._404, // maximum volume
                        inventoryHoldingCosts.get(0), // inventory holding cost per day
                        0.d, // inventory shortage cost per day
                        initVolumeLoads.get(0), // initial volume load
                        this.phLength, // planning horizon length
                        new ArrayList<Integer>(), // truck wid list
                        new ArrayList<>(Collections.nCopies(this.phLength, volumeDemands.get(0))) // forecast volume demands
                )
        );

        // Containers
        for (int i = 1; i < numPoints; i++) {
            this.containers.add(
                    new Point(
                            Parameters.pointIsContainer, // point type
                            i + 1, // container wid
                            i + 1, // dwid
                            lats.get(i), // latitude
                            lons.get(i), // longitude
                            0.d, // service duration
                            0.d, // lower time window bound
                            100000.d, // upper time window bound
                            maxVolumes.get(i), // maximum volume
                            inventoryHoldingCosts.get(i), // inventory holding cost per day
                            0.d, // inventory shortage cost per day
                            initVolumeLoads.get(i), // initial volume load
                            this.phLength, // planning horizon length
                            new ArrayList<Integer>(), // dummy truck wid list
                            new ArrayList<>(Collections.nCopies(this.phLength, volumeDemands.get(i))) // forecast volumes demands
                    )
            );
        }

        // Dumps
        this.dumps.add(
                new Point(
                        Parameters.pointIsDump, // point type
                        Parameters._404, // container wid
                        numPoints + 1, // dwid
                        lats.get(0), // latitude
                        lons.get(0), // longitude
                        0.d, // service duration
                        0.d, // lower time window bound
                        100000.d, // upper time window bound
                        Parameters._404, // maximum volume
                        0.d, // inventory holding cost per day
                        0.d, // inventory shortage cost per day
                        0.d, // initial volume load
                        this.phLength, // planning horizon length
                        new ArrayList<Integer>(), // truck wid list
                        null // null volume demands
                )
        );

        // Trucks
        this.trucks.add(
                new Truck(
                        1, // wid
                        maxVolume, // max volume
                        1.d, // speed
                        0.d, // fixed cost
                        1.d, // distance cost
                        0.d, // time cost
                        this.startingPoints.get(0), // home starting point
                        this.startingPoints.get(0), // current starting point
                        new ArrayList<>(Arrays.asList(this.startingPoints.get(0))), // list of flexible starting points
                        new ArrayList<>(Collections.nCopies(this.phLength, true)), // list of required returns to home
                        new ArrayList<>(Collections.nCopies(this.phLength, true)) // list of truck availabilities
                )
        );

        // Set original number of points before any data restrictions
        // in the Decomposition class
        this.originalNumPoints = this.startingPoints.size() + this.containers.size() + this.dumps.size();

        // Create an array that holds all points, for ease of distance matrix calculation
        ArrayList<Point> allPoints = new ArrayList<>();
        allPoints.addAll(this.startingPoints);
        allPoints.addAll(this.containers);
        allPoints.addAll(this.dumps);

        // Calculate distance matrix size and initialize distance matrix
        int distanceMatrixSize = this.startingPoints.size() + this.containers.size() + this.dumps.size();
        this.distanceMatrix = new double[distanceMatrixSize][distanceMatrixSize];
        // Update matchers
        this.updateMatchers();
        // Extract distance matrix
        if (instanceType == Parameters.benchmarkTypeArchetti2007) {
            // Extract distance matrix as Euclidean distances as in Archetti et al. (2007)
            for (int i = 0; i < allPoints.size(); i++) {
                for (int j = 0; j < allPoints.size(); j++) {
                    this.distanceMatrix[allPoints.get(i).GetSimpleDWid()][allPoints.get(j).GetSimpleDWid()]
                            = Math.round(Math.sqrt(Math.pow(allPoints.get(i).GetLat() - allPoints.get(j).GetLat(), 2)
                                            + Math.pow(allPoints.get(i).GetLon() - allPoints.get(j).GetLon(), 2)));
                }
            }
        } else {
            // Extract distance matrix as Euclidean distances as in Archetti et al. (2012)
            for (int i = 0; i < allPoints.size(); i++) {
                for (int j = 0; j < allPoints.size(); j++) {
                    this.distanceMatrix[allPoints.get(i).GetSimpleDWid()][allPoints.get(j).GetSimpleDWid()]
                            = Math.floor(Math.sqrt(Math.pow(allPoints.get(i).GetLat() - allPoints.get(j).GetLat(), 2)
                                            + Math.pow(allPoints.get(i).GetLon() - allPoints.get(j).GetLon(), 2)) + 0.5d);
                }
            }
        }

        // In order to have a one-to-one conversion between volume and weight,
        // we specify the flow specific weight and its conversion factor as 1
        Parameters.flowSpecWeightCF = 1.d;
        this.flowSpecWeight = 1.d;

        // Change tour start time, end time, and max duration to correspond to file data
        this.tourStartTime = 0.d;
        this.tourEndTime = 100000.d;
        this.tourMaxDur = 100000.d;

        // Close input file
        inputFile.close();
    }

    /**
     * Reads in an instance from a Taillard (1999) type file and populates
     * required fields.
     *
     * @param fileName instance file name
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void readTaillard1999Instance(String fileName) throws FileNotFoundException, IOException {

        // The Taillard (1999) instance represents a VRP with interdepot routes. Therefore,
        // we assign a dummy planning horizon of 1 (as 0 is impossible). Moreover,
        // we disregard overflow cost, emergency cost and route failure cost.
        this.phLength = 1;
        this.overflowCost = 0.d;
        this.emergencyCost = 0.d;
        this.routeFailureCostMultiplier = 0.d;

        // To avoid numeric errors, assign a dummy error sigma of 200. It will not be used anyway.
        this.errorSigma = 200;

        // Number of trucks, containers and dumps
        int numContainers;
        // Point latitudes, longitudes and volume loads 
        ArrayList<Double> lats = new ArrayList<>();
        ArrayList<Double> lons = new ArrayList<>();
        ArrayList<Double> volumeLoads = new ArrayList<>();

        // Open input stream
        BufferedReader inputFile = new BufferedReader(new FileReader(fileName));

        // File line string
        String line;

        // Read first line in file
        line = inputFile.readLine();
        line = " " + line;
        String[] lineInfo = line.split("\\s+");
        numContainers = Integer.parseInt(lineInfo[1]);

        // Read all points
        for (int i = 0; i <= numContainers; i++) {

            // Read in line and split
            line = inputFile.readLine();
            line = " " + line;
            lineInfo = line.split("\\s+");

            // Populate arrays
            lats.add(Double.parseDouble(lineInfo[2]));
            lons.add(Double.parseDouble(lineInfo[3]));
            volumeLoads.add(Double.parseDouble(lineInfo[4]));
        }

        // Initialize arrays for points and trucks
        this.startingPoints = new ArrayList<>();
        this.containers = new ArrayList<>();
        this.dumps = new ArrayList<>();
        this.trucks = new ArrayList<>();

        // Add a starting point
        this.startingPoints.add(
                new Point(
                        Parameters.pointIsSP, // point type
                        Parameters._404, // container wid
                        0, // dwid
                        lats.get(0), // latitude
                        lons.get(0), // longitude
                        0.d, // service duration
                        0.d, // lower time window bound
                        100000.d, // upper time window bound
                        Parameters._404, // maximum volume
                        0.d, // inventory holding cost per day
                        0.d, // inventory shortage cost per day
                        0.d, // initial volume load
                        this.phLength, // planning horizon length
                        new ArrayList<Integer>(), // truck wid list
                        null // null volume demands                        
                )
        );

        // Add a dump
        this.dumps.add(
                new Point(
                        Parameters.pointIsDump, // point type
                        Parameters._404, // container wid
                        10001, // dwid
                        lats.get(0), // latitude
                        lons.get(0), // longitude
                        0.d, // service duration
                        0.d, // lower time window bound
                        100000.d, // upper time window bound
                        Parameters._404, // maximum volume
                        0.d, // inventory holding cost per day
                        0.d, // inventory shortage cost per day
                        0.d, // initial volume load
                        this.phLength, // planning horizon length
                        new ArrayList<Integer>(), // truck wid list
                        null // null volume demands                        
                )
        );

        // Add containers
        for (int i = 1; i <= numContainers; i++) {
            this.containers.add(
                    new Point(
                            Parameters.pointIsContainer, // point type
                            i, // container wid
                            i, // dwid
                            lats.get(i), // latitude
                            lons.get(i), // longitude
                            0.d, // service duration
                            0.d, // lower time window bound
                            100000.d, // upper time window bound
                            Double.POSITIVE_INFINITY, // maximum volume
                            0.d, // inventory holding cost per day
                            0.d, // inventory shortage cost per day
                            volumeLoads.get(i), // initial volume load
                            this.phLength, // planning horizon length
                            new ArrayList<Integer>(), // truck wid list
                            new ArrayList<>(Collections.nCopies(this.phLength, 0.d)) // dummy volume demands     
                    )
            );
        }

        // Now read in trucks
        int truckWid = 1;
        while ((line = inputFile.readLine()) != null) {

            // There are some empty lines between the last truck and the solution.
            // If reached, break this loop
            if (line.isEmpty()) {
                break;
            }

            // If the line is not a comment, it must be a truck
            if (!line.contains("//")) {
                line = " " + line;
                lineInfo = line.split("\\s+");
                double truckMaxVolume = Double.parseDouble(lineInfo[1]);
                double fixedCost = Double.parseDouble(lineInfo[2]);
                double varCost = Double.parseDouble(lineInfo[3]);
                int numHomogTrucks = Integer.parseInt(lineInfo[4]);

                for (int i = 0; i < numHomogTrucks; i++) {
                    this.trucks.add(
                            new Truck(
                                    truckWid, // wid
                                    truckMaxVolume, // max volume
                                    1.d, // speed
                                    fixedCost, // fixed cost
                                    varCost, // distance cost
                                    0.d, // time cost
                                    this.startingPoints.get(0), // home starting point
                                    this.startingPoints.get(0), // current starting point
                                    new ArrayList<>(Arrays.asList(this.startingPoints.get(0))), // list of flexible starting points
                                    new ArrayList<>(Collections.nCopies(this.phLength, true)), // list of required returns to home
                                    new ArrayList<>(Collections.nCopies(this.phLength, true)) // list of truck availabilities
                            )
                    );
                    truckWid++;
                }
            }
        }

        // Set original number of points before any data restrictions
        // in the Decomposition class
        this.originalNumPoints = this.startingPoints.size() + this.containers.size() + this.dumps.size();

        // Create an array that holds all points, for ease of distance matrix calculation
        ArrayList<Point> allPoints = new ArrayList<>();
        allPoints.addAll(this.startingPoints);
        allPoints.addAll(this.dumps);
        allPoints.addAll(this.containers);

        // Calculate distance matrix size and initialize distance matrix
        int distanceMatrixSize = this.startingPoints.size() + this.containers.size() + this.dumps.size();
        this.distanceMatrix = new double[distanceMatrixSize][distanceMatrixSize];
        // Update matchers
        this.updateMatchers();
        // Extract distance matrix as Euclidean distances
        for (int i = 0; i < allPoints.size(); i++) {
            for (int j = 0; j < allPoints.size(); j++) {
                this.distanceMatrix[allPoints.get(i).GetSimpleDWid()][allPoints.get(j).GetSimpleDWid()]
                        = (double) Math.sqrt(Math.pow(allPoints.get(i).GetLat() - allPoints.get(j).GetLat(), 2)
                                + Math.pow(allPoints.get(i).GetLon() - allPoints.get(j).GetLon(), 2));
            }
        }

        // In order to have a one-to-one conversion between volume and weight,
        // we specify the flow specific weight and its conversion factor as 1
        Parameters.flowSpecWeightCF = 1.d;
        this.flowSpecWeight = 1.d;

        // Change tour start time, end time, and max duration to correspond to file data
        this.tourStartTime = 0.d;
        this.tourEndTime = 100000.d;
        this.tourMaxDur = 100000.d;

        // Close input file
        inputFile.close();
    }

    /**
     * Reads in an instance from a Coelho et al. (2012b) (dynamic and
     * stochastic) type file and populates required fields.
     *
     * @param fileName instance file name
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void readCoelho2012bInstance(String fileName) throws FileNotFoundException, IOException {

        // The Coelho (2012b) instance represents an IRP with dynamic and stochastic structure. 
        // The planning horizon length is extracted from the first line in the file below.
        // We disregard overflow cost, emergency cost and route failure cost.
        this.overflowCost = 0.d;
        this.emergencyCost = 0.d;
        this.routeFailureCostMultiplier = 0.d;

        // To avoid numeric errors, assign a dummy error sigma of 200. It will not be used anyway.
        this.errorSigma = 200;

        // Number of containers and maximum volume
        int numContainers;
        int maxVolume;
        // Point latitudes, longitudes, initial volume loads, maximum volumes, 
        // volume demands, inventory holding costs
        ArrayList<Double> lats = new ArrayList<>();
        ArrayList<Double> lons = new ArrayList<>();
        ArrayList<Double> initVolumeLoads = new ArrayList<>();
        ArrayList<ArrayList<Double>> volumeDemands = new ArrayList<>();
        ArrayList<Double> maxVolumes = new ArrayList<>();
        ArrayList<Double> inventoryHoldingCosts = new ArrayList<>();
        ArrayList<Double> inventoryShortageCosts = new ArrayList<>();

        // Open input stream
        BufferedReader inputFile = new BufferedReader(new FileReader(fileName));

        // File line string
        String line;

        // Read first line in file
        line = inputFile.readLine();
        line = " " + line;
        String[] lineInfo = line.split("\\s+");
        numContainers = Integer.parseInt(lineInfo[1]);
        this.phLength = Integer.parseInt(lineInfo[2]);
        maxVolume = Integer.parseInt(lineInfo[3]);

        // Read second line in file
        line = inputFile.readLine();
        line = " " + line;
        lineInfo = line.split("\\s+");
        lats.add(Double.parseDouble(lineInfo[2]));
        lons.add(Double.parseDouble(lineInfo[3]));
        initVolumeLoads.add(Double.parseDouble(lineInfo[4]));
        volumeDemands.add(new ArrayList<Double>(this.phLength));
        for (int d = 0; d < this.phLength; d++) {
            volumeDemands.get(volumeDemands.size() - 1).add(Double.parseDouble(lineInfo[5 + d]));
        }
        maxVolumes.add(Double.POSITIVE_INFINITY);
        inventoryHoldingCosts.add(Double.parseDouble(lineInfo[5 + this.phLength]));
        inventoryShortageCosts.add(0.d);

        // While there are lines to read, read the line correponding to one point
        while ((line = inputFile.readLine()) != null) {
            // Add space in front due to file structure
            line = " " + line;
            lineInfo = line.split("\\s+");
            lats.add(Double.parseDouble(lineInfo[2]));
            lons.add(Double.parseDouble(lineInfo[3]));
            initVolumeLoads.add(Double.parseDouble(lineInfo[4]));
            volumeDemands.add(new ArrayList<Double>(this.phLength));
            for (int d = 0; d < this.phLength; d++) {
                volumeDemands.get(volumeDemands.size() - 1).add(Double.parseDouble(lineInfo[55 + d]));
            }
            maxVolumes.add(Double.parseDouble(lineInfo[55 + this.phLength]));
            inventoryHoldingCosts.add(Double.parseDouble(lineInfo[55 + this.phLength + 1]));
            inventoryShortageCosts.add(Double.parseDouble(lineInfo[55 + this.phLength + 2]));
        }

        // Initialize arrays for points and trucks
        this.startingPoints = new ArrayList<>();
        this.containers = new ArrayList<>();
        this.dumps = new ArrayList<>();
        this.trucks = new ArrayList<>();

        // Fill in arrays with the data that was read from the file
        // Starting point
        this.startingPoints.add(
                new Point(
                        Parameters.pointIsSP, // point type
                        Parameters._404, // container wid
                        1, // dwid
                        lats.get(0), // latitude 
                        lons.get(0), // longitude
                        0.d, // service duration
                        0.d, // lower time window bound
                        100000.d, // upper time window bound
                        Parameters._404, // maximum volume
                        inventoryHoldingCosts.get(0), // inventory holding cost per day
                        inventoryShortageCosts.get(0), // inventory shortage cost per day
                        initVolumeLoads.get(0), // initial volume load
                        this.phLength, // planning horizon length
                        new ArrayList<Integer>(), // truck wid list
                        volumeDemands.get(0) // forecast volume demands
                )
        );

        // Containers
        for (int i = 1; i < numContainers + 1; i++) {
            this.containers.add(
                    new Point(
                            Parameters.pointIsContainer, // point type
                            i + 1, // container wid
                            i + 1, // dwid
                            lats.get(i), // latitude
                            lons.get(i), // longitude
                            0.d, // service duration
                            0.d, // lower time window bound
                            100000.d, // upper time window bound
                            maxVolumes.get(i), // maximum volume
                            inventoryHoldingCosts.get(i), // inventory holding cost per day
                            inventoryShortageCosts.get(i), // inventory shortage cost per day
                            initVolumeLoads.get(i), // initial volume load
                            this.phLength, // planning horizon length
                            new ArrayList<Integer>(), // dummy truck wid list
                            volumeDemands.get(i) // forecast volumes demands
                    )
            );
        }

        // Dumps
        this.dumps.add(
                new Point(
                        Parameters.pointIsDump, // point type
                        Parameters._404, // container wid
                        numContainers + 2, // dwid
                        lats.get(0), // latitude
                        lons.get(0), // longitude
                        0.d, // service duration
                        0.d, // lower time window bound
                        100000.d, // upper time window bound
                        Parameters._404, // maximum volume
                        0.d, // inventory holding cost per day
                        0.d, // inventory shortage cost per day
                        0.d, // initial volume load
                        this.phLength, // planning horizon length
                        new ArrayList<Integer>(), // truck wid list
                        null // null volume demands
                )
        );

        // Trucks
        this.trucks.add(
                new Truck(
                        1, // wid
                        maxVolume, // max volume
                        1.d, // speed
                        0.d, // fixed cost
                        1.d, // distance cost
                        0.d, // time cost
                        this.startingPoints.get(0), // home starting point
                        this.startingPoints.get(0), // current starting point
                        new ArrayList<>(Arrays.asList(this.startingPoints.get(0))), // list of flexible starting points
                        new ArrayList<>(Collections.nCopies(this.phLength, true)), // list of required returns to home
                        new ArrayList<>(Collections.nCopies(this.phLength, true)) // list of truck availabilities
                )
        );

        // Set original number of points before any data restrictions in the Decomposition class
        this.originalNumPoints = this.startingPoints.size() + this.containers.size() + this.dumps.size();

        // Create an array that holds all points, for ease of distance matrix calculation
        ArrayList<Point> allPoints = new ArrayList<>();
        allPoints.addAll(this.startingPoints);
        allPoints.addAll(this.dumps);
        allPoints.addAll(this.containers);

        // Calculate distance matrix size and initialize distance matrix
        int distanceMatrixSize = this.startingPoints.size() + this.containers.size() + this.dumps.size();
        this.distanceMatrix = new double[distanceMatrixSize][distanceMatrixSize];
        // Update matchers
        this.updateMatchers();
        // Extract distance matrix as Euclidean distances
        for (int i = 0; i < allPoints.size(); i++) {
            for (int j = 0; j < allPoints.size(); j++) {
                this.distanceMatrix[allPoints.get(i).GetSimpleDWid()][allPoints.get(j).GetSimpleDWid()]
                        = Math.floor(Math.sqrt(Math.pow(allPoints.get(i).GetLat() - allPoints.get(j).GetLat(), 2)
                                        + Math.pow(allPoints.get(i).GetLon() - allPoints.get(j).GetLon(), 2)) + 0.5d);
            }
        }

        // In order to have a one-to-one conversion between volume and weight,
        // we specify the flow specific weight and its conversion factor as 1
        Parameters.flowSpecWeightCF = 1.d;
        this.flowSpecWeight = 1.d;

        // Change tour start time, end time, and max duration to correspond to file data
        this.tourStartTime = 0.d;
        this.tourEndTime = 100000.d;
        this.tourMaxDur = 100000.d;

        // Close input file
        inputFile.close();
    }

    /**
     * Set for each container the closest dump in terms of back and forth
     * distance as well as the back and forth distance itself.
     */
    private void setContainerClosestDumpsAndBFDistances() {

        // For each container
        for (Point container : this.containers) {
            // Best dump and best distance
            Point bestDump = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            // Check for each dump and update if improved
            for (Point dump : this.dumps) {
                double backAndForthDistance = this.distanceMatrix[container.GetSimpleDWid()][dump.GetSimpleDWid()]
                        + this.distanceMatrix[dump.GetSimpleDWid()][container.GetSimpleDWid()];
                if (backAndForthDistance < bestDistance) {
                    bestDump = dump;
                    bestDistance = backAndForthDistance;
                }
            }
            // Set the best dump and back and forth distance distance for this container
            container.SetContainerClosestDumpAndBFDistance(bestDump, bestDistance);
        }
    }

    /**
     * Verifies data completeness for solving the problem.
     *
     * @return true for data completeness, false otherwise
     */
    public boolean VerifyCompleteness() {

        System.out.println("Running data completeness verification...");

        if (this.startingPoints.isEmpty()) {
            System.err.println("Starting points are empty. Cannot start solving...");
            return false;
        }
        if (this.containers.isEmpty()) {
            System.err.println("Containers are empty. Cannot start solving...");
            return false;
        }
//        for (Point container : this.containers) {
//            if (container.GetFlowWid() != this.recFlowWid) {
//                System.err.println("A container does not correspond to the tour's flow type. Cannot start solving...");
//                return false;
//            }
//        }
        if (this.dumps.isEmpty()) {
            System.err.println("Dumps are empty. Cannot start solving...");
            return false;
        }
        if (this.trucks.isEmpty()) {
            System.out.println("Trucks are empty. Cannot start solving...");
            return false;
        }
        for (Truck truck : this.trucks) {
            if (truck == null) {
                System.err.println("There is a null truck. Cannot start solving...");
                return false;
            }
            if (truck.GetHomeStartingPoint() == null) {
                System.err.println("Truck " + truck.GetWid() + " has a null home starting point. Cannot start solving...");
                return false;
            }
            if (truck.GetCurrentStartingPoint() == null) {
                System.err.println("Truck " + truck.GetWid() + " has a null current starting point. Cannot start solving...");
                return false;
            }
            if (!truck.GetFlexStartingPoints().isEmpty()) {
                for (Point flexStartingPoint : truck.GetFlexStartingPoints()) {
                    if (flexStartingPoint == null) {
                        System.err.println("Truck " + truck.GetWid() + " has a null flexible starting point. Cannot start solving...");
                        return false;
                    }
                }
            }
            if (truck.GetNumRequiredReturns() < this.phLength) {
                System.err.println("Truck " + truck.GetWid() + "'s required returns to home are not defined for the whole planning horizon. "
                        + "Cannot start solving...");
                return false;
            }
            if (truck.GetNumAvailabilities() < this.phLength) {
                System.err.println("Truck " + truck.GetWid() + "'s availabilities are not defined for the whole planning horizon. "
                        + "Cannot start solving...");
                return false;
            }
        }

        // If data passed all completeness tests return true
        System.out.println("Data completeness verification test successful...");
        return true;
    }

    /**
     * Returns a random number generator
     *
     * @return a random number generator
     */
    public Random GetRand() {
        return this.rand;
    }

    /**
     * Returns the Penalties object
     *
     * @return the Penalties object
     */
    public Penalties GetPenalties() {
        return this.penalties;
    }

    /**
     * Sets a new seed to the random number generator. Only used for testing
     * purposes.
     *
     * @param seed random seed
     */
    public void SetRandSeed(long seed) {
        this.rand.setSeed(seed);
    }

    /**
     * Returns standard deviation of the forecasting errors.
     *
     * @return standard deviation of the forecasting errors
     */
    public double GetErrorSigma() {
        return this.errorSigma;
    }

    /**
     * Returns an ArrayList of starting points as Point objects.
     *
     * @return starting points
     */
    public ArrayList<Point> GetStartingPoints() {
        return this.startingPoints;
    }

    /**
     * Returns an ArrayList of containers as Point objects.
     *
     * @return containers
     */
    public ArrayList<Point> GetContainers() {
        return this.containers;
    }

    /**
     * Returns an ArrayList of dumps as Point objects.
     *
     * @return dumps
     */
    public ArrayList<Point> GetDumps() {
        return this.dumps;
    }

    /**
     * Returns an ArrayList of trucks as Truck objects.
     *
     * @return trucks
     */
    public ArrayList<Truck> GetTrucks() {
        return this.trucks;
    }

    /**
     * Returns the flow type wid.
     *
     * @return the flow type wid
     */
    public int GetFlowWid() {
        return this.recFlowWid;
    }

    /**
     * Returns flow specific weight in kg/m3
     *
     * @return flow specific weight in kg/m3
     */
    public double GetFlowSpecWeight() {
        return this.flowSpecWeight;
    }

    /**
     * Returns the distance in km between origin and destination.
     *
     * @param origin origin point
     * @param destination destination point
     * @return distance
     */
    public double GetDistance(Point origin, Point destination) {
        return this.distanceMatrix[origin.GetSimpleDWid()][destination.GetSimpleDWid()];
    }
    
    /**
     * Returns the time difference between origin and destination based on the time windows.
     *
     * @param origin origin point
     * @param destination destination point
     * @return time windows difference
     */
    public double GetTimeDiff(Point origin, Point destination) {
        return Math.abs(origin.GetTWLower()-destination.GetTWLower()) + Math.abs(origin.GetTWUpper()-destination.GetTWUpper());
    }

    /**
     * Returns the travel time in hours between origin and destination.
     *
     * @param origin origin point
     * @param destination destination point
     * @param speed vehicle speed in km/h
     * @return
     */
    public double GetTravelTime(Point origin, Point destination, double speed) {
        return this.distanceMatrix[origin.GetSimpleDWid()][destination.GetSimpleDWid()] / speed;
    }

    /**
     * Returns tour earliest start time.
     *
     * @return tour earliest start time
     */
    public double GetTourStartTime() {
        return this.tourStartTime;
    }

    /**
     * Sets tour earliest start time.
     *
     * @param tourStartTime tour earliest start time
     */
    public void SetTourStartTime(double tourStartTime) {
        this.tourStartTime = tourStartTime;
    }

    /**
     * Returns tour latest finish time.
     *
     * @return tour latest finish time
     */
    public double GetTourEndTime() {
        return this.tourEndTime;
    }

    /**
     * Sets tour latest finish time.
     *
     * @param tourEndTime tour latest finish time
     */
    public void SetTourEndTime(double tourEndTime) {
        this.tourEndTime = tourEndTime;
    }

    /**
     * Returns tour maximum duration.
     *
     * @return tour maximum duration
     */
    public double GetTourMaxDur() {
        return this.tourMaxDur;
    }

    /**
     * Sets tour maximum duration.
     *
     * @param tourMaxDur tour maximum duration
     */
    public void SetTourMaxDur(double tourMaxDur) {
        this.tourMaxDur = tourMaxDur;
    }

    /**
     * Returns planning horizon length.
     *
     * @return planning horizon length
     */
    public int GetPhLength() {
        return this.phLength;
    }

    /**
     * Set planning horizon length.
     *
     * @param phLength new planning horizon length
     */
    public void SetPhLength(int phLength) {
        this.phLength = Math.max(phLength, 1);
        this.phLength = Math.min(phLength, Parameters.hMaxHorizon);
    }

    /**
     * Returns container overflow cost.
     *
     * @return container overflow cost
     */
    public double GetOverflowCost() {
        return this.overflowCost;
    }

    /**
     * Sets the container overflow cost.
     *
     * @param overflowCost container overflow cost
     */
    public void SetOverflowCost(double overflowCost) {
        this.overflowCost = overflowCost;
    }

    /**
     * Returns the route failure cost multiplication factor.
     *
     * @return the route failure cost multiplication factor
     */
    public double GetRouteFailureCostMultiplier() {
        return this.routeFailureCostMultiplier;
    }

    /**
     * Sets the route failure cost multiplication factor.
     *
     * @param routeFailureCostMultiplier route failure cost multiplication
     * factor
     */
    public void SetRouteFailureCostMultiplier(double routeFailureCostMultiplier) {
        this.routeFailureCostMultiplier = routeFailureCostMultiplier;
    }

    /**
     * Returns container emergency collection cost.
     *
     * @return container emergency collection cost
     */
    public double GetEmegencyCost() {
        return this.emergencyCost;
    }

    /**
     * Sets the container emergency collection cost.
     *
     * @param emergencyCost container emergency collection cost
     */
    public void SetEmergencyCost(double emergencyCost) {
        this.emergencyCost = emergencyCost;
    }

    /**
     * Returns the original number of points, ie before any data restrictions
     * made in the Decomposition class.
     *
     * @return the original number of points, ie before any data restrictions
     * made in the Decomposition class
     */
    public int GetOriginalNumPoints() {
        return this.originalNumPoints;
    }
}
