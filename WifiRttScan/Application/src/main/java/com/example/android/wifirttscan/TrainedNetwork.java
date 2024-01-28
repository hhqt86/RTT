package com.example.android.wifirttscan;

import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import androidx.appcompat.app.AppCompatActivity;

public class TrainedNetwork  {

    private Interpreter tflite;
    private AssetFileDescriptor fileModel;
    public TrainedNetwork(AssetFileDescriptor fileModel) {
        try {
            // Initialize the TensorFlow Lite interpreter
            this.fileModel = fileModel;
            tflite = new Interpreter(loadModelFile());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Function to load the TensorFlow Lite model from the assets folder
    private MappedByteBuffer loadModelFile() throws IOException {
        FileInputStream inputStream = new FileInputStream(fileModel.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileModel.getStartOffset();
        long declaredLength = fileModel.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // Function to run inference with input data
    public float[] runInference(float[][] inputArray) {
        float[][] outputArray = new float[inputArray.length][2];
        tflite.run(inputArray, outputArray);
        float result[] = new float[Config.numberOfExaminedAP];//since there is only 1 tuple of input data
        for (int i = 0; i < Config.numberOfExaminedAP; i++){
            result[i] = outputArray[0][i];
        }
        return result;
    }
}
