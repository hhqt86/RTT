package com.example.android.wifirttscan;

import android.net.wifi.ScanResult;

import org.apache.commons.math3.fitting.leastsquares.*;
//import org.apache.commons.math3.optim.Optimum;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.SimpleVectorValueChecker;
import org.apache.commons.math3.util.Pair;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;

import java.util.ArrayList;
import java.util.List;




public class LeastSquareOptimization {
    Coordinate r_optimal;
    double[] base_i_optimal = new double[Config.numberOfExaminedAP];
    double[] distanceToEachAP = new double[Config.numberOfExaminedAP];
    static Coordinate[] apCoordinate;
    static String[] BSSID;
    static double[][] d_i_values;
    static int numberOfObservationPerAP;
    public LeastSquareOptimization(List<ScanResult> mAPWithRange, ArrayList<ArrayList<Double>> rangeList){
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
        //fill missing value if the number of observation is less than numberOfObservationPerAP
        double[] averageValueOfObservationPerAP = new double[Config.numberOfExaminedAP];
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
        int i = 1;
    }
    public void run() {

        double[] r0 = new double[]{0, 0}; // Initial guess for r0
        double[] base_i0 = new double[]{100, 200, 400}; // Initial guess for base_i0

        double[] params0 = new double[r0.length + base_i0.length];
        System.arraycopy(r0, 0, params0, 0, r0.length);
        System.arraycopy(base_i0, 0, params0, r0.length, base_i0.length);

        MultivariateJacobianFunction model = new MultivariateJacobianFunction() {
            @Override
            public Pair<RealVector, RealMatrix> value(RealVector point) {
                return objectiveFunction(point, d_i_values);
            }
        };



        LeastSquaresProblem problem = new LeastSquaresBuilder()
                .model(model)
                .target(new ArrayRealVector(new double[d_i_values.length]))
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
                .start(params0)
                .build();
        LeastSquaresOptimizer optimizer = new LevenbergMarquardtOptimizer();
        LeastSquaresOptimizer.Optimum optimum = optimizer.optimize(problem);
        RealVector optimizedParameters = optimum.getPoint();
        double[] r_optimal = new double[]{optimizedParameters.getEntry(0), optimizedParameters.getEntry(1)};
        double[] base_i_optimal = new double[]{optimizedParameters.getEntry(2), optimizedParameters.getEntry(3), optimizedParameters.getEntry(4)};
        double fun_optimal_RMS = optimum.getRMS();
        double fun_optimal_Cost = optimum.getCost();
        this.r_optimal = new Coordinate(r_optimal[0], r_optimal[1]);
        for (int i = 0; i < Config.numberOfExaminedAP; i++) {
            this.base_i_optimal[i] = base_i_optimal[i];
            this.distanceToEachAP[i] = Config.getDistance(this.r_optimal, Config.apBSSIDLocation[i].location);
        }
        System.out.println("hhqt");
        System.out.println("Optimized r0 values found: " + r_optimal[0] + ", " + r_optimal[1]);
        System.out.println("Optimized base_i0 values: " + base_i_optimal[0] + ", " + base_i_optimal[1] + ", " + base_i_optimal[2]);

    }

    public static Pair<RealVector, RealMatrix> objectiveFunction(RealVector params, double[][] d_i_values) {
        Coordinate r0 = new Coordinate(params.getEntry(0), params.getEntry(1));
        double[] base_i0 = new double[]{params.getEntry(2), params.getEntry(3), params.getEntry(4)};
        /*if (r0.x < -20)
            r0.x = -20;
        if (r0.x > 20)
            r0.x = 20;
        if (r0.y < -20)
            r0.y = -20;
        if (r0.y > 20)
            r0.y = 20;*/

        int apNumber = apCoordinate.length;
        int num_elements = d_i_values.length;
        double[] error = new double[num_elements];

        RealVector values = new ArrayRealVector(num_elements);
        RealMatrix jacobian = new Array2DRowRealMatrix(num_elements, 5);

        for (int k = 0; k < num_elements; k++) {
            error[k] = 0.0;
            Coordinate jacobianLocation = new Coordinate(0,0);
            double[] jacobianBase = new double[apNumber];
            for (int i = 0; i < apNumber; i++) {
                double diff =  (Math.sqrt(Math.pow(r0.x - apCoordinate[i].x, 2) + Math.pow(r0.y - apCoordinate[i].y, 2)))
                        -   Math.abs(base_i0[i] - d_i_values[k][i]);
                error[k] += diff;
                //Jacobian matrix
                jacobianLocation.x +=  (r0.x - apCoordinate[i].x) / Math.sqrt(Math.pow(r0.x - apCoordinate[i].x, 2) + Math.pow(r0.y - apCoordinate[i].y, 2));
                jacobianLocation.y +=  (r0.y - apCoordinate[i].y) / Math.sqrt(Math.pow(r0.x - apCoordinate[i].x, 2) + Math.pow(r0.y - apCoordinate[i].y, 2));
                jacobianBase[i] = - (base_i0[i] - d_i_values[k][i]) /  Math.abs(base_i0[i] - d_i_values[k][i]);
            }
            jacobian.setEntry(k, 0, jacobianLocation.x);
            jacobian.setEntry(k, 1, jacobianLocation.y);
            for (int i = 0; i < apNumber; i++){
                jacobian.setEntry(k, i+ 2, jacobianBase[i]);
            }
            values.setEntry(k, error[k]);
        }

        return new Pair<>(values, jacobian);
    }
}

class Coordinate{
    double x;
    double y;
    public Coordinate(double x0, double y0){
        x = x0;
        y = y0;
    }
}
