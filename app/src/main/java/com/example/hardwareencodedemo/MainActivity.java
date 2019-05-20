package com.example.hardwareencodedemo;

import android.Manifest;
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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;

public class MainActivity extends AppCompatActivity {
    private static final SparseIntArray ORIENTATION = new SparseIntArray();

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    private int frontCameraId = -1, backCameraId = -1;
    private int frontCameraOrientation;
    private int backCameraOrientation;
    private int width, heigth;
    public static final int PERMISSION_CAMERA_CODE = 1;
    private static final String TAG = "MainActivity";
    private String[] ids;
    private CameraManager cameraManager;
    private CameraCharacteristics frontCameraCharacteristics;
    private CameraCharacteristics backCameraCharacteristics;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private TextureView textureView;
    private Button start;
    private BlockingQueue<byte[]> blockingQueue;
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
    private CaptureRequest.Builder recordRequestBuilder;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private ByteBuffer byteBuffer, yBuffer, uBuffer, vBuffer, uv1Buffer, uv2Buffer;
    private String path, yuvpath;
    int yuvSize;
    private WriteFile writeFile;
    private int bestWidth, bestHeight;
    private MediaCodec mediaCodec;
    private MediaFormat mediaFormat;
    private Range<Integer>[] frameRate;

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

        obtainCameraId();

        initMediaCodec();

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    closeRecording();
                    isRecording = false;
                    Log.e(TAG, "onClick: " + "停止录制");
                } else {
                    isRecording = true;
                    startRecording();
                    startEncode();
                }
            }
        });


    }

    private void obtainCameraId() {
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
            StreamConfigurationMap configurationMap = backCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] availablePreviewSizes = configurationMap.getOutputSizes(ImageFormat.YUV_420_888);
            Log.e(TAG, "openCamera: size[]" + Arrays.toString(availablePreviewSizes));
            frameRate = backCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            Log.e(TAG, "obtainCameraId: fps为" + Arrays.toString(frameRate));
            int diff = Integer.MAX_VALUE;
            for (int j = 0; j < availablePreviewSizes.length - 1; j++) {

                int newDiff = Math.abs(width - availablePreviewSizes[j].getHeight()) + Math.abs(heigth - availablePreviewSizes[j].getWidth());
                if (newDiff == 0) {
                    bestWidth = availablePreviewSizes[j].getWidth();
                    bestHeight = availablePreviewSizes[j].getHeight();
                    break;
                }

                if (newDiff < diff) {
                    bestWidth = availablePreviewSizes[j].getWidth();
                    bestHeight = availablePreviewSizes[j].getHeight();
                    diff = newDiff;
                }
            }
            if ((bestHeight & bestWidth) == 1) {
                Log.e(TAG, "openCamera: 未找到最佳适配方案，将采用最低分辨率");
                bestWidth = availablePreviewSizes[availablePreviewSizes.length - 1].getWidth();
                bestHeight = availablePreviewSizes[availablePreviewSizes.length - 1].getHeight();
            }
            Log.e(TAG, "openCamera: bestSize:w=" + bestWidth + "h=" + bestHeight);
            imageReader = ImageReader.newInstance(bestWidth, bestHeight, ImageFormat.YUV_420_888, 1);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Log.e(TAG, "onImageAvailable:获取图片 ");
