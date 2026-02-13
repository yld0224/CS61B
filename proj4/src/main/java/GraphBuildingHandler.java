import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 *  Parses OSM XML files using an XML SAX parser. Used to construct the graph of roads for
 *  pathfinding, under some constraints.
 *  See OSM documentation on
 *  <a href="http://wiki.openstreetmap.org/wiki/Key:highway">the highway tag</a>,
 *  <a href="http://wiki.openstreetmap.org/wiki/Way">the way XML element</a>,
 *  <a href="http://wiki.openstreetmap.org/wiki/Node">the node XML element</a>,
 *  and the java
 *  <a href="https://docs.oracle.com/javase/tutorial/jaxp/sax/parsing.html">SAX parser tutorial</a>.
 *
 *  You may find the CSCourseGraphDB and CSCourseGraphDBHandler examples useful.
 *
 *  The idea here is that some external library is going to walk through the XML
 *  file, and your override method tells Java what to do every time it gets to the next
 *  element in the file. This is a very common but strange-when-you-first-see it pattern.
 *  It is similar to the Visitor pattern we discussed for graphs.
 *
 *  @author Alan Yao, Maurice Lee
 */
public class GraphBuildingHandler extends DefaultHandler {
    /**
     * Only allow for non-service roads; this prevents going on pedestrian streets as much as
     * possible. Note that in Berkeley, many of the campus roads are tagged as motor vehicle
     * roads, but in practice we walk all over them with such impunity that we forget cars can
     * actually drive on them.
     */
    private static final Set<String> ALLOWED_HIGHWAY_TYPES = new HashSet<>(Arrays.asList
            ("motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
                    "residential", "living_street", "motorway_link", "trunk_link", "primary_link",
                    "secondary_link", "tertiary_link"));
    private String activeState = "";
    private final GraphDB g;
    private GraphDB.Node currentNode;
    private GraphDB.Way currentWay;
    private boolean currentWayValidity;

    /**
     * Create a new GraphBuildingHandler.
     * @param g The graph to populate with the XML data.
     */
    public GraphBuildingHandler(GraphDB g) {
        this.g = g;
    }

    /**
     * Called at the beginning of an element. Typically, you will want to handle each element in
     * here, and you may want to track the parent element.
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or
     *            if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace
     *                  processing is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are
     *              not available. This tells us which element we're looking at.
     * @param attributes The attributes attached to the element. If there are no attributes, it
     *                   shall be an empty Attributes object.
     * @throws SAXException Any SAX exception, possibly wrapping another exception.
     * @see Attributes
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (qName.equals("node")) {
            /* We encountered a new <node...> tag. */
            activeState = "node";
            long id = Long.parseLong(attributes.getValue("id"));
            double lat = Double.parseDouble(attributes.getValue("lat"));
            double lon = Double.parseDouble(attributes.getValue("lon"));
            GraphDB.Node n = new GraphDB.Node(id, lat, lon);
            g.addNode(n);
            currentNode = n;
        } else if (qName.equals("way")) {
            /* We encountered a new <way...> tag. */
            activeState = "way";
            long id = Long.parseLong(attributes.getValue("id"));
            GraphDB.Way w = new GraphDB.Way(id);
            g.addWay(w);
            currentWay = w;
        } else if (activeState.equals("way") && qName.equals("nd")) {
            /* While looking at a way, we found a <nd...> tag. */
            long Wayid = Long.parseLong(attributes.getValue("id"));
            long Nodeid = Long.parseLong(attributes.getValue("ref"));
            GraphDB.Node n = g.Nodes.get(Nodeid);
            currentNode = n;
            n.ways.put(Wayid, currentWay);
            if (n.ways.size() >= 2 || currentWay.nodes.isEmpty()) {
                n.isVertex = true;
                g.Vertices.put(n.id, n);
                currentWay.vertices.add(n);
            }
            currentWay.nodes.add(n);
        } else if (activeState.equals("way") && qName.equals("tag")) {
            /* While looking at a way, we found a <tag...> tag. */
            String k = attributes.getValue("k");
            String v = attributes.getValue("v");
            if (k.equals("maxspeed")) {
                currentWay.tags.put(k, v);
            } else if (k.equals("highway")) {
                currentWay.tags.put(k, v);
                if (ALLOWED_HIGHWAY_TYPES.contains(v)) {
                    currentWayValidity = true;
                }else {
                    currentWayValidity = false;
                }
            } else if (k.equals("name")) {
                currentWay.tags.put(k, v);
            }
        } else if (activeState.equals("node") && qName.equals("tag") && attributes.getValue("k")
                .equals("name")) {
                currentNode.tags.put("name", attributes.getValue("v"));
        }
    }

    /**
     * Receive notification of the end of an element. You may want to take specific terminating
     * actions here, like finalizing vertices or edges found.
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or
     *            if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace
     *                  processing is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are
     *              not available.
     * @throws SAXException  Any SAX exception, possibly wrapping another exception.
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("way")) {
            if (currentWayValidity) {
                currentNode.isVertex = true;
                g.Vertices.put(currentNode.id, currentNode);
                currentWay.vertices.add(currentNode);
            }//The last Node of the way must be a vertex.
            else{
                g.Ways.remove(currentWay.id);
                ArrayList<GraphDB.Node> lst = currentWay.nodes;
                for (GraphDB.Node n : lst) {
                    n.ways.remove(currentWay.id);
                    if (n.ways.size() < 2){
                        n.isVertex = false;
                        g.Vertices.remove(n.id);
                    }
                }
            }//The way is invalid, reset all the details.
        }
    }

}
