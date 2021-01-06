
// @author Adam G. Freeman - adamgf@gmail.com
package com.adamfreeman.rnocv3;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Environment;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.File;
import java.util.ArrayList;

// just for testing purposes ...
import android.widget.Toast;

public class RNOpencv3Module extends ReactContextBaseJavaModule {

    private static final String TAG = RNOpencv3Module.class.getSimpleName();

    static {
        System.loadLibrary("opencv_java3");
    }

    private ReactApplicationContext reactContext;


    public RNOpencv3Module(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNOpencv3";
    }

    private void MakeAToast(String message) {
        Toast.makeText(reactContext, message, Toast.LENGTH_LONG).show();
    }

     private File readClassifierFile(String cascadeClassifier) {
      File cascadeFile = null;
      try {
          // load cascade file from application resources
          InputStream is = mContext.getAssets().open(cascadeClassifier);

          if (is == null) {
              Log.e(TAG, "Input stream is nullified!");
          }

          File cacheDir = mContext.getCacheDir();

          cascadeFile = new File(cacheDir, cascadeClassifier);
          FileOutputStream os = new FileOutputStream(cascadeFile);

          byte[] buffer = new byte[4096];
          int bytesRead;
          while ((bytesRead = is.read(buffer)) != -1) {
              os.write(buffer, 0, bytesRead);
          }
          is.close();
          os.close();
      }
      catch (java.io.IOException ioe) {
          Log.e(TAG, "Failed to load cascade. IOException thrown: " + ioe.getMessage());
      }
      finally {
          return cascadeFile;
      }
    }

    @ReactMethod
    public void drawLine(ReadableMap inMat, ReadableMap pt1, ReadableMap pt2, ReadableMap scalarVal, int thickness) {
        int matIndex = inMat.getInt("matIndex");
        Mat testMat = (Mat)MatManager.getInstance().matAtIndex(matIndex);
        double x1 = pt1.getDouble("x");
        double y1 = pt1.getDouble("y");
        double x2 = pt2.getDouble("x");
        double y2 = pt2.getDouble("y");
        Point p1 = new Point(x1,y1);
        Point p2 = new Point(x2,y2);
        Scalar dScalar = Scalar.all(255);
        Imgproc.line(testMat,p1,p2,dScalar,thickness);
        MatManager.getInstance().setMat(matIndex, testMat);
    }

    @ReactMethod
    public void cvtColor(ReadableMap sourceMat, ReadableMap destMat, int convColorCode) {
        int srcMatIndex = sourceMat.getInt("matIndex");
        int dstMatIndex = destMat.getInt("matIndex");

        Mat srcMat = (Mat)MatManager.getInstance().matAtIndex(srcMatIndex);
        Mat dstMat = (Mat)MatManager.getInstance().matAtIndex(dstMatIndex);

        Imgproc.cvtColor(srcMat, dstMat, convColorCode);
        MatManager.getInstance().setMat(dstMatIndex, dstMat);
    }
	
    @ReactMethod
    public void imageToMat(String inPath, final Promise promise) {
        FileUtils.getInstance().imageToMat(inPath, promise);
    }

    @ReactMethod
    public void matToImage(ReadableMap srcMat, String outPath, final Promise promise) {
        int matIndex = srcMat.getInt("matIndex");
        Mat mat = (Mat)MatManager.getInstance().matAtIndex(matIndex);
		FileUtils.getInstance().matToImage(mat, outPath, promise);
    }

    @ReactMethod
    public void invokeMethods(ReadableMap cvInvokeMap) {
        CvInvoke invoker = new CvInvoke();
        WritableArray responseArr = invoker.parseInvokeMap(cvInvokeMap);
        String lastCall = invoker.callback;
		int dstMatIndex = invoker.dstMatIndex;
        sendCallbackData(responseArr, lastCall, dstMatIndex);
    }

