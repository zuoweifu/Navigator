package lab4_205_03.ca.uwaterloo.lab4_205_03;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import android.widget.Toast;
import android.widget.ZoomControls;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import mapper.InterceptPoint;
import mapper.MapLoader;
import mapper.NavigationalMap;
import mapper.MapView;
import mapper.PositionListener;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    //Map view stuff
    MapView mv;
    int zoom = 40;
    List<String> mapNames;
    PointF nextPoint;

    //compasses for north and navigation
    CompassView northCompass, navCompass;

    //alpha for low pass filter
    float alpha = (float) 0.15;
    float[] newAcceleration = {0, 0, 0};

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
    float x = 0;
    float y = 0;
    float steplength = 0;

    //step counter and associated text view
    int count = 0;
    TextView stepCounter;
    TextView displacement;

    //toggled by debug checkbox
    boolean debug = false;

    //views
    LinearLayout debugLayout, mainLayout, compassLayout;

    //displacement array
    private List<Float> headingArray = new ArrayList<>();
    private float avgHeading = 0;

    //Navigation stuff
    private float stepSize = 1; //feet per step?
    NavigationalMap map;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Do not put anything before here, super always has to go first

        //init layouts and views
        initLayouts();

        //init map stuff
        initMap();

        //setup sensors
        initSensors();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    private void initLayouts() {
        debugLayout = ((LinearLayout) findViewById(R.id.debugLinearLayout));
        mainLayout = ((LinearLayout) findViewById(R.id.linearLayout));
        compassLayout = (LinearLayout) findViewById(R.id.compassView);

        //step counter view
        stepCounter = (TextView) findViewById(R.id.stepCounter);
        stepCounter.setText("Step Counter \n " + count);

        //create compasses
        navCompass = new CompassView(getApplicationContext(),
                400);
        compassLayout.addView(navCompass);
        navCompass.setVisibility(View.VISIBLE);

        northCompass = new CompassView(getApplicationContext(),
                400);
        compassLayout.addView(northCompass);
        northCompass.setVisibility(View.VISIBLE);

        //displacement view
        displacement = (TextView) findViewById(R.id.displacement);
        displacement.setText("Displacement: \n x = " + x + "\n y = " + y);
    }

    private void initSensors() {
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, accelerationSensor, SensorManager.SENSOR_DELAY_FASTEST);
        Sensor magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void initMap() {
        //init map selector spinner
        final Spinner mapChoices = (Spinner) findViewById(R.id.mapChoices);
        File f = new File(getExternalFilesDir(null).toString());
        File file[] = f.listFiles();
        mapNames = new ArrayList<>();
        for (File aFile : file) {
            Log.d("onCreate", "fileName: " + aFile.getName());
            if (aFile.getName().contains(".svg")) {
                mapNames.add(aFile.getName());
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, mapNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapChoices.setAdapter(adapter);

        mapChoices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
                //load map
                mv.setUserPath(null);
                map = MapLoader.loadMap(getExternalFilesDir(null), mapNames.get(position));
                mv.setMap(map);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }

        });


        //init zoom buttons
        ZoomControls zoomControls = (ZoomControls) findViewById(R.id.zoomControls);
        assert zoomControls != null;
        zoomControls.setOnZoomInClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zoom+=5;
                mv.setScale(zoom, zoom);
            }
        });
        zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zoom-=5;
                if(zoom<0){
                    zoom=0;
                }
                mv.setScale(zoom, zoom);
            }
        });

        //Init mapview
        Point size = getScreenSize();
        mv = new MapView(getApplicationContext(), size.x, 1000, zoom, zoom);
        mv.addListener(new PositionListener() {
            @Override
            public void originChanged(MapView source, PointF loc) {
                Log.d("origin changed", "origin changed");
                mv.setUserPoint(mv.getOriginPoint());
                mv.setUserPath(calculateFullRoute(mv.getOriginPoint(), mv.getDestinationPoint()));
                reset(null);
            }

            @Override
            public void destinationChanged(MapView source, PointF dest) {
                Log.d("dest changed", "dest changed");
                mv.setUserPath(calculateFullRoute(mv.getOriginPoint(), mv.getDestinationPoint()));
                reset(null);
            }
        });
        Log.d("onCreate", mainLayout.getMeasuredWidth() + "");

        //register map view
        registerForContextMenu(mv);
        //load map
        map = MapLoader.loadMap(getExternalFilesDir(null), "E2-3344.svg");
        mv.setMap(map);
        //add to debugLayout, added in position 3
        mainLayout.addView(mv, 3);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //check sensor type
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

            float[] smoothValue = lowpass(event.values);

            //state machine
            if (smoothValue[2] > 1.25 && state == DOWN_STEP) {//transition from DOWN_STEP to UP_STEP
                state = UP_STEP;
            } else if (smoothValue[2] < 0 && state == UP_STEP) {//transition from UP_STEP to DOWN_STEP
                state = DOWN_STEP;
                count++;
                List<PointF> rte = calculateFullRoute(mv.getUserPoint(), mv.getDestinationPoint());
                if (rte != null) {
                    mv.setUserPath(rte);
                }
                x += Math.sin(Math.toRadians(avgHeading));
                y += Math.cos(Math.toRadians(avgHeading));
                displacement.setText("Displacement: \n x = " + x + "\n y = " + y);
                //change user point on map
                setUserLocation(x, y);
            } else if (smoothValue[2] < 0 && state == ERROR) {//transition from ERROR to DOWN_STEP (avoids incrementing)
                state = DOWN_STEP;
            } else if ((smoothValue[0] > 3 || smoothValue[1] > 3 || smoothValue[2] > 5) && state == UP_STEP) {//transition from UP_STEP to ERROR
                state = ERROR;
            }
            stepCounter.setText("Step Counter \n " + count);
        } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            if (state == UP_STEP) {
                headingArray.add(event.values[0]);
            } else if (state == DOWN_STEP) {
                //avgHeading = getAvgHeading();
                avgHeading = event.values[0];
            }


            northCompass.setTheta(event.values[0]);
            navCompass.setTheta((float) getHeadingToPoint());

            if (debug) {
                ((TextView) findViewById(R.id.debugTextview)).setText("values:\n" + event.values[0] + "\n" + avgHeading);
            }
        }
    }

    private void setUserLocation(float x, float y) {
        PointF user = mv.getOriginPoint();
        mv.setUserPoint(user.x + x * stepSize, user.y - y * stepSize);
        double distLeftx=0,distLefty=0 , distLeft=0;
        distLeftx = mv.getDestinationPoint().x - mv.getUserPoint().x;
        distLefty = mv.getDestinationPoint().y - mv.getUserPoint().y;
        distLeft = Math.sqrt(Math.pow(distLeftx,2) + Math.pow(distLefty,2));
        if(distLeft < (2*stepSize))
        {
            Context context = getApplicationContext();
            CharSequence text = "You have reached your destination";
            int duration = Toast.LENGTH_SHORT;
            
            Toast toast = Toast.makeText(context, text, duration);
            toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, 0, 0);
            toast.show();
        }
    }

    /**
     * Calculates most efficient route from starting point to end point
     *
     * @return Route
     */
    private List<PointF> calculateFullRoute(PointF origin, PointF destination) {
        List<PointF> rte = new ArrayList<>();
        rte.add(origin);

        //detect all obstacles between origin and destination
        List<InterceptPoint> intersections = map.calculateIntersections(origin, destination);

        PointF[] obstacle = new PointF[4];
        //i is a mod 4 counter
        int i = 0;
        Log.d("calculateFullRoute", "Length of intersections:" + intersections.size());


            for (int j = 0; j < intersections.size(); j++) {
                Log.d("calculateFullRoute", "looping through points, i:" + i);
                InterceptPoint points = intersections.get(j);
                i = j % 4;

                obstacle[i] = points.getPoint();

                double offset = .2;
                List<PointF> subrte;
                if (i == 3) {
                    //fixes the points so that they are not in walls

                    obstacle[0].x += offset * Math.signum(origin.x - obstacle[0].x);
                    obstacle[0].y += offset * Math.signum(origin.y - obstacle[0].y);
                    obstacle[3].x += offset * Math.signum(destination.x - obstacle[3].x);
                    obstacle[3].y += offset * Math.signum(destination.y - obstacle[3].y);

                    subrte = calculateRoute(obstacle[0], obstacle[3]);
                    if (subrte == null)
                    {
                        Log.d("calculateFullRoute", "subrte = null!");
                        obstacle[0].x += offset * Math.signum(origin.x - obstacle[0].x);
                        obstacle[0].y += offset * Math.signum(origin.y - obstacle[0].y);
                        obstacle[1].x += offset * Math.signum(destination.x - obstacle[1].x);
                        obstacle[1].y += offset * Math.signum(destination.y - obstacle[1].y);
                        subrte = calculateRoute(obstacle[0], obstacle[1]);

                        if(subrte == null)
                        {
                            return null;
                        }
                    }

                        Log.d("calculateFullRoute", "Added " + subrte.size() + " to rte.");
                        rte.addAll(subrte);

                //if its on the last point or the first point, check if the corner is cut
                }else if((intersections.size()==2 ||j==intersections.size()-1)&&obstacle[0]!=null &&obstacle[1]!=null){
                    Log.d("calculateFullRoute", "subrte = null!");
                    obstacle[0].x += offset * Math.signum(origin.x - obstacle[0].x);
                    obstacle[0].y += offset * Math.signum(origin.y - obstacle[0].y);
                    obstacle[1].x += offset * Math.signum(destination.x - obstacle[1].x);
                    obstacle[1].y += offset * Math.signum(destination.y - obstacle[1].y);
                    subrte = calculateRoute(obstacle[0], obstacle[1]);

                    if(subrte == null)
                    {
                        return null;
                    }
                }
            }
            rte.add(destination);
            rte = simplifyRoute(rte);
            nextPoint = rte.get(1);
            return rte;


    }

    /**
     * Calculates most efficient route from origin to destination
     *
     * @return Route
     */
    private List<PointF> calculateRoute(PointF origin, PointF destination) {
        //number of steps taken before it gives up
        int timeOut = 50;

        //final route to return
        List<PointF> rte = new ArrayList<>();

        //possible routes
        List<PointF>[] rteOptions = (ArrayList<PointF>[]) new ArrayList[2];
        rteOptions[0] = new ArrayList<>();
        rteOptions[1] = new ArrayList<>();

        //indicates whether the routeOptions actually got from start to finish
        boolean[] rteComplete = {false, false};

        //used to calculate if there is a clear path in between two points
        List<InterceptPoint> intersects;

        //increment of angle to try to avoid wall
        double increment = .2;

        //loop through the algorithm twice, once turning right and once turning left
        for (int i = 0; i < 2; i++) {
            //add initial point
            rteOptions[i].add(origin);

            //search for new points until the route is complete
            boolean searchForPoints = true;
            while (!rteComplete[i] && searchForPoints) {
                if (rteOptions[i].size() > timeOut) {
                    break;
                }
                //Create a test point as a possible next step
                PointF testPoint = new PointF(0, 0);
                float angleToDest = (float) Math.atan2((destination.y - rteOptions[i].get(rteOptions[i].size() - 1).y), (destination.x - rteOptions[i].get(rteOptions[i].size() - 1).x));//in radians

                //if i is zero, compare to -incremenet
                // if i is 1, compare to increment
                Log.d("Calculate route", "Increment: " + increment * (i - .5) * 2 + " step size:" + stepSize);
                float testAngle = (float) (angleToDest - increment * (i - .5) * 2);

                //loop through the testAngles until it finds a step that works
                do {
                    testAngle += increment * (i - .5) * 2;
                    testPoint.x = (float) (Math.cos(testAngle) * stepSize + rteOptions[i].get(rteOptions[i].size() - 1).x);
                    testPoint.y = (float) (Math.sin(testAngle) * stepSize + rteOptions[i].get(rteOptions[i].size() - 1).y);
                    intersects = map.calculateIntersections(rteOptions[i].get(rteOptions[i].size() - 1), testPoint);
                    //Log.d("Calculate route","angle:" + Math.toDegrees(testAngle) + " angle to dest" + Math.toDegrees(angleToDest)+ " intersect size:" + intersects.size() + " step size:" + stepSize);

                    //intersects will be empty if step was clear
                    if (intersects.isEmpty()) {
                        Log.d("Calculate route", "Calculated another point!" + i + " size:" + rteOptions[i].size() + " angle:" + Math.toDegrees(testAngle) + " angle to dest" + Math.toDegrees(angleToDest));
                        rteOptions[i].add(testPoint);
                        break;
                    }
                    //error checking
                    if (testAngle > Math.PI * 2 + angleToDest || testAngle < angleToDest - Math.PI * 2) {
                        Log.d("Calculate route", "Failed to find a point");
                        rteComplete[i] = false;
                        searchForPoints = false;
                    }
                } while (searchForPoints);

                //if rte complete, add destination and finish
                if (map.calculateIntersections(rteOptions[i].get(rteOptions[i].size() - 1), destination).isEmpty()) {
                    rteOptions[i].add(destination);
                    rteComplete[i] = true;
                }
            }
        }
        Log.d("end of calculate route", "segLengths:" + rteOptions[0].size() + " , " + rteOptions[1].size());


        //chose the more efficient route
        if (rteOptions[0].size() > rteOptions[1].size() && rteComplete[1]) {
            Log.d("end of calculate route", "took option 1:" + rteOptions[1].size());
            rte.addAll(rteOptions[1]);
        } else if (rteComplete[0]) {
            Log.d("end of calculate route", "took option 0:" + rteOptions[0].size());
            rte.addAll(rteOptions[0]);
        } else {
            Log.d("end of calculate route", "No valid options");
            return null;
        }
        rte = simplifyRoute(rte);

        Log.d("end of calculate route", "returned route of length " + rte.size());
        return rte;
    }

    private List<PointF> simplifyRoute(List<PointF> rte) {
        Log.d("simplify route", "Simplifying route of size " + rte.size());
        if (rte.size() <= 2) {
            return rte;
        }
        for (int i = rte.size() - 1; i > 1; i--) {
            if (map.calculateIntersections(rte.get(0), rte.get(i)).isEmpty()) {
                Log.d("simplify route", "Removing points, i: " + i);
                for (int j = i - 1; j >= 1; j--) {
                    rte.remove(j);
                }
                break;
            }
        }
        PointF firstPoint = rte.get(0);
        rte.remove(0);
        List<PointF> simplifiedRoute = simplifyRoute(rte);
        simplifiedRoute.add(0, firstPoint);
        return simplifiedRoute;
    }

    public Point getScreenSize() {
        Point size = new Point();
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        size.x = displaymetrics.heightPixels;
        size.y = displaymetrics.widthPixels;
        return size;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        mv.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return super.onContextItemSelected(item) || mv.onContextItemSelected(item);
    }


    //smooths out data
    float[] lowpass(float[] linearAcceleration) {
        for (int i = 0; i < 3; i++) {
            newAcceleration[i] = alpha * linearAcceleration[i] + (1 - alpha) * newAcceleration[i];
        }
        return newAcceleration;
    }

    //return average heading from heading Array from 0-360
    float getAvgHeading() {
        if (headingArray.isEmpty()) {
            return avgHeading;
        }
        //if heading array is not empty, take the average, then empty it
        //a,b,c,d are the 1,2,3, and 4 quadrants
        int[] quadrants = {0, 0, 0, 0};
        float avg = 0;

        //check for amount in each quadrant
        for (float i : headingArray) {
            if (i < 90) {
                quadrants[0]++;
            }
            if (i >= 90 && i < 180) {
                quadrants[1]++;
            }
            if (i >= 180 && i < 270) {
                quadrants[2]++;
            }
            if (i >= 270 && i < 360) {
                quadrants[3]++;
            }
        }
        //if in both 1st and 4th quadrant, assume those are the only 2 quadrants with important values
        //any values <180, add 360 so the average can be accurate
        if (quadrants[0] > 0 && quadrants[3] > 0) {
            for (int i = 0; i < headingArray.size(); i++) {
                if (headingArray.get(i) < 180) {
                    headingArray.set(i, headingArray.get(i) + 360);
                }
            }
        }

        //take average
        for (float i : headingArray) {
            avg += i;
        }
        avg /= headingArray.size();

        //reset heading array
        headingArray.clear();

        //limit avg to [0-360) range
        if (avg >= 360) {
            avg -= 360;
        }
        return avg;
    }

    //method called when reset button is clicked
    public void reset(View view) {
        count = 0;
        x = 0;
        y = 0;
        stepCounter.setText("Step Counter \n " + count);
        displacement.setText("Displacement: \n x = " + x + "\n y = " + y);
        setUserLocation(x,y);
    }


    /**
     * called when debug box is checked
     * Toggles visibility of debug layout
     * @param view
     */
    public void debug(View view) {
        if (debug) {
            //remove debug stuff
            debug = false;
            findViewById(R.id.debugLayout).setVisibility(View.GONE);
        } else {
            debug = true;
            //add debug stuff
            findViewById(R.id.debugLayout).setVisibility(View.VISIBLE);
        }
    }

    /**
     * Blank, needed because of the interface
     * @param sensor
     * @param accuracy
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * stepLength is called whenever someone manually enters in a step length
     * @param view
     */
    public void stepLength(View view) {
        stepSize = Float.parseFloat(((EditText) findViewById(R.id.steplength)).getText().toString());
    }

    /**
     * Autogenerated override code that doesn't really matter
     */
    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW,
                "Main Page",
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                Uri.parse("android-app://lab4_205_03.ca.uwaterloo.lab4_205_03/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    /**
     * Autogenerated override code that doesn't really matter
     */
    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW,
                "Main Page",
                Uri.parse("http://host/path"),
                Uri.parse("android-app://lab4_205_03.ca.uwaterloo.lab4_205_03/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    public float getHeadingToPoint() {
        if(nextPoint==null){
            return 0;
        }
        return (float) ((float) 90+Math.toDegrees(Math.atan2(nextPoint.y-mv.getUserPoint().y,nextPoint.x-mv.getUserPoint().x)));
    }
}

