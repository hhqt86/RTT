/*
 * Copyright (C) 2018 Google Inc. All Rights Reserved.
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
package com.example.android.wifirttscan;

import static com.example.android.wifirttscan.AccessPointRangingResultsActivity.SCAN_RESULT_EXTRA;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;

import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.example.android.wifirttscan.MyAdapter.ScanResultClickListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;


/**
 * Displays list of Access Points enabled with WifiRTT (to check distance). Requests location
 * permissions if they are not approved via secondary splash screen explaining why they are needed.
 */


public class MainActivity extends AppCompatActivity implements ScanResultClickListener {

    private static final String TAG = "MainActivity";

    private boolean mLocationPermissionApproved = false;

    List<ScanResult> mAccessPointsUnSupporting80211mc;

    private WifiManager mWifiManager;
    private WifiScanReceiver mWifiScanReceiver;

    private TextView mOutputTextView;
    private RecyclerView mRecyclerView;

    private MyAdapter mAdapter;

    // For ranging request
    private int mNumberOfRangeRequests;
    private ScanResult mScanResult;
    private WifiRttManager mWifiRttManager;
    private trainingRttRangingResultCallback trainingmRttRangingResultCallback;
    private testingRttRangingResultCallback testingmRttRangingResultCallback;
    private static float[][] rangeDistanceListForNN = new float[100][Config.numberOfExaminedAP];
    private static int elementCountForNN = 0;
    // Triggers additional RangingRequests with delay (mMillisecondsDelayBeforeNewRangingRequest).
    final Handler mRangeRequestDelayHandler = new Handler();
    private int mMillisecondsDelayBeforeNewRangingRequest = 1000; //hhqt set default value to 1000ms
    private String mMAC;
    private int mNumberOfSuccessfulRangeRequests;
    private long referenceMillis;
    List<ScanResult> mAPWithRange;
    ArrayList<ArrayList<Double>> rangeList; //rangeList[numberOfAP][numberOfInstance]
    int writeRangeListLocationID = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOutputTextView = findViewById(R.id.access_point_summary_text_view);
        mRecyclerView = findViewById(R.id.recycler_view);

        // Improve performance if you know that changes in content do not change the layout size
        // of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        LayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);

        mAccessPointsUnSupporting80211mc = new ArrayList<>();

        mAdapter = new MyAdapter(mAccessPointsUnSupporting80211mc, this);
        mRecyclerView.setAdapter(mAdapter);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiScanReceiver = new WifiScanReceiver();

        // For ranging request
        mNumberOfRangeRequests = 0;
        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        trainingmRttRangingResultCallback = new trainingRttRangingResultCallback();
        testingmRttRangingResultCallback = new testingRttRangingResultCallback();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

        mLocationPermissionApproved =
                ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        registerReceiver(
                mWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        unregisterReceiver(mWifiScanReceiver);
    }

    private void logToUi(final String message) {
        if (!message.isEmpty()) {
            Log.d(TAG, message);
            mOutputTextView.setText(message);
        }
    }

    @Override
    public void onScanResultItemClick(ScanResult scanResult) {
        Log.d(TAG, "onScanResultItemClick(): ssid: " + scanResult.SSID);

        Intent intent = new Intent(this, AccessPointRangingResultsActivity.class);
        intent.putExtra(SCAN_RESULT_EXTRA, scanResult);
        startActivity(intent);
    }

    public void onClickFindDistancesToAccessPoints(View view) {
        if (mLocationPermissionApproved) {
            logToUi(getString(R.string.retrieving_access_points));
            mWifiManager.startScan();

        } else {
            // On 23+ (M+) devices, fine location permission not granted. Request permission.
            Intent startIntent = new Intent(this, LocationPermissionRequestActivity.class);
            startActivity(startIntent);
        }
    }

    private class WifiScanReceiver extends BroadcastReceiver {

        private boolean inListOfExamineAP(String BSSID){

            for (int i = 0; i < Config.numberOfExaminedAP; i++){
                if (BSSID.equals(Config.apBSSIDLocation[i].BSSID) || Config.apBSSIDLocation[i].BSSID.equals("")){
                    return true;
                }
            }
            return false;
        }

