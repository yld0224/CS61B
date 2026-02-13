import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.TreeMap;

/**
 * Graph for storing all of the intersection (vertex) and road (edge) information.
 * Uses your GraphBuildingHandler to convert the XML files into a graph. Your
 * code must include the vertices, adjacent, distance, closest, lat, and lon
 * methods. You'll also need to include instance variables and methods for
 * modifying the graph (e.g. addNode and addEdge).
 *
 * @author Alan Yao, Josh Hug
 */
public class GraphDB {
    public static class Node {
        long id;
        double lat;
        double lon;
        boolean isVertex = false;
        TreeMap<String,String> tags = new TreeMap<>();
        LinkedHashMap<Long,Way> ways = new LinkedHashMap<>();
        Node(long id, double lat, double lon) {
            this.id = id;
            this.lat = lat;
            this.lon = lon;
        }
    }
    public static class Way{
        long id;
        ArrayList<Node> nodes = new ArrayList<>();
        ArrayList<Node> vertices = new ArrayList<>();
        TreeMap<String,String> tags = new TreeMap<>();
        Way(long id) {
            this.id = id;
        }
    }

    LinkedHashMap<Long,Way> Ways = new LinkedHashMap<>();
    LinkedHashMap<Long,Node> Nodes = new LinkedHashMap<>();
    LinkedHashMap<Long,Node> Vertices = new LinkedHashMap<>();

    public void addNode(Node n){
        Nodes.put(n.id, n);
        if (n.isVertex){
            Vertices.put(n.id, n);
        }
    }
    public void addWay(Way w){
        Ways.put(w.id, w);
    }
    /**
     * Example constructor shows how to create and start an XML parser.
     * You do not need to modify this constructor, but you're welcome to do so.
     * @param dbPath Path to the XML file to be parsed.
     */
    public GraphDB(String dbPath) {
        try {
            File inputFile = new File(dbPath);
            FileInputStream inputStream = new FileInputStream(inputFile);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            GraphBuildingHandler gbh = new GraphBuildingHandler(this);
            saxParser.parse(inputStream, gbh);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        clean();
    }

    /**
     * Helper to process strings into their "cleaned" form, ignoring punctuation and capitalization.
     * @param s Input string.
     * @return Cleaned string.
     */
    static String cleanString(String s) {
        return s.replaceAll("[^a-zA-Z ]", "").toLowerCase();
    }

    /**
     *  Remove nodes with no connections from the graph.
     *  While this does not guarantee that any two nodes in the remaining graph are connected,
     *  we can reasonably assume this since typically roads are connected.
     */
    private void clean() {
        java.util.Iterator<java.util.Map.Entry<Long,Node>> iter = Nodes.entrySet().iterator();
        while (iter.hasNext()) {
            java.util.Map.Entry<Long,Node> entry = iter.next();
            if (entry.getValue().ways.isEmpty()){
                iter.remove();
            }
        }
    }

    /**
     * Returns an iterable of all vertex IDs in the graph.
     * @return An iterable of id's of all vertices in the graph.
     */
    Iterable<Long> vertices() {
        return Vertices.keySet();
    }

    /**
     * Returns ids of all vertices adjacent to v.
     * @param v The id of the vertex we are looking adjacent to.
     * @return An iterable of the ids of the neighbors of v.
     */
    Iterable<Long> adjacent(long v) {
        LinkedHashMap<Long,Way> ws = Vertices.get(v).ways;
        ArrayList<Long> ret = new ArrayList<>();
        for (Way w : ws.values()){
            for (int i = 0; i < w.vertices.size(); ++i){
                if (w.vertices.get(i).id == v){
                    if (validateIndex(i-1, w.vertices)){
                        ret.add(w.vertices.get(i-1).id);
                    }
                    if (validateIndex(i+1, w.vertices)){
                        ret.add(w.vertices.get(i+1).id);
                    }
                }
            }
        }
        return ret;
    }

    boolean validateIndex(int index, ArrayList<Node> vertices){
        return index >= 0 && index < vertices.size();
    }

    /**
     * Returns the great-circle distance between vertices v and w in miles.
     * Assumes the lon/lat methods are implemented properly.
     * <a href="https://www.movable-type.co.uk/scripts/latlong.html">Source</a>.
     * @param v The id of the first vertex.
     * @param w The id of the second vertex.
     * @return The great-circle distance between the two locations from the graph.
     */
    double distance(long v, long w) {
        return distance(lon(v), lat(v), lon(w), lat(w));
    }

    static double distance(double lonV, double latV, double lonW, double latW) {
        double phi1 = Math.toRadians(latV);
        double phi2 = Math.toRadians(latW);
        double dphi = Math.toRadians(latW - latV);
        double dlambda = Math.toRadians(lonW - lonV);

        double a = Math.sin(dphi / 2.0) * Math.sin(dphi / 2.0);
        a += Math.cos(phi1) * Math.cos(phi2) * Math.sin(dlambda / 2.0) * Math.sin(dlambda / 2.0);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 3963 * c;
    }

    /**
     * Returns the initial bearing (angle) between vertices v and w in degrees.
     * The initial bearing is the angle that, if followed in a straight line
     * along a great-circle arc from the starting point, would take you to the
     * end point.
     * Assumes the lon/lat methods are implemented properly.
     * <a href="https://www.movable-type.co.uk/scripts/latlong.html">Source</a>.
     * @param v The id of the first vertex.
     * @param w The id of the second vertex.
     * @return The initial bearing between the vertices.
     */
    double bearing(long v, long w) {
        return bearing(lon(v), lat(v), lon(w), lat(w));
    }

    static double bearing(double lonV, double latV, double lonW, double latW) {
        double phi1 = Math.toRadians(latV);
        double phi2 = Math.toRadians(latW);
        double lambda1 = Math.toRadians(lonV);
        double lambda2 = Math.toRadians(lonW);

        double y = Math.sin(lambda2 - lambda1) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2);
        x -= Math.sin(phi1) * Math.cos(phi2) * Math.cos(lambda2 - lambda1);
        return Math.toDegrees(Math.atan2(y, x));
    }

    /**
     * Returns the vertex closest to the given longitude and latitude.
     * @param lon The target longitude.
     * @param lat The target latitude.
     * @return The id of the node in the graph closest to the target.
     */
    long closest(double lon, double lat) {
        double minDist = Double.MAX_VALUE;
        long id = -1;
        for (Node vertex :Vertices.values()){
            double dist =  distance(lon, lat, vertex.lon, vertex.lat);
            if (dist < minDist){
                minDist = dist;
                id = vertex.id;
            }
        }
        return id;
    }

    /**
     * Gets the longitude of a vertex.
     * @param v The id of the vertex.
     * @return The longitude of the vertex.
     */
    double lon(long v) {
        return Nodes.get(v).lon;
    }

    /**
     * Gets the latitude of a vertex.
     * @param v The id of the vertex.
     * @return The latitude of the vertex.
     */
    double lat(long v) {return Nodes.get(v).lat;}
}
