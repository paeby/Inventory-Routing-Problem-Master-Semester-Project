package alns;

import alns.data.Data;
import alns.data.Truck;
import alns.param.Parameters;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * This class is used for generating real instances for comparison to executed
 * tours and serializing them to files.
 *
 * @author Markov
 * @version 1.0
 */
public class GR {

    // DB connection
    private Connection conn = null;

    // Date formatter
    private final DateFormat formatter;

    // Zone wid, client wid, flow type wid, tour type wid,
    // forecast type, and forecast model type
    private final int zoneWid;
    private final int clientWid;
    private final int flowTypeWid;
    private final int tourTypeWid;
    private final int forecastType;
    private final int modelType;

    // Tour start time, tour end time, tour max duration
    private final double tourStartTime;
    private final double tourEndTime;
    private final double tourMaxDur;

    // Container overflow cost, emergency collection cost
    // and route failure cost multiplier
    private final double overflowCost;
    private final double emergencyCost;
    private final double routeFailureCostMultiplier;

    // Number of days to extract data for
    private final int nbDays;

    // Collections
    private final ArrayList<Date> mondays;
    private final LinkedHashMap<Date, ArrayList<Integer>> tourWidsMondays;

    /**
     * Assignment and initialization constructor.
     *
     * @param nbDays number of days to extract data for
     * @param zoneWid zone wid
     * @param clientWid client wid
     * @param flowTypeWid flow type wid
     * @param tourTypeWid tour type wid
     * @param forecastType forecast type
     * @param modelType forecast model type
     * @param tourStartTime tour start time
     * @param tourEndTime tour end time
     * @param tourMaxDur tour max duration
     * @param overflowCost container overflow cost
     * @param emergencyCost container emergency collection cost
     * @param routeFailureCostMultiplier route failure cost multiplier
     *
     * @throws java.sql.SQLException
     * @throws org.rosuda.REngine.Rserve.RserveException
     * @throws org.rosuda.REngine.REXPMismatchException
     * @throws java.io.IOException
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.text.ParseException
     */
    public GR(int nbDays, int zoneWid, int clientWid, int flowTypeWid, int tourTypeWid, int forecastType, int modelType,
            double tourStartTime, double tourEndTime, double tourMaxDur,
            double overflowCost, double emergencyCost, double routeFailureCostMultiplier)
            throws SQLException, RserveException, REXPMismatchException, IOException, NoSuchAlgorithmException, ParseException {

        // Assign zone wid, client wid, flow type wid, tour type wid,
        // forecast type and model type
        this.zoneWid = zoneWid;
        this.clientWid = clientWid;
        this.flowTypeWid = flowTypeWid;
        this.tourTypeWid = tourTypeWid;
        this.forecastType = forecastType;
        this.modelType = modelType;

        // Assign tour start time, tour end time, tour max duration
        this.tourStartTime = tourStartTime;
        this.tourEndTime = tourEndTime;
        this.tourMaxDur = tourMaxDur;

        // Assign container overflow cost, emergency collection cost
        // and route failure cost multiplier
        this.overflowCost = overflowCost;
        this.emergencyCost = emergencyCost;
        this.routeFailureCostMultiplier = routeFailureCostMultiplier;

        // Number of days to extract data for
        this.nbDays = nbDays;

        // Data formatter
        this.formatter = new SimpleDateFormat("yyyy-MM-dd");

        // Initialize collections
        this.mondays = new ArrayList<>();
        this.tourWidsMondays = new LinkedHashMap<>();

        // Establish DB connection
        System.out.println(">>> OPENING DB CONNECTION...");
        this.conn = DriverManager.getConnection("jdbc:postgresql://localhost/" + Parameters.dbName
                + "?user=" + Parameters.dbUser
                + "&password=" + Parameters.dbPassword);

        // Immediately run a query for removing duplicates from the tourcontainer table
        PreparedStatement statement;
        statement = this.conn.prepareStatement("DELETE FROM "
                + "containerlevel "
                + "WHERE id "
                + "IN (SELECT id FROM "
                + "(SELECT id, ROW_NUMBER() OVER (partition BY container_wid, time_stamp ORDER BY id) AS rnum FROM containerlevel) t "
                + "WHERE t.rnum > 1)");
        statement.execute();
        statement.close();
    }