        private List<ScanResult> find80211mcUnSupportedAccessPoints(
                @NonNull List<ScanResult> originalList) {
            List<ScanResult> newList = new ArrayList<ScanResult>(Config.numberOfExaminedAP);
            boolean[] mark = new boolean[Config.numberOfExaminedAP];//check if the examinedAP has been added to list
            for (int i = 0; i < Config.numberOfExaminedAP; i++){
                newList.add(new ScanResult());
            }
            for (ScanResult scanResult : originalList) {
                if (!scanResult.is80211mcResponder()) {
                    for (int i = 0; i < Config.numberOfExaminedAP; i++) {//Only take the examined AP
                        if (mark[i] == false && (scanResult.BSSID.equals(Config.apBSSIDLocation[i].BSSID) || Config.apBSSIDLocation[i].BSSID.equals(""))) {
                            newList.set(i, scanResult);
                            mark[i] = true;
                            break;
                        }
                    }

                /*if (newList.size() >= RangingRequest.getMaxPeers()) {
                    break;
                }*/
                }
            }
            return newList;
        }



        // This is checked via mLocationPermissionApproved boolean
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {

            List<ScanResult> scanResults = mWifiManager.getScanResults();
            if (scanResults != null) {
                if (mLocationPermissionApproved) {
                    mAccessPointsUnSupporting80211mc = find80211mcUnSupportedAccessPoints(scanResults);

                    mAdapter.swapData(mAccessPointsUnSupporting80211mc);

                    logToUi(
                            scanResults.size()
                                    + " APs discovered, "
                                    + mAccessPointsUnSupporting80211mc.size()
                                    + " non 2sided-RTT capable.");

                } else {
                    // TODO (jewalker): Add Snackbar regarding permissions
                    Log.d(TAG, "Permissions not allowed.");
                }
            }
        }
    }



