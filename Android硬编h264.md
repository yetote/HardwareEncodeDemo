### 准备工作
- ```camera2```使用
    - 申请权限，获取CameraManager服务
    ```
  cameraManager = (CameraManager) getSystemService(Service.CAMERA_SERVICE);
        if (cameraManager == null) {
            Toast.makeText(this, "无法获取相机服务", Toast.LENGTH_SHORT).show();
            return;
        }
    ```
    - 获取相机的id以及参数信息
    ```
    for (int i = 0; i < cameraIds.length; i++) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraIds[i]);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    Log.e(TAG, "achieveCameraInfo: 找到前置摄像机");
                    frontCameraCharacteristics = cameraCharacteristics;
                    frontCameraId = i;
                } else if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraCharacteristics = cameraCharacteristics;
                    backCameraId = i;
                    Log.e(TAG, "achieveCameraInfo: 找到后置摄像机");
                }
            }
            if (frontCameraId == -1) {
                Toast.makeText(this, "未找到前置摄像机", Toast.LENGTH_SHORT).show();
            }

            if (backCameraId == -1) {
                Toast.makeText(this, "未找到后置摄像机", Toast.LENGTH_SHORT).show();
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    ```
    - 检查相机的级别(可选)
    ```
        checkSupportLevel("后置", backCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
                    
    ```
    ```
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

    ```
    - 获取最佳预览尺寸
    ```
  StreamConfigurationMap configurationMap = backCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] photoSize = configurationMap.getOutputSizes(ImageFormat.YUV_420_888);
                    Log.e(TAG, "achieveCameraInfo: photoSize" + Arrays.toString(photoSize));
                    chooseBestSize(photoSize);
    ```

    ```
        private void chooseBestSize(Size[] photoSize) {
        if (photoSize.length == 0) {
            Log.e(TAG, "chooseBestSize: photoSize为空");
            bestWidth = bestHeight = -1;
            return;
        }

        float displayRatio = (float) width / (float) height;
        float ratio = Float.MAX_VALUE;
        Log.e(TAG, "chooseBestSize: 屏幕高宽比" + displayRatio);
        for (int i = 0; i < photoSize.length; i++) {
            if (((float) photoSize[i].getHeight()) / ((float) photoSize[i].getWidth()) == displayRatio) {
                bestHeight = photoSize[i].getWidth();
                bestWidth = photoSize[i].getHeight();
                Log.e(TAG, "chooseBestSize: " + ((float) photoSize[i].getHeight()) / ((float) photoSize[i].getWidth()));
                break;
            }

            float diffRatio = ((float) photoSize[i].getHeight()) / ((float) photoSize[i].getWidth()) - displayRatio;
            if (Math.abs(diffRatio) < ratio) {
                ratio = Math.abs(diffRatio);
                bestHeight = photoSize[i].getWidth();
                bestWidth = photoSize[i].getHeight();
                Log.e(TAG, "chooseBestSize: NewRatio" + ratio);
            }
            Log.e(TAG, "onCreate: bestWidth=" + bestWidth + "\nbestHeight=" + bestHeight);
        }

    } private void chooseBestSize(Size[] photoSize) {
        if (photoSize.length == 0) {
            Log.e(TAG, "chooseBestSize: photoSize为空");
            bestWidth = bestHeight = -1;
            return;
        }

        float displayRatio = (float) width / (float) height;
        float ratio = Float.MAX_VALUE;
        Log.e(TAG, "chooseBestSize: 屏幕高宽比" + displayRatio);
        for (int i = 0; i < photoSize.length; i++) {
            if (((float) photoSize[i].getHeight()) / ((float) photoSize[i].getWidth()) == displayRatio) {
                bestHeight = photoSize[i].getWidth();
                bestWidth = photoSize[i].getHeight();
                Log.e(TAG, "chooseBestSize: " + ((float) photoSize[i].getHeight()) / ((float) photoSize[i].getWidth()));
                break;
            }

            float diffRatio = ((float) photoSize[i].getHeight()) / ((float) photoSize[i].getWidth()) - displayRatio;
            if (diffRatio > 0) continue;
            if (Math.abs(diffRatio) < ratio) {
                ratio = Math.abs(diffRatio);
                bestHeight = photoSize[i].getWidth();
                bestWidth = photoSize[i].getHeight();
                Log.e(TAG, "chooseBestSize: NewRatio" + ratio);
            }
            Log.e(TAG, "onCreate: bestWidth=" + bestWidth + "\nbestHeight=" + bestHeight);
        }

    }

    ```
    - 开启预览
    ```
     private void openPreview() {
        if (previewSurface == null) {
            Log.e(TAG, "openPreview: 未获取到用于预览的surface");
            return;
        }
        if (cameraDevice == null) {
            Log.e(TAG, "openPreview: 未找到用于打开的相机");
            return;
        }
        try {
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
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
    ```
    - 录像
    ```
        private void startRecord() {
        surfaceTexture.setDefaultBufferSize(bestHeight, bestWidth);
        previewSurface = new Surface(surfaceTexture);
        if (captureSession != null) {
            captureSession.close();
        }
        try {
            recordBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            recordBuilder.addTarget(previewSurface);
            recordBuilder.addTarget(imageReader.getSurface());
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface(), previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        captureSession = session;
                        recordBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                        captureSession.setRepeatingRequest(recordBuilder.build(), null, backgroundHandler);
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

    ```
