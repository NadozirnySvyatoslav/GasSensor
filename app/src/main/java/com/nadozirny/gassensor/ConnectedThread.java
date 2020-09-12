package com.nadozirny.gassensor;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConnectedThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private final Handler mHandler;

    public ConnectedThread(BluetoothSocket socket, Handler handler) {
        mmSocket = socket;
        mHandler = handler;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the input and output streams, using temp objects because
        // member streams are final
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) { }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];  // buffer store for the stream
        int bytes; // bytes returned from read()
        // Keep listening to the InputStream until an exception occurs
        String data="";
        int buffer_size=1024;
        while (true) {
            try {
                // Read from the InputStream
                bytes = mmInStream.available();
                if(bytes != 0) {
                    buffer = new byte[buffer_size];
                    SystemClock.sleep(100); //pause and wait for rest of data. Adjust this depending on your sending speed.
                    bytes = mmInStream.available(); // how many bytes are ready to be read?
                    if (bytes > buffer_size) bytes=buffer_size;
                    bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                    String s=new String(buffer,0,bytes);
                    data=data + s;
                    int pos=data.indexOf("\r\n");
                    if (pos<0) continue;
                    String to_send=data.substring(0,pos+2);
                    mHandler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, to_send.getBytes())
                            .sendToTarget(); // Send the obtained bytes to the UI activity
                    data=data.substring(pos+2,data.length());
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
        mHandler.obtainMessage(MainActivity.READ_ERROR, 1, -1, "")
                .sendToTarget();
    }

    /* Call this from the main activity to send data to the remote device */
    public void write(String input) {
        byte[] bytes = input.getBytes();           //converts entered String into bytes
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) { }
    }

    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) { }
    }
}