package alns.data;

import alns.param.Parameters;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Represents a truck and extends getter methods for all of its characteristics
 *
 * @author Markov
 * @version 1.0
 */
public class Truck implements Serializable {

    // Descriptive data
    private final int wid;              // truck wid
    private final int type;             // truck type
    private final String identifier;    // truck identifier
    private final String name;          // truck name
    private final int clientWid;        // truck client wid
    private final int zoneWid;          // truck zone wid
    private final int flowWid;          // truck flow type wid
    private final double maxVolume;     // truck maximum volume in liters
    private final double maxWeight;     // truck maximum weight in kg
    private final double speed;         // truck speed in km/h
    private final double fixedCost;     // truck fixed cost
    private final double distanceCost;  // truck running cost per km
    private final double timeCost;      // truck driver wage per hour

    // Truck home starting point - the truck's assignment starting point, or "home" depot.
    private final Point homeStartingPoint;
    // Truck current starting point - the truck's starting point at the launch of the algorithm.
    // It could be different or the same as the home starting point.
    private Point currStartingPoint;
    // Flexible starting points are starting points at which the truck is allowed to finish a tour. 
    // By default, this list includes the home starting point as well, and may include any other starting points.
    // When a truck finishes its tour at a given starting point, it starts its next tour from that same starting point.
    // The code takes care of truck relocations as well, i.e. a truck may be moved from one starting point
    // to another on a given day, without collecting any containers, and the relocation cost is taken into account.
    private final ArrayList<Point> flexStartingPoints;
    // A boolean list with true on a given day if the truck is required to return to its home depot on that day and false
    // if not. That is, when we provide flexible starting points, the truck is allowed to use them, but we may still
    // want force the truck to return to its home starting depot on a given day. This list serves exactly this purpose.
    private final ArrayList<Boolean> requiredReturnsToHome;
    // Truck availabilities for the planning horizon - true if the truck is available on a givne day and false if not.
    private final ArrayList<Boolean> availabilities;

    /**
     * Assignment constructor.
     *
     * @param wid truck wid
     * @param type truck type
     * @param identifier truck identifier
     * @param name truck name
     * @param clientWid truck client wid
     * @param zoneWid truck zone wid
     * @param flowWid truck flow type wid
     * @param maxVolume truck maximum volume in liters
     * @param maxWeight truck maximum weight in kg
     * @param speed truck speed in km/h
     * @param fixedCost truck fixed cost
     * @param distanceCost truck running cost per km
     * @param timeCost truck driver wage per hour
     * @param homeStartingPoint truck home starting point
     * @param currStartingPoint truck current starting point
     * @param flexStartingPoints truck flexible starting points
     * @param requiredReturnsToHome a boolean list with true if the truck is
     * required to return to the home depot on a given day, and false if not
     * @param availabilities a boolean list of truck availabilities for the
     * planning horizon (starting today)
     */
    public Truck(int wid, int type, String identifier, String name, int clientWid, int zoneWid, int flowWid,
            double maxVolume, double maxWeight, double speed, double fixedCost, double distanceCost, double timeCost,
            Point homeStartingPoint, Point currStartingPoint, ArrayList<Point> flexStartingPoints,
            ArrayList<Boolean> requiredReturnsToHome, ArrayList<Boolean> availabilities) {

        // Initialize fields
        this.wid = wid;
        this.type = type;
        this.identifier = identifier;
        this.name = name;
        this.clientWid = clientWid;
        this.zoneWid = zoneWid;
        this.flowWid = flowWid;
        this.maxVolume = maxVolume;
        this.maxWeight = maxWeight;
        this.speed = speed;
        this.fixedCost = fixedCost;
        this.distanceCost = distanceCost;
        this.timeCost = timeCost;

        // Home, current and flexile starting points, and 
        // required returns to the home starting point are assigned by value of reference
        this.homeStartingPoint = homeStartingPoint;
        this.currStartingPoint = currStartingPoint;
        this.flexStartingPoints = flexStartingPoints;
        this.requiredReturnsToHome = requiredReturnsToHome;
        // Availabilities are assigned by value of reference
        this.availabilities = availabilities;
    }