//                    isRecording = true;
                    Image image = reader.acquireNextImage();
                    dataEnqueue(image);
                    image.close();
                }
            }, backgroundHandler);
        }
    }


    private void initMediaCodec() {
        try {
            if (bestHeight == 0 || bestWidth == 0) {
                Log.e(TAG, "initMediaCodec: 无法获取最佳宽度和高度");
                return;
            }
//            MediaCodecInfo codecInfo = MediaCodecInfo.VideoCapabilities
//            codecInfo.getCapabilitiesForType;
            byteBuffer = ByteBuffer.allocate(bestWidth * bestHeight * 3 / 2).order(ByteOrder.nativeOrder());
            yBuffer = ByteBuffer.allocate(bestWidth * bestHeight).order(ByteOrder.nativeOrder());
            uBuffer = ByteBuffer.allocate(bestWidth * bestHeight / 4).order(ByteOrder.nativeOrder());
            vBuffer = ByteBuffer.allocate(bestWidth * bestHeight / 4).order(ByteOrder.nativeOrder());
            uv1Buffer = ByteBuffer.allocate(bestWidth * bestHeight / 2).order(ByteOrder.nativeOrder());
            uv2Buffer = ByteBuffer.allocate(bestWidth * bestHeight / 2).order(ByteOrder.nativeOrder());

            mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, bestWidth, bestHeight);
            Log.e(TAG, "initMediaCodec: 宽和高" + bestWidth + bestHeight);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bestWidth * bestHeight * 30 * 3);
            mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, BITRATE_MODE_VBR);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void startEncode() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mediaCodec.start();
                byte[] pts = null;
                while (true) {
                    int flag = 0;
                    try {
                        if (!isRecording && blockingQueue.size() == 0) {
                            Log.e(TAG, "startEncode: 编码完成");
                            mediaCodec.stop();
                            mediaCodec.release();
                            break;
                        }
                        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                        if (inputBufferIndex != -1) {

                            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                            if (inputBuffer != null) {
                                inputBuffer.clear();
                                byte[] bytes = blockingQueue.take();
                                change2YUV420P(inputBuffer, bytes);
//                                writeFile.write(inputBuffer);
                                if (!isRecording && blockingQueue.size() == 0) {
                                    Log.e(TAG, "run: 最后一帧");
                                    flag = BUFFER_FLAG_END_OF_STREAM;
                                }
                                mediaCodec.queueInputBuffer(inputBufferIndex, 0, inputBuffer.limit(), System.currentTimeMillis(), flag);
                                Log.e(TAG, "run: bytesize" + inputBuffer.limit());
                            }
                        }
                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        int outBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                        while (outBufferIndex >= 0) {
                            ByteBuffer outBuffer = mediaCodec.getOutputBuffer(outBufferIndex);
                            if (pts == null) {
                                if (bufferInfo.flags == 2) {
                                    Log.e(TAG, "run: 第一帧");
                                    pts = new byte[bufferInfo.size];
                                    Log.e(TAG, "run:第一帧长度 " + outBuffer.limit());
                                    Log.e(TAG, "run:第一帧falsg " + bufferInfo.flags);
                                    Log.e(TAG, "run: ptsSize" + pts.length);
                                    outBuffer.get(pts);
                                }
                            }
                            if (bufferInfo.flags == 1) {
                                Log.e(TAG, "run: 关键帧");
                                writeFile.write(pts);
                            } else {
                                outBuffer.position(bufferInfo.offset);
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            }
                            writeFile.write(outBuffer);
                            mediaCodec.releaseOutputBuffer(outBufferIndex, false);
                            outBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }

        }).start();

    }

    private void initViews() {
        textureView = findViewById(R.id.textureView);
        start = findViewById(R.id.start);
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        System.out.println();
        Log.e(TAG, "initViews: width" + width + "height" + heigth);

        blockingQueue = new LinkedBlockingDeque();

        path = this.getExternalCacheDir().getPath() + "/res/output.h264";
        yuvpath = this.getExternalCacheDir().getPath() + "/res/image.yuv";
        writeFile = new WriteFile(path);

    }

    private void dataEnqueue(Image image) {
        int w = image.getWidth();
        int h = image.getHeight();
        Log.e(TAG, "dataEnqueue: imageformat" + image.getFormat());
        byte[] ybytes = new byte[w * h];
        byte[] uv1bytes = new byte[w * h / 2];
        byte[] uv2bytes = new byte[w * h / 2];
        byte[] bytes = new byte[w * h * 2];
        long putTime = System.currentTimeMillis();
        image.getPlanes()[0].getBuffer().get(ybytes, 0, w * h);
        image.getPlanes()[1].getBuffer().get(uv1bytes, 0, w * h / 2 - 2);
        image.getPlanes()[2].getBuffer().get(uv2bytes, 0, w * h / 2 - 2);
//        uvbytes[image.getWidth() * image.getHeight() / 2 - 1] = image.getPlanes()[2].getBuffer().get(w * h / 2 - 2);
        System.arraycopy(ybytes, 0, bytes, 0, ybytes.length);
        System.arraycopy(uv1bytes, 0, bytes, ybytes.length, uv1bytes.length);
        System.arraycopy(uv2bytes, 0, bytes, ybytes.length + uv1bytes.length, uv1bytes.length);

        try {
            blockingQueue.put(bytes);
            Log.e(TAG, "dataEnqueue: " + bytes[76552]);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "change2YUV420P: 耗时" + (System.currentTimeMillis() - putTime));
    }

    private boolean change2YUV420P(ByteBuffer byteBuffer, byte[] bytes) {
        Log.e(TAG, "change2YUV420P: bytebuffer.capt" + byteBuffer.capacity());
        byte[] uv1Bytes = new byte[bestHeight * bestWidth / 2];
        byte[] uv2Bytes = new byte[bestHeight * bestWidth / 2];
        Log.e(TAG, "change2YUV420P:bestHeight * bestWidth= " + bestHeight * bestWidth);
        clearBuffer(byteBuffer, yBuffer, uBuffer, vBuffer, uv1Buffer, uv2Buffer);
        yBuffer.put(bytes, 0, bestHeight * bestWidth);
        if (yBuffer.position() != bestWidth * bestHeight) {
            Log.e(TAG, "change2YUV420P: y分量长度不正确");
            return false;
        }
        System.arraycopy(bytes, bestHeight * bestWidth, uv1Bytes, 0, bestHeight * bestWidth / 2);
//        System.arraycopy(bytes, bestHeight * bestWidth * 3 / 2, uv2Bytes, 0, bestHeight * bestWidth / 2);
////        for (int j = 0; j < uv1Bytes.length; j++) {
//            if ((j & 1) == 0) {
//                vBuffer.put(uvBytes[j]);
//            } else {
//                uBuffer.put(uvBytes[j]);
//            }
//        }
//        if (uBuffer.position() != bestWidth * bestHeight / 4 || vBuffer.position() != bestWidth * bestHeight / 4) {
//            Log.e(TAG, "change2YUV420P: uv分量长度不正确 ,usize=" + uBuffer.limit() + ",vsize=" + vBuffer.limit() + ",正确的数量是" + bestWidth * bestHeight / 4);
//            return false;
//        }
        uv1Buffer.put(uv1Bytes);
        uv2Buffer.put(uv2Bytes);
        yBuffer.flip();
        uBuffer.flip();
        vBuffer.flip();
        uv1Buffer.flip();
        uv2Buffer.flip();
        byteBuffer.put(yBuffer);
        byteBuffer.put(uv1Buffer);
//        byteBuffer.put(uv2Buffer);
//        byteBuffer.put(uBuffer);
//        byteBuffer.put(vBuffer);

        return true;
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
        yBuffer.flip();
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
            buffers[j].clear();
        }

    }

    private void openCamera() {


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
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            recordRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            recordRequestBuilder.addTarget(imageReader.getSurface());
            recordRequestBuilder.addTarget(surface);
            recordRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
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
