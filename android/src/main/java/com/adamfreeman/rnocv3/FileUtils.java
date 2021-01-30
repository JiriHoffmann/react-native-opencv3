// @author Adam G. Freeman - adamgf@gmail.com, 04/07/2019
package com.adamfreeman.rnocv3;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableNativeMap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import android.media.ExifInterface;
import android.graphics.Matrix;
import android.util.Log;


class FileUtils {

    private static final String TAG = FileUtils.class.getSimpleName();

    private static FileUtils fileUtils = null;

    private FileUtils() {
    }

    // static method to create instance of Singleton class
    public static FileUtils getInstance() {
        if (fileUtils == null)
            fileUtils = new FileUtils();

        return fileUtils;
    }

    private static void reject(Promise promise, String filepath, Exception ex) {
        if (ex instanceof FileNotFoundException) {
            rejectFileNotFound(promise, filepath);
            return;
        }

        promise.reject(null, ex.getMessage());
    }

    private static void rejectFileNotFound(Promise promise, String filepath) {
        promise.reject("ENOENT", "ENOENT: no such file or directory, open '" + filepath + "'");
    }

    private static void rejectFileIsDirectory(Promise promise, String filepath) {
        promise.reject("EISDIR", "EISDIR: illegal operation on a directory, open '" + filepath + "'");
    }

    private static void rejectInvalidParam(Promise promise, String param) {
        promise.reject("EINVAL", "EINVAL: invalid parameter, read '" + param + "'");
    }

    public static void imageToMat(final String inPath, final Promise promise) {
        try {
            if (inPath == null || inPath.length() == 0) {
                rejectInvalidParam(promise, inPath);
                return;
            }

            File inFileTest = new File(inPath);
            if (!inFileTest.exists()) {
                rejectFileNotFound(promise, inPath);
                return;
            }
            if (inFileTest.isDirectory()) {
                rejectFileIsDirectory(promise, inPath);
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(inPath);
            if (bitmap == null) {
                throw new IOException("Decoding error unable to decode: " + inPath);
            }

            ExifInterface exif = new ExifInterface(inPath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            if (orientation != 1) {

                Matrix matrix = new Matrix();
                switch (orientation) {
                    case 2:
                        matrix.setScale(-1, 1);
                        break;
                    case 3:
                        matrix.postRotate(180);
                        break;
                    case 4:
                        matrix.postRotate(180);
                        matrix.postScale(-1, 1);
                        break;
                    case 5:
                        matrix.postRotate(90);
                        matrix.postScale(-1, 1);
                        break;
                    case 6:
                        matrix.postRotate(90);
                        break;
                    case 7:
                        matrix.postRotate(-90);
                        matrix.postScale(-1, 1);
                        break;
                    case 8:
                        matrix.postRotate(-90);
                        break;
                    default:
                        break;
                }


                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), matrix, true);
            }


            Mat img = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC4);
            Utils.bitmapToMat(bitmap, img);
            int matIndex = MatManager.getInstance().addMat(img);

            WritableNativeMap result = new WritableNativeMap();
            result.putInt("cols", img.cols());
            result.putInt("rows", img.rows());
            result.putInt("matIndex", matIndex);
            promise.resolve(result);
        } catch (Exception ex) {
            reject(promise, "EGENERIC", ex);
        }
    }

    public static void matToImage(final Mat mat, final String outPath, final Promise promise) {
        try {
            if (outPath == null || outPath.length() == 0) {
                // TODO: if no path sent in then auto-generate??!!!?
                rejectInvalidParam(promise, outPath);
                return;
            }

            Bitmap bm = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bm);

            int width = bm.getWidth();
            int height = bm.getHeight();

            FileOutputStream file = new FileOutputStream(outPath);

            if (file != null) {
                String fileType = "";
                int i = outPath.lastIndexOf('.');
                if (i > 0) {
                    fileType = outPath.substring(i + 1).toLowerCase();
                } else {
                    rejectInvalidParam(promise, outPath);
                    file.close();
                    return;
                }

                if (fileType.equals("png")) {
                    bm.compress(Bitmap.CompressFormat.PNG, 100, file);
                } else if (fileType.equals("jpg") || fileType.equals("jpeg")) {
                    bm.compress(Bitmap.CompressFormat.JPEG, 80, file);
                } else {
                    rejectInvalidParam(promise, outPath);
                    file.close();
                    return;
                }
                file.close();
            } else {
                rejectFileNotFound(promise, outPath);
                return;
            }

            WritableNativeMap result = new WritableNativeMap();
            result.putInt("width", width);
            result.putInt("height", height);
            result.putString("uri", outPath);
            promise.resolve(result);
        } catch (Exception ex) {
            reject(promise, "EGENERIC", ex);
        }
    }
}
