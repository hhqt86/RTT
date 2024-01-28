package com.example.android.wifirttscan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RemoveOutliers {
    ArrayList<Double> cleanedData;
    public static double calculateMean(ArrayList<Double> array) {
        double sum = 0.0;
        for (double value : array) {
            sum += value;
        }
        return sum / array.size();
    }

    public static double calculateStandardDeviation(ArrayList<Double> array, double mean) {
        double sum = 0.0;
        for (double value : array) {
            sum += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sum / array.size());
    }

    public static ArrayList<Double> removeOutliers(ArrayList<Double> array, double threshold) {
        double mean = calculateMean(array);
        double standardDeviation = calculateStandardDeviation(array, mean);
        ArrayList<Double> cleanedList = new ArrayList<>();
        for (double value : array) {
            if (Math.abs(value - mean) < threshold * standardDeviation) {
                cleanedList.add(value);
            }
        }
        /*double[] cleanedArray = new double[cleanedList.size()];
        for (int i = 0; i < cleanedList.size(); i++) {
            cleanedArray[i] = cleanedList.get(i);
        }*/
        return cleanedList;
    }

    public RemoveOutliers (ArrayList<Double> data) {
        double threshold = 2.0;
        cleanedData = removeOutliers(data, threshold);
    }

    public ArrayList<Double> getCleanedData(){
        return this.cleanedData;
    }

}

