package com.example.ar_base_app;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.layers.IntegratedMeshLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.ArcGISTiledElevationSource;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.NavigationConstraint;
import com.esri.arcgisruntime.mapping.view.AtmosphereEffect;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.SpaceEffect;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.arcgisruntime.toolkit.ar.ArcGISArView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private ArcGISArView mArView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get the AR view from the activity
        mArView = findViewById(R.id.arView);
        mArView.registerLifecycle(getLifecycle());

        // disable touch interactions with the scene view
        mArView.getSceneView().setOnTouchListener((view, motionEvent) -> true);
        
        ArcGISScene scene = new ArcGISScene(Basemap.createImagery());

        // create an integrated mesh layer
        Portal portal = new Portal("https://www.arcgis.com");
        PortalItem portalItem = new PortalItem(portal, "1f97ba887fd4436c8b17a14d83584611");
        IntegratedMeshLayer integratedMeshLayer = new IntegratedMeshLayer(portalItem);
        scene.getOperationalLayers().add(integratedMeshLayer);

        // create an elevation source and add it to the scene
        ArcGISTiledElevationSource elevationSource = new ArcGISTiledElevationSource(
                "https://elevation3d.arcgis.com/arcgis/rest/services/WorldElevation3D/Terrain3D/ImageServer");
        scene.getBaseSurface().getElevationSources().add(elevationSource);
        // disable the navigation constraint
        scene.getBaseSurface().setNavigationConstraint(NavigationConstraint.STAY_ABOVE);

        // add the scene to the scene view
        mArView.getSceneView().setScene(scene);

        // wait for the layer to load, then set the AR camera
        integratedMeshLayer.addDoneLoadingListener(() -> {
            if (integratedMeshLayer.getLoadStatus() == LoadStatus.LOADED) {
                Envelope envelope = integratedMeshLayer.getFullExtent();
                Camera camera = new Camera(envelope.getCenter().getY(), envelope.getCenter().getX(), 2500, 0, 90, 0);
                mArView.setOriginCamera(camera);
            } else {
                String error ="Error loading integrated mesh layer:" + integratedMeshLayer.getLoadError().getMessage();
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                Log.e(TAG, error);
            }
        });

        // set the translation factor to enable rapid movement through the scene
        mArView.setTranslationFactor(4000);

        // turn the space and atmosphere effects on for an immersive experience
        mArView.getSceneView().setSpaceEffect(SpaceEffect.STARS);
        mArView.getSceneView().setAtmosphereEffect(AtmosphereEffect.REALISTIC);
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        //start tracking when app is being used
        if (mArView != null) {
            mArView.startTracking(ArcGISArView.ARLocationTrackingMode.IGNORE);
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
