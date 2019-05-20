package com.example.hardwareencodedemo;

import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * @author ether QQ:503779938
 * @name HardwareEncodeDemo
 * @class name：com.example.hardwareencodedemo
 * @class describe
 * @time 2019/5/14 11:38
 * @change
 * @chang time
 * @class describe
 */
public class WriteFile {
    private FileOutputStream outputStream;
    private FileChannel fileChannel;
    private static final String TAG = "WriteFile";
    private ByteBuffer byteBuffer;

    public WriteFile(String path) {
        try {
            outputStream = new FileOutputStream(path);
            fileChannel = outputStream.getChannel();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public boolean write(ByteBuffer byteBuffer) {
        Log.e(TAG, "write: " + byteBuffer.limit());
        if (fileChannel == null) {
            Log.e(TAG, "write: 未打开channel");
            return false;
        }
        if (byteBuffer.position() != 0) {
            byteBuffer.flip();
        }

        try {
            fileChannel.write(byteBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "write: 文件写入成功");
        return true;

    }

    public boolean write(byte[] bytes) {
        byteBuffer = ByteBuffer.allocate(bytes.length).order(ByteOrder.nativeOrder());
        byteBuffer.clear();
        byteBuffer.put(bytes);
        return write(byteBuffer);
    }
}
