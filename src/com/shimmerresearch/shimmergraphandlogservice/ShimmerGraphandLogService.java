//v0.2 -  8 January 2013

/*
 * Copyright (c) 2010, Shimmer Research, Ltd.
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:

 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Shimmer Research, Ltd. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author Jong Chern Lim
 * @date   October, 2013
 */

package com.shimmerresearch.shimmergraphandlogservice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.BiMap;
import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.service.ShimmerService;
import com.shimmerresearch.service.ShimmerService.LocalBinder;
import com.shimmerresearch.tools.Logging;
//import android.graphics.Matrix;

public class ShimmerGraphandLogService extends ServiceActivity {
	private static final String TAG = "MainActivity";
	private static final int PREPARE_INTRVL = 30000; //ms
	private static Context context;
	static final int REQUEST_ENABLE_BT = 1;
	static final int REQUEST_CONNECT_SHIMMER = 2;
	static final int REQUEST_CONFIGURE_SHIMMER = 3;
	static final int REQUEST_CONFIGURE_VIEW_SENSOR = 4;
	static final int REQUEST_COMMAND_SHIMMER = 5;
	static final int REQUEST_LOGFILE_SHIMMER = 6;
	private static GraphView mGraph;
		
	//private DataMethods DM;
	private static TextView mTitle;
	private static TextView mTitleLogging;
	private static TextView mValueSensor1;
	private static TextView mValueSensor2;
	private static TextView mValueSensor3;
	private static TextView mTVPRR;
	
	private static TextView mTextSensor1;
	private static TextView mTextSensor2;
	private static TextView mTextSensor3;
	//JD: listview of connected devices
	private static ListView mListConnectedDev;
	
	
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Name of the connected device
    // private static String mBluetoothAddress = null;
	// Connected device list
    private static ArrayList<HashMap<String, String>> mConnectedDeviceList = null;
	private static SimpleAdapter mConnectedDeviceAdapters;
	private static String mNewConnectedDeviceAddr = null;	// newly connected device address
    private static String mSelectedDeviceName = null;	// name of device currently selected
    private static String mSelectedDeviceAddr = null;   // address of device currently selected
    // Member object for communication services
    private String mSignaltoGraph;
    private static String mSensorView = ""; //The sensor device which should be viewed on the graph
    private static int mGraphSubSamplingCount = 0; //10 
    private static String mSubjectName = "Default";
    //static Logging log = new Logging(mFileName,"\t"); //insert file name
    private static boolean mEnableLogging = false;
    Dialog mDialog;
    int dialogEnabledSensors=0;
    
    //continuously recording
    private PowerManager pm;
    private PowerManager.WakeLock wl;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        Log.d("ShimmerActivity","On Create");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);

        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);
        getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.d("ShimmerActivity","On Create");
        mTitle = (TextView) findViewById(R.id.title_right_text);
        // Set up the custom title
        mTitleLogging = (TextView) findViewById(R.id.title_left_text);
      
      
        mDialog = new Dialog(this);
        
        /* JD: initialize view for connected device list */
        mListConnectedDev = (ListView) findViewById(R.id.connected_dev);
		mConnectedDeviceList = new ArrayList<HashMap<String, String>>();
		mConnectedDeviceAdapters = new SimpleAdapter(this, mConnectedDeviceList, 
				 R.layout.connected_device, new String[]{"device_cell","state_cell"}, 
				 new int[]{R.id.device_cell, R.id.state_cell});
		mListConnectedDev.setAdapter(mConnectedDeviceAdapters);
		registerForContextMenu(mListConnectedDev);
		
    	mGraph = (GraphView)findViewById(R.id.graph);
        mValueSensor1 = (TextView) findViewById(R.id.sensorvalue1);
        mValueSensor2 = (TextView) findViewById(R.id.sensorvalue2);
        mValueSensor3 = (TextView) findViewById(R.id.sensorvalue3);
        mTextSensor1 =  (TextView) findViewById(R.id.LabelSensor1);
        mTextSensor2 =  (TextView) findViewById(R.id.LabelSensor2);
        mTextSensor3 =  (TextView) findViewById(R.id.LabelSensor3);
        DisplayMetrics metrics = this.getResources().getDisplayMetrics();
		 switch(metrics.densityDpi){
	     case DisplayMetrics.DENSITY_LOW:
	    	 mTitleLogging.setTextSize(10);
	    	 mTitle.setTextSize(10);
	    	 mValueSensor1.setTextSize(10);
	    	 mValueSensor2.setTextSize(10);
	    	 mValueSensor3.setTextSize(10);
	    	 mTextSensor1.setTextSize(10);
	    	 mTextSensor2.setTextSize(10);
	    	 mTextSensor3.setTextSize(10);
	                break;
	     case DisplayMetrics.DENSITY_MEDIUM:
	    	 mTitleLogging.setTextSize(10);
	    	 mTitle.setTextSize(10);
	    	 mValueSensor1.setTextSize(14);
	    	 mValueSensor2.setTextSize(14);
	    	 mValueSensor3.setTextSize(14);
	    	 mTextSensor1.setTextSize(14);
	    	 mTextSensor2.setTextSize(14);
	    	 mTextSensor3.setTextSize(14);
	                 break;
	     case DisplayMetrics.DENSITY_HIGH:
	    	 mTitleLogging.setTextSize(16);
	    	 mTitle.setTextSize(16);
	    	 mValueSensor1.setTextSize(16);
	    	 mValueSensor2.setTextSize(16);
	    	 mValueSensor3.setTextSize(16);
	    	 mTextSensor1.setTextSize(16);
	    	 mTextSensor2.setTextSize(16);
	    	 mTextSensor3.setTextSize(16);
	                 break;
	     case DisplayMetrics.DENSITY_XHIGH:
	    	 mTitle.setTextSize(18);
	    	 mTitleLogging.setTextSize(18);
	    	 mValueSensor1.setTextSize(18);
	    	 mValueSensor2.setTextSize(18);
	    	 mValueSensor3.setTextSize(18);
	    	 mTextSensor1.setTextSize(18);
	    	 mTextSensor2.setTextSize(18);
	    	 mTextSensor3.setTextSize(18);
	    	 break;
		 }
		 
	     // keep awake
	        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
	        //wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"CPU on");
		 if (!isMyServiceRunning())
	      {
	      	Log.d("ShimmerH","Oncreate2");
	      	Intent intent=new Intent(this, ShimmerService.class);
	      	startService(intent);
	      	if (mServiceFirstTime==true){
	      		Log.d("ShimmerH","Oncreate3");
	  			getApplicationContext().bindService(intent,mTestServiceConnection, Context.BIND_AUTO_CREATE);
	  			mServiceFirstTime=false;
	  		}
	      	mTitle.setText(R.string.title_not_connected); // if no service is running means no devices are connected
	      //wake lock started
	      	//wl.acquire();
	      }         
