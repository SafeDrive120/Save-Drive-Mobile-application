package org.tensorflow.lite.examples.detection;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.env.Utils;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.YoloV4Classifier;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

// Add Location Listener
public class MainActivity extends AppCompatActivity implements LocationListener {

    public static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    public static int mode;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Array for Spinner
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        List<String> states = Arrays.asList("Select Feet!","14","18","22","26","30","34","40");
        List<String> states2 = Arrays.asList("Select GPS!","GPS Connected","GPS not Connected");

        final Spinner spinner = findViewById(R.id.spin);
        final Spinner spinner2 = findViewById(R.id.spin2);
        Button cameraButton;

        //Set Adapter
        ArrayAdapter adapter = new ArrayAdapter(getApplicationContext(), R.layout.color_spinner_layout, states);
        adapter.setDropDownViewResource(R.layout.color_drowndown_menu);
        ArrayAdapter adapter2 = new ArrayAdapter(getApplicationContext(), R.layout.color_spinner_layout, states2);
        adapter2.setDropDownViewResource(R.layout.color_drowndown_menu);

        //Set Spinner
        spinner.setAdapter(adapter);
        spinner2.setAdapter(adapter2);

        //Get the Permission of GPS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1000);
        }

        //Camera Button Click
        cameraButton = findViewById(R.id.cameraButton);
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Select from Spinner
                String check = spinner.getSelectedItem().toString();
                String check2 = spinner2.getSelectedItem().toString();

                //If Spinner not is Default
                if (!check.equals("Select Feet!") && !check2.equals("Select GPS!"))
                {
                    boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    Bundle bundle = new Bundle();
                    //If User Click GPS Connected
                    if (check2.equals("GPS Connected"))
                    {
                        //Check GPS is enabled
                        if (enabled)
                        {
                            mode = 2;
                            int state = Integer.parseInt(check);
                            //Set Bundle
                            bundle.putInt("mode", mode);
                            bundle.putInt("state", state);
                            //Send it to DetectorActivity
                            Intent it = new Intent(MainActivity.this, DetectorActivity.class);
                            it.putExtras(bundle);
                            startActivity(it);
                        }
                        else
                        {
                            //Print Toast
                            Toast.makeText(MainActivity.this, "Please Check! GPS Was Not Connected", Toast.LENGTH_SHORT).show();
                        }
                    }
                    //If User Click GPS not Connected
                    else if (check2.equals("GPS not Connected"))
                    {
                        //Check
                        if (enabled)
                        {
                            //If GPS connected then Toast
                            Toast.makeText(MainActivity.this, "Please Select The Correct Option! Your GPS is Connected", Toast.LENGTH_SHORT).show();
                        }
                        else
                        {
                            mode = 3;
                            int state = Integer.parseInt(check);
                            //Create Bundle
                            bundle.putInt("mode", mode);
                            bundle.putInt("state", state);
                            //Send it to DetectorActivity
                            Intent it = new Intent(MainActivity.this, DetectorActivity.class);
                            it.putExtras(bundle);
                            startActivity(it);
                        }
                    }
                }
            }
        });


    }

    @Override
    public void onLocationChanged(Location location) {

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
