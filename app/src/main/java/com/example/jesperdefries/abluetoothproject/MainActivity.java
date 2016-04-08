package com.example.jesperdefries.abluetoothproject;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    TextView myLabel;
    EditText myTextbox;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button openButton = (Button) findViewById(R.id.open);
        Button sendButton = (Button) findViewById(R.id.send);
        Button closeButton = (Button) findViewById(R.id.close);
        myLabel = (TextView) findViewById(R.id.label);
        myTextbox = (EditText) findViewById(R.id.entry);

        counter = 0;

        // TODO: 05-04-2016 make check for bluetooth is turned on
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        while (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
            //Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivityForResult(enableBluetooth, 0);
        }

        if (mBluetoothAdapter == null) {
            // TODO: 05-04-2016 error message and shutdown
            Toast.makeText(this, "No Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
        }else {
            try {
                findBluetooth();
                openBluetooth();
            } catch (IOException e) {
                e.printStackTrace();
            }



            //Open Button
/*            openButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        findBluetooth();
                        openBluetooth();
                    } catch (IOException ex) {
                    }
                }
            });*/

            /***
             * send data
             */
/*            sendButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        sendData();
                    } catch (IOException ex) {
                    }
                }
            });*/

            /***
             * close connection to bluetooth device
             */
/*            closeButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        closeBluetooth();
                    } catch (IOException ex) {
                    }
                }
            });*/
        }
    }

    private void findBluetooth() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("OBDLink MX")) {
                    mmDevice = device;
                    break;
                }
            }
        }
        System.out.println("Bluetooth Device Found");
    }

    /***
     * open bluetooth, connect to socket and send setup data
     * @throws IOException
     */
    private void openBluetooth() throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        // prepare to read setup file
        final Scanner s = new Scanner(getResources().openRawResource(R.raw.canbussetup)).useDelimiter("\r");
        String word = null;
        // send line-by-line
        try {
            while (s.hasNext()) {
                word = s.next();
                sendData(word);
                SystemClock.sleep(200);
            }
        } finally {
            System.out.println("finally.....");
            s.close();
        }

        beginListenForData();
        System.out.println("Bluetooth Opened");
    }

    /***
     * handle incomming strings
     * @param data
     */
    private void handleData(String data){
        if(!(data.equals('\r')||data.equals('?')||data.isEmpty())) {
            // TODO: 08-04-2016 detect/select pid

            // TODO: 08-04-2016 stuff
            counter++;
            myLabel.setText(counter);
        }
    }

    /***
     * listen to the inputsocket
     */
    private void beginListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 13;              //This is the ASCII code for carriage return

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    // handle incomming data
                                    handler.post(new Runnable() {
                                        public void run() {
                                            //"412 74 0F 00 36 7A 92 00 10"; // hastighed og odometer
                                            //"210 00 00 4F 41 00 00 00 00"; // throttle
                                            //"236 00";                      // steering wheel
                                            handleData(data);
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    /***
     * send data-string
     * @param string
     * @throws IOException
     */
    private void sendData(String string) throws IOException{
        String msg = string + "\r";
        mmOutputStream.write(msg.getBytes());
    }

    /***
     * send contents of textbox
     * @throws IOException
     */
    private void sendData() throws IOException {
        String msg = myTextbox.getText().toString();
        msg += "\r";
        mmOutputStream.write(msg.getBytes());
    }

    /***
     * close bluetooth connection
     * @throws IOException
     */
    private void closeBluetooth() throws IOException {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        myLabel.setText("Bluetooth Closed");
    }
}