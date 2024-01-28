package com.example.android.wifirttscan;

import android.net.wifi.ScanResult;
import android.os.Environment;

import org.apache.commons.math3.fitting.leastsquares.*;
//import org.apache.commons.math3.optim.Optimum;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.util.Pair;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;




public class LeastSquareOptimizationOnDistanceOnly {
    double[] base_i_optimal = new double[Config.numberOfExaminedAP];
    double[] distanceToEachAP = new double[Config.numberOfExaminedAP];
    static Coordinate[] apCoordinate;
    static String[] BSSID;
    static double[][] d_i_values;
    static int numberOfObservationPerAP;
    double[] averageValueOfObservationPerAP = new double[Config.numberOfExaminedAP];
    public double[][] optimalExpResult= new double[10000][6];
    public int expCount = 0;
    public LeastSquareOptimizationOnDistanceOnly(List<ScanResult> mAPWithRange, ArrayList<ArrayList<Double>> rangeList){
        apCoordinate = new Coordinate[mAPWithRange.size()];
        BSSID = new String[mAPWithRange.size()];
        numberOfObservationPerAP = 0;
        /*for (int i = 0; i < mAPWithRange.size(); i++){
            BSSID[i] = mAPWithRange.get(i).BSSID;
            for (int j = 0; j < Config.numberOfExaminedAP; j++) {
                if (mAPWithRange.get(i).BSSID.equals(Config.apBSSIDLocation[j].BSSID)){
                    apCoordinate[i] = new Coordinate(Config.apBSSIDLocation[j].location.x, Config.apBSSIDLocation[j].location.y);
                    break;
                }
            }
        }*/
        //---------This code for testing, use the comment above code for untesting----------
        for (int i = 0; i < mAPWithRange.size(); i++){
            apCoordinate[i] = new Coordinate(Config.apBSSIDLocation[i].location.x, Config.apBSSIDLocation[i].location.y);
        }
        //----------End of Testing code
        for (int i = 0; i < rangeList.size(); i++){
            if (rangeList.get(i).size() > numberOfObservationPerAP){
                numberOfObservationPerAP = rangeList.get(i).size();
            }
        }
        d_i_values = new double[numberOfObservationPerAP][Config.numberOfExaminedAP];
        //Remove outlier
        for (int i = 0; i < rangeList.size(); i++){
            ArrayList<Double> data = rangeList.get(i);
            RemoveOutliers rOutliers = new RemoveOutliers(data);
            ArrayList<Double> cleanedData = rOutliers.getCleanedData();
            rangeList.set(i, cleanedData);
        }

        //fill missing value if the number of observation is less than numberOfObservationPerAP
        for (int i = 0; i < rangeList.size(); i++){
            double average = 0;
            for (int j = 0; j < rangeList.get(i).size(); j++){
                average += rangeList.get(i).get(j);
            }
            average = average / rangeList.get(i).size();
            averageValueOfObservationPerAP[i] = average;
        }
        for (int i = 0; i < rangeList.size(); i++){
            for (int j = 0; j < rangeList.get(i).size(); j++){
                d_i_values[j][i] = rangeList.get(i).get(j);
            }
            for (int j = rangeList.get(i).size(); j < numberOfObservationPerAP; j++){
                d_i_values[j][i] = averageValueOfObservationPerAP[i];
            }
        }
    }

    private double getMinimumReadingOfAP(int apIndex){
        double min = 10e6;
        for (int i = 0; i < numberOfObservationPerAP; i++){
            if (d_i_values[i][apIndex] < min){
                min = d_i_values[i][apIndex];
            }
        }
        return min;
    }

