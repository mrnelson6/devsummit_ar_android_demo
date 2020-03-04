package com.example.ar_base_app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

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


        requestPermissions();
    }

    private void setupArView()
    {
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

        setupAirplaneFinder();
    }

    private void setupAirplaneFinder()
    {
        // get plane model from assets
        copyFileFromAssetsToCache(getString(R.string.bristol_dae));
        copyFileFromAssetsToCache(getString(R.string.bristol_png));
        copyFileFromAssetsToCache(getString(R.string.logo_jpg));
        copyFileFromAssetsToCache(getString(R.string.b_787_8_dae));
        copyFileFromAssetsToCache(getString(R.string.texture_png));
        copyFileFromAssetsToCache(getString(R.string.texture_ref_png));

        _graphicsOverlay = new GraphicsOverlay();
        mArView.getSceneView().getGraphicsOverlays().add(_graphicsOverlay);
        _airplaneFinder = new AirplaneFinder(_graphicsOverlay, getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        _airplaneFinder = new AirplaneFinder(_graphicsOverlay, getCacheDir() + File.separator);

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

    /**
     * Request read external storage for API level 23+.
     */
    private void requestPermissions() {
        // define permission to request
        String[] reqPermission = { Manifest.permission.INTERNET,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION};
        int requestCode = 2;
        if (ContextCompat.checkSelfPermission(this, reqPermission[0]) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, reqPermission[1]) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, reqPermission[2]) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, reqPermission[3]) == PackageManager.PERMISSION_GRANTED){
            setupArView();
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
        if (grantResults.length >= 4 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED
                && grantResults[2] == PackageManager.PERMISSION_GRANTED
                && grantResults[3] == PackageManager.PERMISSION_GRANTED)
        {
            setupArView();
        } else {
            // report to user that permission was denied
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    private static final String TAG = MainActivity.class.getSimpleName();
    private void copyFileFromAssetsToCache(String fileName) {
        AssetManager assetManager = getApplicationContext().getAssets();

        File file = new File(getCacheDir() + File.separator + fileName);

        if (!file.exists()) {
            try {
                InputStream in = assetManager.open(fileName);
                OutputStream out = new FileOutputStream(getCacheDir() + File.separator + fileName);
                byte[] buffer = new byte[1024];
                int read = in.read(buffer);
                while (read != -1) {
                    out.write(buffer, 0, read);
                    read = in.read(buffer);
                }
                Log.i(TAG, fileName + " copied to cache.");
            } catch (Exception e) {
                Log.e(TAG, "Error writing " + fileName + " to cache. " + e.getMessage());
            }
        } else {
            Log.i(TAG, fileName + " already in cache.");
        }
    }
}