    /**
     * Alternative assignment constructor for the benchmark instances.
     *
     * @param wid truck wid
     * @param maxVolume truck maximum volume in liters
     * @param speed truck speed in km/h
     * @param fixedCost truck fixed cost
     * @param distanceCost truck running cost per km
     * @param timeCost truck driver wage per hour
     * @param homeStartingPoint truck home starting point
     * @param currStartingPoint truck current starting point
     * @param flexStartingPoints truck flexible starting points
     * @param requiredReturnsToHome a boolean list with true if the truck is
     * required to return to the home depot on a given day, and false if not
     * @param availabilities a boolean list of truck availabilities for the
     * planning horizon (starting today)
     */
    public Truck(int wid, double maxVolume, double speed, double fixedCost, double distanceCost, double timeCost,
            Point homeStartingPoint, Point currStartingPoint, ArrayList<Point> flexStartingPoints,
            ArrayList<Boolean> requiredReturnsToHome, ArrayList<Boolean> availabilities) {

        // Initialize fields
        this.wid = wid;
        this.type = Parameters._404;
        this.identifier = Parameters.emptyString;
        this.name = Parameters.emptyString;
        this.clientWid = Parameters._404;
        this.zoneWid = Parameters._404;
        this.flowWid = Parameters._404;
        this.maxVolume = maxVolume;
        this.maxWeight = Double.POSITIVE_INFINITY;
        this.speed = speed;
        this.fixedCost = fixedCost;
        this.distanceCost = distanceCost;
        this.timeCost = timeCost;

        // Home, current and flexile starting points, and 
        // required returns to the home starting point are assigned by value of reference
        this.homeStartingPoint = homeStartingPoint;
        this.currStartingPoint = currStartingPoint;
        this.flexStartingPoints = flexStartingPoints;
        this.requiredReturnsToHome = requiredReturnsToHome;
        // Availabilities are assigned by value of reference
        this.availabilities = availabilities;
    }

    /**
     * Copy constructor.
     *
     * @param truck Truck reference
     */
    public Truck(Truck truck) {

        // Copy descriptive data by value
        this.wid = truck.wid;
        this.type = truck.type;
        this.identifier = truck.identifier;
        this.name = truck.name;
        this.clientWid = truck.clientWid;
        this.zoneWid = truck.zoneWid;
        this.flowWid = truck.flowWid;
        this.maxVolume = truck.maxVolume;
        this.maxWeight = truck.maxWeight;
        this.speed = truck.speed;
        this.fixedCost = truck.fixedCost;
        this.distanceCost = truck.distanceCost;
        this.timeCost = truck.timeCost;

        // Home, current and flexile starting points, and 
        // required returns to the home starting point are copied by value of reference
        this.homeStartingPoint = truck.homeStartingPoint;
        this.currStartingPoint = truck.currStartingPoint;
        this.flexStartingPoints = truck.flexStartingPoints;
        this.requiredReturnsToHome = truck.requiredReturnsToHome;
        // Availabilities are copied by value of reference 
        this.availabilities = truck.availabilities;
    }

    /**
     * Returns truck wid.
     *
     * @return truck wid
     */
    public int GetWid() {
        return this.wid;
    }

    /**
     * Returns truck type.
     *
     * @return truck type
     */
    public int GetType() {
        return this.type;
    }

    /**
     * Returns truck identifier.
     *
     * @return truck identifier
     */
    public String GetIdentifier() {
        return this.identifier;
    }

    /**
     * Returns truck name.
     *
     * @return truck name
     */
    public String GetName() {
        return this.name;
    }

    /**
     * Returns truck client wid
     *
     * @return truck client wid
     */
    public int GetClientWid() {
        return this.clientWid;
    }

    /**
     * Returns truck zone wid.
     *
     * @return truck zone wid
     */
    public int GetZoneWid() {
        return this.zoneWid;
    }

    /**
     * Returns truck flow type wid.
     *
     * @return truck flow type wid
     */
    public int GetFlowWid() {
        return this.flowWid;
    }

    /**
     * Returns truck max effective volume in liters.
     *
     * @return truck max effective volume in liters
     */
    public double GetMaxEffectiveVolume() {
        return this.maxVolume * Parameters.policyTruckEffectiveVolumeFraction;
    }

    /**
     * Returns truck max volume in liters.
     *
     * @return truck max volume in liters
     */
    public double GetMaxVolume() {
        return this.maxVolume;
    }

    /**
     * Returns truck max effective weight in kg.
     *
     * @return truck max effective weight in kg
     */
    public double GetMaxEffectiveWeight() {
        return this.maxWeight * Parameters.policyTruckEffectiveWeightFraction;
    }