    public void run() {

        double[] L = new double[]{0, 0}; // Initial guess for r0
        double[] params = new double[]{L[0],L[1]};
        double[] baseInitial = new double[apCoordinate.length]; // Initial guess for base_i0
        double[] base = new double[apCoordinate.length]; // Initial guess for base_i0
        for (int i = 0; i < apCoordinate.length; i++){
            baseInitial[i] =  getMinimumReadingOfAP(i);
        }
        for (int test0 = -6; test0 <= 0; test0++){//Scan the base in range of 3 meters
            for (int test1 = -6; test1 <= 0; test1++){
                for (int test2 = -6; test2 <= 0; test2++){
                    base[0] = baseInitial[0] + 0.5 * test0;
                    base[1] = baseInitial[1] + 0.5 * test1;
                    base[2] = baseInitial[2] + 0.5 * test2;
                    if (Math.abs(base[0] - 2561.03100585937) < 1e-5 && Math.abs(base[1] - 2484.80590820312) < 1e-5 && Math.abs(base[2] - 2560.59301757812) < 1e-5){
                        base[0] = base[0];
                    }
                    final double[][] distanceReading = new double[numberOfObservationPerAP][apCoordinate.length];
                    for (int i = 0; i < numberOfObservationPerAP; i++){
                        for (int j = 0; j < apCoordinate.length; j++){
                            distanceReading[i][j] = d_i_values[i][j] - base[j];
                        }
                    }
                    MultivariateJacobianFunction model = new MultivariateJacobianFunction() {
                        @Override
                        public Pair<RealVector, RealMatrix> value(RealVector point) {
                            return objectiveFunction(point, distanceReading);
                        }
                    };
                    LeastSquaresProblem problem = new LeastSquaresBuilder()
                            .model(model)
                            .target(new ArrayRealVector(new double[distanceReading.length]))
                            .checker(new ConvergenceChecker<LeastSquaresProblem.Evaluation>() {
                                @Override
                                public boolean converged(int i, LeastSquaresProblem.Evaluation previous, LeastSquaresProblem.Evaluation current) {
                                    double previousCost = previous.getCost();
                                    double currentCost = current.getCost();
                                    //double current.getPoint().getEntry(0)
                                    double costDifference = Math.abs(previousCost - currentCost);
                                    return costDifference < 1e-8; // Adjust the threshold
                                }
                            })
                            .maxEvaluations(10000)
                            .maxIterations(1000)
                            .start(params)
                            .build();
                    LeastSquaresOptimizer optimizer = new LevenbergMarquardtOptimizer();
                    LeastSquaresOptimizer.Optimum optimum = optimizer.optimize(problem);
                    RealVector optimizedParameters = optimum.getPoint();
                    double[] r_optimal = new double[]{optimizedParameters.getEntry(0), optimizedParameters.getEntry(1)};
                    double fun_optimal_RMS = optimum.getRMS();
                    double fun_optimal_Cost = optimum.getCost();
                    optimalExpResult[expCount][0] =  r_optimal[0];
                    optimalExpResult[expCount][1] =  r_optimal[1];
                    optimalExpResult[expCount][2] = base[0];
                    optimalExpResult[expCount][3] = base[1];
                    optimalExpResult[expCount][4] = base[2];
                    optimalExpResult[expCount][5] = fun_optimal_Cost;
                    expCount++;
                    //System.out.println("hhqt," + r_optimal[0] + "," + r_optimal[1] + "," + base[0] + "," + base[1] + "," + base[2] + "," + fun_optimal_Cost);
                }
            }
        }
    }

    public static Pair<RealVector, RealMatrix> objectiveFunction(RealVector params, double[][] distanceReading) {
        Coordinate r0 = new Coordinate(params.getEntry(0), params.getEntry(1));

        /*if (r0.x < -20)
            r0.x = -20;
        if (r0.x > 20)
            r0.x = 20;
        if (r0.y < -20)
            r0.y = -20;
        if (r0.y > 20)
            r0.y = 20;*/

        int apNumber = apCoordinate.length;
        int num_elements = distanceReading.length;
        double[] error = new double[num_elements];

        RealVector values = new ArrayRealVector(num_elements);
        RealMatrix jacobian = new Array2DRowRealMatrix(num_elements, 2);

        for (int k = 0; k < num_elements; k++) {
            error[k] = 0.0;
            Coordinate jacobianLocation = new Coordinate(0,0);
            for (int i = 0; i < apNumber; i++) {
                double diff =  Math.pow((Math.sqrt(Math.pow(r0.x - apCoordinate[i].x, 2) + Math.pow(r0.y - apCoordinate[i].y, 2)))
                        -   distanceReading[k][i],2);
                error[k] += diff;
                //Jacobian matrix
                jacobianLocation.x += 2 * Math.sqrt(diff) *  (r0.x - apCoordinate[i].x) / Math.sqrt(Math.pow(r0.x - apCoordinate[i].x, 2) + Math.pow(r0.y - apCoordinate[i].y, 2));
                jacobianLocation.y += 2 * Math.sqrt(diff) *  (r0.y - apCoordinate[i].y) / Math.sqrt(Math.pow(r0.x - apCoordinate[i].x, 2) + Math.pow(r0.y - apCoordinate[i].y, 2));
            }
            jacobian.setEntry(k, 0, jacobianLocation.x);
            jacobian.setEntry(k, 1, jacobianLocation.y);
            values.setEntry(k, error[k]);
        }

        return new Pair<>(values, jacobian);
    }
}



