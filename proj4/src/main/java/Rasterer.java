import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * This class provides all code necessary to take a query box and produce
 * a query result. The getMapRaster method must return a Map containing all
 * seven of the required fields, otherwise the front end code will probably
 * not draw the output correctly.
 */
public class Rasterer {

    public static final double ROOT_ULLAT = 37.892195547244356, ROOT_ULLON = -122.2998046875,
            ROOT_LRLAT = 37.82280243352756, ROOT_LRLON = -122.2119140625;

    LinkedList<Double> lonDPPs = new LinkedList<>();
    public Rasterer() {
        double tmp =  (ROOT_LRLON - ROOT_ULLON) / 256.0;
        for (int i = 0; i <= 7; ++i){
            lonDPPs.add(tmp);
            tmp /= 2;
        }
    }

    /**
     * Takes a user query and finds the grid of images that best matches the query. These
     * images will be combined into one big image (rastered) by the front end. <br>
     *
     *     The grid of images must obey the following properties, where image in the
     *     grid is referred to as a "tile".
     *     <ul>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         (LonDPP) possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     *
     * @param params Map of the HTTP GET request's query parameters - the query box and
     *               the user viewport width and height.
     *
     * @return A map of results for the front end as specified: <br>
     * "render_grid"   : String[][], the files to display. <br>
     * "raster_ul_lon" : Number, the bounding upper left longitude of the rastered image. <br>
     * "raster_ul_lat" : Number, the bounding upper left latitude of the rastered image. <br>
     * "raster_lr_lon" : Number, the bounding lower right longitude of the rastered image. <br>
     * "raster_lr_lat" : Number, the bounding lower right latitude of the rastered image. <br>
     * "depth"         : Number, the depth of the nodes of the rastered image <br>
     * "query_success" : Boolean, whether the query was able to successfully complete; don't
     *                    forget to set this to true on success! <br>
     */
    public Map<String, Object> getMapRaster(Map<String, Double> params) {
        double lonDPP = (params.get("lrlon") - params.get("ullon")) / params.get("w");
        int d = 7;
        for (int i = 0; i <= 7; ++i){
            if (lonDPPs.get(i) <= lonDPP){
                d = i;
                break;
            }
        }
        double cur_ur_ullon = ROOT_ULLON;
        double cur_ur_lrlon = ROOT_LRLON;
        double cur_ur_ullat = ROOT_ULLAT;
        double cur_ur_lrlat = ROOT_LRLAT;
        for (int i = 0; i < d; ++i){
            double avg_x = (cur_ur_ullon + cur_ur_lrlon) / 2;
            double avg_y = (cur_ur_ullat + cur_ur_lrlat) / 2;
            if (params.get("ullon") < avg_x && params.get("ullat") > avg_y){
                cur_ur_lrlon = avg_x;
                cur_ur_lrlat = avg_y;
            }
            else if (params.get("ullon") >= avg_x && params.get("ullat") > avg_y){
                cur_ur_ullon = avg_x;
                cur_ur_lrlat = avg_y;
            }else if (params.get("ullon") >= avg_x && params.get("ullat") <= avg_y){
                cur_ur_ullon = avg_x;
                cur_ur_ullat = avg_y;
            }else if(params.get("ullon") < avg_x && params.get("ullat") <= avg_y){
                cur_ur_ullat = avg_y;
                cur_ur_lrlon = avg_x;
            }
        }
        double cur_lr_ullon = ROOT_ULLON;
        double cur_lr_lrlon = ROOT_LRLON;
        double cur_lr_ullat = ROOT_ULLAT;
        double cur_lr_lrlat = ROOT_LRLAT;
        for (int i = 0; i < d; ++i){
            double avg_x = (cur_lr_ullon + cur_lr_lrlon) / 2;
            double avg_y = (cur_lr_ullat + cur_lr_lrlat) / 2;
            if (params.get("lrlon") <= avg_x && params.get("lrlat") >= avg_y){
                cur_lr_lrlon = avg_x;
                cur_lr_lrlat = avg_y;
            }
            else if (params.get("lrlon") > avg_x && params.get("lrlat") >= avg_y){
                cur_lr_ullon = avg_x;
                cur_lr_lrlat = avg_y;
            }else if (params.get("lrlon") > avg_x && params.get("lrlat") < avg_y){
                cur_lr_ullon = avg_x;
                cur_lr_ullat = avg_y;
            }else if(params.get("lrlon") <= avg_x && params.get("lrlat") < avg_y){
                cur_lr_ullat = avg_y;
                cur_lr_lrlon = avg_x;
            }
        }
        Map<String, Object> results = new HashMap<>();
        results.put("raster_ul_lon",cur_ur_ullon);
        results.put("raster_ul_lat",cur_ur_ullat);
        results.put("raster_lr_lon",cur_lr_lrlon);
        results.put("raster_lr_lat",cur_lr_lrlat);
        results.put("depth",d);
        long ul_x = java.lang.Math.round((cur_ur_ullon - ROOT_ULLON) / (ROOT_LRLON - ROOT_ULLON) * (1 << d));
        long ul_y = java.lang.Math.round((cur_ur_ullat - ROOT_ULLAT) / (ROOT_LRLAT - ROOT_ULLAT) * (1 << d));
        long lr_x = java.lang.Math.round((cur_lr_ullon - ROOT_ULLON) / (ROOT_LRLON - ROOT_ULLON) * (1 << d));
        long lr_y = java.lang.Math.round((cur_lr_ullat - ROOT_ULLAT) / (ROOT_LRLAT - ROOT_ULLAT) * (1 << d));
        String[][] render_grid = new String[(int)(lr_y - ul_y + 1)][(int)(lr_x - ul_x + 1)];
        for (int i = 0; i < render_grid.length; ++i){
            for (int j = 0; j < render_grid[0].length; ++j){
                render_grid[i][j] = "d" + d + "_" + "x" + (ul_x + j) + "_" + "y" +  (ul_y + i) + ".png";
            }
        }
        results.put("render_grid", render_grid);
        results.put("query_success", true);
        return results;
    }

}
