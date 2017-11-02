package com.housebrandapps.compasstape;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import static android.content.Context.SENSOR_SERVICE;

public class CompassTapeLayout extends View implements SensorEventListener, LocationListener {

    public static final int TEXT_SIZE = 42;

    private static final int LOCATION_MIN_TIME = 2000;
    private static final int LOCATION_MIN_DISTANCE = 5;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private Paint paint = new Paint();
    private static final float LINE_SPACES = 15;
    private static final int LINE_WIDTH = 2;
    private static final int LINE_HEIGHT = 60;
    private static final int HEIGHT = 380;
    private Bitmap verticalDegreeLines;

    private float screenWidth = 0;
    private int screenHeight = 0;

    private GeomagneticField geomagneticField;

    private long timer = 0L;

    public CompassTapeLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public CompassTapeLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }


    private void initView() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);

            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_MIN_TIME, LOCATION_MIN_DISTANCE, this);
//                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_MIN_TIME, LOCATION_MIN_DISTANCE, this);
            }
        }


        sensorManager = (SensorManager) getContext().getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        verticalDegreeLines = getDegreesBitmap();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenWidth = w;
        screenHeight = h;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);

        timer = System.currentTimeMillis();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sensorManager.unregisterListener(this);
    }

    float[] gravity;
    float[] geomagnetic;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (timer < System.currentTimeMillis() - 120) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                gravity = lowPassFilter(event.values, gravity);
            }
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                geomagnetic = lowPassFilter(event.values, geomagnetic);
            }
            if (gravity != null && geomagnetic != null) {
                float R[] = new float[9];
                float I[] = new float[9];
                boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
                if (success) {
                    float orientation[] = new float[3];
                    SensorManager.getOrientation(R, orientation);
                    degreeBearing = (float) (((Math.toDegrees(orientation[0]) + 360f) % 360f) - 180f);
                    invalidate();
                }
            }
            timer = System.currentTimeMillis();
        }
    }

    private float degreeBearing = 0;

    private String longitude = "NA";
    private String latitude = "NA";

    @Override
    protected void onDraw(Canvas canvasLines) {
        super.onDraw(canvasLines);

        paint.setColor(Color.WHITE);
        paint.setTextSize(TEXT_SIZE);
        paint.setTextAlign(Paint.Align.CENTER);

        float screenCenter = screenWidth / 2f;
        int bitmapWidth = verticalDegreeLines.getWidth();

        canvasLines.drawBitmap(verticalDegreeLines, (screenCenter - ((degreeBearing) * LINE_SPACES)) - (bitmapWidth / 2f), 0, paint);

        canvasLines.drawText("" + degreeBearing, screenCenter, screenHeight - 80, paint);
//        canvasLines.drawText("lat: " + latitude, screenCenter, screenHeight - 160, paint);
//        canvasLines.drawText("long: " + longitude, screenCenter, screenHeight - 200, paint);

    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private Bitmap getDegreesBitmap() {
        Bitmap backingBitmap = Bitmap.createBitmap((int) (720f * LINE_SPACES), HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvasLines = new Canvas(backingBitmap);
        Paint linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
        linePaint.setTextSize(TEXT_SIZE);
        linePaint.setTextAlign(Paint.Align.CENTER);

        drawLiners(canvasLines, linePaint, 0);
        drawLiners(canvasLines, linePaint, (int) (360 * LINE_SPACES));

        return backingBitmap;
    }

    private void drawLiners(Canvas canvasLines, Paint linePaint, int initX) {
        int degreeCounter = 0;
        for (int i = 0; i <= 360; i++) {
            int lineWidth = 1;
            int lineHeight = HEIGHT / 4;

            float xPos = initX + (i * (LINE_SPACES));

            degreeCounter = i + 180;
            if (degreeCounter >= 360) {
                degreeCounter = degreeCounter - 360;
            }
            switch (degreeCounter) {
                case 0: // north
                case 360:
                    lineHeight += 10;
                    lineWidth += LINE_WIDTH;
                    canvasLines.drawText("N", xPos, lineHeight + 25, linePaint);
                    break;
                case 90: // East
                    lineHeight += 10;
                    lineWidth += LINE_WIDTH;
                    canvasLines.drawText("E", xPos, lineHeight + 25, linePaint);
                    break;
                case 180: // south
                    lineHeight += 10;
                    lineWidth += LINE_WIDTH;
                    canvasLines.drawText("S", xPos, lineHeight + 25, linePaint);
                    break;
                case 270: //west
                    lineHeight += 10;
                    lineWidth += LINE_WIDTH;
                    canvasLines.drawText("W", xPos, lineHeight + 25, linePaint);
                    break;
                default:
                    if (degreeCounter % 15 == 0) {
                        lineHeight += 10;
                        lineWidth += LINE_WIDTH;
                        canvasLines.drawText("" + degreeCounter, xPos, lineHeight + 25, linePaint);
                    }
                    break;
            }
            canvasLines.drawRect(initX + (i * LINE_SPACES), 0, initX + ((i * LINE_SPACES) + lineWidth), lineHeight - 10, linePaint);
        }
    }

    static final float DELTA = 0.98f;

    protected float[] lowPassFilter(float[] input, float[] output) {
        if (output == null) return input;
        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + DELTA * (input[i] - output[i]);
        }
        return output;
    }

    @Override
    public void onLocationChanged(Location location) {
//        updateLocation(location);
//        geomagneticField = new GeomagneticField(
//                (float) location.getLatitude(),
//                (float) location.getLongitude(),
//                (float) location.getAltitude(),
//                System.currentTimeMillis());
//
//        azimuth += geomagneticField.getDeclination(); // converts magnetic north into true north
//        Location locNorth = new Location("northloc");
//        locNorth.setLatitude(0d);
//        locNorth.setLongitude(0d);
//
//        float bearing = location.bearingTo(locNorth); // (it's already in degrees)
//        degreeBearing = azimuth - bearing;
    }

    private void updateLocation(Location location) {
        latitude = "" + location.getLatitude();
        longitude = "" + location.getLongitude();
//        geomagneticField.getX()
        invalidate();
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}