### 编码流程
    - 初始化编码器
    ```
        private void initMediaCodec() {
        try {
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, bestWidth, bestHeight);
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

    ``` 

    - 入队流程

    ```
    private void enqueueData(Image img) {
        long nowTime = System.currentTimeMillis();
        byte[] yByte = new byte[encodeWidth * encodeHeight];
        if (encodeWidth * encodeHeight != img.getPlanes()[0].getBuffer().limit()) {
            Log.e(TAG, "enqueueData: y分量数目不对" + img.getPlanes()[0].getBuffer().limit());
            return;
        }
        img.getPlanes()[0].getBuffer().get(yByte);
        byte[] uvByte = new byte[encodeWidth * encodeHeight/ 2];
        img.getPlanes()[1].getBuffer().get(uvByte, 0, encodeWidth * encodeHeight/2 - 1);

        uvByte[uvByte.length - 1] = img.getPlanes()[2].getBuffer().get(encodeWidth * encodeHeight/2 - 2);

        byte[] dataByte = new byte[encodeWidth * encodeHeight * 3 / 2];
        System.arraycopy(yByte, 0, dataByte, 0, yByte.length);
        System.arraycopy(uvByte, 0, dataByte, yByte.length, uvByte.length);
        try {
            blockingQueue.put(dataByte);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "enqueueData: 耗时" + (System.currentTimeMillis() - nowTime));
    }
    ```      
    - 解码流程
    ```
        encodeThread = new Thread(() -> {
            mediaCodec.start();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            for (; ; ) {
                try {
                    if (!isRecording && blockingQueue.isEmpty()) {
                        Log.e(TAG, "run: 编码完成");
                        break;
                    }

                    int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                    if (inputBufferIndex < 0) {
                        Log.e(TAG, "run: 未获取到入队缓冲区索引");
                        continue;
                    }

                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                    if (inputBuffer == null) {
                        Log.e(TAG, "run: 获取入队缓冲区错误");
                        continue;
                    }
                    byte[] data = blockingQueue.take();
                    Log.e(TAG, "run: 出队数组长度" + data.length);
                    inputBuffer.clear();
                    inputBuffer.put(data);
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, data.length, System.currentTimeMillis(), 0);

                    int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    while (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                        if (outputBuffer != null) {
                            if (pps == null) {
                                if (bufferInfo.flags == 2) {
                                    Log.e(TAG, "run: 第一帧");
                                    pps = new byte[bufferInfo.size];
                                    Log.e(TAG, "run:第一帧长度 " + outputBuffer.limit());
                                    Log.e(TAG, "run:第一帧flag " + bufferInfo.flags);
                                    Log.e(TAG, "run: ppsSize" + pps.length);
                                    outputBuffer.get(pps);
                                }
                            }

                                if (bufferInfo.flags == 1) {
                                    Log.e(TAG, "run: 关键帧");
                                    writeFile.write(pps);
                                } else {
                                    outputBuffer.position(bufferInfo.offset);
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                                }
                                writeFile.write(outputBuffer);
                                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        }
                        outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        });
    ```       
至此，camera2采集与硬编h264的代码全部写完