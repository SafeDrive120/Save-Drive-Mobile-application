/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Formatter;
import java.util.Locale;
import java.util.List;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.YoloV4Classifier;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

public class DetectorActivity extends CameraActivity implements OnImageAvailableListener{
    private static final Logger LOGGER = new Logger();
    private static final int TF_OD_API_INPUT_SIZE = 416;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "yolotiny.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    //Set the Rickshaw Accuracy
    private static final float MINIMUM_CONFIDENCE_TF_OD_API_RICKSHAW = 0.9f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    //Set the Delay in Warning 1000 = 1sec
    private static final int short_delay = 100;
    public int value;
    public static int mode_value;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;
    private Classifier detector;
    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private ArrayList<Toast> msjsToast = new ArrayList<Toast>();
    private boolean computingDetection = false;
    private long timestamp = 0;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private BorderedText borderedText;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);
        tracker = new MultiBoxTracker(this);
        int cropSize = TF_OD_API_INPUT_SIZE;
        try {
            detector = YoloV4Classifier.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_IS_QUANTIZED);
//            detector = TFLiteObjectDetectionAPIModel.create(
//                    getAssets(),
//                    TF_OD_API_MODEL_FILE,
//                    TF_OD_API_LABELS_FILE,
//                    TF_OD_API_INPUT_SIZE,
//                    TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();
        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);
        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    //It will all the remaining toasts
    private void killAllToast(){
        for(Toast t:msjsToast){
            if(t!=null) {
                t.cancel();
            }
        }
        msjsToast.clear();
    }

    //speed kill toast
    private void SpeedKillToast()
    {
        for(Toast t:msjsToast){
            if (t!=null){
                t.cancel();
            }
        }
    }

    //focal lenght function
    private float FocalLenght(float measures_dist, float real_width, float width_in_frame){
        float focal_lenght = (width_in_frame*measures_dist)/real_width;
        return focal_lenght;
    }

    private float Distance_Finder(float focal_lenght, float real_width, float width_in_frame){
        float distance = (real_width*focal_lenght)/width_in_frame;
        return distance;
    }

    //Add a parameter
    @Override
    protected void processImage(float Speed) {

        ++timestamp;
        final long currTimestamp = timestamp;

        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        Log.e("CHECK", "run: " + results.size());

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        float minimumConfidence2 = MINIMUM_CONFIDENCE_TF_OD_API_RICKSHAW;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence2 = MINIMUM_CONFIDENCE_TF_OD_API_RICKSHAW;
                                break;
                        }

                        //Get the bundle from MainActivity Extract the value and mode value.
                        Bundle bundle = getIntent().getExtras();
                        value = bundle.getInt("state");
                        mode_value = bundle.getInt("mode");

                        Integer apxdist = 0;

                        //Measure Distance in Foot
                        if(value == 14)
                        {
                            apxdist = 160;
                        }
                        else if(value == 18)
                        {
                            apxdist = 150;
                        }
                        else if(value == 22)
                        {
                            apxdist = 120;
                        }
                        else if(value == 26)
                        {
                            apxdist = 100;
                        }
                        else if(value == 30)
                        {
                            apxdist = 90;
                        }
                        else if(value == 34)
                        {
                            apxdist = 80;
                        }
                        else if(value == 40)
                        {
                            apxdist = 70;
                        }

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            Log.d("Result", String.valueOf(result));
                            final RectF location = result.getLocation();
                            final String title = result.getTitle();
                            MediaPlayer ring= MediaPlayer.create(DetectorActivity.this,R.raw.warn);

                            //Check Conditions
                            if (location != null && ((title.equals("person") && result.getConfidence() >= minimumConfidence) || (title.equals("car") && result.getConfidence() >= minimumConfidence) || (title.equals("motorcycles") && result.getConfidence() >= minimumConfidence) || (title.equals("heavytraffic") && result.getConfidence() >= minimumConfidence) || (title.equals("rickshaw") && result.getConfidence() >= minimumConfidence2))) {
                                canvas.drawRect(location, paint);
                                Log.d("null",String.valueOf(location));

                                //Calculate Collision Possible
                                float mid_x = (Float.parseFloat(String.valueOf(location.left)) + Float.parseFloat(String.valueOf(location.right)))/2;
                                float mid_y = (Float.parseFloat(String.valueOf(location.top)) + Float.parseFloat(String.valueOf(location.bottom)))/2;
                                float apx_dist = Float.parseFloat(String.valueOf(location.right)) - Float.parseFloat(String.valueOf(location.left));
                                float width = location.width();
                                Log.d("width:", String.valueOf(width));

                                Log.d("MID x:", String.valueOf(mid_x));
                                Log.d("MID y:", String.valueOf(mid_y));
                                Log.d("Width in Frame:", String.valueOf(apx_dist));

                                Log.d("Title", title);

                                if (mode_value == 2)
                                {
                                    if (Speed != 0.00)
                                    {
                                        if(title.equals("person"))
                                        {
                                            killAllToast();
                                            Toast toast = Toast.makeText(DetectorActivity.this, "ALERT! HURDLE " + title.toUpperCase() + " IN THE FRONT.", Toast.LENGTH_SHORT);
                                            msjsToast.add(toast);
                                            TextView v = (TextView) toast.getView().findViewById(android.R.id.message);
                                            toast.getView().setBackgroundColor(Color.BLACK);
                                            v.setTextColor(Color.RED);
                                            v.setTextSize(20);
                                            toast.setDuration(short_delay);
                                            toast.show();
                                            ring.start();
                                        }
                                        else if(apx_dist > apxdist){
                                                if(mid_x>50 && mid_x<250){
                                                    killAllToast();
                                                    Toast toast = Toast.makeText(DetectorActivity.this, "ALERT! " + title.toUpperCase() + " IN THE FRONT.", Toast.LENGTH_SHORT);
                                                    msjsToast.add(toast);
                                                    TextView v = (TextView) toast.getView().findViewById(android.R.id.message);
                                                    toast.getView().setBackgroundColor(Color.BLACK);
                                                    v.setTextColor(Color.RED);
                                                    v.setTextSize(20);
                                                    toast.setDuration(short_delay);
                                                    toast.show();
                                                    ring.start();
                                                }
                                        }
                                    }
                                }
                                else if (mode_value == 3)
                                {
                                    if(title.equals("person"))
                                    {
                                        killAllToast();
                                        Toast toast = Toast.makeText(DetectorActivity.this, "ALERT! HURDLE " + title.toUpperCase() + " IN THE FRONT.", Toast.LENGTH_SHORT);
                                        msjsToast.add(toast);
                                        TextView v = (TextView) toast.getView().findViewById(android.R.id.message);
                                        toast.getView().setBackgroundColor(Color.BLACK);
                                        v.setTextColor(Color.RED);
                                        v.setTextSize(20);
                                        toast.setDuration(short_delay);
                                        toast.show();
                                        ring.start();
                                    }
                                    else if(apx_dist > apxdist){
                                            if(mid_x>50 && mid_x<250){
                                                killAllToast();
                                                Toast toast = Toast.makeText(DetectorActivity.this, "ALERT! " + title.toUpperCase() + " IN THE FRONT." , Toast.LENGTH_SHORT);
                                                msjsToast.add(toast);
                                                TextView v = (TextView) toast.getView().findViewById(android.R.id.message);
                                                toast.getView().setBackgroundColor(Color.BLACK);
                                                v.setTextColor(Color.RED);
                                                v.setTextSize(20);
                                                toast.setDuration(short_delay);
                                                toast.show();
                                                ring.start();
                                            }
                                    }
                                }

                                cropToFrameTransform.mapRect(location);

                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showFrameInfo(previewWidth + "x" + previewHeight);
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                        showInference(lastProcessingTimeMs + "ms");
                                    }
                                });
                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }
}