    /**
     * Extracts an ArrayList of Mondays in the historical collection period.
     *
     * @throws java.sql.SQLException
     * @throws java.text.ParseException
     */
    private void extractMondays() throws SQLException, ParseException {

        // SQL query
        PreparedStatement statement;
        statement = this.conn.prepareStatement("SELECT DISTINCT DATE(execution) AS monday"
                + " FROM tour"
                + " WHERE EXTRACT(DOW FROM execution) = 1"
                + " ORDER BY monday");

        // Execute query
        ResultSet result = statement.executeQuery();

        // Fill in the ArrayList of Mondays
        while (result.next()) {
            String monday = result.getString("monday");
            this.mondays.add(this.formatter.parse(monday));
        }

        // Clean up
        result.close();
        statement.close();
    }

    /**
     * Extracts a LinkedHashMap of Mondays to tour wids executed in the weeks
     * starting on the Mondays.
     *
     * @param monday Monday in the historical collection period
     * @throws java.sql.SQLException
     */
    private void extractTourWidsMondays() throws SQLException {

        // For each Monday in the ArrayList of Mondays
        for (Date monday : this.mondays) {

            // SQL query
            PreparedStatement statement;
            statement = this.conn.prepareStatement("SELECT DISTINCT(wid) as tour_wid"
                    + " FROM tour"
                    + " WHERE client_wid = " + this.clientWid
                    + " AND flowtype_wid = " + this.flowTypeWid
                    + " AND tourtype_wid = " + this.tourTypeWid
                    + " AND validation IS NOT NULL"
                    + " AND DATE(execution) >= '" + this.formatter.format(monday) + "'::date"
                    + " AND DATE(execution) <= ('" + this.formatter.format(monday) + "'::date + '" + (this.nbDays - 1) + " days'::interval)");

            // Execute query
            ResultSet result = statement.executeQuery();

            // Tour wids for the week starting on this Monday
            ArrayList<Integer> tourWids = new ArrayList<>();
            while (result.next()) {
                tourWids.add(result.getInt("tour_wid"));
            }

            // Clean up
            result.close();
            statement.close();

            // Put in map
            this.tourWidsMondays.put(monday, tourWids);
        }
    }

    /**
     * Returns flexible starting point wids corresponding to the client, zone
     * wid and flow type wid. The additional parameter that is used is kind =
     * 'COLLECTION', i.e. for points actually belonging to the collector and not
     * to EcoWaste for after sales, for example.
     *
     * @param homeSPWid home starting point wid
     * @return flexible starting point wids corresponding to the client, zone
     * wid and flow type wid
     * @throws java.sql.SQLException
     */
    private ArrayList<Integer> getFlexStartingPointWids(int homeSPWid) throws SQLException {

        // ArrayList of flexible starting point wids
        ArrayList<Integer> flexStartingPointWids = new ArrayList<>();

        // SQL query
        PreparedStatement statement;
        statement = this.conn.prepareStatement("SELECT wid AS startingpoint_wid FROM startingpoint"
                + " WHERE client_wid = " + this.clientWid
                + " AND (zone_wid IS NULL OR zone_wid = " + this.zoneWid + ")"
                + " AND (flowtype_wid IS NULL OR flowtype_wid = " + this.flowTypeWid + ")"
                + " AND kind = 'COLLECTION'");

        // Execute query
        ResultSet result = statement.executeQuery();

        // Fill in the ArrayList of flexible startnig point wids
        while (result.next()) {
            int spWid = result.getInt("startingpoint_wid");
            // We do not want to repeat the home starting point wid
            if (spWid != homeSPWid) {
                flexStartingPointWids.add(spWid);
            }
        }

        // Clean up
        result.close();
        statement.close();

        // Return flexible starting point wids
        return flexStartingPointWids;
    }