    // IMPT NOTE: retArr can either be one single array or an array of arrays ...
	// TODO: move this into RNOpencv3Util class ...				
    public void sendCallbackData(WritableArray retArr, String callback, int dstMatIndex) {
        if (callback != null && !callback.equals("") && dstMatIndex >= 0 && dstMatIndex < 1000) {
            WritableMap response = new WritableNativeMap();
            response.putArray("payload", retArr);
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(callback, response);
        }
        else {
            // not necessarily error condition unless dstMatIndex >= 1000
            if (dstMatIndex == 1000) {
                Log.e(TAG, "SecurityException thrown attempting to invoke method.  Check your method name and parameters and make sure they are correct.");
            }
            else if (dstMatIndex == 1001) {
                Log.e(TAG, "IllegalAccessException thrown attempting to invoke method.  Check your method name and parameters and make sure they are correct.");
            }
            else if (dstMatIndex == 1002) {
                Log.e(TAG, "InvocationTargetException thrown attempting to invoke method.  Check your method name and parameters and make sure they are correct.");
            }
        }
    }

    private String getPartJSON(Mat dFace, String partKey, Rect part) {

        StringBuffer sb = new StringBuffer();
        if (partKey != null) {
            sb.append(",\"" + partKey + "\":");
        }

        double widthToUse = dFace.cols();
        double heightToUse = dFace.rows();

        double X0 = part.tl().x;
        double Y0 = part.tl().y;
        double X1 = part.br().x;
        double Y1 = part.br().y;

        double x = X0/widthToUse;
        double y = Y0/heightToUse;
        double w = (X1 - X0)/widthToUse;
        double h = (Y1 - Y0)/heightToUse;

//        switch(mRotation) {
//            case Core.ROTATE_90_CLOCKWISE:
//                x = Y0/heightToUse;
//                y = 1.0 - X1/widthToUse;
//                w = (Y1 - Y0)/heightToUse;
//                h = (X1 - X0)/widthToUse;
//                break;
//            case Core.ROTATE_180:
//                x = 1.0 - X1/widthToUse;
//                y = 1.0 - Y1/heightToUse;
//                break;
//            case Core.ROTATE_90_COUNTERCLOCKWISE:
//                x = 1.0 - Y1/heightToUse;
//                y = X0/widthToUse;
//                w = (Y1 - Y0)/heightToUse;
//                h = (X1 - X0)/widthToUse;
//                break;
//            default:
//                break;
//        }

        sb.append("{\"x\":"+x+",\"y\":"+y+",\"width\":"+w+",\"height\":"+h);
        if (partKey != null) {
            sb.append("}");
        }
        return sb.toString();
    }

    @ReactMethod
    public void invokeMethodWithCallback(String in, String func, ReadableMap params, String out, String callback) {
        int dstMatIndex = (new CvInvoke()).invokeCvMethod(in, func, params, out);
        WritableArray retArr = MatManager.getInstance().getMatData(dstMatIndex, 0, 0);
        sendCallbackData(retArr, callback, dstMatIndex);
    }

    @ReactMethod
    public void invokeMethod(String func, ReadableMap params) {
        (new CvInvoke()).invokeCvMethod(null, func, params, null);
    }

    @ReactMethod
    public void invokeInOutMethod(String in, String func, ReadableMap params, String out) {
        (new CvInvoke()).invokeCvMethod(in, func, params, out);
    }

    private void resolveMatPromise(int matIndex, int rows, int cols, int cvtype, final Promise promise) {
        WritableNativeMap result = new WritableNativeMap();
        result.putInt("rows", rows);
        result.putInt("cols", cols);
        if (cvtype != -1) {
            result.putInt("CvType", cvtype);
        }
        result.putInt("matIndex", matIndex);
        promise.resolve(result);
    }

    @ReactMethod
    public void MatWithScalar(int rows, int cols, int cvtype, ReadableMap scalarMap, final Promise promise) {
        int matIndex = MatManager.getInstance().createMat(cols, rows, cvtype, scalarMap);
        resolveMatPromise(matIndex, rows, cols, cvtype, promise);
    }

    @ReactMethod
    public void MatWithParams(int rows, int cols, int cvtype, final Promise promise) {
        int matIndex = MatManager.getInstance().createMat(cols, rows, cvtype, null);
        resolveMatPromise(matIndex, rows, cols, cvtype, promise);
    }

    @ReactMethod
    public void Mat(final Promise promise) {
        int matIndex = MatManager.getInstance().createEmptyMat();
        resolveMatPromise(matIndex, 0, 0, -1, promise);
    }

