package com.example.ar_base_app;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.toolkit.ar.ArcGISArView;

public class MainActivity extends AppCompatActivity {

    private ArcGISArView mArView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get the AR view from the activity
        mArView = findViewById(R.id.arView);
        mArView.registerLifecycle(getLifecycle());

        //Adding these 2 lines shows the globe
        //This verifies we setup the app correctly
        ArcGISScene scene = new ArcGISScene(Basemap.createImagery());
        mArView.getSceneView().setScene(scene);
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        //start tracking when app is being used
        if (mArView != null) {
            mArView.startTracking(ArcGISArView.ARLocationTrackingMode.INITIAL);
        }
    }

    @Override
    public synchronized void onPause() {
        //stop tracking when app is not being used
        if (mArView != null) {
            mArView.stopTracking();
        }
        super.onPause();
    }
}
