package com.colbybishop.redrover;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements SensorEventListener{

    //Fields for accelerometer
    Sensor accelerometer;
    SensorManager sm;
    public TextView acceleration;

    //Fields for bluetooth connection hand
    Handler bluetoothIn;
    final int handlerState = 0; //used to identify handler message
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder recDataString = new StringBuilder();
    private ConnectedThread mConnectedThread;

    //global counter
    public int counter = 1000;

    // SPP UUID service (Popular, this should work for most devices)
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // String for MAC address
    private static String address;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Set up sensor and link to textView
        sm = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        acceleration = (TextView)findViewById(R.id.accelerometer);

        //Set up handler (made for two way handling, only really used here for writing)
        bluetoothIn = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == handlerState) { //if message is what we want
                    String readMessage = (String) msg.obj; //msg.arg1 = bytes from connect thread
                    recDataString.append(readMessage); //keep appending to string until
                    int endOfLineIndex = recDataString.indexOf("~"); //determine the end-of-line
                    if (endOfLineIndex > 0) { //make sure there data before ~
                        recDataString.delete(0, recDataString.length()); //clear all string data
                    }
                }
            }
        };

        //Get Bluetooth adapter
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        counter = counter + 1;

        //Send (write) the new acc. data over bluetooth connection
        String sendString = ">" + 1 + "," + counter + "," +
                -(event.values[0]) + "," +
                event.values[1] + "," +
                event.values[2];
        mConnectedThread.write(sendString);
        //byte[] ba = sendString.getBytes(); //local byte array
        //String readMessage = new String(ba); //byte[] to string (debugging)

        //Displays accelerometer data on screen
        acceleration.setText("x: " + event.values[0] + "\nY: " +
                event.values[1] + "\nZ: " +
                event.values[2]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy){}

    //Create socket for communication
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        //creates secure outgoing connection with BT device using UUID
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    @Override
    public void onResume() {
        super.onResume();

        //Get MAC address from DeviceListActivity via intent
        Intent intent = getIntent();

        //Get the MAC address from the DeviceListActivity via EXTRA
        address = intent.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        //Create device and set the MAC address
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_LONG).show();
        }
        //Establish the Bluetooth socket connection.
        try {
            btSocket.connect();
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {}
        }
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        try {
            //Close Bluetooth sockets when leaving activity
            btSocket.close();
        } catch (IOException e2) {}
    }

    //Checks that the Android device Bluetooth is available and prompts to be turned on if off
    private void checkBTState() {

        if(btAdapter==null) {
            Toast.makeText(getBaseContext(), "Device does not support bluetooth", Toast.LENGTH_SHORT).show();
        } else {
            if (!btAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    //Create new class for connected thread
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //Creation of the connected thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException ignored) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        //To receive data from device
        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            //Keep looping to listen for received messages
            while (true) {
                try {
                    bytes = mmInStream.read(buffer); //read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the UI Activity via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }
        //write method
        public void write(String input) {
            byte[] bytess = input.getBytes(); //converts entered String into bytes
            try {
                mmOutStream.write(bytess); //write bytes over BT connection via out stream
            } catch (IOException e) {
                finish(); //if you cannot write, close the application
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
