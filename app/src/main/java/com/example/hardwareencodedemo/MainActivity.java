package com.example.hardwareencodedemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class MainActivity extends AppCompatActivity {
    private CameraManager cameraManager;
    private int frontCameraId = -1, backCameraId = -1;
    private int frontCameraOrientation;
    private CameraCharacteristics frontCameraCharacteristics;
    private int backCameraOrientation;
    private CameraCharacteristics backCameraCharacteristics;
    private static final String TAG = "MainActivity";
    private TextureView textureView;
    private Button start;
    private String[] ids;
    public static final int PERMISSION_CAMERA_CODE = 1;
    private CameraDevice cameraDevice;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private CaptureRequest.Builder previewRequestBuilder;
    private CameraCaptureSession captureSession;
    private int width, heigth;
    private ImageReader imageReader;
    private BlockingQueue blockingQueue;
    TextureView.SurfaceTextureListener listener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_CODE);
            } else {
                openCamera();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    private boolean isRecording;
    private int i = 0;
    private CaptureRequest.Builder recordRequestBuilder;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private ByteBuffer byteBuffer;
    private String path;
    int yuvSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Display dm = this.getWindowManager().getDefaultDisplay();
        Point point = new Point();
        dm.getSize(point);
        width = point.x;
        heigth = point.y;
        yuvSize = width * heigth * 3 / 2;
        initViews();


        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    closeRecording();
                    isRecording = false;
                    Log.e(TAG, "onClick: " + "停止录制");
                } else {
                    startRecording();
                    isRecording = true;
                }
            }
        });
    }

    private void initViews() {
        textureView = findViewById(R.id.textureView);
        start = findViewById(R.id.start);
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        Log.e(TAG, "initViews: width" + width + "height" + heigth);
        imageReader = ImageReader.newInstance(width, heigth, ImageFormat.YUV_420_888, 1);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                dataEnqueue(image);
                image.close();
            }
        }, backgroundHandler);
        blockingQueue = new LinkedBlockingDeque();
        byteBuffer = ByteBuffer.allocate(width * heigth * 3 / 2).order(ByteOrder.nativeOrder());
        path = this.getExternalCacheDir().getPath() + "/res/output.yuv";

    }

    private void dataEnqueue(Image image) {
        Image.Plane[] planes = image.getPlanes();

        for (int i = 0; i < planes.length; i++) {
            ByteBuffer iBuffer = planes[i].getBuffer();
            int iSize = iBuffer.remaining();
            Log.e(TAG, "pixelStride  " + planes[i].getPixelStride());
            Log.e(TAG, "rowStride   " + planes[i].getRowStride());
            Log.e(TAG, "width  " + image.getWidth());
            Log.e(TAG, "height  " + image.getHeight());
            Log.e(TAG, "bufferSize  " + iBuffer.position());
            Log.e(TAG, "Finished reading data from plane  " + i);
        }
    }

    private void openCamera() {

        cameraManager = (CameraManager) this.getSystemService(CAMERA_SERVICE);
        try {
            ids = cameraManager.getCameraIdList();
            for (int i = 0; i < ids.length; i++) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(ids[i]);
                final int orientation = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (orientation == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = i;
                    frontCameraOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    frontCameraCharacteristics = cameraCharacteristics;
                } else {
                    backCameraId = i;
                    backCameraOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    backCameraCharacteristics = cameraCharacteristics;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (frontCameraId != -1) {
            checkSupportLevel("前置", frontCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
        }

        if (backCameraId != -1) {
            checkSupportLevel("后置", backCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
        }

        try {
            if (backCameraId != -1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                }

                cameraManager.openCamera(ids[backCameraId], new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        Log.e(TAG, "onOpened: 打开成功");
                        cameraDevice = camera;
                        createPreview();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        Log.e(TAG, "onDisconnected:相机失去连接");
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.e(TAG, "onError: 打开相机失败");
                    }
                }, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void checkSupportLevel(String camera, int supportLevel) {
        switch (supportLevel) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                Toast.makeText(this, camera + "支持级别为:不支持", Toast.LENGTH_LONG).show();
                break;
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                Toast.makeText(this, camera + "支持级别为:简单支持", Toast.LENGTH_LONG).show();
                break;
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL:
                Toast.makeText(this, camera + "支持级别为:部分支持", Toast.LENGTH_LONG).show();
                break;
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                Toast.makeText(this, camera + "设备还支持传感器，闪光灯，镜头和后处理设置的每帧手动控制，以及高速率的图像捕获",
                        Toast.LENGTH_LONG).show();
                break;
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                Toast.makeText(this, camera + "设备还支持YUV重新处理和RAW图像捕获，以及其他输出流配置", Toast.LENGTH_LONG).show();
                break;
            default:
                Toast.makeText(this, camera + "未检测到相机信息", Toast.LENGTH_LONG).show();
                break;
        }

    }

    private void createPreview() {
        if (!textureView.isAvailable()) {
            Log.e(TAG, "createPreview: textureView不可用");
        }
        surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(width, heigth);
        surface = new Surface(surfaceTexture);
        try {
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    Toast.makeText(MainActivity.this, "摄像头完成配置，可以处理Capture请求了。", Toast.LENGTH_SHORT).show();
                    previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    CaptureRequest captureRequest = previewRequestBuilder.build();
                    try {
                        captureSession.setRepeatingRequest(captureRequest, null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void closeCamera() {
        cameraDevice.close();
    }

    private void startRecording() {
        closePreviewSession();
        try {
            recordRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            recordRequestBuilder.addTarget(imageReader.getSurface());
            recordRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    recordRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    try {
                        captureSession.setRepeatingRequest(recordRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void closePreviewSession() {
        if (captureSession != null) {
            captureSession.close();
        }
    }

    private void closeRecording() {
        if (captureSession != null) {
            captureSession.close();
        }
        createPreview();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_CAMERA_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera();
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (textureView.isAvailable()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_CODE);
            } else {
                openCamera();
            }
        } else {
            textureView.setSurfaceTextureListener(listener);
        }
    }
}