    /**
     * Returns truck max weight in kg.
     *
     * @return truck max weight in kg
     */
    public double GetMaxWeight() {
        return this.maxWeight;
    }

    /**
     * Returns truck speed in km/h.
     *
     * @return truck speed in km/h
     */
    public double GetSpeed() {
        return this.speed;
    }

    /**
     * Returns truck fixed cost.
     *
     * @return truck fixed cost
     */
    public double GetFixedCost() {
        return this.fixedCost;
    }

    /**
     * Returns truck running cost per km.
     *
     * @return truck running cost per km
     */
    public double GetDistanceCost() {
        return this.distanceCost;
    }

    /**
     * Returns truck driver wage per hour.
     *
     * @return truck driver wage per hour
     */
    public double GetTimeCost() {
        return this.timeCost;
    }

    /**
     * Returns the truck home starting point. The home starting point is the
     * starting point to which the truck is normally assigned. We assume it is
     * preferable but not required for the truck to return to this starting
     * point at the end of the tour.
     *
     * @return the truck home starting point
     */
    public Point GetHomeStartingPoint() {
        return this.homeStartingPoint;
    }

    /**
     * Returns the truck current starting point. The current starting point is
     * the starting point where the truck is located at the launch of the
     * algorithm and from which it therefore departs on day = 0. It could be the
     * same or different from the home starting point.
     *
     * @return the truck current starting point
     */
    public Point GetCurrentStartingPoint() {
        return this.currStartingPoint;
    }
    
    /**
     * Sets the truck current starting point. The current starting point is
     * the starting point where the truck is located at the launch of the
     * algorithm and from which it therefore departs on day = 0. It could be the
     * same or different from the home starting point.
     * 
     * @param currStartingPoint the truck current starting point
     */
    public void SetCurrentStartingPoint(Point currStartingPoint) {
        this.currStartingPoint = currStartingPoint;
    }

    /**
     * Returns an ArrayList of the truck flexible starting points. The flexible
     * starting points are the starting points where the truck is allowed to
     * finish a tour (in addition to its home starting point), and from where it
     * departs the next day (if it is in the planning horizon).
     *
     * @return an ArrayList of the truck flexible starting points
     */
    public ArrayList<Point> GetFlexStartingPoints() {
        return this.flexStartingPoints;
    }

    /**
     * Returns true if the truck is required to return to its home starting
     * point on day d of the planning horizon, and false if not.
     *
     * @param d day in the planning horizon
     * @return true if the truck is required to return to its home starting
     * point on day d of the planning horizon, and false if not
     */
    public boolean RequiredReturnToHome(int d) {
        return this.requiredReturnsToHome.get(d);
    }

    /**
     * Sets required returns to home for all days of the planning horizon.
     *
     * @param requiredReturnsToHome ArrayList of boolean for new required
     * returns to home
     */
    public void SetRequiredReturnsToHome(ArrayList<Boolean> requiredReturnsToHome) {
        // Clear required returns to home
        this.requiredReturnsToHome.clear();
        // Reset required returns to home
        for (boolean requiredReturnToHome : requiredReturnsToHome) {
            this.requiredReturnsToHome.add(requiredReturnToHome);
        }
    }

    /**
     * Returns the size of the ArrayList of required returns to the home
     * starting point.
     *
     * @return the size of the ArrayList of required returns to the home
     * starting point
     */
    public int GetNumRequiredReturns() {
        return this.requiredReturnsToHome.size();
    }

    /**
     * Returns the truck availability on the specified day in the planning
     * horizon.
     *
     * @param d day in the planning horizon
     * @return the truck availability on the specified day in the planning
     * horizon
     */
    public boolean IsAvailable(int d) {
        return this.availabilities.get(d);
    }

    /**
     * Sets availabilities for all days of the planning horizon.
     *
     * @param availabilities ArrayList of boolean for new availabilities
     */
    public void SetAvailabilities(ArrayList<Boolean> availabilities) {
        // Clear availabilities
        this.availabilities.clear();
        // Reset availabilities
        for (boolean availability : availabilities) {
            this.availabilities.add(availability);
        }
    }

    /**
     * Returns the size of the ArrayList of availabilities.
     *
     * @return the size of the ArrayList of availabilities
     */
    public int GetNumAvailabilities() {
        return this.availabilities.size();
    }

}
