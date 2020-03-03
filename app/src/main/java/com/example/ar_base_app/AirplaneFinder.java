package com.example.ar_base_app;

import com.esri.arcgisruntime.geometry.AngularUnit;
import com.esri.arcgisruntime.geometry.AngularUnitId;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.GeodeticCurveType;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.LinearUnit;
import com.esri.arcgisruntime.geometry.LinearUnitId;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReference;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LayerSceneProperties;
import com.esri.arcgisruntime.symbology.ModelSceneSymbol;
import com.esri.arcgisruntime.symbology.Renderer;
import com.esri.arcgisruntime.symbology.SimpleRenderer;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class AirplaneFinder {

    public GraphicsOverlay _graphicsOverlay;
    private ModelSceneSymbol _smallPlane3DSymbol;
    private ModelSceneSymbol _largePlane3DSymbol;
    private int _smallPlaneSize = 60;
    private int _largePlaneSize = 20;
    public Map<String, Plane> planes = new HashMap();
    public Point _center;
    public double _coordinateTolerance = 0.5;
    private  int _secondsPerCleanup = 30;
    private int _updatesPerSecond = 30;
    private  int _secondsPerQuery = 10;
    private String _basePath;
    public AirplaneFinder(GraphicsOverlay go, String basePath)
    {
        _graphicsOverlay = go;
        _basePath = basePath;
        setupScene();
    }

    public void setCenter(Point center)
    {
        _center = center;
    }

    public void setupScene() {
        //Setup plane symbols
        _smallPlane3DSymbol = new ModelSceneSymbol(GetSmallPlane(), _smallPlaneSize);
        _largePlane3DSymbol = new ModelSceneSymbol(GetLargePlane(), _largePlaneSize);
        _smallPlane3DSymbol.loadAsync();
        _largePlane3DSymbol.loadAsync();

        _graphicsOverlay.getSceneProperties().setSurfacePlacement(LayerSceneProperties.SurfacePlacement.ABSOLUTE);
        SimpleRenderer renderer3D = new SimpleRenderer();
        Renderer.SceneProperties renderProperties = renderer3D.getSceneProperties();
        renderProperties.setHeadingExpression("[HEADING]");
        renderProperties.setPitchExpression("[PITCH]");
        renderProperties.setRollExpression("[Roll]");
        _graphicsOverlay.setRenderer(renderer3D);
    }

    private String GetSmallPlane()
    {
        String ret_val = _basePath + "/Bristol.dae";
        return ret_val;
    }

    private String GetLargePlane()
    {
        String ret_val = _basePath + "/B_787_8.dae";
        return ret_val;
    }

    private int updateCounter = -1;
    public void animatePlanes()
    {
        updateCounter++;
        //deletes planes that haven't sent us a new update in a while
        if (updateCounter % (_secondsPerCleanup * _updatesPerSecond) == 0)
        {
            ArrayList<String> planes_to_remove = new ArrayList();
            int unixTimestamp = (int) (System.currentTimeMillis() / 1000);
            for (HashMap.Entry<String, Plane> plane : planes.entrySet()) {
                if(unixTimestamp - plane.getValue()._lastUpdate > _secondsPerCleanup)
                {
                    _graphicsOverlay.getGraphics().remove(plane.getValue()._graphic);
                    planes_to_remove.add(plane.getKey());
                }
            }

            for(String callsign : planes_to_remove)
            {
                planes.remove(callsign);
            }
        }
        //if we haven't queried the nearby planes locations in a while do this
        if (updateCounter % (_updatesPerSecond * _secondsPerQuery) == 0)
        {
            AddPlanesViaAPI();
        }
        //Move the planes every frame along their heading with the given velocity
        else
        {
            try
            {
                for (HashMap.Entry<String, Plane> plane : planes.entrySet()) {
                    Point location = (Point)plane.getValue()._graphic.getGeometry();
                    Point updatedLocation = GeometryEngine.moveGeodetic(location,
                            plane.getValue()._velocity / _updatesPerSecond,
                            new LinearUnit(LinearUnitId.METERS),
                            plane.getValue()._heading,
                            new AngularUnit(AngularUnitId.DEGREES),
                            GeodeticCurveType.GEODESIC);

                    double delta_z = updatedLocation.getZ() + (plane.getValue()._verticalRateOfChange / _updatesPerSecond);
                    Point updated_map_location = new Point(updatedLocation.getX(), updatedLocation.getY(), delta_z, SpatialReference.create(4326));
                    plane.getValue()._graphic.setGeometry(updated_map_location);
                    if(!plane.getValue()._bigPlane)
                    {
                        plane.getValue()._graphic.getAttributes().put("HEADING", plane.getValue()._heading);
                    }
                }

            }catch(Exception e){}
        }
    }

    //Queries opensky to get the nearby planes and adds them into the list of planes we are drawing
    private void AddPlanesViaAPI()
    {
        try {
            Point center;
            if (_center == null) {
                //defaulting to Redlands, CA
                center = new Point(-117.18, 33.5556, SpatialReference.create(4326));
            } else {
                center = _center;
            }

            Envelope searchArea = new Envelope(center, _coordinateTolerance, _coordinateTolerance);
            double xMax = searchArea.getXMax();
            double xMin = searchArea.getXMin();
            double yMax = searchArea.getYMax();
            double yMin = searchArea.getYMin();

            String request = "https://opensky-network.org/api/states/all?lamin=" + yMin +
                    "&lomin=" + xMin + "&lamax=" + yMax + "&lomax=" + xMax;
            URL url = new URL(request);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            Scanner sc = new Scanner(in);
            String responseString = "";
            while(sc.hasNext())
            {
                responseString += sc.next();
            }

            int unixTimestamp = (int) (System.currentTimeMillis() / 1000);
            String states = responseString.substring(30, responseString.length() - 30);
            String[] elements = states.split("\\[");

            for(int i = 0; i < elements.length; i++)
            {
                String[] attributes = elements[i].split(",");
                //check if the plane has a valid location
                if(attributes[5] != "null" && attributes[6] != "null")
                {
                    String callsign = attributes[1].substring(1, attributes[1].length() - 2);
                    int last_timestamp = 0;
                    double lon = Double.parseDouble(attributes[5]);
                    double lat = Double.parseDouble(attributes[6]);
                    double alt = 0.0;
                    if (attributes[13] != "null")
                    {
                        alt = Double.parseDouble(attributes[13]);
                    }
                    else if(attributes[7] != "null")
                    {
                        alt = Double.parseDouble(attributes[7]);
                    }
                    double velocity = 0.0;
                    double heading = 0.0;
                    double vert_rate = 0.0;
                    if (attributes[9] != "null")
                    {
                        velocity = Double.parseDouble(attributes[9]);
                    }

                    if (attributes[10] != "null")
                    {
                        heading = Double.parseDouble(attributes[10]);
                    }

                    if (attributes[11] != "null")
                    {
                        vert_rate = Double.parseDouble(attributes[11]);
                    }

                    if (attributes[3] != "null")
                    {
                        last_timestamp = Integer.parseInt(attributes[3]);
                    }

                    Point location = new Point(lon, lat, alt, SpatialReference.create(4326));
                    int time_difference = unixTimestamp - last_timestamp;

                    Point updatedLocation = GeometryEngine.moveGeodetic(location,
                            velocity * time_difference,
                            new LinearUnit(LinearUnitId.METERS),
                            heading,
                            new AngularUnit(AngularUnitId.DEGREES),
                            GeodeticCurveType.GEODESIC);

                    double delta_z = updatedLocation.getZ() + (vert_rate * time_difference);
                    Point updated_map_location = new Point(updatedLocation.getX(), updatedLocation.getY(), delta_z, SpatialReference.create(4326));

                    if(planes.containsKey(callsign))
                    {
                        Plane currPlane = planes.get(callsign);
                        currPlane._graphic.setGeometry(updated_map_location);
                        currPlane._graphic.getAttributes().put("HEADING", heading + 180);
                        currPlane._graphic.getAttributes().put("CALLSIGN", callsign);
                        currPlane._velocity = velocity;
                        currPlane._verticalRateOfChange = vert_rate;
                        currPlane._heading = heading;
                        currPlane._lastUpdate = last_timestamp;
                    }
                    else
                    {
                        // the N at the beginning of the callsign means its a small plane
                        if(callsign.length() > 0 && callsign.charAt(0) == 'N')
                        {
                            Graphic gr = new Graphic(updated_map_location, _smallPlane3DSymbol);
                            gr.getAttributes().put("HEADING", heading);
                            gr.getAttributes().put("CALLSIGN", callsign);
                            Plane p = new Plane(gr, velocity, vert_rate, heading, last_timestamp, false, callsign);
                            planes.put(callsign, p);
                            _graphicsOverlay.getGraphics().add(gr);
                        }
                        else
                        {
                            Graphic gr = new Graphic(updated_map_location, _largePlane3DSymbol);
                            gr.getAttributes().put("HEADING", heading + 180);
                            gr.getAttributes().put("CALLSIGN", callsign);
                            Plane p = new Plane(gr, velocity, vert_rate, heading, last_timestamp, true, callsign);
                            planes.put(callsign, p);
                            _graphicsOverlay.getGraphics().add(gr);
                        }
                    }
                }
            }


        } catch(Exception e){}
    }
}
