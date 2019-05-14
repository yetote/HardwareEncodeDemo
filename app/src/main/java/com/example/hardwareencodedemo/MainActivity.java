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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
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
    private ByteBuffer byteBuffer, yBuffer, uBuffer, vBuffer;
    private String path;
    int yuvSize;
    private WriteFile writeFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Display dm = this.getWindowManager().getDefaultDisplay();
        Point point = new Point();
        dm.getSize(point);
//        width = 1600;
//        heigth = 1200;
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
        yBuffer = ByteBuffer.allocate(width * heigth).order(ByteOrder.nativeOrder());
        uBuffer = ByteBuffer.allocate(width * heigth / 4).order(ByteOrder.nativeOrder());
        vBuffer = ByteBuffer.allocate(width * heigth / 4).order(ByteOrder.nativeOrder());
        path = this.getExternalCacheDir().getPath() + "/res/output.yuv";
        writeFile = new WriteFile(path);

    }

    private void dataEnqueue(Image image) {
        if (change2YUV420P(byteBuffer, image)) {
            writeFile.write(byteBuffer);
        }

    }

    private boolean change2YUV420P(ByteBuffer byteBuffer, Image image) {
        clearBuffer(byteBuffer, yBuffer, uBuffer, vBuffer);
        int w = image.getWidth();
        int h = image.getHeight();
        Log.e(TAG, "change2YUV420P: 获取的图片数据宽度为:" + w + "高度为:" + h);
        yBuffer = image.getPlanes()[0].getBuffer();
        if (yBuffer.limit() != w * h) {
            Log.e(TAG, "change2YUV420P: y数据获取不正确，当前数量为:" + yBuffer.limit() + "正确数量为:" + w * h);
            return false;
        }
        Log.e(TAG, "change2YUV420P: ySize" + yBuffer.limit());
        Toast.makeText(this, "大小为" + image.getPlanes()[1].getBuffer().limit(), Toast.LENGTH_SHORT).show();
        if (image.getPlanes()[1].getPixelStride() == 2) {
            Log.e(TAG, "change2YUV420P: 当前格式为yuv420sp格式");
            for (int j = 0; j < image.getPlanes()[1].getBuffer().limit(); j++) {
                if ((j & 1) == 0) {
                    uBuffer.put(image.getPlanes()[1].getBuffer().get(j));
                } else {
                    vBuffer.put(image.getPlanes()[1].getBuffer().get(j));
                }
            }
            vBuffer.put(image.getPlanes()[2].getBuffer().get(w * h / 2 - 2));
        } else {
            Log.e(TAG, "change2YUV420P: 当前格式为yuv420p格式" + image.getPlanes()[1].getPixelStride());
            uBuffer = image.getPlanes()[1].getBuffer();
            vBuffer = image.getPlanes()[2].getBuffer();
        }

        if (uBuffer.position() != w * h / 4 || vBuffer.position() != w * h / 4) {
            Log.e(TAG, "change2YUV420P: uv数据出错,uSize:" + uBuffer.position() + "vSize:" + vBuffer.position() + "正确的数据为:" + w * h / 4);
            return false;
        }
        uBuffer.flip();
        vBuffer.flip();
        Log.e(TAG, "change2YUV420P: bytesize" + byteBuffer.position());
        byteBuffer.put(yBuffer);
        Log.e(TAG, "change2YUV420P: bytesize" + byteBuffer.position());
        byteBuffer.put(uBuffer);
        Log.e(TAG, "change2YUV420P: bytesize" + byteBuffer.position());
        byteBuffer.put(vBuffer);
        Log.e(TAG, "change2YUV420P: bytesize" + byteBuffer.position());
        Log.e(TAG, "change2YUV420P: 总数据大小为:" + byteBuffer.position());

        return true;
    }


    private void clearBuffer(ByteBuffer... buffers) {
        for (int j = 0; j < buffers.length; j++) {
            if (buffers[i].position() != 0) {
                buffers[i].clear();
            }
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
                    StreamConfigurationMap configurationMap = backCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] availablePreviewSizes = configurationMap.getOutputSizes(SurfaceTexture.class);
                    Log.e(TAG, "openCamera: size[]" + Arrays.toString(availablePreviewSizes));
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
//        closePreviewSession();
        try {
            recordRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            recordRequestBuilder.addTarget(imageReader.getSurface());
            recordRequestBuilder.addTarget(surface);
//            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
//                @Override
//                public void onConfigured(@NonNull CameraCaptureSession session) {
//                    captureSession = session;
//                    recordRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
//                    try {
//                        captureSession.setRepeatingRequest(recordRequestBuilder.build(), null, backgroundHandler);
//                    } catch (CameraAccessException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                @Override
//                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
//
//                }
//            }, backgroundHandler);
            //创建会话
            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.d(TAG, "onCaptureCompleted: ");
                }
            };

            //停止连续取景
            captureSession.stopRepeating();
            //捕获照片
            captureSession.capture(recordRequestBuilder.build(), captureCallback, null);

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
