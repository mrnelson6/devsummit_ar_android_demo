package com.example.ar_base_app;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.widget.Button;

import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.ArcGISTiledElevationSource;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.NavigationConstraint;
import com.esri.arcgisruntime.mapping.Surface;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.toolkit.ar.ArLocationDataSource;
import com.esri.arcgisruntime.toolkit.ar.ArcGISArView;
import com.esri.arcgisruntime.toolkit.control.JoystickSeekBar;

public class MainActivity extends AppCompatActivity {

    private ArcGISArView mArView;
    private float mCurrentVerticalOffset = 30;
    private boolean mIsCalibrating = false;
    private View mCalibrationView;
    private GraphicsOverlay _graphicsOverlay;
    private AirplaneFinder _airplaneFinder;
    Handler handler;
    HandlerThread handlerThread = new HandlerThread("Animation");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        //get the AR view from the activity
        mArView = findViewById(R.id.arView);
        mArView.registerLifecycle(getLifecycle());

        setupScene();
        setupCalibration();
    }

    private void setupCalibration()
    {
        mCalibrationView = findViewById(R.id.calibrationView);

        // show/hide calibration view
        Button calibrationButton = findViewById(R.id.calibrateButton);
        calibrationButton.setOnClickListener(v -> {
            // toggle calibration
            mIsCalibrating = !mIsCalibrating;
            if (mIsCalibrating) {
                mArView.getSceneView().getScene().getBaseSurface().setOpacity(0.7f);
                mCalibrationView.setVisibility(View.VISIBLE);
            } else {
                mArView.getSceneView().getScene().getBaseSurface().setOpacity(0.2f);
                mCalibrationView.setVisibility(View.GONE);
            }
        });

        // wire up joystick seek bars to allow manual calibration of height and heading
        JoystickSeekBar headingJoystick = findViewById(R.id.headingJoystick);
        // listen for calibration value changes for heading
        headingJoystick.addDeltaProgressUpdatedListener(delta -> {
            // get the origin camera
            Camera camera = mArView.getOriginCamera();
            // add the heading delta to the existing camera heading
            double heading = camera.getHeading() + delta;
            // get a camera with a new heading
            Camera newCam = camera.rotateTo(heading, camera.getPitch(), camera.getRoll());
            // apply the new origin camera
            mArView.setOriginCamera(newCam);
        });
        JoystickSeekBar altitudeJoystick = findViewById(R.id.altitudeJoystick);
        // listen for calibration value changes for altitude
        altitudeJoystick.addDeltaProgressUpdatedListener(delta -> {
            mCurrentVerticalOffset += delta;
            // get the origin camera
            Camera camera = mArView.getOriginCamera();
            // elevate camera by the delta
            Camera newCam = camera.elevate(delta);
            // apply the new origin camera
            mArView.setOriginCamera(newCam);
        });
    }


    private void setupScene()
    {
        // disable touch interactions with the scene view
        mArView.getSceneView().setOnTouchListener((view, motionEvent) -> true);
        // create a scene and add it to the scene view
        ArcGISScene scene = new ArcGISScene(Basemap.createImagery());
        mArView.getSceneView().setScene(scene);
        // create and add an elevation surface to the scene
        ArcGISTiledElevationSource elevationSource = new ArcGISTiledElevationSource("https://elevation3d.arcgis.com/arcgis/rest/services/WorldElevation3D/Terrain3D/ImageServer");
        Surface elevationSurface = new Surface();
        elevationSurface.getElevationSources().add(elevationSource);
        mArView.getSceneView().getScene().setBaseSurface(elevationSurface);
        // allow the user to navigate underneath the surface
        elevationSurface.setNavigationConstraint(NavigationConstraint.NONE);
        // hide the basemap. The image feed provides map context while navigating in AR
        elevationSurface.setOpacity(0.2f);
        // disable plane visualization. It is not useful for this AR scenario.
        mArView.getArSceneView().getPlaneRenderer().setEnabled(false);
        mArView.getArSceneView().getPlaneRenderer().setVisible(false);
        // add an ar location data source to update location
        mArView.setLocationDataSource(new ArLocationDataSource(this));

        // this step is handled on the back end anyways, but we're applying a vertical offset to every update as per the
        // calibration step above
        mArView.getLocationDataSource().addLocationChangedListener(locationChangedEvent -> {
            Point updatedLocation = locationChangedEvent.getLocation().getPosition();
            mArView.setOriginCamera(new Camera(
                    new Point(updatedLocation.getX(), updatedLocation.getY(), updatedLocation.getZ() + mCurrentVerticalOffset),
                    mArView.getOriginCamera().getHeading(), mArView.getOriginCamera().getPitch(),
                    mArView.getOriginCamera().getRoll()));
            //update the center for the airplane query
            if(_airplaneFinder != null)
            {
                _airplaneFinder.setCenter(updatedLocation);
            }
        });

        _graphicsOverlay = new GraphicsOverlay();
        mArView.getSceneView().getGraphicsOverlays().add(_graphicsOverlay);
        _airplaneFinder = new AirplaneFinder(_graphicsOverlay, getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());

        //animate the planes
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        handler.postDelayed(runnableCode, 2000);
    }

    //animates the planes
    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            if(_airplaneFinder != null)
            {
                _airplaneFinder.animatePlanes();
                handler.postDelayed(this, 1000 / 30);
            }
        }
    };

    @Override
    public synchronized void onResume() {
        super.onResume();

        //start tracking when app is being used
        if (mArView != null) {
            mArView.startTracking(ArcGISArView.ARLocationTrackingMode.CONTINUOUS);
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
