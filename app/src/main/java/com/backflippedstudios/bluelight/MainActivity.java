package com.backflippedstudios.bluelight;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

        BluetoothSocket mmSocket;
        BluetoothDevice mmDevice = null;

        final byte delimiter = 33;
        int readBufferPosition = 0;
        boolean sendingCommand = false;


        public void sendBtMsg(String msg2send){
            //UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
            UUID uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"); //Standard SerialPortService ID
            try {

                mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
                if (!mmSocket.isConnected()){
                    mmSocket.connect();
                }

                String msg = msg2send;
                //msg += "\n";
                OutputStream mmOutputStream = mmSocket.getOutputStream();
                mmOutputStream.write(msg.getBytes());

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            final Handler handler = new Handler();

            final TextView myLabel = (TextView) findViewById(R.id.btResult);
            final Button tempButton = (Button) findViewById(R.id.tempButton);
            final Button lightOnButton = (Button) findViewById(R.id.lightOn);
            final Button lightOffButton = (Button) findViewById(R.id.lightOff);
            final Button fadeInOutButton = (Button) findViewById(R.id.fadeInOut);
            final Button blinkButton = (Button) findViewById(R.id.blink);
            final TextView status = (TextView) findViewById(R.id.status);

            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            final class workerThread implements Runnable {

                private String btMsg;

                public workerThread(String msg) {
                    btMsg = msg;
                }

                public void run(){
                   //Make sure any interrupted thread closes the connection before proceding
                    if(sendingCommand){
                        try {
                            mmSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    handler.post(new Runnable(){
                        public void run(){
                            status.setText("Sending Message to RPi");
                        }
                    });

                    sendBtMsg(btMsg);
                    handler.post(new Runnable(){
                        public void run(){
                            status.setText("Message sent...Checking for reply");
                        }
                    });
                    //This loop count will limit the number of time waiting for reply
                    //If we go beyond the limit we will kill the thread
                    Integer loopCnt = 1;
                    while(!Thread.currentThread().isInterrupted())
                    {
                        loopCnt++;
                        if(loopCnt>30){
                            Log.d("Debug", "run: Exiting thread due to timeout");
                            handler.post(new Runnable(){
                                public void run(){
                                    status.setText("Timed out waiting for reply");

                                }
                            });
                            break;
                        }
                        handler.post(new Runnable(){
                            public void run(){
                                status.append(".");

                            }
                        });

                        int bytesAvailable;
                        boolean workDone = false;
                        sendingCommand = true;

                        try {
                            //Check to see how many bytes are in reply message
                            //And read them in chunks.
                            final InputStream mmInputStream;
                            mmInputStream = mmSocket.getInputStream();
                            bytesAvailable = mmInputStream.available();
                            if(bytesAvailable > 0)
                            {

                                byte[] packetBytes = new byte[bytesAvailable];
                                Log.e("Aquarium recv bt","bytes available");
                                byte[] readBuffer = new byte[1024];
                                mmInputStream.read(packetBytes);

                                for(int i=0;i<bytesAvailable;i++)
                                {
                                    byte b = packetBytes[i];
                                    //Reached the end of the reply, convert bytes into a String
                                    //and set reply text
                                    if(b == delimiter)
                                    {
                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                        final String data = new String(encodedBytes, "US-ASCII");
                                        readBufferPosition = 0;

                                        //The variable data now contains our full command
                                        handler.post(new Runnable()
                                        {
                                            public void run()
                                            {
                                                myLabel.setText(data);
                                                status.setText("Reply finished");
                                            }
                                        });

                                        workDone = true;
                                        break;


                                    }
                                    else
                                    {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }

                                if (workDone == true){
                                    sendingCommand = false;
                                    mmSocket.close();
                                    break;
                                }

                            }
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }

                    }
                }
            };


            // start temp button handler
            tempButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // Perform action on temp button click

                    (new Thread(new workerThread("temp"))).start();

                }
            });
            //end temp button handler

            //start light on button handler
            lightOnButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // Perform action on temp button click

                    (new Thread(new workerThread("lightOn"))).start();

                }
            });
            //end light on button handler

            //start light off button handler
            lightOffButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // Perform action on temp button click

                    (new Thread(new workerThread("lightOff"))).start();

                }
            });
            // end light off button handler

            fadeInOutButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    (new Thread(new workerThread("fadeLED"))).start();
                }
            });

            blinkButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    (new Thread(new workerThread("blink"))).start();
                }
            });


            if(!mBluetoothAdapter.isEnabled())
            {
                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetooth, 0);
            }

            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if(pairedDevices.size() > 0)
            {
                for(BluetoothDevice device : pairedDevices)
                {
                    Log.d("DEBUG", "onCreate: Looking at device: " + device.getName());
                    if(device.getName().equals("raspberrypiWebSitesHost") || device.getName().equals("Adams RPi")) //Note, you will need to change this to match the name of your device
                    {
                        Log.e("Aquarium",device.getName());
                        mmDevice = device;
                        break;
                    }
                }
            }
        }
    }