//		 
//	      if (mBluetoothAddress!=null){
//	      	mTitle.setText(R.string.title_connected_to);
//	          mTitle.append(mBluetoothAddress);    
//	      }
//		 
//		 
//		 if (mEnableLogging==false){
//		      	mTitleLogging.setText("Logging Disabled");
//		      } else if (mEnableLogging==true){
//		      	mTitleLogging.setText("Logging Enabled");
//	      }
		      
		 
		mTVPRR =  (TextView) findViewById(R.id.textViewPRR);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null) {
        	Toast.makeText(this, "Device does not support Bluetooth\nExiting...", Toast.LENGTH_LONG).show();
        	finish();
        }
        
        ShimmerGraphandLogService.context = getApplicationContext();
    }
    
    @Override
    public void onStart() {
    	super.onStart();

    	if(!mBluetoothAdapter.isEnabled()) {     	
        	Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        	startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    	}
    	else {
    		
    		
    		
    	}
    }
    
    
    
    @Override
	public void onPause() {
		super.onPause();
		Log.d("ShimmerActivity","On Pause");
		//finish();
		if(mServiceBind == true){
  		  //getApplicationContext().unbindService(mTestServiceConnection); 
  	  }
	}

	public void onResume() {
		super.onResume();
		ShimmerGraphandLogService.context = getApplicationContext();
		Log.d("ShimmerActivity","On Resume");
		Intent intent=new Intent(this, ShimmerService.class);
  	  	Log.d("ShimmerH","on Resume");
  	  	getApplicationContext().bindService(intent,mTestServiceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		mService.onDestroy();
		//wl.release();
		super.onDestroy();
		Log.d("ShimmerActivity","On Destroy");
	}
	
	
	 protected ServiceConnection mTestServiceConnection = new ServiceConnection() {

	      	public void onServiceConnected(ComponentName arg0, IBinder service) {
	      		// TODO Auto-generated method stub
	      		Log.d("ShimmerService", "service connected from main activity");
	      		LocalBinder binder = (LocalBinder) service;
	      		mService = binder.getService();
	      		mServiceBind = true;
	      		mService.setGraphHandler(mHandler);
	      		
	      	}
	      	public void onServiceDisconnected(ComponentName arg0) {
	      		// TODO Auto-generated method stub
	      		mServiceBind = false;
	      	}
	    };
	
	
	// JD: The Handler that gets information back from the BluetoothChatService and ShimmerService
	private static Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case Shimmer.MESSAGE_STATE_CHANGE:
					switch (msg.arg1) {
						case Shimmer.STATE_CONNECTED:
		                	//this has been deprecated
		                	/*Log.d("ShimmerActivity","ms1");
		                    mTitle.setText(R.string.title_connected_to);
		                    mBluetoothAddress=((ObjectCluster)msg.obj).mBluetoothAddress;
		                    mTitle.append(mBluetoothAddress);    
		                    mService.enableGraphingHandler(true);*/
							break;
						case Shimmer.MSG_STATE_FULLY_INITIALIZED:
							Log.d("ShimmerActivity","Message Fully Initialized Received from Shimmer driver");
							/* JD: new device connected, add it to connected device list */
							mNewConnectedDeviceAddr=((ObjectCluster)msg.obj).mBluetoothAddress;
							boolean isNew = true;
							for(int i = 0; i < mConnectedDeviceList.size(); i++){
								if(mConnectedDeviceList.get(i).get("device_cell").contains(mNewConnectedDeviceAddr)){
									isNew = false;
									break;
								}
							}
							if(isNew){
								String devName = ((ObjectCluster)msg.obj).mMyName;
								if(devName.equalsIgnoreCase("Device")){
									/* they are shimmers, give them some alias */
									mService.setDeviceName(mNewConnectedDeviceAddr, 
	                    			mNewConnectedDeviceAddr.substring(12, 17).replace(":", ""));
								}
								/* select the new device */ 
								mSelectedDeviceName = mService.getDeviceName(mNewConnectedDeviceAddr);
								mSelectedDeviceAddr = mNewConnectedDeviceAddr;
										
								/* refresh connected list */
								String dispDevice = devName+"\n"+mNewConnectedDeviceAddr;
								String dispState = (mService.getEnableLogging())?"LOG Enabled":"Log Disabled";
								dispState += "\nConnected";
								HashMap<String, String> map = new HashMap<String, String>();
								map.put("device_cell", dispDevice);
								map.put("state_cell", dispState);
								mConnectedDeviceList.add(map);
								mConnectedDeviceAdapters.notifyDataSetChanged();
								/* refresh connected count */
								mTitle.setText(R.string.title_connected_to);
								mTitle.append(" "+mService.getDevicesConnectedCount()+" Devices");    
	                    		mService.enableGraphingHandler(true);
							}
                    		break;
						case Shimmer.STATE_CONNECTING:
							Log.d("ShimmerActivity","Driver is attempting to establish connection with Shimmer device");
							mTitle.setText(R.string.title_connecting);
							break;
						case Shimmer.MSG_STATE_STREAMING:
							String address = ((ObjectCluster)msg.obj).mBluetoothAddress;
							/* refresh UI */
							for(int i = 0; i < mConnectedDeviceList.size(); i++){
								if(mConnectedDeviceList.get(i).get("device_cell").contains(address)){
									String newDispState = mConnectedDeviceList.get(i).get("state_cell").split("\n")[0] + "\nStreaming";
									mConnectedDeviceList.get(i).put("state_cell", newDispState);
									mConnectedDeviceAdapters.notifyDataSetChanged();
									break;
								}
							}
							break;
						case Shimmer.STATE_NONE:
							Log.d("ShimmerActivity","Shimmer No State");
							int i = 0;
							for(i = 0; i < mConnectedDeviceList.size(); i++){
								if(mConnectedDeviceList.get(i).get("device_cell").
										contains(((ObjectCluster)msg.obj).mBluetoothAddress)){
									mConnectedDeviceList.remove(i);
									mConnectedDeviceAdapters.notifyDataSetChanged();
									break;
								}
							}
							if(mConnectedDeviceList.size()>0){
								/* re-select first device in the list */
								mNewConnectedDeviceAddr = null;
								mSelectedDeviceAddr = 
									mConnectedDeviceList.get(0).get("device_cell").split("\n")[1];
								mSelectedDeviceName = mService.getDeviceName(mSelectedDeviceAddr);
							}else{
								mNewConnectedDeviceAddr = null;
								mSelectedDeviceAddr = null;
								mSelectedDeviceName = null;
							}
							/* refresh connected count */
							mTitle.setText(R.string.title_connected_to);
							mTitle.append(" "+mService.getDevicesConnectedCount()+" Devices");
							// this also stops streaming
							break;
					}
					break;
				case Shimmer.MESSAGE_STOP_STREAMING_COMPLETE:
					String address =  msg.getData().getString("Bluetooth Address");
					boolean stop  =  msg.getData().getBoolean("Streaming Stopped");
					int queue = stop ? 0 : msg.getData().getInt("Queue Size Remaining");
					/* refresh UI */
					for(int i = 0; i < mConnectedDeviceList.size(); i++){
						if(mConnectedDeviceList.get(i).get("device_cell").contains(address)){
							String newDispState = mConnectedDeviceList.get(i).get("state_cell").split("\n")[0];
							if(queue>0){
								newDispState += "\nWrite Log Remaining "+queue;
							}else{
								newDispState += "\nConnected";
							}
							mConnectedDeviceList.get(i).put("state_cell", newDispState);
							mConnectedDeviceAdapters.notifyDataSetChanged();
							break;
						}
					}
					break;
				case Shimmer.MESSAGE_READ:
            	    if ((msg.obj instanceof ObjectCluster)){
            	    	ObjectCluster objectCluster =  (ObjectCluster) msg.obj; 
            	    	Log.d("ShimmerActivity","MSGREAD");

            	    	int[] dataArray = new int[0];
            	    	double[] calibratedDataArray = new double[0];
            	    	String[] sensorName = new String[0];
            	    	String units="";
            	    	String calibratedUnits="";
            	    	String calibratedUnits2="";
            		
            	    	//mSensorView determines which sensor to graph
            	    	if (mSensorView.equals("Accelerometer")){
            	    		sensorName = new String[3]; // for x y and z axis
            	    		dataArray = new int[3];
	            			calibratedDataArray = new double[3];
	            			sensorName[0] = "Accelerometer X";
	            			sensorName[1] = "Accelerometer Y";
	            			sensorName[2] = "Accelerometer Z";
	            			if (mService.getShimmerVersion(mSelectedDeviceAddr)==Shimmer.SHIMMER_3){
	            				if (mService.getAccelRange(mSelectedDeviceAddr)==0){
	            					units="u12"; // units are just merely an indicator to correct the graph
	            				} else {
	            					units="i16";
	            				}
	            			} else {
	            				units="u12";
	            			}
            	    	}
	            		if (mSensorView.equals("Low Noise Accelerometer")){
	            			sensorName = new String[3]; // for x y and z axis
	            			dataArray = new int[3];
	            			calibratedDataArray = new double[3];
	            			sensorName[0] = "Low Noise Accelerometer X";
	            			sensorName[1] = "Low Noise Accelerometer Y";
	            			sensorName[2] = "Low Noise Accelerometer Z";
	            			units="u12"; // units are just merely an indicator to correct the graph
	            		}
	            		if (mSensorView.equals("Wide Range Accelerometer")){
	            			sensorName = new String[3]; // for x y and z axis
	            			dataArray = new int[3];
	            			calibratedDataArray = new double[3];
	            			sensorName[0] = "Wide Range Accelerometer X";
	            			sensorName[1] = "Wide Range Accelerometer Y";
	            			sensorName[2] = "Wide Range Accelerometer Z";
	            			units="i16";
	            		}
	            		if (mSensorView.equals("Gyroscope")){
	            			sensorName = new String[3]; // for x y and z axis
	            			dataArray = new int[3];
	            			calibratedDataArray = new double[3];
	            			sensorName[0] = "Gyroscope X";
	            			sensorName[1] = "Gyroscope Y";
	            			sensorName[2] = "Gyroscope Z";
	            			units="i16";
	            		}
	            		if (mSensorView.equals("Magnetometer")){
	            			sensorName = new String[3]; // for x y and z axis
	            			dataArray = new int[3];
	            			calibratedDataArray = new double[3];
	            			sensorName[0] = "Magnetometer X";
	            			sensorName[1] = "Magnetometer Y";
	            			sensorName[2] = "Magnetometer Z";
	            			units="i12";
	            		}
	            		if (mSensorView.equals("Barometer")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "Barometer";
	            			units="u16";
	            		}
	            		if (mSensorView.equals("GSR")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "GSR";
	            			units="u16";
	            		}
	            		if (mSensorView.equals("EMG")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "EMG";
	            			units="u12";
	            		}
	            		if (mSensorView.equals("ECG")){
	            			sensorName = new String[2]; 
	            			dataArray = new int[2];
	            			calibratedDataArray = new double[2];
	            			sensorName[0] = "ECG RA-LL";
	            			sensorName[1] = "ECG LA-LL";
	            			units="u12";
	            		}
	            		if (mSensorView.equals("Strain Gauge")){
	            			sensorName = new String[2]; 
	            			dataArray = new int[2];
	            			calibratedDataArray = new double[2];
	            			sensorName[0] = "Strain Gauge High";
	            			sensorName[1] = "Strain Gauge Low";
	            			units="u12";
	            		}
	            		if (mSensorView.equals("Heart Rate")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "Heart Rate";
	            			units="u8";
	            			if (mService.getFWVersion(mSelectedDeviceAddr)>0.1){
	            				units="u16";
	            			}
	            		}
	            		if (mSensorView.equals("ExpBoardA0")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "ExpBoard A0";
	            			units="u12";
	            		}
	            		if (mSensorView.equals("ExpBoardA7")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "ExpBoard A7";
	            			units="u12";
	            		}
	            		if (mSensorView.equals("Battery Voltage")){
	            			sensorName = new String[2]; 
	            			dataArray = new int[2];
	            			calibratedDataArray = new double[2];
	            			sensorName[0] = "VSenseReg";
	            			sensorName[1] = "VSenseBatt";
	            			units="u12";
	            		}
	            		if (mSensorView.equals("Timestamp")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "Timestamp";
	            			units="u16";
	            		}
	            		if (mSensorView.equals("External ADC A7")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "External ADC A7";
	            			units="u16";
	            		}
	            		if (mSensorView.equals("External ADC A6")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "External ADC A6";
	            			units="u16";
	            		}
	            		if (mSensorView.equals("External ADC A15")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "External ADC A15";
	            			units="u16";
	            		}
	            		if (mSensorView.equals("Internal ADC A1")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "Internal ADC A1";
	            			units="u16";
	            		}
	            		if (mSensorView.equals("Internal ADC A12")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "Internal ADC A12";
	            			units="u16";
	            		}
	            		if (mSensorView.equals("Internal ADC A13")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "Internal ADC A13";
	            			units="u16";
	            		}
	            		if (mSensorView.equals("Internal ADC A14")){
	            			sensorName = new String[1]; 
	            			dataArray = new int[1];
	            			calibratedDataArray = new double[1];
	            			sensorName[0] = "Internal ADC A14";
	            			units="u16";
	            		}
	            		if (mSensorView.equals("Pressure")){
	            			sensorName = new String[2]; 
	            			dataArray = new int[2];
	            			calibratedDataArray = new double[2];
	            			sensorName[0] = "Pressure";
	            			sensorName[1] = "Temperature";
	            			units="u16";
	            		}
	            		String deviceName = objectCluster.mMyName;
	            		//log data

            		if (sensorName.length!=0){  // Device 1 is the assigned user id, see constructor of the Shimmer
				 	    if (sensorName.length>0){
				 	    	Collection<FormatCluster> ofFormats = objectCluster.mPropertyCluster.get(sensorName[0]);  // first retrieve all the possible formats for the current sensor device
				 	    	FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(ofFormats,"CAL")); 
				 	    	if (formatCluster != null) {
				 	    		//Obtain data for text view
				 	    		calibratedDataArray[0] = formatCluster.mData;
				 	    		calibratedUnits = formatCluster.mUnits;
				 	    		Log.d("ShimmerActivity","MSGREAD2");
				 	    		//Obtain data for graph
				 	    		if(objectCluster.mBluetoothAddress.equalsIgnoreCase(ShimmerService.LOCAL_DEVICE_ADDR)){
					 	    		dataArray[0] = (int) (calibratedDataArray[0]*10);
					 	    	}else{
					 	    		dataArray[0] = (int)((FormatCluster)ObjectCluster.returnFormatCluster(ofFormats,"RAW")).mData; 
					 	    	}
					 	    }
				 	    }
				 	    if (sensorName.length>1) {
				 	    	Collection<FormatCluster> ofFormats = objectCluster.mPropertyCluster.get(sensorName[1]);  // first retrieve all the possible formats for the current sensor device
				 	    	FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(ofFormats,"CAL"));
				 	    	if (formatCluster != null ) {
					 	    	calibratedDataArray[1] = formatCluster.mData;
					 	    	//Obtain data for text view
					 	    	calibratedUnits2 = formatCluster.mUnits;
					 	    	//Obtain data for graph
					 	    	if(objectCluster.mBluetoothAddress.equalsIgnoreCase(ShimmerService.LOCAL_DEVICE_ADDR)){
					 	    		dataArray[1] = (int) (calibratedDataArray[1]*10);
					 	    	}else{
					 	    		dataArray[1] = (int)((FormatCluster)ObjectCluster.returnFormatCluster(ofFormats,"RAW")).mData; 
					 	    	}
				 	    	}
				 	    }
				 	    if (sensorName.length>2){
				 	    	Collection<FormatCluster> ofFormats = objectCluster.mPropertyCluster.get(sensorName[2]);  // first retrieve all the possible formats for the current sensor device
				 	    	FormatCluster formatCluster = ((FormatCluster)ObjectCluster.returnFormatCluster(ofFormats,"CAL")); 
				 	    	if (formatCluster != null) {
				 	    		calibratedDataArray[2] = formatCluster.mData;
				 	    		//Obtain data for graph
				 	    		if(objectCluster.mBluetoothAddress.equalsIgnoreCase(ShimmerService.LOCAL_DEVICE_ADDR)){
					 	    		dataArray[2] = (int) (calibratedDataArray[2]*10);
					 	    	}else{
					 	    		dataArray[2] = (int)((FormatCluster)ObjectCluster.returnFormatCluster(ofFormats,"RAW")).mData; 
					 	    	}
				 	    	}
			            }

				 	    //in order to prevent LAG the number of data points plotted is REDUCED
				 	    int maxNumberofSamplesPerSecond=50; //Change this to increase/decrease the number of samples which are graphed
				 	    int subSamplingCount=0;
				 	    if (mService.getSamplingRate(objectCluster.mBluetoothAddress)>maxNumberofSamplesPerSecond){
				 	    	subSamplingCount=(int) (mService.getSamplingRate(objectCluster.mBluetoothAddress)/maxNumberofSamplesPerSecond);
				 	    	mGraphSubSamplingCount++;
				 	    	Log.d("SSC",Integer.toString(subSamplingCount));
				 	    }
				 	    if (mGraphSubSamplingCount==subSamplingCount){
				 	    	mGraph.setDataWithAdjustment(dataArray,"Shimmer : " + deviceName,units);
				 	    	mTVPRR.setText("PRR : "+String.format("%.2f", mService.getPacketReceptionRate(objectCluster.mBluetoothAddress))+ "%");
							if (calibratedDataArray.length>0) {
								mValueSensor1.setText(String.format("%.4f",calibratedDataArray[0]));
								mTextSensor1.setText(sensorName[0] + "("+calibratedUnits+")");
							}
							if (calibratedDataArray.length>1) {
								mValueSensor2.setText(String.format("%.4f",calibratedDataArray[1]));
								mTextSensor2.setText(sensorName[1] + "("+calibratedUnits2+")");
							}
							if (calibratedDataArray.length>2) {
								mValueSensor3.setText(String.format("%.4f",calibratedDataArray[2]));
								mTextSensor3.setText(sensorName[2] + "("+calibratedUnits+")");
							}	        			
							mGraphSubSamplingCount=0;
				 	    }
					}
            	}
                break;
            case Shimmer.MESSAGE_ACK_RECEIVED:
            	break;
            case Shimmer.MESSAGE_DEVICE_NAME:
                // save the connected device's name
                Toast.makeText(getContext(), "Connected to "
                               + mNewConnectedDeviceAddr, Toast.LENGTH_SHORT).show();
                break;
            case Shimmer.MESSAGE_TOAST:
                Toast.makeText(getContext(), msg.getData().getString(Shimmer.TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
	
    private static Context getContext(){
    	return ShimmerGraphandLogService.context;
    }
    
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
    	case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                //setMessage("\nBluetooth is now enabled");
                Toast.makeText(this, "Bluetooth is now enabled", Toast.LENGTH_SHORT).show();
            } else {
                // User did not enable Bluetooth or an error occured
            	Toast.makeText(this, "Bluetooth not enabled\nExiting...", Toast.LENGTH_SHORT).show();
                finish();       
            }
            break;
    	case REQUEST_CONNECT_SHIMMER:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                String address = data.getExtras()
                        .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                Log.d("ShimmerActivity",address);
          		mService.connectShimmer(address, "Device");
          		mSelectedDeviceAddr = address;
          		mService.setGraphHandler(mHandler);
                
                //mShimmerDevice.connect(address,"gerdavax");
                //mShimmerDevice.setgetdatainstruction("a");
            }
            break;
    	case REQUEST_COMMAND_SHIMMER:
    		/* only for shimmer devices, not phone */
    		if (resultCode == Activity.RESULT_OK) {
	    		if(data.getExtras().getBoolean("ToggleLED",false) == true)
	    		{
	    			mService.toggleAllLEDS();
	    		}
	    		
	    		if(data.getExtras().getDouble("SamplingRate",-1) != -1)
	    		{
	    			mService.writeSamplingRate(mSelectedDeviceAddr, data.getExtras().getDouble("SamplingRate",-1));
	    			Log.d("ShimmerActivity",Double.toString(data.getExtras().getDouble("SamplingRate",-1)));
	    			mGraphSubSamplingCount=0;
	    		}
	    		
	    		if(data.getExtras().getInt("AccelRange",-1) != -1)
	    		{
	    			mService.writeAccelRange(mSelectedDeviceAddr, data.getExtras().getInt("AccelRange",-1));
	    		}
	    		
	    		if(data.getExtras().getInt("GyroRange",-1) != -1)
	    		{
	    			mService.writeGyroRange(mSelectedDeviceAddr, data.getExtras().getInt("GyroRange",-1));
	    		}
	    		
	    		if(data.getExtras().getInt("PressureResolution",-1) != -1)
	    		{
	    			mService.writePressureResolution(mSelectedDeviceAddr, data.getExtras().getInt("PressureResolution",-1));
	    		}
	    		
	    		if(data.getExtras().getInt("MagRange",-1) != -1)
	    		{
	    			mService.writeMagRange(mSelectedDeviceAddr, data.getExtras().getInt("MagRange",-1));
	    		}
	    		
	    		if(data.getExtras().getInt("GSRRange",-1) != -1)
	    		{
	    			mService.writeGSRRange(mSelectedDeviceAddr,data.getExtras().getInt("GSRRange",-1));
	    		}
	    		if(data.getExtras().getDouble("BatteryLimit",-1) != -1)
	    		{
	    			mService.setBattLimitWarning(mSelectedDeviceAddr, data.getExtras().getDouble("BatteryLimit",-1));
	    		}
    		}
    		break;
    	case REQUEST_LOGFILE_SHIMMER:
    		if (resultCode == Activity.RESULT_OK) {
    			mEnableLogging = data.getExtras().getBoolean("LogFileEnableLogging");
    			if (mEnableLogging==true){
    				mService.setEnableLogging(mEnableLogging);
    			}
    			Log.d(TAG, "log enable is "+mEnableLogging);
    			//set the filename in the LogFile
    			mSubjectName=data.getExtras().getString("SubjectName");
    			mService.setLoggingName(mSubjectName);
    			
    			/* refresh UI */
				for(int i = 0; i < mConnectedDeviceList.size(); i++){
					String newDispState = "";
					if (mEnableLogging==false){
						newDispState = "LOG Disabled\n"+mConnectedDeviceList.get(i).get("state_cell").split("\n")[1];
	    	        } else if (mEnableLogging==true){
	    	        	newDispState = "LOG Enabled\n"+mConnectedDeviceList.get(i).get("state_cell").split("\n")[1];
	    	        }
					Log.d(TAG, newDispState);
					mConnectedDeviceList.get(i).put("state_cell", newDispState);
				}
				mConnectedDeviceAdapters.notifyDataSetChanged();
				mTitleLogging.setText(mSubjectName);
    		}
    		break;
        }
	}
	
	@Override
	/**
	 * JD: new option menu
	 */
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		/* allows nothing if no device connected */
		menu.findItem(R.id.disconnect).setEnabled(false);
		menu.findItem(R.id.streamall).setEnabled(false);
		menu.findItem(R.id.streamnone).setEnabled(false);
		menu.findItem(R.id.viewsensor).setEnabled(false);
		menu.findItem(R.id.logfile).setEnabled(true);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		//disable graph edit for sensors which are not enabled
		MenuItem disconnectItem = menu.findItem(R.id.disconnect);
		MenuItem streamItem = menu.findItem(R.id.streamall);
		MenuItem nstreamItem = menu.findItem(R.id.streamnone);
		MenuItem viewItem = menu.findItem(R.id.viewsensor);
		MenuItem logItem = menu.findItem(R.id.logfile);
		
		if((mService.getDevicesConnectedCount() > 0)){
			disconnectItem.setEnabled(true);
		}else{
			disconnectItem.setEnabled(false);
		}
		
		if((mService.getDevicesConnectedCount() > 0)&&(mService.getDevicesStreamingCount()==0)){
			streamItem.setEnabled(true);
			nstreamItem.setEnabled(false);
		}else{
			streamItem.setEnabled(false);
			if(mService.getDevicesConnectedCount() == 0){
				nstreamItem.setEnabled(false);
			}else{
				nstreamItem.setEnabled(true);
			}
		}
		
		if(mService.isDevicesConnected(mSelectedDeviceAddr)){
			viewItem.setEnabled(true);
		}else{
			viewItem.setEnabled(false);
		}
		
		if(mService.getDevicesStreamingCount()>0){
			logItem.setEnabled(false);
		}else{
			logItem.setEnabled(true);
		}
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.scan) {
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_SHIMMER);
			return true;
		} else if (itemId == R.id.disconnect) {
			if ((mService.getDevicesConnectedCount() > 0)) {
				mService.disconnectAllDevices();
			}else{
				Toast.makeText(getContext(), "No action required", Toast.LENGTH_SHORT).show();
			}
			return true;
		} else if (itemId == R.id.streamall) {
			if((mService.getDevicesConnectedCount() > 0)&&(mService.getDevicesStreamingCount()==0)){
				mHandler.postDelayed(new Runnable(){
					@Override
					public void run() {
						mService.startStreamingAllDevices();
						beep();
					}
				}, PREPARE_INTRVL);
			}else{
				Toast.makeText(getContext(), mService.getDevicesStreamingCount()+" device is already streaming", 
						Toast.LENGTH_SHORT).show();
			}
			return true;
		} else if (itemId == R.id.streamnone) {
			if((mService.getDevicesConnectedCount() > 0)&&(mService.getDevicesStreamingCount()>0)){
				mService.stopStreamingAllDevices();
			}else{
				Toast.makeText(getContext(), "No action required", Toast.LENGTH_SHORT).show();
			}
			return true;
		} else if (itemId == R.id.viewsensor) {
			showSelectSensorPlot();
			return true;
		} else if (itemId == R.id.logfile) {
			Intent logfileIntent=new Intent(this, LogFileActivity.class);
			startActivityForResult(logfileIntent, REQUEST_LOGFILE_SHIMMER);
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}
	
	/**
	 * disable graph edit for sensors which are not enabled
	 */
	public void showSelectSensorPlot(){
		mDialog.setContentView(R.layout.dialog_sensor_view);
		TextView title = (TextView) mDialog.findViewById(android.R.id.title);
 		title.setText("Select Signal");
 		final ListView listView = (ListView) mDialog.findViewById(android.R.id.list);
		listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		List<String> sensorList = mService.getListofEnabledSensors(mSelectedDeviceAddr);
		sensorList.add("Timestamp");
		final String[] sensorNames = sensorList.toArray(new String[sensorList.size()]);
		ArrayAdapter<String> adapterSensorNames = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, android.R.id.text1, sensorNames);
		listView.setAdapter(adapterSensorNames);
		
		listView.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				// TODO Auto-generated method stub
    			mSensorView=sensorNames[arg2];
    			mTextSensor1.setText("");
    			mTextSensor2.setText("");
    			mTextSensor3.setText("");
    			if (mSensorView.equals("Accelerometer")){
        			mTextSensor1.setText("AccelerometerX");
        			mTextSensor2.setText("AccelerometerY");
        			mTextSensor3.setText("AccelerometerZ");
        		}
    			if (mSensorView.equals("Wide Range Accelerometer")){
        			mTextSensor1.setText("AccelerometerX");
        			mTextSensor2.setText("AccelerometerY");
        			mTextSensor3.setText("AccelerometerZ");
        		}
    			if (mSensorView.equals("Low Noise Accelerometer")){
        			mTextSensor1.setText("AccelerometerX");
        			mTextSensor2.setText("AccelerometerY");
        			mTextSensor3.setText("AccelerometerZ");
        		}
    			if (mSensorView.equals("Accelerometer")){
        			mTextSensor1.setText("AccelerometerX");
        			mTextSensor2.setText("AccelerometerY");
        			mTextSensor3.setText("AccelerometerZ");
        		}
        		if (mSensorView.equals("Gyroscope")){
        			mTextSensor1.setText("GyroscopeX");
        			mTextSensor2.setText("GyroscopeY");
        			mTextSensor3.setText("GyroscopeZ");
        		}
        		if (mSensorView.equals("Magnetometer")){
        			mTextSensor1.setText("MagnetometerX");
        			mTextSensor2.setText("MagnetometerY");
        			mTextSensor3.setText("MagnetometerZ");
        		}
        		if(mSensorView.equals("Barometer")){
        			mTextSensor1.setText("Barometer");
        		}
        		if (mSensorView.equals("GSR")){
        			mTextSensor1.setText("GSR");
        		}
        		if (mSensorView.equals("EMG")){
        			mTextSensor1.setText("EMG");
        		}
        		if (mSensorView.equals("ECG")){
        			mTextSensor1.setText("ECGRALL");
        			mTextSensor2.setText("ECGLALL");
        		}
        		if (mSensorView.equals("Strain Gauge")){
        			mTextSensor1.setText("Strain Gauge High");
        			mTextSensor2.setText("Strain Gauge Low");
        		}
        		if (mSensorView.equals("Heart Rate")){
        			mTextSensor1.setText("Heart Rate");
        		}
        		if (mSensorView.equals("ExpBoardA0")){
        			mTextSensor1.setText("ExpBoardA0");
        		}
        		if (mSensorView.equals("ExpBoardA7")){
        			mTextSensor1.setText("ExpBoardA7");
        		}
        		if (mSensorView.equals("Timestamp")){
        			mTextSensor1.setText("TimeStamp");
        		} 
        		if (mSensorView.equals("Battery Voltage")){
        			mTextSensor1.setText("VSenseReg");
        			mTextSensor2.setText("VSenseBatt");
        		}
        		if (mSensorView.equals("External ADC A7")){
        			mTextSensor1.setText("External ADC A7");
        		}
        		if (mSensorView.equals("External ADC A6")){
        			mTextSensor1.setText("External ADC A6");
        		}
        		if (mSensorView.equals("External ADC A15")){
        			mTextSensor1.setText("External ADC A15");
        		}
        		if (mSensorView.equals("Internal ADC A1")){
        			mTextSensor1.setText("Internal ADC A1");
        		}
        		if (mSensorView.equals("Internal ADC A12")){
        			mTextSensor1.setText("Internal ADC A12");
        		}
        		if (mSensorView.equals("Internal ADC A13")){
        			mTextSensor1.setText("Internal ADC A13");
        		}
        		if (mSensorView.equals("Internal ADC A14")){
        			mTextSensor1.setText("Internal ADC A14");
        		}
        		if (mSensorView.equals("Pressure")){
        			mTextSensor1.setText("Pressure");
        			mTextSensor2.setText("Temperature");
        		}
    			mDialog.dismiss();
			}
		});
		mDialog.show();
 		
	}
	
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo){
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.context_menu, menu); 
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		//String select = ((TextView) info.targetView).getText().toString();
		String select = ((TextView)info.targetView.findViewById(R.id.device_cell)).getText().toString();
		mSelectedDeviceName = select.split("\n")[0];
		mSelectedDeviceAddr = select.split("\n")[1];
		mService.setOperatingDevice(mSelectedDeviceAddr);
		if(mSelectedDeviceAddr.equalsIgnoreCase(ShimmerService.LOCAL_DEVICE_ADDR)){
			menu.findItem(R.id.settings).setVisible(false);
			menu.findItem(R.id.commands).setVisible(false);
		}
		Toast.makeText(getContext(), 
				"selected: "+mSelectedDeviceName+":"+mSelectedDeviceAddr, Toast.LENGTH_SHORT).show();
	}
	
	public boolean onContextItemSelected(MenuItem item){
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo(); 
		switch(item.getItemId()){
			case R.id.startStream:
				if (mService.isDeviceStreaming(mSelectedDeviceAddr)){
					Toast.makeText(getContext(), mSelectedDeviceAddr+" is already streaming", 
										Toast.LENGTH_SHORT).show();
					Log.d("Shimmer", "already streaming");
				}else if((!mSelectedDeviceAddr.equalsIgnoreCase(ShimmerService.LOCAL_DEVICE_ADDR))&&
						!(mService.GetInstructionStatus(mSelectedDeviceAddr))){
					Toast.makeText(getContext(), mSelectedDeviceAddr+" is in the middle of something", 
							Toast.LENGTH_SHORT).show();
				}else{
					mService.startStreaming(mSelectedDeviceAddr);
					Log.d("Shimmer", "start streaming");
					//log = new Logging(mFileName,"\t");
				}
				return true;
			case R.id.stopStream:
				if (mService.isDeviceStreaming(mSelectedDeviceAddr)){
					mService.stopStreaming(mSelectedDeviceAddr);
				}else{
					Toast.makeText(getContext(), mSelectedDeviceAddr+" is not streaming at all", 
							Toast.LENGTH_SHORT).show();
				}
				return true;
			case R.id.settings:
				if(mService.isDeviceStreaming(mSelectedDeviceAddr)||
						!mService.GetInstructionStatus(mSelectedDeviceAddr)){
					Toast.makeText(getContext(), mSelectedDeviceAddr+" is in the middle of something", 
							Toast.LENGTH_SHORT).show();
				}else{
					Shimmer shimmer = mService.getShimmer(mSelectedDeviceAddr);
					showEnableSensors(shimmer.getListofSupportedSensors(),mService.getEnabledSensors(mSelectedDeviceAddr));
				}
				return true;
			case R.id.commands:
				if(mService.isDeviceStreaming(mSelectedDeviceAddr)||
						!mService.GetInstructionStatus(mSelectedDeviceAddr)){
					Toast.makeText(getContext(), mSelectedDeviceAddr+" is in the middle of something", 
							Toast.LENGTH_SHORT).show();
				}else{
		     		Intent commandIntent=new Intent(this, CommandsActivity.class);
		     		commandIntent.putExtra("BluetoothAddress",mSelectedDeviceAddr);
		     		commandIntent.putExtra("SamplingRate",mService.getSamplingRate(mSelectedDeviceAddr));
		     		commandIntent.putExtra("AccelerometerRange",mService.getAccelRange(mSelectedDeviceAddr));
		     		commandIntent.putExtra("GSRRange",mService.getGSRRange(mSelectedDeviceAddr));
		     		commandIntent.putExtra("BatteryLimit",mService.getBattLimitWarning(mSelectedDeviceAddr));
		     		startActivityForResult(commandIntent, REQUEST_COMMAND_SHIMMER);
				}
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}
	
	public void showEnableSensors(final String[] sensorNames, int enabledSensors){
		dialogEnabledSensors=enabledSensors;
		mDialog.setContentView(R.layout.dialog_enable_sensor_view);
		TextView title = (TextView) mDialog.findViewById(android.R.id.title);
 		title.setText("Select Signal");
 		final ListView listView = (ListView) mDialog.findViewById(android.R.id.list);
		listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		ArrayAdapter<String> adapterSensorNames = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, android.R.id.text1, sensorNames);
		listView.setAdapter(adapterSensorNames);
		final BiMap<String,String> sensorBitmaptoName;
		sensorBitmaptoName = Shimmer.generateBiMapSensorIDtoSensorName(mService.getShimmerVersion(mSelectedDeviceAddr));
		for (int i=0;i<sensorNames.length;i++){
			int iDBMValue = Integer.parseInt(sensorBitmaptoName.inverse().get(sensorNames[i]));	
			if( (iDBMValue & enabledSensors) >0){
				listView.setItemChecked(i, true);
			}
		}	
		listView.setOnItemClickListener(new OnItemClickListener(){	
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int clickIndex, long arg3) {
				int sensorIdentifier = Integer.parseInt(sensorBitmaptoName.inverse().get(sensorNames[clickIndex]));
				//check and remove any old daughter boards (sensors) which will cause a conflict with sensorIdentifier 
				dialogEnabledSensors = mService.sensorConflictCheckandCorrection(mSelectedDeviceAddr,dialogEnabledSensors,sensorIdentifier);
				//update the checkbox accordingly
				for (int i=0;i<sensorNames.length;i++){
					int iDBMValue = Integer.parseInt(sensorBitmaptoName.inverse().get(sensorNames[i]));	
					if( (iDBMValue & dialogEnabledSensors) >0){
						listView.setItemChecked(i, true);
					} else {
						listView.setItemChecked(i, false);
					}
				}
			}	
		});
		
		Button mDoneButton = (Button)mDialog.findViewById(R.id.buttonEnableSensors);
		mDoneButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				mService.setEnabledSensors(dialogEnabledSensors,mSelectedDeviceAddr);
				mDialog.dismiss();
			}});
		mDialog.show();
	}
	
	/**
	 * Play a sound indicating the start of logging
	 */
	private void beep(){
		try {
		    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
		    Ringtone r = RingtoneManager.getRingtone(this, notification);
		    r.play();
		} catch (Exception e) {
		    e.printStackTrace();
		}
	}
}