package lab1_205_03.uwaterloo.ca.lab1_205_03;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import android.view.View;

import ca.uwaterloo.sensortoy.LineGraphView;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    LineGraphView graph;


    private SensorManager sensorManager;
    private Sensor accelerometerSensor, lightSensor,magneticField,rotationVector;

    //acclerometer gravity calculations
    private float[] gravity = new float[3];
    private float[] linear_acceleration = new float[3];
    final float alpha = (float) 0.8;

    private float[] maxAccelValues = {0,0,0}; //x,y,z
    private float[] maxMagValues = {0,0,0}; //x,y,z
    private float[] maxRotationalValues = {0,0,0}; //x,y,z

    private TextView[] accelerometerViews, magneticViews, rotationViews;
    private TextView lightSensorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LinearLayout layout = ((LinearLayout)findViewById(R.id.linearLayout));

        //add graph
        graph = new LineGraphView(getApplicationContext(),
                100,
                Arrays.asList("x", "y", "z"));
        layout.addView(graph);
        graph.setVisibility(View.VISIBLE);

        //setup sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        magneticField= sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, magneticField , SensorManager.SENSOR_DELAY_NORMAL);
        rotationVector= sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this, rotationVector , SensorManager.SENSOR_DELAY_NORMAL);

        accelerometerViews = createTextViews("Acceleration", layout);
        magneticViews = createTextViews("Magnetic Field", layout);
        rotationViews = createTextViews("Rotational Vector", layout);

        //add light sensor differently
        TextView lightHeader = new TextView(getApplicationContext());
        lightHeader.setText("Light Sensor");
        lightHeader.setTextSize(30);
        lightHeader.setTextColor(Color.parseColor("#000000"));
        layout.addView(lightHeader);
        lightSensorView = new TextView(getApplicationContext());
        lightSensorView.setTextSize(20);
        lightSensorView.setTextColor(Color.parseColor("#000000"));
        layout.addView(lightSensorView);

        //create reset button
        Button reset = new Button(getApplicationContext());
        reset.setText("Reset Max");
        reset.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                //reset max values
                for(int i=0; i<3; i++){
                    maxAccelValues[i]=0;
                }
                for(int i=0; i<3; i++){
                    maxMagValues[i]=0;
                }
                for(int i=0; i<3; i++){
                    maxRotationalValues[i]=0;
                }
            }
        });
        layout.addView(reset);
    }

    //creates text view for X,Y,Z sensors. Returns array of ids
    public TextView[] createTextViews(String name, LinearLayout linLayout){

        TextView views[] = new TextView[6];
        TextView header = new TextView(getApplicationContext());
        header.setText(name);
        header.setTextSize(30);
        header.setTextColor(Color.parseColor("#000000"));
        linLayout.addView(header);

        for(int i=0; i<6;i++){
            views[i] = new TextView(getApplicationContext());
            views[i].setTextSize(20);
            views[i].setTextColor(Color.parseColor("#000000"));
            linLayout.addView(views[i]);
        }
        return views;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
    //sort by sensor type
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            graph.addPoint(event.values);
            //credit to android documentation
            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

            //credit to android documentation
            // Remove the gravity contribution with the high-pass filter.
            linear_acceleration[0] = event.values[0] - gravity[0];
            linear_acceleration[1] = event.values[1] - gravity[1];
            linear_acceleration[2] = event.values[2] - gravity[2];


            //set max accelerations
            for (int i = 0; i < 3; i++) {
                if (Math.abs(linear_acceleration[i]) > Math.abs(maxAccelValues[i])) {
                    maxAccelValues[i] = linear_acceleration[i];
                }
            }
            updateText(linear_acceleration, maxAccelValues,accelerometerViews);
        }
        else if (event.sensor.getType() == Sensor.TYPE_LIGHT){
            lightSensorView.setText(event.values[0]+"");
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            //set max values
            for (int i = 0; i < 3; i++) {
                if (Math.abs(event.values[i]) > Math.abs(maxMagValues[i])) {
                    maxMagValues[i] = event.values[i];
                }
            }
            updateText(event.values,maxMagValues,magneticViews);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR){
            //set max values
            for (int i = 0; i < 3; i++) {
                if (Math.abs(event.values[i]) > Math.abs(maxRotationalValues[i])) {
                    maxRotationalValues[i] = event.values[i];
                }
            }
            updateText(event.values,maxRotationalValues,rotationViews);
        }
    }

    private void updateText(float[] values, float[] max, TextView[] views) {
        //set current values
        views[0].setText(String.format("X: %.6f" , values[0]));
        views[1].setText(String.format("Y: %.6f" , values[1]));
        views[2].setText(String.format("Z: %.6f" , values[2]));

        //set max values
        views[3].setText(String.format("X max: %.6f" , max[0]));
        views[4].setText(String.format("Y max: %.6f" ,  max[1]));
        views[5].setText(String.format("Z max: %.6f" ,  max[2]));

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
