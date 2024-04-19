package com.example.sensorapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity
    implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetometer;
    private TextView mTextSensorAzimuth;
    private TextView mTextSensorPitch;
    private TextView mTextSensorRoll;
    private float[] mAccelerometerData = new float[3];
    private float[] mMagnetometerData = new float[3];
    private ImageView mSpotTop;
    private ImageView mSpotBottom;
    private ImageView mSpotLeft;
    private ImageView mSpotRight;
    private long lastUpdateTime = 0;
    private enum Orientation {
        PORTRAIT("Portrait"),
        LANDSCAPE_LEFT("Landscape (Left)"),
        LANDSCAPE_RIGHT("Landscape (Right)"),
        UPSIDE_DOWN("Upside Down");

        private String description;

        Orientation(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private Orientation currentOrientation = Orientation.PORTRAIT;
    private Map<Orientation, Long> orientationTime = new HashMap<>();
    private long TotalTime = 0;
    private double percentage =0.0;
    private Handler mHandler = new Handler();
    private static final float VALUE_DRIFT = 0.05f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        mTextSensorAzimuth = (TextView) findViewById(R.id.value_azimuth);
        mTextSensorPitch = (TextView) findViewById(R.id.value_pitch);
        mTextSensorRoll = (TextView) findViewById(R.id.value_roll);
        mSpotTop = (ImageView) findViewById(R.id.spot_top);
        mSpotBottom = (ImageView) findViewById(R.id.spot_bottom);
        mSpotLeft = (ImageView) findViewById(R.id.spot_left);
        mSpotRight = (ImageView) findViewById(R.id.spot_right);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        // Initialize orientation times
        for (Orientation orientation : Orientation.values()) {
            orientationTime.put(orientation, 0L);
        }
        Button showTimesButton = findViewById(R.id.btn_show_percentage);
        showTimesButton.setOnClickListener(new View.OnClickListener() {
            private boolean isDisplayed = false; // Initial state is not displayed

            @Override
            public void onClick(View v) {
                if (isDisplayed) {
                    // Hide the display
                    TextView display = findViewById(R.id.orientation_percentage_display);
                    display.setVisibility(View.GONE); // Use View.INVISIBLE if you just want to make it invisible but still take up space in layout
                } else {
                    // Show the display
                    displayPercentage(); // This will update the TextView text
                    TextView display = findViewById(R.id.orientation_percentage_display);
                    display.setVisibility(View.VISIBLE);
                }
                isDisplayed = !isDisplayed; // Toggle the state
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mSensorAccelerometer != null) {
            mSensorManager.registerListener(this, mSensorAccelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (mSensorMagnetometer != null) {
            mSensorManager.registerListener(this, mSensorMagnetometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        TextView display = findViewById(R.id.orientation_percentage_display);
        display.setVisibility(View.GONE);
        mHandler.postDelayed(mUpdateOrientationTimesTask, 10);
        mHandler.postDelayed(mUpdatePercentage, 10);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(this);
        mHandler.removeCallbacks(mUpdateOrientationTimesTask);
        mHandler.removeCallbacks(mUpdatePercentage);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        int sensorType = sensorEvent.sensor.getType();
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                mAccelerometerData = sensorEvent.values.clone();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mMagnetometerData = sensorEvent.values.clone();
                break;
            default:
                return;
        }

        float[] rotationMatrix = new float[9];
        boolean rotationOK = SensorManager.getRotationMatrix(rotationMatrix, null,
                mAccelerometerData, mMagnetometerData);
        float[] orientationValues = new float[3];
        if (rotationOK) {
            SensorManager.getOrientation(rotationMatrix, orientationValues);
        }

        float azimuth = orientationValues[0];
        float pitch = orientationValues[1];
        float roll = orientationValues[2];

        if (Math.abs(pitch) < VALUE_DRIFT) {
            pitch = 0;
        }
        if (Math.abs(roll) < VALUE_DRIFT) {
            roll = 0;
        }
        // Determine orientation
        Orientation newOrientation;
        if (Math.abs(pitch) < Math.abs(roll)) {
            if (roll > VALUE_DRIFT) {
                newOrientation = Orientation.LANDSCAPE_LEFT;
            } else {
                newOrientation = Orientation.LANDSCAPE_RIGHT;
            }
        } else {
            if (pitch > VALUE_DRIFT) {
                newOrientation = Orientation.UPSIDE_DOWN;
            } else {
                newOrientation = Orientation.PORTRAIT;
            }
        }

        // Update the time spent in each orientation
        updateOrientationTime(newOrientation);

        mTextSensorAzimuth.setText(getResources().getString(R.string.value_format, azimuth));
        mTextSensorPitch.setText(getResources().getString(R.string.value_format, pitch));
        mTextSensorRoll.setText(getResources().getString(R.string.value_format, roll));

        mSpotTop.setAlpha(0f);
        mSpotBottom.setAlpha(0f);
        mSpotLeft.setAlpha(0f);
        mSpotRight.setAlpha(0f);
        //radian values overflow 1.0 max, but it's okay
        //  we don't care about tracking the full device tilt
        if (pitch > 0) {
            mSpotBottom.setAlpha(pitch);
        } else {
            mSpotTop.setAlpha(Math.abs(pitch));
        }
        if (roll > 0) {
            mSpotLeft.setAlpha(roll);
        } else {
            mSpotRight.setAlpha(Math.abs(roll));
        }
    }
    private void updateOrientationTime(Orientation newOrientation) {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime != 0) {
            long timeSpent = currentTime - lastUpdateTime;
            orientationTime.put(currentOrientation, orientationTime.getOrDefault(currentOrientation, 0L) + timeSpent);
        }
        lastUpdateTime = currentTime;
        currentOrientation = newOrientation;
    }
    private Runnable mUpdateOrientationTimesTask = new Runnable() {
        @Override
        public void run() {
            displayOrientationTimes();
            mHandler.postDelayed(this, 10); // Update every second
        }
    };

    private Runnable mUpdatePercentage = new Runnable() {
        @Override
        public void run() {
            displayPercentage(); // Call the method to update the UI.
            mHandler.postDelayed(this, 1000); // You can adjust the update frequency as needed.
        }
    };
    private void displayOrientationTimes() {
        StringBuilder times = new StringBuilder();
        times.append("Orientation Times\n");
        for (Map.Entry<Orientation, Long> entry : orientationTime.entrySet()) {
            long timeInSeconds = entry.getValue() / 1000; // Convert ms to seconds
            times.append(String.format("%s: %ds\n", entry.getKey().getDescription(), timeInSeconds));
        }
        // Assuming there's a TextView with id `orientation_time_display`
        TextView display = findViewById(R.id.orientation_time_display);
        display.setText(times.toString());
    };
    private void displayPercentage() {
        StringBuilder percent = new StringBuilder();
        percent.append("Percentage:\n");
        TotalTime = 0; // Reset TotalTime for the new calculation.

        // Calculate the total time
        for (long time : orientationTime.values()) {
            TotalTime += time;
        }

        if (TotalTime > 0) {
            for (Map.Entry<Orientation, Long> entry : orientationTime.entrySet()) {
                percentage = 100.0 * entry.getValue() / TotalTime;
                percent.append(String.format("%s: %.2f%%\n", entry.getKey().getDescription(), percentage));
            }
        } else {
            for (Orientation orientation : Orientation.values()) {
                percent.append(String.format("%s: %.2f%%\n", orientation.getDescription(), 0.0));
            }
        }

        TextView display = findViewById(R.id.orientation_percentage_display);
        display.setText(percent.toString());
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //intentionally blank
    }
}