package com.opencv.tuxin.detectanswersheets;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import java.io.ByteArrayOutputStream;


public class AnswerSheetBase{
    public static final String TAG = "tuxin";
    public static final int SUM_OF_QUESTIONS = 54;

    private String imgPath;
    private GetDataFromNative dataFromNative;
    private int[] studentAnswers = new int[SUM_OF_QUESTIONS];
    private int[] resultInt;
    private int[] correctAnswers;
    private Bitmap warpPicture;

    private int wResize = 1080;
    private int hResize = 1920;

    private boolean isGetDataFromNative = false;

    AnswerSheetBase(){
    }
    /*  提供检测图片的 path，该函数必须第一个调用，否则将出错，
     *  有了 path 后，立即调用 getDataFromNative() 为了得
     *  到 resultInt，这是检测结果的像素值，有了它，就可以调用
     *  getWarpPicture 得到 resultBitmap、可以调用
     *  getStudentAnswers() 得到 studentAnswers。                  */
    public void setImgPath(String imgPath){
        this.imgPath = imgPath;
        getDataFromNative();
    }

    /*  调用 native 方法，得到 resultInt，有了该数组，就可以调用
     *  getStudentAnswers() 和 getStudentNumbers() 等函数。
     *  第一步：得到尺寸合适（1080，1920）的原图片
     *  第二步：把得到的图片的像素值转换为 int 数组。
     *  第三步：调用 native 方法，得到 GetDataFromNative 类。
     *  第四步：把 GetDataFromNative 类的像素值复制给 resultInt。*/
    private void getDataFromNative(){
        ///　先把原图　resize
        Bitmap photoResize = getResizeBitmap(wResize, hResize);
        /// 创建一个 （wResize，hResize）的数组
        int[] pix = new int[wResize * hResize];
        /// 得到 resize 图片内的像素值，结果存在 pix 内
        photoResize.getPixels(pix, 0, wResize, 0, 0, wResize, hResize);

        /// 调用 native 方法，得到 GetDataFromNative 类
        dataFromNative = getAnswerSheetInfo(pix, wResize, hResize);
        /// 得到 resultInt 后，应该把原来的数据释放掉
        resultInt = dataFromNative.imageDataWarp;

        isGetDataFromNative = true;
        photoResize.recycle();
    }

    /*  把像素值转换为 warpPicture。 未完成，需要在 res 里放入图片，说明检测失败。*/
    protected Bitmap getWarpPicture(){
        if (imgPath != null && !isImgFormPathTooSmall() && dataFromNative.isRectangle) {
            warpPicture = Bitmap.createBitmap(wResize, hResize, Bitmap.Config.RGB_565);
            if (dataFromNative.isRectangle == true) {
                warpPicture.setPixels(resultInt, 0, wResize, 0, 0, wResize, hResize);
            } else {
                /// 让 resultBitmap 从 res 里面载入一张图片，说明没有找到 rectangle
            }
        } else {
            /// 未完成.... 需要在 res 文件中添加一张图片说明没有设置 imgPath 或者选择的图片太小， 或者图片无法找到 rectangle
        }
        return warpPicture;
    }

    /*  重新设置图片的尺寸，因为得到的图片一般都很大，我们需要在这里
     *  先进行一次压缩，之后传到 native 的时候只需要传输较小尺寸的
     *  图片，可以节省内存。                                      */
    private Bitmap getResizeBitmap(int newWidth, int newHeight){
        /// 先简单的压缩尺寸
        Bitmap bm = getCompressImageBySize();
        /// 得到压缩后的 bitmap 的尺寸
        int width = bm.getWidth();
        int height = bm.getHeight();
        /// 计算压缩的系数
        float scaleWidth = ((float)newWidth) / width;
        float scaleHeight = ((float)newHeight) / height;
        /// 生成一个相关系数的 matrix
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        /// resize 图片
        Bitmap resizeBitmap = Bitmap.createBitmap(bm,0,0,width,height,matrix,false);
        /// 释放原压缩的图片
        return resizeBitmap;
    }

    /*  按尺寸简单的压缩，执行此方法前需要调用 setImgPath(String imgPath)，
     *  以便得到原图片的地址，否则将会出错。                                  */
    private Bitmap getCompressImageBySize(){
        /// 得到 options 属性
        BitmapFactory.Options options = new BitmapFactory.Options();
        /// 设置 inJustDecodeBounds = true 后，解析图片只解析边界大小
        options.inJustDecodeBounds = true;
        Bitmap bitmap = (Bitmap) BitmapFactory.decodeFile(imgPath,options);
        /// 得到原图片的尺寸
        int w = options.outWidth;
        int h = options.outHeight;
        //Log.e(TAG, "width = " + w);
        //Log.e(TAG, "height = " + h);

        options.inSampleSize = 1;

        if (w > wResize && h > hResize){
            int widthRatio = w / wResize;
            int heightRatio = h / hResize;
            options.inSampleSize = heightRatio > widthRatio ? heightRatio : widthRatio;
        }
        /// 设为 false 再 decodeFile 就能得到新的图片
        options.inJustDecodeBounds =false;
        bitmap = (Bitmap) BitmapFactory.decodeFile(imgPath,options);
        return bitmap;
    }

