package com.example.secret_data_label_mission;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import android.Manifest;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.android.Utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.opencv.core.Core;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ExecutorService cameraExecutor;

    private boolean hasDetectedRectangle = false;  // 클래스 최상단에 추가


    static {
        System.out.println("들어오는지 확인");
        System.loadLibrary("opencv_java4"); // 버전에 따라 이름 바뀔 수 있음
    }

    //OpenCVLoader 라이브러리 활성 테스트
    static {
        System.out.println(Core.getVersionString() + "ASEJFIOSJ");
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV initialization failed");
        } else {
            Log.d("OpenCV", "OpenCV initialized");
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.previewView);
        checkCameraPermission();
        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }


    private void startCamera() {
        /**
         ProcessCameraProvider.getInstance(this);
         -> 현재 컨텍스트에서 카메라 공급자를 초기화 함
         -> 쉬운버전) 카메라 공급자에게 카메라 준비해줘!

         ListenableFuture<ProcessCameraProvider> cameraProviderFuture
         -> ListenableFuture는 카메라 객체를 가져오기 위한것
         -> <>는 미래에 돌려줄 값의 타입을 지정하는것 즉 ProcessCameraProvider타입을 돌려줄거야 라는 의미

         최종: 현재 컨텍스트에 ProcessCameraProvider라는 객체를 반환을 합니다. 하기 위해서 ListenableFuture를 이용해서 카메라 공급자를 불러옵니다
         */
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        /**
         *   cameraProviderFuture.addListener(() -> { ... }, executor)
         *   -> 카메라가 준비가 되면 실행되는 코드
         *   -> 비동기적으로 카메라 공급자를 가져오는 작업
         *   -> 공급자 즉 객체를 가져오게 되면 등록을 해야함
         *   -> () -> { ... }는 람다식 표현
         *
         *   최종: 카메라가 준비되면 여기 코드에서 실행을 해!
         */
        cameraProviderFuture.addListener(() -> {
            try {

                /**
                 * ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                 * 카메라 공급자 준비 완료 후 get()으로 객체를 꺼냄
                 * 최종: 카메라맨 준비 완료
                 *
                 * Preview preview = new Preview.Builder().build();
                 * Preview 영화관으로 비유하면 스크린을 설치하는 과정.
                 * Builder() 시공자를 부름.
                 * build()는 스크린 만들었어
                 * preview는 스크린
                 * 최종: 스크린만 만듬
                 *
                 * preview.setSurfaceProvider(previewView.getSurfaceProvider());
                 * setSurfaceProvider은 벽에 스크린 설치하는 메소드
                 * previewView.getSurfaceProvider()는 스크린을 설치할 벽 위치
                 * 최종: 스크린을 설치할 벽에 스크린 설치함
                 *
                 * ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                 * 이미지 분석기 만듬
                 * .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                 *  너무 많은 프레임이 들어올 경우 최신 프레임 상태만 유지해
                 * .build();
                 * 실제 이미지 프레임 분석한 usecase
                 */
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get(); //ㅇ
                Preview preview = new Preview.Builder().build(); // ㅇ
                preview.setSurfaceProvider(previewView.getSurfaceProvider()); //ㅇ
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                /**
                 * imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer()
                 * imageAnalysis 앞에서 만든 실시간 분석 파이프라인
                 * setAnalyzer 분석할 사장이 아닌 직원을 지정하는것
                 * cameraExecutor 분석하는 로직이 시간이 많이 걸리기에 메인 스레드가 아닌 백그라운드 실행자에서 실행
                 * analyze()는 프레임 들어왔어
                 * processImage(imageProxy); 분석 시작을 의미함
                 */
                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        processImage(imageProxy);
                    }
                });

                /**
                 * CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                 * 어떤 카메라를 쓸지 정한다.
                 * CameraSelector.DEFAULT_BACK_CAMERA;는 후면 카메라를 쓴다는 의미
                 *
                 * cameraProvider.unbindAll();
                 * 기존에 사용하고 있는 카메라 끊기
                 *
                 * cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                 * 앞에서 진행한 (누가 이걸 관리할건가?, 어떤 카메라를 사용할지, 어떤 화면에? 프리뷰화면, 어떤 분석기? 실시간 프레임 분석)
                 *
                 * cameraProvider.unbindAll();
                 * 새 장비 연결하고 촬영 시작
                 *
                 *
                 */
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }


    /**
     * @param imageProxy 는 원본 이미지 추출하기
     * 필름 봉투에 실제 필름 사진을 꺼내는 작업
     * mediaImage != null 봉투에 이미지가 없으면 종료 해야함
     *
     * 이 함수는 카메라 프레임을 받아서
     * → Bitmap으로 변환하고,
     * → 회전 보정하고,
     * → OpenCV용 Mat 객체로 만들어서 분석 준비하는 함수예요!
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void processImage(ImageProxy imageProxy) {
        @OptIn(markerClass = ExperimentalGetImage.class) Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            Bitmap bitmap = toBitmap(mediaImage);
            bitmap = rotateBitmap(bitmap, imageProxy.getImageInfo().getRotationDegrees());

            // OpenCV로 변환
            Mat mat = new Mat(); // 분석실에 들어온 이미지를 크기를 확인함
            Utils.bitmapToMat(bitmap, mat);


            // [2] 이미지 처리 (추가할 OpenCV 사각형 인식 코드)
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(mat, mat, new Size(5, 5), 0);
            Imgproc.Canny(mat, mat, 75, 200);

            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

            for (MatOfPoint contour : contours) {
                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                double peri = Imgproc.arcLength(contour2f, true);
                MatOfPoint2f approx = new MatOfPoint2f();
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true);

                if (approx.total() == 4) {
                    Point[] points = approx.toArray();

                    double slope1 = getSlope(points[0], points[1]); // top
                    double slope2 = getSlope(points[2], points[3]); // bottom
                    double slope3 = getSlope(points[1], points[2]); // right
                    double slope4 = getSlope(points[3], points[0]); // left

                    double tolerance = 0.1;
                    boolean topBottomParallel = Math.abs(slope1 - slope2) < tolerance;
                    boolean leftRightParallel = Math.abs(slope3 - slope4) < tolerance;

                    if (topBottomParallel && leftRightParallel) {
                        Log.d("RectangleCheck", "네 변이 평행함 → 정확한 사각형!");

                        // 시각적으로 표시하고 싶다면
                        for (int i = 0; i < 4; i++) {
                            Imgproc.circle(mat, points[i], 10, new Scalar(0, 255, 0), -1);
                        }
                    } else {
                        Log.d("RectangleCheck", "사각형이 왜곡되었음 → 변이 기울어짐");
                    }
                    break; // 하나만 인식하면 종료 (원하면 여러 개도 가능)
                }
            }





            mat.release();
        }
        imageProxy.close();
    }

    /**
     * @param image
     * @return
     * 카메라에서 준 필름을 이용해서 사진관에서 실 물체 사진을 받을수 있게 하는 과정
     * bitmap은 디지털 사진을 의미함
     */
    private Bitmap toBitmap(Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer vuBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int vuSize = vuBuffer.remaining();

        byte[] nv21 = new byte[ySize + vuSize];
        yBuffer.get(nv21, 0, ySize);
        vuBuffer.get(nv21, ySize, vuSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    /**
     * @param bitmap
     * @param rotationDegrees
     * @return
     *
     * 이미지를 회원 시켜서 가로로 찍어도 새 종이에 깔끔하게 다시 인화한것
     * 이미지를 회원한다는 의미는 방향을 이상하게 해도 똑바로 보일수 있게 하는것
     */
    private Bitmap rotateBitmap(Bitmap bitmap, float rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    /**
     * 매니패스트에는 카메라를 사용하기 위해 권한 설정을 함
     * 설정한 권한을 체킹하기 위한 메소드
     */
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    double getSlope(Point p1, Point p2) {
        return (p2.y - p1.y) / (p2.x - p1.x + 1e-8); // 0으로 나누기 방지
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}