    public void collectDataForTraining(View view) {

        mAPWithRange = new ArrayList<ScanResult>();
        rangeList = new ArrayList<ArrayList<Double>>();
        for (int i = 0; i < mAccessPointsUnSupporting80211mc.size();i++){
            //mAPWithRange.add(mAccessPointsUnSupporting80211mc.get(i));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mAPWithRange.add(new ScanResult(mAccessPointsUnSupporting80211mc.get(i)));
            }
            rangeList.add(new ArrayList<Double>());
        }
        int i = 0;
        referenceMillis = Calendar.getInstance().getTimeInMillis();
        startRangingRequest(0);
        // Permission for fine location should already be granted via MainActivity (you can't get
        // to this class unless you already have permission. If they get to this class, then disable
        // fine location permission, we kick them back to main activity.

    }

    public void liveTesting(View view) {

        mAPWithRange = new ArrayList<ScanResult>();
        rangeList = new ArrayList<ArrayList<Double>>();
        for (int i = 0; i < mAccessPointsUnSupporting80211mc.size();i++){
            //mAPWithRange.add(mAccessPointsUnSupporting80211mc.get(i));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mAPWithRange.add(new ScanResult(mAccessPointsUnSupporting80211mc.get(i)));
            }
            rangeList.add(new ArrayList<Double>());
        }
        int i = 0;
        referenceMillis = Calendar.getInstance().getTimeInMillis();
        startRangingRequest(1);
        // Permission for fine location should already be granted via MainActivity (you can't get
        // to this class unless you already have permission. If they get to this class, then disable
        // fine location permission, we kick them back to main activity.

    }

    private void writeToFileRangeListOptimizeResult(LeastSquareOptimizationOnDistanceOnly Op, int testCaseID){
        String fileName = "ExperimentResult/ExperimentResult" + testCaseID + ".txt";

        // Get the external storage directory
        File externalStorageDir = Environment.getExternalStorageDirectory();

        // Create a file object
        File privateDir = getExternalFilesDir(null);
        File file = new File(privateDir, fileName);

        try {
            // Open a FileOutputStream to write to the file
            file.getParentFile().mkdirs();
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file, false);
            for (int i = 0; i < Op.expCount; i++) {
                for (int j = 0; j < 5; j++){
                    fos.write((Op.optimalExpResult[i][j] + ",").getBytes());
                }
                fos.write((Op.optimalExpResult[i][5] + "\n").getBytes());
            }
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception
        }
    }

    private void writeToFileRangeListOptimizeResult(LeastSquareOptimization Op){
        String fileName = "rangeList.txt";

        // Get the external storage directory
        File externalStorageDir = Environment.getExternalStorageDirectory();

        // Create a file object
        File privateDir = getExternalFilesDir(null);
        File file = new File(privateDir, fileName);

        try {
            // Open a FileOutputStream to write to the file
            file.getParentFile().mkdirs();
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write("------------Optimal Result---------------\n".getBytes());
            fos.write((Op.r_optimal.x +  " " + Op.r_optimal.y + "\n").getBytes());
            fos.write((Op.base_i_optimal[0] + " " + Op.base_i_optimal[1] + " " + Op.base_i_optimal[2] +"\n").getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception
        }
    }
    private void writeToFileRangeList(ArrayList<ArrayList<Double>> RangeList, int writeRangeLisLocationID) {
        String fileName = "rangeListTest.txt";

        // Get the external storage directory
        //File externalStorageDir = Environment.getExternalStorageDirectory();

        // Create a file object
        File privateDir = getExternalFilesDir(null);
        File file = new File(privateDir, fileName);

        try {
            // Open a FileOutputStream to write to the file
            file.getParentFile().mkdirs();
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write(("-------------RangeList" + writeRangeLisLocationID + "---------\n").getBytes());
            for (int element = 0; element < rangeList.get(0).size(); element++){
                for (int apID = 0; apID < rangeList.size(); apID++){
                    fos.write((rangeList.get(apID).get(element) + ",").getBytes());
                }
                fos.write("\n".getBytes());
                fos.write("\n".getBytes());
            }
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception
        }
    }

    private String readRangeListFileAndProcessForEachTestCase() {
        String fileContent;
        try {
            String fileName = "rangeList.txt";
            File privateDir = getExternalFilesDir(null);
            File file = new File(privateDir, fileName);

            if (file.exists()) {
                int testCaseID = 0;
                FileInputStream fileInputStream = new FileInputStream(file);
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                BufferedReader reader = new BufferedReader(inputStreamReader);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.charAt(0) == '-'){//Begin a new test
                        rangeList = new ArrayList<ArrayList<Double>>();
                        for (int i = 0; i < Config.numberOfExaminedAP; i++){
                            rangeList.add(new ArrayList<Double>());
                        }
                        mAPWithRange = new ArrayList<ScanResult>();
                        for (int i = 0; i < Config.numberOfExaminedAP; i++){
                            mAPWithRange.add(new ScanResult());
                            line = reader.readLine();
                            int pointerLocation = 0;
                            for (int endLocation = 1; endLocation < line.length(); endLocation++){
                                if (line.charAt(endLocation) == ' '){
                                    Double reading = Double.parseDouble(line.substring(pointerLocation, endLocation));
                                    pointerLocation = endLocation + 1;
                                    rangeList.get(i).add(reading);
                                }
                            }
                            reader.readLine();
                        }
                        LeastSquareOptimizationOnDistanceOnly lsOp = new LeastSquareOptimizationOnDistanceOnly(mAPWithRange, rangeList);
                        lsOp.run();
                        System.out.println("Running Testcase: " + testCaseID);
                        writeToFileRangeListOptimizeResult(lsOp, testCaseID++);
                    }
                }





            } else {
                System.out.println("File not found.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    public void startRangingRequest(int mode) {
        //mode = 0: collectDataForTraining; mode = 1: liveTesting
        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            finish();
        }

        mNumberOfRangeRequests++;
        //hhqt take the first item to get ranged

        RangingRequest.Builder builder = new RangingRequest.Builder();
        for (ScanResult element : mAccessPointsUnSupporting80211mc) {
            if (element.BSSID != null) {
                builder.addAccessPoint(element);
            }
        }
        // Build the RangingRequest
        RangingRequest rangingRequest = builder.build();


        if (mode == 0) { //Collect data for training
            mWifiRttManager.startRanging(
                    rangingRequest, getApplication().getMainExecutor(), trainingmRttRangingResultCallback);
        }
        else{
            if (mode == 1){//live Testing
                rangeDistanceListForNN = new float[100][Config.numberOfExaminedAP];
                elementCountForNN = 0;
                mWifiRttManager.startRanging(
                        rangingRequest, getApplication().getMainExecutor(), testingmRttRangingResultCallback);
            }
        }


    }
    private class trainingRttRangingResultCallback extends RangingResultCallback {

        private void queueNextRangingRequest() {
            mRangeRequestDelayHandler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            startRangingRequest(0);
                        }
                    },
                    mMillisecondsDelayBeforeNewRangingRequest);
        }

        @Override
        public void onRangingFailure(int code) {
            Log.d(TAG, "onRangingFailure() code: " + code);
            queueNextRangingRequest();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> list) {
            Log.d(TAG, "onRangingResults(): " + list);
            // Because we are only requesting RangingResult for one access point (not multiple
            // access points), this will only ever be one. (Use loops when requesting RangingResults
            // for multiple access points.)
            double[] rangeDistance = new double[list.size()];
            for (int i = 0; i < list.size(); i++){
                rangeDistance[i] = -1;
                RangingResult rangingResult = list.get(i);
                    if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS) {

                        mNumberOfSuccessfulRangeRequests++;
                        float rangeSTD = rangingResult.getDistanceStdDevMm() / 1000f;
                        if (rangeSTD < 10) {
                            rangeDistance[i] = (rangingResult.getDistanceMm() / 1000f);
                        }
                        float successRatio =
                                ((float) mNumberOfSuccessfulRangeRequests
                                        / (float) mNumberOfRangeRequests)
                                        * 100;
                    }  else {
                        Log.d(TAG, "RangingResult failed.");
                    }

            }
            int rangeDistanceIndex = 0; //Since there maybe some APs that do not respond, use this index
                                        //to indicate the correct rangeDistance
            for (int i = 0; i < mAccessPointsUnSupporting80211mc.size(); i++){
                //mAPWithRange.get(i).SSID += " " + rangeDistance[i];
                //rangeList.get(i).add(rangeDistance[i]);
                if (mAccessPointsUnSupporting80211mc.get(i).BSSID != null) {
                    rangeList.get(i).add(rangeDistance[rangeDistanceIndex]);
                    rangeDistanceIndex++;
                }
                else{
                    rangeList.get(i).add(-1.0);
                }
            }
            long currentMillis = Calendar.getInstance().getTimeInMillis();
            if (currentMillis - referenceMillis <= Config.dataTrainingDuration) {//collect data for duration seconds
                logToUi("Time: " + (currentMillis - referenceMillis) / 1000);
                queueNextRangingRequest();
            }
            else{
                //ResultView rv = getAcquireInformation(mAPWithRange, rangeList);
                System.out.println("---hhqt----");
                double[] averageDistanceOfEachAP = new double[rangeList.size()];
                int[] countAverageDistanceOfEachAP = new int[rangeList.size()];
                for (int i = 0; i < rangeList.size(); i++){
                    System.out.println(mAPWithRange.get(i).SSID + " " + mAPWithRange.get(i).BSSID);
                    for (int j = 0; j < rangeList.get(i).size(); j++){
                        if (rangeList.get(i).get(j) != -1){
                            averageDistanceOfEachAP[i] += rangeList.get(i).get(j);
                            countAverageDistanceOfEachAP[i]++;
                        }
                        System.out.print(rangeList.get(i).get(j) + " ");
                    }
                    averageDistanceOfEachAP[i] = averageDistanceOfEachAP[i] * 1.0 / countAverageDistanceOfEachAP[i];
                    System.out.println();
                }
                String outputLog = "";
                for (int i = 0; i < rangeList.size(); i++){
                    if (countAverageDistanceOfEachAP[i] != 0) {
                        outputLog += "Average Distance for AP" + i + ": " + averageDistanceOfEachAP[i] + "\n";
                    }
                    else{
                        outputLog += "Average Distance for AP" + i + ": Cannot range to this AP \n";
                    }
                }
                //logToUi(outputLog);
                //This place is the code for optimzation when not testing
                //LeastSquareOptimization lsOp = new LeastSquareOptimization(mAPWithRange, rangeList);
                //lsOp.run();
                /*This code for reading the range and write to file*/
                writeToFileRangeList(rangeList, writeRangeListLocationID);
                mAdapter.swapData(mAPWithRange);
                logToUi("Finish Location: " + writeRangeListLocationID++);
                /*logToUi("Optimal location: " + lsOp.r_optimal.x + " " + lsOp.r_optimal.y
                            + "\nBase each AP: " + lsOp.base_i_optimal[0] + " " + lsOp.base_i_optimal[1] + " " + lsOp.base_i_optimal[2]
                            + "\nDistance to each AP: " + lsOp.distanceToEachAP[0] + " " + lsOp.distanceToEachAP[1] + " " + lsOp.distanceToEachAP[2]
                        );*/
            }
        }
    }

    private class testingRttRangingResultCallback extends RangingResultCallback {

        private void queueNextRangingRequest() {
            mRangeRequestDelayHandler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            startRangingRequest(1);
                        }
                    },
                    mMillisecondsDelayBeforeNewRangingRequest);
        }

        @Override
        public void onRangingFailure(int code) {
            Log.d(TAG, "onRangingFailure() code: " + code);
            queueNextRangingRequest();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> list) {
            String outputLog = "";
            Log.d(TAG, "onRangingResults(): " + list);
            // Because we are only requesting RangingResult for one access point (not multiple
            // access points), this will only ever be one. (Use loops when requesting RangingResults
            // for multiple access points.)
            float[] rangeDistance = new float[list.size()];

            for (int i = 0; i < list.size(); i++) {
                rangeDistance[i] = -1;
                RangingResult rangingResult = list.get(i);
                if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS) {
                    mNumberOfSuccessfulRangeRequests++;
                    float rangeSTD = rangingResult.getDistanceStdDevMm() / 1000f;
                    if (rangeSTD < 10) {
                        rangeDistance[i] = (rangingResult.getDistanceMm() / 1000f);
                    }
                    float successRatio =
                            ((float) mNumberOfSuccessfulRangeRequests
                                    / (float) mNumberOfRangeRequests)
                                    * 100;
                } else {
                    Log.d(TAG, "RangingResult failed.");
                }

            }
            boolean isEnoughData = true;
            for (int i = 0; i < Config.numberOfExaminedAP; i++){
                if (rangeDistance[i] == -1){
                    isEnoughData = false;
                }
            }
            if (isEnoughData){
                for (int i = 0; i < Config.numberOfExaminedAP; i++){
                    rangeDistanceListForNN[elementCountForNN][i] = rangeDistance[i];
                }
                elementCountForNN++;
            }
            long currentMillis = Calendar.getInstance().getTimeInMillis();
            if (currentMillis - referenceMillis <= Config.dataTestingDuration) {//collect data for duration seconds
                logToUi("Time: " + (currentMillis - referenceMillis) / 1000);
                queueNextRangingRequest();
            }
            else {
                //Load the trained network and use new data for live testing
                try {
                    AssetFileDescriptor fileModel = getAssets().openFd("modelRelu.tflite");
                    TrainedNetwork trainedNetwork = new TrainedNetwork(fileModel);
                    float[][] inputDataForNetwork = new float[elementCountForNN][Config.numberOfExaminedAP];
                    if (elementCountForNN > 0) {
                        float[] distanceToAP = trainedNetwork.runInference(inputDataForNetwork);
                        outputLog += "Distance to AP ( ";

                        for (int i = 0; i < Config.numberOfExaminedAP; i++) {
                            outputLog += i + " ";
                        }
                        outputLog += "): ( ";
                        for (int k = 0; k < elementCountForNN; k++){
                            for (int i = 0; i < Config.numberOfExaminedAP; i++) {
                                outputLog += distanceToAP[i] + " ";
                            }
                            outputLog += "); (";
                        }
                    } else {
                        outputLog = "No data from AP ";
                        for (int i = 0; i < Config.numberOfExaminedAP; i++) {
                            if (rangeDistance[i] == -1) {
                                outputLog += i + " ";
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            logToUi(outputLog);
            mAdapter.swapData(mAPWithRange);
        }
    }
}