    /**
     * Returns an ArrayList of dump wids corresponding to the client, zone wid
     * and flow type wid.
     *
     * @return an ArrayList of dump wids
     * @throws java.sql.SQLException
     */
    private ArrayList<Integer> getDumpWids() throws SQLException {

        // ArrayList of dump wids
        ArrayList<Integer> dumpWids = new ArrayList<>();

        // SQL query
        PreparedStatement statement;
        statement = this.conn.prepareStatement("SELECT wid AS dump_wid FROM dump"
                + " WHERE client_wid = " + this.clientWid
                + " AND (zone_wid IS NULL OR zone_wid = " + this.zoneWid + ")"
                + " AND (flowtype_wid IS NULL OR flowtype_wid = " + this.flowTypeWid + ")");

        // Execute query
        ResultSet result = statement.executeQuery();

        // Fill in ArrayList of dump wids
        while (result.next()) {
            dumpWids.add(result.getInt("dump_wid"));
        }

        // Clean up
        result.close();
        statement.close();

        // Returns dump wids
        return dumpWids;
    }

    /**
     * Returns an ArrayList of boolean for the availabilities of the passed
     * truck and for the days of the planning horizon starting on the passed
     * Monday. Truck availabilities are determined by checking on which days the
     * truck performed tours of another flow type. For these days, the truck is
     * considered unavailable for collecting this.flowTypeWid.
     *
     * @param truckWid truck wid
     * @param monday Monday in the historical collection period
     * @return ArrayList of boolean for the availabilities on the days of the
     * planning horizon
     * @throws java.sql.SQLException
     * @throws java.text.ParseException
     */
    private ArrayList<Boolean> getFlowCollectionBasedAvailabilities(int truckWid, Date monday) throws SQLException, ParseException {

        // Initialize an ArrayList of availabilities
        ArrayList<Boolean> availabilities = new ArrayList<>();

        // SQL query
        PreparedStatement statement;
        statement = this.conn.prepareStatement("SELECT DISTINCT DATE(execution) AS date"
                + " FROM tour"
                + " WHERE truck_wid = " + truckWid
                + " AND flowtype_wid != " + this.flowTypeWid // flow type is different from the current flow wid!!!
                + " AND tourtype_wid = " + this.tourTypeWid
                + " AND validation IS NOT NULL"
                + " AND DATE(execution) >=  '" + this.formatter.format(monday) + "'::date"
                + " AND DATE(execution) <= ('" + this.formatter.format(monday) + "'::date + '" + (this.nbDays - 1) + " days'::interval)"
                + " ORDER BY date");

        // Execute query
        ResultSet result = statement.executeQuery();

        // Fill in the array of dates on which the truck is used for collecing other flow types
        ArrayList<Date> usedDates = new ArrayList<>();
        while (result.next()) {
            String date = result.getString("date");
            usedDates.add(this.formatter.parse(date));
        }

        // Clean up
        result.close();
        statement.close();

        // Get Calendar instance and set date to the passed Monday
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(monday);
        // Get date
        Date date = calendar.getTime();

        // Loop over the planning horizon
        for (int d = 0; d < this.nbDays; d++) {
            // If the date is in the list of dates on which the truck is used for collecting
            // other flow types or it is Saturday or Sunday, add false to availabilities; otherwise add true
            if (usedDates.contains(date)
                    || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                    || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                availabilities.add(false);
            } else {
                availabilities.add(true);
            }
            // Increment date by one day
            calendar.add(Calendar.DATE, 1);
            date = calendar.getTime();
        }

        // Return availabilities
        return availabilities;
    }

    /**
     * Returns an ArrayList of default truck availabilities. It contains true
     * for all working days and false for Saturday and Sunday.
     *
     * @param truckWid truck wid
     * @param monday Monday in the historical collection period
     * @return
     */
    private ArrayList<Boolean> getDefaultAvailabilities(int truckWid, Date monday) {

        // Initialize an ArrayList of default availabilities
        ArrayList<Boolean> availabilities = new ArrayList<>();

        // Get Calendar instance and set date to the passed Monday
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(monday);

        // Loop over the planning horizon, add false for Saturday and Sunday
        // and true for all other days
        for (int d = 0; d < this.nbDays; d++) {
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                    || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                availabilities.add(false);
            } else {
                availabilities.add(true);
            }
            // Increment calendar by one day
            calendar.add(Calendar.DATE, 1);
        }