    @ReactMethod
    public void getMatData(ReadableMap mat, int rownum, int colnum, final Promise promise) {
        promise.resolve(MatManager.getInstance().getMatData(mat.getInt("matIndex"), rownum, colnum));
    }

    // TODO: not sure if this code should be moved to MatManager
    @ReactMethod
    public void setTo(ReadableMap mat, ReadableMap cvscalar) {
        MatManager.getInstance().setTo(mat.getInt("matIndex"), cvscalar);
    }

    // TODO: ditto previous
    @ReactMethod
    public void put(ReadableMap mat, int rownum, int colnum, ReadableArray data) {
        MatManager.getInstance().put(mat.getInt("matIndex"), rownum, colnum, data);
    }

    @ReactMethod
    public void transpose(ReadableMap mat) {
        MatManager.getInstance().transpose(mat.getInt("matIndex"));
    }

    @ReactMethod
    public void deleteMat(ReadableMap mat) {
        MatManager.getInstance().deleteMatAtIndex(mat.getInt("matIndex"));
    }

    @ReactMethod
    public void deleteMats() {
        MatManager.getInstance().deleteAllMats();
    }

    @ReactMethod
    public void useCascadeOnImage(String cascadeClassifier, ReadableMap mat, final Promise promise) {
        // int matIndex = MatManager.getInstance().createMatOfFloat(lomatfloat, himatfloat);
        WritableNativeMap result = new WritableNativeMap();
        File cascadeFile = readClassifierFile(cascadeClassifier + ".xml");
        if (cascadeFile != null) {
            CascadeClassifier classifier = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if (classifier.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                classifier = null;
            }
            else {
                Log.i(TAG, "Loaded classifier from " + cascadeFile.getAbsolutePath());
            }
            cascadeFile.delete();

            int srcMatIndex = mat.getInt("matIndex");

            Mat in = (Mat)MatManager.getInstance().matAtIndex(srcMatIndex);

            MatOfRect faces = new MatOfRect();
            ArrayList<MatOfPoint2f> landmarks = new ArrayList<>();
            boolean landmarksFound = false;
            if (classifier != null && in != null) {
                classifier.detectMultiScale(in, faces, 1.1, 2, 0| Objdetect.CASCADE_SCALE_IMAGE, new Size(24, 24), new Size());
                
            }
            Rect[] facesArray = faces.toArray();

            String faceInfo = "";
            if (facesArray.length > 0) {
                Log.e(TAG, "faces length");
                StringBuffer sb = new StringBuffer();
                sb.append("{\"faces\":[");
                for (int i = 0; i < facesArray.length; i++) {
                    sb.append(getPartJSON(in, null, facesArray[i]));
                    String id = "faceId" + i;
                    sb.append(",\"faceId\":\""+id+"\"");
                    if (i != (facesArray.length - 1)) {
                        sb.append("},");
                    }
                    else {
                        sb.append("}");
                    }
                }
                sb.append("]}");
                faceInfo = sb.toString();
            }
            promise.resolve(faceInfo);
            // WritableMap response = new WritableNativeMap();
            // //Log.d(TAG, "payload is: " + faceInfo);
            // response.putString("payload", faceInfo);
            // mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            //   .emit("onFacesDetectedCv", response);
        }
        // Toast.makeText(reactContext, cascadeClassifier, Toast.LENGTH_LONG).show();
        Log.e(TAG, "TES2T TES22222T TE2ST T22EST 2TEST TEST TEST 2TEST TEST");
        promise.resolve(null);
    }




    @ReactMethod
    public void MatOfInt(int lomatint, int himatint, final Promise promise) {
        int matIndex = MatManager.getInstance().createMatOfInt(lomatint, himatint);
        WritableNativeMap result = new WritableNativeMap();
        result.putInt("matIndex", matIndex);
        promise.resolve(result);
    }

    @ReactMethod
    public void MatOfFloat(float lomatfloat, float himatfloat, final Promise promise) {
        int matIndex = MatManager.getInstance().createMatOfFloat(lomatfloat, himatfloat);
        WritableNativeMap result = new WritableNativeMap();
        result.putInt("matIndex", matIndex);
        promise.resolve(result);
    }
	
	
}