    /*  设置正确答案，有了正确答案，就可以圈出哪题做错了或者圈出正确答案    */
    protected void setCorrectAnswers(int[] correctAnswers){
        this.correctAnswers = correctAnswers;
        /*for (int i = 0; i < studentAnswers.length; i++){
            Log.e(TAG,"correctAnswers" + i + " = " + correctAnswers[i]);
        }*/
    }

    /*  得到显示错题的图片，用 studentAnswers 和 correctAnswers
     *  做比较，然后圈出不一样的题号，即错题的题号                       */
    protected Bitmap getErrorAnswersPicture(){
        Bitmap errorAnswersPicture = null;
        /// 调用 native 方法，得到学生的答案, 如果检测成功
        if (imgPath != null && !isImgFormPathTooSmall() && dataFromNative.isRectangle) {
            studentAnswers = getStudentAnswers(dataFromNative.imageDataWarp, wResize, hResize);

            for (int i = 0; i < studentAnswers.length; i++) {
                Log.e(TAG, "studentAnswers" + (i + 1) + " = " + studentAnswers[i]);
            }
            /// 按质量压缩图片避免同时存在三张很大的图片
            errorAnswersPicture = compressByQuality(warpPicture).copy(Bitmap.Config.RGB_565, true);
            Canvas canvas = new Canvas(errorAnswersPicture);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStrokeWidth(4.5f);
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < correctAnswers.length; i++) {
                if (studentAnswers[i] != correctAnswers[i]) {
                    int cols = i % 3;
                    int rows = i / 3;
                    int cx = (int) (0.05 * wResize + 0.345 * cols * wResize);
                    int cy = (int) (0.3 * hResize + 0.0395 * rows * hResize);
                    int radius = 25;
                    canvas.drawCircle(cx, cy, radius, paint);
                }
            }
        } else{ /// 如果没有检测成功
            for (int i = 0; i < SUM_OF_QUESTIONS; i++)
                studentAnswers[i] = 0;
        }

        /*int cx=100;
        int cy=100;
        int radius=20;
        canvas.drawCircle(cx, cy, radius, paint);*/
        //chooseView.setImageBitmap(warpPicture);
        return errorAnswersPicture;
    }
    protected Bitmap getCorrectAnswersPicture(){
        Bitmap correctAnswersPicture = null;

        if (imgPath != null && !isImgFormPathTooSmall() && dataFromNative.isRectangle) {
            correctAnswersPicture = compressByQuality(warpPicture).copy(Bitmap.Config.RGB_565,true);
            Canvas canvas = new Canvas(correctAnswersPicture);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStrokeWidth(4.5f);
            paint.setStyle(Paint.Style.STROKE);

            for (int i = 0; i < correctAnswers.length; i++) {
                //if (studentAnswers[i] != correctAnswers[i] ){
                int cols = i % 3;
                int rows = i / 3;
                int px_num = (int) (0.027 * wResize + 0.345 * cols * wResize + 0.06 * wResize);
                int py_num = (int) (0.29 * hResize + 0.0395 * rows * hResize);

                int w_letter = (int) (0.039 * wResize);//39
                int h_letter = (int) (0.023 * hResize);
                int px_letter = (int) (0.0525 * wResize);

            for (int j = 1; j < 5; j ++) {
                //px_num += j * px_letter;
                if(correctAnswers[i] == j)
                    canvas.drawRect(new Rect(px_num + (j-1) * px_letter,
                                            py_num,
                                            px_num + w_letter + (j-1) * px_letter,
                                            py_num + h_letter), paint);

            }
               /* 查看所图取的选项
                for (int j = 1; j < 5; j++) {
                    //px_num += j * px_letter;
                    if (studentAnswers[i] == j)
                        canvas.drawRect(new Rect(px_num + (j - 1) * px_letter,
                                py_num,
                                px_num + w_letter + (j - 1) * px_letter,
                                py_num + h_letter), paint);

                }*/
            }
        } else {
        ///添加说明图片
        }
        return correctAnswersPicture;
    }
    private boolean isImgFormPathTooSmall(){
        /// 得到 options 属性
        BitmapFactory.Options options = new BitmapFactory.Options();
        /// 设置 inJustDecodeBounds = true 后，解析图片只解析边界大小
        options.inJustDecodeBounds = true;
        Bitmap bitmap = (Bitmap) BitmapFactory.decodeFile(imgPath,options);

        return (options.outWidth < wResize || options.outHeight < hResize );
    }

    /*  通过压缩质量来压缩图片大小，该函数可以被外部函数直接调用。
     *  注意，调用的原图片不会被销毁，为了避免 OMM 注意不要创建过多的新图。    */
    protected Bitmap compressByQuality(Bitmap bitmap){
        Bitmap compressedBitmap = null;
        if (bitmap != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int quality = 60;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            //Log.e(TAG, "质量压缩到原来的" + quality + "%时大小为：" + baos.toByteArray().length + "byte");
            compressedBitmap = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.toByteArray().length);
        } else {
            /// 载入图片，说明失败
            compressedBitmap = Bitmap.createBitmap(wResize, hResize, Bitmap.Config.RGB_565);
        }
        return compressedBitmap;
    }

    private native GetDataFromNative getAnswerSheetInfo(int[] buf, int w, int h);
    private native int[] getStudentAnswers(int[] buf,int w, int h);
    static {
        System.loadLibrary("opencvDetect");
    }
}
