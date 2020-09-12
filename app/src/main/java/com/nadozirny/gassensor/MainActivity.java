package com.nadozirny.gassensor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Console;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private static final String TAG = "GasSensor";
    private BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ConnectedThread mConnectedThread;
    private BluetoothSocket mBTSocket = null;
    private Handler mHandler;
    String[] sensor_types = {"MQ-135", "MQ-2", "MQ-3", "MQ-7", "MQ-8"};
    String[] gas_types = {"NH3/NO/CO/CO2", "H2/LPG/CH4/CO/Alco", "Benzin/Alco", "CO", "Alco"};
    BluetoothDevice sensor;
    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    public final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    public final static int READ_ERROR = 4; // used in bluetooth handler to identify message update
    public HashMap<String, Sensor> sensors = new HashMap<String, Sensor>();
    public MainActivity activity = this;
    public Location location;
    PowerManager.WakeLock wakeLock=null;
    EditText text;
    Button start;
    Button stop;
    long start_date;
    @Override
    protected void onDestroy() {
        wakeLock.release();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBTAdapter = BluetoothAdapter.getDefaultAdapter();
        text = (EditText) findViewById(R.id.text);
        start=findViewById(R.id.btn_start);
        stop=findViewById(R.id.btn_stop);

        final LinearLayout board = (LinearLayout) findViewById(R.id.sensors);
        text.append("GasSensor v0.1\n");
        PowerManager powerManager = (PowerManager)this.getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "GasSensor:ScreenLock");
        wakeLock.acquire();

        LocationManager locationManager = (LocationManager) getSystemService(this.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            text.append("Geolocation disabled\n");
        }else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }


        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG,readMessage);
                    String[] lines=readMessage.split("\t");
                    if (lines.length > 1){
                        for(int i=1; i<lines.length;i++) {
                            int pos=lines[i].indexOf(":");
                            if (pos == -1  ) continue;
                            String[] params=lines[i].split(":");
                            if (params.length < 2) continue;
                            final String name = params[0];
                            int index = Arrays.binarySearch(sensor_types, name);
                            if (index <  0  ) continue;
                            final String gas_name=gas_types[index];
                            String value = params[1];
                            Log.d(TAG,name + " ("+gas_name+") " + value);
                            ProgressBar progress=null;
                            TextView textvalue=null;
                            TextView textname=null;
                            TextView textmin=null;
                            TextView textmax=null;
                            if (!sensors.containsKey(name)){
                                LayoutInflater inflater = LayoutInflater.from(activity);
                                View view = inflater.inflate(R.layout.sensor, null,false);
                                Sensor s=new Sensor();
                                s.view=view;
                                board.addView(view);
                                sensors.put(name,s);
                            }
                            try {
                                final int int_value=Integer.parseInt(value.replace("\r\n",
                                        ""));
                                sensors.get(name).value = int_value;
                                if (sensors.get(name).min_value > int_value)
                                    sensors.get(name).min_value=int_value;
                                if (sensors.get(name).max_value < int_value)
                                    sensors.get(name).max_value=int_value;
                                progress=sensors.get(name).view.findViewById(R.id.sensor_view);
                                textvalue=sensors.get(name).view.findViewById(R.id.sensor_value);;
                                textvalue.setText(value);
                                textname=sensors.get(name).view.findViewById(R.id.sensor_name);
                                textname.setText(gas_name);
                                textmin=sensors.get(name).view.findViewById(R.id.sensor_min);
                                textmin.setText("" +sensors.get(name).min_value );
                                textmax=sensors.get(name).view.findViewById(R.id.sensor_max);
                                textmax.setText("" +sensors.get(name).max_value );
                                progress.setMax(sensors.get(name).max_value);
                                progress.setMin(sensors.get(name).min_value);
                                progress.setProgress(int_value);

                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        HttpURLConnection conn = null;
                                        try {
                                            if (new Date().getTime() - start_date > 60000){
                                                URL url = new URL("http://events.xpro.pp.ua/gasmeter/_doc");
                                                conn = (HttpURLConnection) url.openConnection();
                                                conn.setRequestMethod("POST");
                                                conn.setRequestProperty("Content-Type", "application/json");
                                                conn.setRequestProperty("Accept", "application/json");
                                                conn.setDoOutput(true);
                                                conn.setDoInput(true);
                                                TimeZone tz = TimeZone.getTimeZone("UTC");
                                                DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                                                df.setTimeZone(tz);
                                                String nowAsISO = df.format(new Date());
                                                JSONObject jsonParam = new JSONObject();
                                                JSONObject loc = new JSONObject();
                                                loc.put("lat", location.getLatitude());
                                                loc.put("lon", location.getLongitude());

                                                jsonParam.put("timestamp", nowAsISO);
                                                jsonParam.put("model", name);
                                                jsonParam.put("name", gas_name);
                                                jsonParam.put("value", int_value);
                                                jsonParam.put("alt", location.getAltitude());
                                                jsonParam.put("value", int_value);
                                                jsonParam.put("location", loc);

                                                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                                                os.writeBytes(jsonParam.toString());
                                                os.flush();
                                                os.close();
                                                Log.d(TAG,jsonParam.toString());
                                                try(BufferedReader br = new BufferedReader(
                                                        new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                                                    StringBuilder response = new StringBuilder();
                                                    String responseLine = null;
                                                    while ((responseLine = br.readLine()) != null) {
                                                        response.append(responseLine.trim());
                                                    }
                                                    Log.d(TAG,response.toString());
                                                }

                                            }



                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {
                                            if (conn != null) // Make sure the connection is not null.
                                                conn.disconnect();
                                        }
                                    }
                                }).start();

                            }catch(Exception e){

                            }
                        }
                    }
                }

                if(msg.what == CONNECTING_STATUS){
                    if(msg.arg1 == 1) {
                        text.append("Connected to Device: " + msg.obj + "\n");
                        start.setEnabled(false);
                        stop.setEnabled(true);
                    }
                    else {
                        text.append("Connection Failed\n");
                        start.setEnabled(true);
                        stop.setEnabled(false);
                    }
                }
                if(msg.what == READ_ERROR){
                    if(msg.arg1 == 1)
                        text.append("Device disconnected\n");
                    start.setEnabled(true);
                    stop.setEnabled(false);
                }
            }
        };
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start.setEnabled(false);
                stop.setEnabled(false);
                Connect();
                start_date=new Date().getTime();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start.setEnabled(true);
                stop.setEnabled(false);
                mConnectedThread.cancel();
            }
        });



    }
    private void Connect(){
        if(mBTAdapter.isEnabled()) {
            mPairedDevices = mBTAdapter.getBondedDevices();
            for (BluetoothDevice device : mPairedDevices) {
                if (device.getName().compareTo("TestBtHC04") ==0 ){
                    sensor=device;
                    text.append("Bluetooth GAS Sensor found\n");
                    break;
                }
                Log.d(TAG,device.getName() +" >> " + device.getAddress());
            }
            if (sensor == null)
                text.append("Bluetooth GAS Sensor not found\n");
            else{
                text.append("Connecting...\n");
                new Thread()
                {
                    @Override
                    public void run() {
                        boolean fail = false;
                        BluetoothDevice device = mBTAdapter.getRemoteDevice(sensor.getAddress());
                        try {
                            mBTSocket = createBluetoothSocket(sensor);
                        } catch (IOException e) {
                            fail = true;
                            text.append("Connect failed\n");
                        }
                        // Establish the Bluetooth socket connection.
                        try {
                            mBTSocket.connect();
                        } catch (IOException e) {
                            try {
                                fail = true;
                                mBTSocket.close();
                                mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                        .sendToTarget();
                            } catch (IOException e2) {
                                text.append("Connect failed\n");
                            }
                        }
                        if(!fail) {
                            mConnectedThread = new ConnectedThread(mBTSocket, mHandler);
                            mConnectedThread.start();
                            mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, sensor.getName())
                                    .sendToTarget();
                        }
                    }
                }.start();
            }

        }else{
            text.append("Bluetooth is disabled\n");
        }
    }
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BT_MODULE_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
        }
        return  device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }

    @Override
    public void onLocationChanged(@NonNull Location l) {
        location=l;
    }

    public class Sensor{
        public int value;
        public int min_value=Integer.MAX_VALUE;
        public int max_value=0;
        View view;
    }
}

