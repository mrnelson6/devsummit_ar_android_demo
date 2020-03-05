package com.example.ar_base_app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.GeodeticCurveType;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.LinearUnit;
import com.esri.arcgisruntime.geometry.LinearUnitId;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.NavigationConstraint;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.DefaultSceneViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.TransformationMatrixCameraController;
import com.esri.arcgisruntime.toolkit.ar.ArcGISArView;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ArcGISArView mArView;
    private boolean mHasConfiguredScene = false;
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get the AR view from the activity
        mArView = findViewById(R.id.arView);
        mArView.registerLifecycle(getLifecycle());

        requestPermissions();
        // on tap
        mArView.getSceneView().setOnTouchListener(new DefaultSceneViewOnTouchListener(mArView.getSceneView()) {
            @Override public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
                // get the hit results for the tap
                List<HitResult> hitResults = mArView.getArSceneView().getArFrame().hitTest(motionEvent);
                // check if the tapped point is recognized as a plane by ArCore
                if (!hitResults.isEmpty() && hitResults.get(0).getTrackable() instanceof Plane) {
                    // get a reference to the tapped plane
                    Plane plane = (Plane) hitResults.get(0).getTrackable();
                    Toast.makeText(MainActivity.this, "Plane detected with a width of: " + plane.getExtentX(), Toast.LENGTH_SHORT)
                            .show();
                    // get the tapped point as a graphics point
                    android.graphics.Point screenPoint = new android.graphics.Point(Math.round(motionEvent.getX()),
                            Math.round(motionEvent.getY()));
                    // if initial transformation set correctly
                    if (mArView.setInitialTransformationMatrix(screenPoint)) {
                        // the scene hasn't been configured
                        if (!mHasConfiguredScene) {
                            loadScene(plane);
                        } else if (mArView.getSceneView().getScene() != null) {
                            // use information from the scene to determine the origin camera and translation factor
                            updateTranslationFactorAndOriginCamera(mArView.getSceneView().getScene(), plane);
                        }
                    }
                } else {
                    String error = "ArCore doesn't recognize this point as a plane.";
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, error);
                }
                return super.onSingleTapConfirmed(motionEvent);
            }
        });
    }

    /**
     * Load the mobile scene package and get the first (and only) scene inside it. Set it to the ArView's SceneView and
     * set the base surface to opaque and remove any navigation constraint, thus allowing the user to look at a scene
     * from below. Then call updateTranslationFactorAndOriginCamera with the plane detected by ArCore.
     *
     * @param plane detected by ArCore based on a tap from the user. The loaded scene will be pinned on this plane.
     */
    private void loadScene(Plane plane) {
        // create a scene from a webscene
        ArcGISScene scene = new ArcGISScene("https://www.arcgis.com/home/webscene/viewer.html?webscene=6bf6d9f17bdd4d33837e25e1cae4e9c9");
        scene.addDoneLoadingListener(() -> {
            // if it loaded successfully and the mobile scene package contains a scene
            if (scene.getLoadStatus() == LoadStatus.LOADED) {
                // add the scene to the AR view's scene view
                mArView.getSceneView().setScene(scene);
                // set the base surface to fully opaque
                //set clipping distance and remove this
                mArView.setClippingDistance(180.0);
                //you could uncomment this line if you didn't want to see the base surface
                //scene.getBaseSurface().setOpacity(0);
                // let the camera move below ground
                scene.getBaseSurface().setNavigationConstraint(NavigationConstraint.NONE);
                mHasConfiguredScene = true;
                // set translation factor and origin camera for scene placement in AR
                updateTranslationFactorAndOriginCamera(scene, plane);
            } else {
                String error = "Failed to load the scene: " + scene.getLoadError()
                        .getMessage();
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                Log.e(TAG, error);
            }
        });
        // load the scene
        scene.loadAsync();
    }
    /**
     * Load the scene's first layer and calculate its geographical width. Use the scene's width and ArCore's assessment
     * of the plane's width to set the AR view's translation transformation factor. Use the center of the scene, corrected
     * for elevation, as the origin camera's look at point.
     *
     * @param scene to display
     * @param plane detected by ArCore to which the scene should be pinned
     */
    private void updateTranslationFactorAndOriginCamera(ArcGISScene scene, Plane plane) {
        boolean set_hardcoded_values = true;
        if(set_hardcoded_values)
        {
            mArView.setTranslationFactor(700);
            mArView.setOriginCamera(
                    new Camera(new Point(-117.168654, 32.71012, 0.0), 0, 90, 0));
        } else {
            //dynamically calculate the translation factor and origin camera based off the extent and elevation of the scene
            scene.getOperationalLayers().get(0).addDoneLoadingListener(() -> {
                // get the scene extent
                Envelope layerExtent = scene.getOperationalLayers().get(0).getFullExtent();
                // calculate the width of the layer content in meters
                double width = GeometryEngine
                        .lengthGeodetic(layerExtent, new LinearUnit(LinearUnitId.METERS), GeodeticCurveType.GEODESIC);
                // set the translation factor based on scene content width and desired physical size
                mArView.setTranslationFactor(width / plane.getExtentX());
                // find the center point of the scene content
                Point centerPoint = layerExtent.getCenter();
                // find the altitude of the surface at the center
                ListenableFuture<Double> elevationFuture = mArView.getSceneView().getScene().getBaseSurface()
                        .getElevationAsync(centerPoint);
                elevationFuture.addDoneListener(() -> {
                    try {
                        double elevation = elevationFuture.get();
                        // create a new origin camera looking at the bottom center of the scene
                        mArView.setOriginCamera(
                                new Camera(new Point(centerPoint.getX(), centerPoint.getY(), elevation), 0, 90, 0));
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting elevation at point: " + e.getMessage());
                    }
                });
            });
            // load the scene's first layer
            scene.getOperationalLayers().get(0).loadAsync();
        }
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

    /**
     * Request read external storage for API level 23+.
     */
    private void requestPermissions() {
        // define permission to request
        String[] reqPermission = { Manifest.permission.INTERNET,
                Manifest.permission.CAMERA};
        int requestCode = 2;
        if (ContextCompat.checkSelfPermission(this, reqPermission[0]) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, reqPermission[1]) == PackageManager.PERMISSION_GRANTED){
        } else {
            // request permission
            ActivityCompat.requestPermissions(this, reqPermission, requestCode);
        }
    }

    /**
     * Handle the permissions request response.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (grantResults.length >= 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED)
        {
        } else {
            // report to user that permission was denied
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
