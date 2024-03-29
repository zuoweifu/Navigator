package lab2_205_03.uwaterloo.ca.lab2_205_03;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements SensorEventListener{
    //graph for debugging
    LineGraphView graph;

    //stuff to get sensor data
    private SensorManager sensorManager;
    private Sensor accelerationSensor;

    //file writing stuff
    PrintWriter osw;
    FileOutputStream os;

    //alpha for low pass filter. Can be changed via debugging
    float alpha = (float) 0.15;
    float[] newAcceleration = {0,0,0};

    //state machine
    /*
    current state |  transition |  next state
    DOWN_STEP----->(Z>threshold)----->UP_STEP
    UP_STEP------->(Z<0)------------->DOWN_STEP
    UP_STEP----->(Z>error threshold)->ERROR
    ERROR-------->(Z<0)-------------->DOWN_STEP
     */
    final int UP_STEP = 0;  //When Z acceleration is above threshold but below error threshold
    final int DOWN_STEP = 1;    //When Z is below 0
    final int ERROR = -1;       //When Z is too large, goes into error state, used for shake detection
    int state = DOWN_STEP;

    //step counter and associated text view
    int count=0;
    TextView stepCounter;

    //toggled by debug checkbox
    boolean debug=false;

    float [] max = {0,0,0};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //setup sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, accelerationSensor, SensorManager.SENSOR_DELAY_FASTEST);


        //step counter view
        stepCounter = (TextView)findViewById(R.id.stepCounter);
        stepCounter.setText("Step Counter \n "+ count);

        //create graph, but put it in debugLayout, which is hidden by default
        graph = new LineGraphView(getApplicationContext(),
                100,
                Arrays.asList("x", "y", "z"));
        ((LinearLayout) findViewById(R.id.debugLayout)).addView(graph);
        graph.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //check sensor type
        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){

            float[] smoothValue = lowpass(event.values);
            setMax(smoothValue);

            //debugging stuff
            if(debug){
                // --> write data to file
                DateFormat dateFormat = new SimpleDateFormat(":yyyy/MM/dd HH_mm_ss");
                Date date = new Date();
                osw.println(":"+smoothValue[0] + ":"+smoothValue[1] + ":"+smoothValue[2] + dateFormat.format(date));

                graph.addPoint(smoothValue);
                ((TextView)findViewById(R.id.debugTextview)).setText("Max values:" +max[0] + "," + max[1] + "," + max[2] +"\nState:" + state);
            }

            //state machine
            if(smoothValue[2]>1.25 && state==DOWN_STEP)
            {//transition from DOWN_STEP to UP_STEP
                state=UP_STEP;
            }
            else if(smoothValue[2]<0 && state==UP_STEP)
            {//transition from UP_STEP to DOWN_STEP
                state=DOWN_STEP;
                count++;
            }
            else if(smoothValue[2]<0 && state==ERROR)
            {//transition from ERROR to DOWN_STEP (avoids incrementing)
                state=DOWN_STEP;
            }
            else if((smoothValue[0]>3 || smoothValue[1]>3 ||smoothValue[2]>5) && state==UP_STEP)
            {//transition from UP_STEP to ERROR
                state=ERROR;
            }
            stepCounter.setText("Step Counter \n "+ count);
        }
    }

    //smooths out data
    float[] lowpass(float[] linearAcceleration)
    {
        for(int i = 0; i <3; i++)
        {
            newAcceleration[i] = alpha* linearAcceleration[i] + (1-alpha) * newAcceleration[i];
        }
        return newAcceleration;
    }


    //method called when reset button is clicked
    public void reset(View view) {
        count=0;
        stepCounter.setText("Step Counter \n "+ count);

        //reset max if debugging is on
        if(debug){
            for(int i=0; i<3; i++){
                max[i]=0;
            }
        }
    }


    //debug tools
    public void debug(View view) {
        if(debug){
            debug=false;

            ((LinearLayout) findViewById(R.id.debugLayout)).setVisibility(View.INVISIBLE);

            //close file
            osw.close();

        }else{
            debug=true;

            //write to file
            try {
                os = new FileOutputStream(
                        new File(getExternalFilesDir(null),
                                "step_log.txt"));
                osw = new PrintWriter(
                        new OutputStreamWriter(os));
            } catch (IOException e) {
                e.printStackTrace();
            }

            //add debug stuff
            ((LinearLayout) findViewById(R.id.debugLayout)).setVisibility(View.VISIBLE);
        }
    }


    //Method called when sendAlpha button clicked
    public void sendAlpha(View view) {
        EditText textbox= (EditText) findViewById(R.id.setAlpha);
        alpha = Float.parseFloat(textbox.getText().toString());
    }

    //setMax acceleration, used for debugging purposes only.
    public void setMax(float[] accel) {
       for(int i=0; i<3;i++){
           if(accel[i]>max[i]){
               max[i]=accel[i];
           }
       }
    }


    @Override
    public void onDestroy(){
        osw.close();
        super.onDestroy();
    }

    //Needed due to interface!
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

