package com.example.ar_base_app;

import com.esri.arcgisruntime.mapping.view.Graphic;

public class Plane {
    public String _callSign;
    public double _velocity;
    public double _verticalRateOfChange;
    public double _heading;
    public Graphic _graphic;
    public int _lastUpdate;
    public boolean _bigPlane;

    public Plane(Graphic graphic, double velocity, double verticalRateOfChange, double heading, int lastUpdate,
                 boolean bigPlane, String callSign) {
        _graphic = graphic;
        _velocity = velocity;
        _verticalRateOfChange = verticalRateOfChange;
        _heading = heading;
        _lastUpdate = lastUpdate;
        _bigPlane = bigPlane;
        _callSign = callSign;
    }
}
