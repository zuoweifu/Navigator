package lab3_205_03.uwaterloo.ca.lab3_205_03;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.lang.Math;

import android.view.ContextMenu.ContextMenuInfo;
import android.view.ContextMenu;
import android.view.MenuItem;
import mapper.MapLoader;
import mapper.NavigationalMap;
import mapper.MapView;


public class MainActivity extends AppCompatActivity implements SensorEventListener{

    //Map view stuff
    MapView  mv;

    //compass for debugging
    CompassView compass;

    //stuff to get sensor data
    private SensorManager sensorManager;
    private Sensor accelerationSensor, magSensor;

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
    float x=0;
    float y=0;

    //step counter and associated text view
    int count=0;
    TextView stepCounter;
    TextView displacement;
    TextView avgheading;

    //toggled by debug checkbox
    boolean debug=false;

    //views
    LinearLayout debugLayout;
    LinearLayout mainLayout;

    //displacement array
    private List<Float> headingArray = new ArrayList<>();
    private float avgHeading=0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Do not put anything before here, super always has to go first

        //init layouts
        debugLayout = ((LinearLayout) findViewById(R.id.debugLinearLayout));
        mainLayout = ((LinearLayout) findViewById(R.id.linearLayout));


        //Init mapview
        mv = new  MapView(getApplicationContext(), 1000, 1000, 40, 40);
        Log.d("onCreate",mainLayout.getMeasuredWidth()+"");

        //register map view
        registerForContextMenu(mv);
        //load map
        NavigationalMap  map = MapLoader.loadMap(getExternalFilesDir(null),"E2-3344.svg");
        mv.setMap(map);
        //add to debugLayout
        mainLayout.addView(mv,3);
        //debugLayout.addView(mv,3);


        //setup sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, accelerationSensor, SensorManager.SENSOR_DELAY_FASTEST);
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_FASTEST);


        //step counter view
        stepCounter = (TextView)findViewById(R.id.stepCounter);
        stepCounter.setText("Step Counter \n "+ count);

        //create compass, but put it in debugLayout, which is hidden by default
        compass = new CompassView(getApplicationContext(),
                800);
        debugLayout.addView(compass);
        compass.setVisibility(View.VISIBLE);


        //displacement view
        displacement = (TextView)findViewById(R.id.displacement);
        displacement.setText("Displacement: \n x = " + x +"\n y = " +y);

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //check sensor type
        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){

            float[] smoothValue = lowpass(event.values);

            //debugging stuff
            if(debug){
                // --> write data to file
                DateFormat dateFormat = new SimpleDateFormat(":yyyy/MM/dd HH_mm_ss");
                Date date = new Date();
                osw.println(":"+smoothValue[0] + ":"+smoothValue[1] + ":"+smoothValue[2] + dateFormat.format(date));

                //compass.addPoint(smoothValue);
                //((TextView)findViewById(R.id.debugTextview)).setText("Max values:" +max[0] + "," + max[1] + "," + max[2] +"\nState:" + state);
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
                x += Math.sin(Math.toRadians(avgHeading));
                y += Math.cos(Math.toRadians(avgHeading));
                displacement.setText("Displacement: \n x = " + x +"\n y = " +y);
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
        }else if(event.sensor.getType() == Sensor.TYPE_ORIENTATION){
            if(state==UP_STEP){
                headingArray.add(event.values[0]);
            }else if(state==DOWN_STEP) {
                avgHeading = getAvgHeading();
            }

            if(debug){
                compass.setTheta(event.values[0]);
                ((TextView)findViewById(R.id.debugTextview)).setText("values:\n" +event.values[0] + "\n" + avgHeading);
            }
        }
    }

    @Override
    public  void  onCreateContextMenu(ContextMenu  menu , View v, ContextMenuInfo  menuInfo) {
        super.onCreateContextMenu(menu , v, menuInfo);mv.onCreateContextMenu(menu , v, menuInfo);
    }

    @Override
    public  boolean  onContextItemSelected(MenuItem  item) {
        return  super.onContextItemSelected(item) ||  mv.onContextItemSelected(item);
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

    //return average heading from heading Array from 0-360
    float getAvgHeading(){
        if(headingArray.isEmpty()){
            return avgHeading;
        }
        //if heading array is not empty, take the average, then empty it
        //a,b,c,d are the 1,2,3, and 4 quadrants
        int[] quadrants={0,0,0,0};
        float avg=0;

        //check for amount in each quadrant
        for(float i : headingArray){
            if(i<90){
                quadrants[0]++;
            }
            if(i>=90&&i<180){
                quadrants[1]++;
            }
            if(i>=180&&i<270){
                quadrants[2]++;
            }
            if(i>=270&&i<360){
                quadrants[3]++;
            }
        }
        //if in both 1st and 4th quadrant, assume those are the only 2 quadrants with important values
        //any values <180, add 360 so the average can be accurate
        if(quadrants[0]>0 && quadrants[3]>0){
            for(int i=0; i<headingArray.size();i++){
                if(headingArray.get(i)<180){
                    headingArray.set(i,headingArray.get(i)+360);
                }
            }
        }

        //take average
        for(float i: headingArray){
            avg+=i;
        }
        avg/=headingArray.size();

        //reset heading array
        headingArray.clear();

        //limit avg to [0-360) range
        if(avg>=360){
            avg-=360;
        }
        return avg;
    }

    //method called when reset button is clicked
    public void reset(View view) {
        count=0;
        x=0;
        y=0;
        stepCounter.setText("Step Counter \n "+ count);
        displacement.setText("Displacement: \n x = " + x +"\n y = " +y);
    }


    //debug tools
    public void debug(View view) {
        if(debug){
            debug=false;

            findViewById(R.id.debugLayout).setVisibility(View.INVISIBLE);

            //close file
            osw.close();

        }else{
            debug=true;

            //write to file
            try {
                os = new FileOutputStream(new File(getExternalFilesDir(null), "step_log.txt"));
                osw = new PrintWriter(new OutputStreamWriter(os));
            } catch (IOException e) {
                e.printStackTrace();
            }

            //add debug stuff
            findViewById(R.id.debugLayout).setVisibility(View.VISIBLE);
        }
    }

    //Method called when sendAlpha button clicked
    public void sendAlpha(View view) {
        EditText textbox= (EditText) findViewById(R.id.setAlpha);
        alpha = Float.parseFloat(textbox.getText().toString());
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