        // Return default availabilities
        return availabilities;
    }

    /**
     * Returns an ArrayList of default required returns to home. Each day in the
     * planning horizon is a required return to home.
     *
     * @param truckWid truck wid
     * @return an ArrayList of required returns to home
     */
    private ArrayList<Boolean> getDefaultRequiredReturnsToHome(int truckWid) {

        // Initialize an ArrayList of required returns to home
        ArrayList<Boolean> requiredReturnsToHome = new ArrayList<>();

        // Fill in with true for the planning horizon
        for (int d = 0; d < this.nbDays; d++) {
            requiredReturnsToHome.add(true);
        }

        // Return required returns to home
        return requiredReturnsToHome;
    }

    /**
     * Extracts the truck, starting point and container wids from the week of
     * collections starting on the passed Monday. They correspond to the passed
     * client wid and flow type wid and for a tour of type collection. The truck
     * to starting point map is based on the starting point that was recorded
     * for each tour plus the flexible starting point wids.
     *
     * @param monday Monday in the historical collection period
     * @return an ImmutablePair of truck to starting points map and container
     * wids
     * @throws java.sql.SQLException
     */
    private ImmutablePair<LinkedHashMap<Integer, ArrayList<Integer>>, ArrayList<Integer>>
            extractTruckSPAndContainerWids(Date monday) throws SQLException {

        // Tour wids for this Monday
        String tourWids = this.tourWidsMondays.get(monday).toString()
                .substring(1, this.tourWidsMondays.get(monday).toString().length() - 1);

        // SQL query
        PreparedStatement statement;
        statement = this.conn.prepareStatement("SELECT DISTINCT(tc.container_wid), t.truck_wid, t.startingpoint_wid "
                + " FROM tourcontainer tc"
                + " INNER JOIN tour t"
                + " ON tc.tour_wid = t.wid"
                + " WHERE POSITION(CAST(t.wid AS TEXT) IN '" + tourWids + "') > 0");

        // Execute query
        ResultSet result = statement.executeQuery();

        // Truck to starting points map and container wids for a new instance
        LinkedHashMap<Integer, ArrayList<Integer>> truckSP = new LinkedHashMap<>();
        ArrayList<Integer> containerWids = new ArrayList<>();

        // Populate container wids and truck to starting points map for a new instance
        while (result.next()) {

            // Add next container
            containerWids.add(result.getInt("container_wid"));

            // Initialize an ArrayList of starting points to pass to the truck
            ArrayList<Integer> spList = new ArrayList<>();
            // Home and current starting point is the same and is the one from the tour table
            int homeSPWid = result.getInt("startingpoint_wid");
            int currSPWid = homeSPWid;
            // Flexible starting point wids
            ArrayList<Integer> flexSPWids = this.getFlexStartingPointWids(homeSPWid);
            // Add home, current, and flexible starting point wids
            spList.add(homeSPWid);
            spList.add(currSPWid);
            for (Integer flexSPWid : flexSPWids) {
                spList.add(flexSPWid);
            }
            // Add to map
            truckSP.put(result.getInt("truck_wid"), spList);
        }

        // Clean up
        result.close();
        statement.close();

        // Return an immutable pair of truck to starting points map and container wids
        return new ImmutablePair<>(truckSP, containerWids);
    }

    /**
     * Returns the total number of container visits during the week starting on
     * Monday.
     *
     * @param monday Monday in the historical collection period
     * @return the total number of container visits during the week starting on
     * Monday
     * @throws java.sql.SQLException
     */
    private int getWeeklyNumContainerVisits(Date monday) throws SQLException {

        // Tour wids for this Monday
        String tourWids = this.tourWidsMondays.get(monday).toString()
                .substring(1, this.tourWidsMondays.get(monday).toString().length() - 1);

        // SQL query
        PreparedStatement statement;
        statement = this.conn.prepareStatement("SELECT"
                + " COUNT(tc.container_wid) AS count"
                + " FROM tourcontainer tc"
                + " WHERE POSITION(CAST(tc.tour_wid AS TEXT) IN '" + tourWids + "') > 0");

        // Execute query
        ResultSet result = statement.executeQuery();

        // Weekly number of container visits
        int weeklyNumContainerVisits = 0;

        // Update weekly number of container visits
        while (result.next()) {
            weeklyNumContainerVisits = result.getInt("count");
        }

        // Clean up
        result.close();
        statement.close();

        // Return weekly number of container visits
        return weeklyNumContainerVisits;
    }

    /**
     * Returns the total number of dump visits during the week starting on
     * Monday.
     *
     * @param monday Monday in the historical collection period
     * @return the total number of dump visits during the week starting on
     * Monday
     * @throws java.sql.SQLException
     */
    private int getWeeklyNumDumpVisits(Date monday) throws SQLException {

        // Tour wids for this Monday
        String tourWids = this.tourWidsMondays.get(monday).toString()
                .substring(1, this.tourWidsMondays.get(monday).toString().length() - 1);

        // SQL query
        PreparedStatement statement;
        statement = this.conn.prepareStatement("SELECT"
                + " COUNT(tc.dump_wid) AS count"
                + " FROM tourcontainer tc"
                + " WHERE POSITION(CAST(tc.tour_wid AS TEXT) IN '" + tourWids + "') > 0"
                + " AND tc.dump_wid IS NOT NULL");

        // Execute query
        ResultSet result = statement.executeQuery();

        // Weekly number of dump visits
        int weeklyNumDumpVisits = 0;

        // Update weekly number of dump visits
        while (result.next()) {
            weeklyNumDumpVisits = result.getInt("count");
        }

        // Clean up
        result.close();
        statement.close();

        // Returns weekly number of dump visits
        return Math.max(1, weeklyNumDumpVisits);
    }

    /**
     * Returns the total estimated volume collected in the week starting on the
     * passed Monday.
     *
     * @param monday Monday in the historical collection period
     * @return total estimated volume collected in the week starting on the
     * passed Monday
     * @throws java.sql.SQLException
     */
    private double getEstimatedExecutedWeeklyVolume(Date monday) throws SQLException {

        // Tour wids for this Monday
        String tourWids = this.tourWidsMondays.get(monday).toString()
                .substring(1, this.tourWidsMondays.get(monday).toString().length() - 1);

        // SQL query
        PreparedStatement statement;
        statement = this.conn.prepareStatement("SELECT"
                + " tc.container_wid,"
                + " (tc.estimatedlevel * ct.volume / 100) AS volume_load"
                + " FROM tourcontainer tc"
                + " LEFT JOIN container c"
                + " ON tc.container_wid = c.wid"
                + " LEFT JOIN containertype ct"
                + " ON c.containertype_wid = ct.wid"
                + " WHERE POSITION(CAST(tc.tour_wid AS TEXT) IN '" + tourWids + "') > 0");

        // Execute query
        ResultSet result = statement.executeQuery();

        // Initialize total weekly volume as 0
        double totalWeeklyVolume = 0.d;

        // Update total weekly volume
        while (result.next()) {
            totalWeeklyVolume += result.getDouble("volume_load");
        }

        // Clean up
        result.close();
        statement.close();

        // Return total weekly volume
        return totalWeeklyVolume;
    }

    /**
     * Returns the average estimated level at collection on the days of the
     * planning horizon.
     *
     * @param monday Monday in the historical collection period
     * @return the average estimated level at collection on the days of the
     * planning horizon
     * @throws java.sql.SQLException
     */
    private ArrayList<String> getAvgEstimatedLevelsAtCollectionOnDays(Date monday) throws SQLException {

        // Tour wids for this Monday
        String tourWids = this.tourWidsMondays.get(monday).toString()
                .substring(1, this.tourWidsMondays.get(monday).toString().length() - 1);

        // Get Calendar instance and set date to the passed Monday
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(monday);
        // Get date
        Date date = calendar.getTime();

        // Average estimaed level at collection on the days of the planning horizon
        ArrayList<String> avgEstimatedLevelsAtCollectionOnDays = new ArrayList<>();

        // Loop over the planning horizon and estimate average estimated level at collection
        for (int d = 0; d < this.nbDays; d++) {

            // SQL query
            PreparedStatement statement;
            statement = this.conn.prepareStatement("SELECT"
                    + " AVG(tc.estimatedlevel) AS avg_estimatedlevel"
                    + " FROM tourcontainer tc"
                    + " LEFT JOIN tour t"
                    + " ON t.wid = tc.tour_wid"
                    + " WHERE POSITION(CAST(tc.tour_wid AS TEXT) IN '" + tourWids + "') > 0"
                    + " AND DATE(t.execution) = '" + this.formatter.format(date) + "'::date"
            );

            // Execute query
            ResultSet result = statement.executeQuery();

            // Update average estimated level at collection on this day and add to ArrayList
            String avgEstimatedLevel = "";
            while (result.next()) {
                avgEstimatedLevel = result.getString("avg_estimatedlevel");
            }
            avgEstimatedLevelsAtCollectionOnDays.add(avgEstimatedLevel);

            // Clean up
            result.close();
            statement.close();

            // Increment date by one day
            calendar.add(Calendar.DATE, 1);
            date = calendar.getTime();
        }

        // Return average estimated levels at collection on
        // the days of the planning horizon
        return avgEstimatedLevelsAtCollectionOnDays;
    }

    /**
     * Returns the total reported volume collected in the week starting on the
     * passed Monday.
     *
     * @param monday Monday in the historical collection period
     * @return total reported volume collected in the week starting on the
     * passed Monday
     * @throws java.sql.SQLException
     */
    private double getReportedExecutedWeeklyVolume(Date monday) {
        // TO CHECK IF IT MAKES SENSE TO IMPLEMENT THIS
        return Parameters._404;
    }

    /**
     * Returns an Immutable pair of total weekly cost and number of dump visits
     * for the week starting on the passed Monday.
     *
     * @param monday Monday in the historical collection period
     * @return an Immutable pair of total weekly cost and number of dump visits
     * @throws java.sql.SQLException
     */
    private ImmutablePair<Double, Integer> getExecutedWeeklyCostAndNumDumpVisits(Date monday) throws SQLException {

        // Initialize total weekly cost as 0
        double totalWeeklyCost = 0.d;
        int totalWeeklyNumDumpVisits = 0;

        for (int tourWid : this.tourWidsMondays.get(monday)) {

            // SQL query
            PreparedStatement statement;
            statement = this.conn.prepareStatement("SELECT"
                    + " tc.container_wid, ct.collectiontime, tc.ecopoint_wid, tc.dump_wid, d.waittime,"
                    + " t.truck_wid, tr.fixed_cost, tr.distance_cost, tr.time_cost, tr.speed, t.startingpoint_wid"
                    + " FROM tourcontainer tc"
                    + " LEFT JOIN tour t"
                    + " ON tc.tour_wid = t.wid"
                    + " LEFT JOIN dump d"
                    + " ON tc.dump_wid = d.wid"
                    + " LEFT JOIN container c"
                    + " ON tc.container_wid = c.wid"
                    + " LEFT JOIN containertype ct"
                    + " ON c.containertype_wid = ct.wid"
                    + " LEFT JOIN truck tr"
                    + " ON t.truck_wid = tr.wid"
                    + " WHERE t.wid = " + tourWid
                    + " ORDER BY order_c ASC"); // very important to order ascending

            // Execute query
            ResultSet result = statement.executeQuery();

            // Starting point wid
            int startingPointWid = 0;
            // Truck costs and speed
            double truckFixedCost = 0.d;
            double truckDistanceCost = 0.d;
            double truckTimeCost = 0.d;
            double truckSpeed = 0.d;
            // Tour sequence of dwids and tour service time
            ArrayList<Integer> tourDWidSequence = new ArrayList<>();
            double tourServiceTime = 0.d;

            // Number of ecopoints in tour
            int numEcopoints = 0;

            // A flag denoting whether the last point before the depot in the database is a dump
            boolean lastPointIsDump = false;

            // Loop over results
            while (result.next()) {

                // Update number of ecopoints
                numEcopoints++;

                // Starting point
                startingPointWid = result.getInt("startingpoint_wid");

                // Truck costs
                truckFixedCost = result.getDouble("fixed_cost");
                truckDistanceCost = result.getDouble("distance_cost");
                truckTimeCost = result.getDouble("time_cost");
                truckSpeed = result.getDouble("speed");

                // Tour sequence of dwids and tour service time, 
                // accounting for presence of dumps
                tourDWidSequence.add(result.getInt("ecopoint_wid"));
                tourServiceTime += result.getDouble("collectiontime") / 60.d;
                lastPointIsDump = false;
                if (result.getInt("dump_wid") != 0) {
                    tourDWidSequence.add(result.getInt("dump_wid"));
                    tourServiceTime += result.getDouble("waittime") / 60.d; // THIS IS ALWAYS NULL IN THE DB
                    lastPointIsDump = true;
                    totalWeeklyNumDumpVisits++;
                }
            }

            // Clean up
            result.close();
            statement.close();

            // ArrayList of available dump wids
            ArrayList<Integer> avblDumpWids = new ArrayList<>();

            // Add starting point wid to the beginning
            tourDWidSequence.add(0, startingPointWid);
            // If lastPoint is a dump, add only it to the list of available dump wids for the search below.
            // Otherwise, add all available dumps for the search below, and add the first of them to the 
            // tourDWidSequence.
            if (lastPointIsDump) {
                avblDumpWids.add(tourDWidSequence.get(tourDWidSequence.size() - 1));
            } else {
                avblDumpWids = this.getDumpWids();
                tourDWidSequence.add(avblDumpWids.get(0));
                totalWeeklyNumDumpVisits++;
            }
            // Add starting point wid to the end
            tourDWidSequence.add(startingPointWid);

            // Find the best configuration for the final dump
            double bestTourCost = Double.POSITIVE_INFINITY;
            for (int dumpWid : avblDumpWids) {

                // Set new final dump
                tourDWidSequence.set(tourDWidSequence.size() - 2, dumpWid);

                // Query to extract all necessary distances
                String distanceQuery = "SELECT distance FROM distance WHERE false";
                for (int i = 0; i < tourDWidSequence.size() - 1; i++) {
                    distanceQuery += " OR (wid_start = " + tourDWidSequence.get(i) + " AND wid_end = " + tourDWidSequence.get(i + 1) + ")";
                }
                // Prepare statement
                statement = this.conn.prepareStatement(distanceQuery);
                // Execute query
                result = statement.executeQuery();

                // Calculate total tour distance in km
                double tourLength = 0.d;
                while (result.next()) {
                    tourLength += result.getDouble("distance") / 1000.d;
                }

                // Clean up
                result.close();
                statement.close();

                // Tour duration is travel time plus service time
                double tourDuration = (tourLength / truckSpeed) + tourServiceTime;

                // Calculate tour cost, compare to best tour cost and update if better
                double tourCost = truckFixedCost + truckDistanceCost * tourLength + truckTimeCost * tourDuration;
                if (tourCost < bestTourCost) {
                    bestTourCost = tourCost;
                }

                // As a sanity check, reassign 0 to best tour cost if there
                // are no ecopoints in the tour
                if (numEcopoints == 0) {
                    bestTourCost = 0;
                }
            }

            // Update total weekly cost by this tour's cost
            totalWeeklyCost += bestTourCost;
        }

        // Return an immutable pair of total weekly cost and number of dump visits
        return new ImmutablePair<>(totalWeeklyCost, totalWeeklyNumDumpVisits);
    }

    /**
     * Generates and exports IRP instances, each representing a week starting on
     * Monday.
     *
     * @throws java.sql.SQLException
     * @throws org.rosuda.REngine.Rserve.RserveException
     * @throws org.rosuda.REngine.REXPMismatchException
     * @throws java.io.IOException
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.text.ParseException
     */
    public void GenerateAndExportInstances() throws SQLException, RserveException,
            REXPMismatchException, IOException, NoSuchAlgorithmException, ParseException {

        // Print message
        System.out.println("\n\n\n>>> GENERATING HISTORICAL INSTANCES FOR"
                + " FORECAST TYPE " + this.forecastType
                + " AND MODEL TYPE " + this.modelType);

        // Extract Mondays and tour wids for each Monday
        this.extractMondays();
        this.extractTourWidsMondays();

        // Print writer for exporting cost and volume of executed tours
        PrintWriter writer = new PrintWriter(new FileWriter(Parameters.csvExportFolder + "executedTourCharacteristics" + this.flowTypeWid + ".csv"), true);
        writer.write("Instance, Cost, Volume, Num_Tours, Num_ContVisits, Num_DumpVisits");
        for (int d = 0; d < this.nbDays; d++) {
            writer.write(", avgEstimatedLevelAtCollectionOnDay_" + d);
        }
        writer.write("\n");

        // For the week starting on each Monday, build and serialize an instance
        // and add volume and cost to the csv file for the executed tours
        for (Date monday : this.mondays) {

            // Print message
            System.out.println("\n\n\n>>> GENERATING INSTANCE FOR " + this.formatter.format(monday));

            // Update monday table so that when the Data object is created, the Forecast class will receive the correct
            // demand forecasts, error sigma, and probabilities
            PreparedStatement statement;
            statement = this.conn.prepareStatement("UPDATE monday"
                    + " SET monday = '" + this.formatter.format(monday) + "'::date,"
                    + " forecast_type = " + this.forecastType + ","
                    + " model_type = " + this.modelType + ","
                    + " flowtype_wid = " + this.flowTypeWid
                    + " WHERE id = 1");
            statement.execute();
            statement.close();

            // Extract the truck to starting point map and the containers on this Monday
            ImmutablePair<LinkedHashMap<Integer, ArrayList<Integer>>, ArrayList<Integer>> truckSPAndcontainerWids = this.extractTruckSPAndContainerWids(monday);
            LinkedHashMap<Integer, ArrayList<Integer>> truckSP = truckSPAndcontainerWids.getLeft();
            ArrayList<Integer> containerWids = truckSPAndcontainerWids.getRight();

            // Build a data object. It will extract the correct dumps based on the additional parameters it receives (zone wid in particular).
            // When the Forecast class is initialized inside Data, and when it calls the R based functions
            // they will check the Monday, forecast type, model type, and flowtype wid from the monday table
            Data data = new Data(truckSP, containerWids, this.zoneWid, this.clientWid, this.flowTypeWid, this.nbDays);
            // Set new tour start time, tour end time, tour max duration
            data.SetTourStartTime(this.tourStartTime);
            data.SetTourEndTime(this.tourEndTime);
            data.SetTourMaxDur(this.tourMaxDur);
            // Set new container overflow cost, emergency collection cost and route failure cost multiplier
            data.SetOverflowCost(this.overflowCost);
            data.SetEmergencyCost(this.emergencyCost);
            data.SetRouteFailureCostMultiplier(this.routeFailureCostMultiplier);
            // Set new truck availabilities and required returns to home
            for (Truck truck : data.GetTrucks()) {
                truck.SetAvailabilities(this.getDefaultAvailabilities(truck.GetWid(), monday));
                truck.SetRequiredReturnsToHome(this.getDefaultRequiredReturnsToHome(truck.GetWid()));
            }

            // Instance file name
            String fileName = "DATA_C" + this.clientWid + "_F" + this.flowTypeWid + "_M" + this.formatter.format(monday)
                    + "_FT" + this.forecastType + "_MT" + this.modelType + ".ser";
            // Serialize and export only if it satisfies the data completeness criteria
            if (data.VerifyCompleteness() == true) {
                System.out.println(">>> INSTANCE PASSED VERIFICATION TEST. EXPORTING...");
                FileData.ExportData(data, Parameters.dataObjectFolder ,fileName);
                // Write to csv file
                // Formatted string of average estimated levels at collection on day of the planning horizon
                String getAvgEstimatedLevelsAtCollectionOnDays
                        = this.getAvgEstimatedLevelsAtCollectionOnDays(monday).toString()
                        .substring(1, this.getAvgEstimatedLevelsAtCollectionOnDays(monday).toString().length() - 1);
                // Executed weekly cost and number of dump visits
                ImmutablePair<Double, Integer> executedWeeklyCostAndNumDumpVisits = this.getExecutedWeeklyCostAndNumDumpVisits(monday);
                double executedWeeklyCost = executedWeeklyCostAndNumDumpVisits.getLeft();
                int executedWeeklyNumDumpVisits = executedWeeklyCostAndNumDumpVisits.getRight();
                writer.write(fileName
                        + "," + executedWeeklyCost
                        + "," + this.getEstimatedExecutedWeeklyVolume(monday)
                        + "," + this.tourWidsMondays.get(monday).size()
                        + "," + this.getWeeklyNumContainerVisits(monday)
                        + "," + executedWeeklyNumDumpVisits
                        + "," + getAvgEstimatedLevelsAtCollectionOnDays
                        + "\n");
            } else {
                System.out.println(">>> INSTANCE DID NOT PASS VERIFICATION TEST...");
            }
        }

        // Close writer
        writer.close();
    }

    /**
     * Closes DB connection.
     *
     * @throws SQLException
     */
    public void Close() throws SQLException {
        // Close connection
        System.out.println(">>> CLOSING DB CONNECTION...");
        this.conn.close();
    }
}
