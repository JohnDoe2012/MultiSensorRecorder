package com.example.phonesensors;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.driver.*;
import com.shimmerresearch.tools.Logging;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class PhoneDevice implements SensorEventListener{
	private static final String TAG = "LOCAL";
	public static final int SAMPLING_INTRVL = 20000; 	//ms
	public static final int SENSOR_BARO = 0x100;
	
	private Context mContext;
	private Handler mHandler;
	private String mBluetoothAddress;
	private String mMyName;
	private int mState;
	
//	private Timer mSensorTimer;
	private ExceptionProofExecutor mSensorScheduler;
	private SensorManager mSensorManager;
	private int mSensorCapa = 0;
	private float bufAcce[];
	private float bufOrie[];
	private float bufGyro[];
	private float bufBaro[];
	private long mSamplingRate = SAMPLING_INTRVL;	//ms
//	private boolean isFirst = false;
	
	/* Wifi Scanning device*/
	private String mWifiAddress;
	private Timer mWifiScanner = null;
	private Thread mWifiScannerThread = null;
	private Process su = null;
	private Logging mWifiLogging;
	
	/**
	 * Constructor
	 * @param aContext
	 * @param aHandler
	 */
	public PhoneDevice(Context aContext, Handler aHandler){
		mContext = aContext;
		mHandler = aHandler;
		mState = Shimmer.STATE_NONE;
		mBluetoothAddress = BluetoothAdapter.getDefaultAdapter().getAddress();
		setDeviceName();
		mSensorManager = (SensorManager)mContext.getSystemService(Context.SENSOR_SERVICE);
		WifiManager wifiMan = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
		mWifiAddress = wifiMan.getConnectionInfo().getMacAddress();
		getCapability();
		Log.d(TAG, "local device created");
	}
	
	/**
	 * Get available sensors on local device
	 * @return available sensors
	 */
	public int getCapability(){
		List<Sensor> mSensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
		Log.d(TAG, "Capability: "+mSensorList.size());
		for(int i = 0; i < mSensorList.size(); i++){
			Sensor s = mSensorList.get(i);
			switch(s.getType()){
				case Sensor.TYPE_ACCELEROMETER:
					mSensorCapa = mSensorCapa|Shimmer.SENSOR_ACCEL;
					break;
				case Sensor.TYPE_ORIENTATION:
					mSensorCapa = mSensorCapa|Shimmer.SENSOR_MAG;
					break;
				case Sensor.TYPE_GYROSCOPE:
					mSensorCapa = mSensorCapa|Shimmer.SENSOR_GYRO;
					break;
				case Sensor.TYPE_PRESSURE:
					mSensorCapa = mSensorCapa|SENSOR_BARO;
					break;
				default:
					break;
			}
			Log.d(TAG, s.getName()+":"+s.getType()+":"+mSensorCapa);
		}
		/* debug */
		if((mSensorCapa&Shimmer.SENSOR_ACCEL)!=0) Log.d(TAG, "ACCELEROMETER ON");
		if((mSensorCapa&Shimmer.SENSOR_MAG)!=0) Log.d(TAG, "ORIENTATION ON");
		if((mSensorCapa&Shimmer.SENSOR_GYRO)!=0) Log.d(TAG, "GYROSCOPE ON");
		if((mSensorCapa&SENSOR_BARO)!=0) Log.d(TAG, "PRESSURE ON");
		
		return mSensorCapa;
	}
	
	/**
	 * Connect to local device by registering sensors
	 */
	public void connect(){
		if(mState==Shimmer.STATE_NONE){			
			/* SENSOR_DELAY_FASTEST: 20ms */
			if(((mSensorCapa&0xFFF) & Shimmer.SENSOR_ACCEL)!=0){
				bufAcce = new float[3];
				mSensorManager.registerListener(this, 
					mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 
					SensorManager.SENSOR_DELAY_NORMAL);
			}
			if(((mSensorCapa&0xFFF) & Shimmer.SENSOR_MAG)!=0){
				bufOrie = new float[3];
				mSensorManager.registerListener(this, 
					mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), 
					SensorManager.SENSOR_DELAY_NORMAL);
			}
			if(((mSensorCapa&0xFFF) & Shimmer.SENSOR_GYRO)!=0){
				bufGyro = new float[3];
				mSensorManager.registerListener(this, 
					mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 
					SensorManager.SENSOR_DELAY_NORMAL);
			}
			if(((mSensorCapa&0xFFF) & SENSOR_BARO)!=0){
				bufBaro = new float[1];
				mSensorManager.registerListener(this, 
					mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), 
					SensorManager.SENSOR_DELAY_NORMAL);
			}
			setState(Shimmer.MSG_STATE_FULLY_INITIALIZED);
		}
	}
	
	/**
	 * Disconnect a phone device
	 */
	public void destory(){
		if(mState!=Shimmer.STATE_NONE){
			stopStreaming();
			mSensorManager.unregisterListener(this);
			setState(Shimmer.STATE_NONE);
		}
	}
	
	/**
	 * Start streaming from a phone device 
	 */
	public void startStreaming(){
		if(mState==Shimmer.MSG_STATE_FULLY_INITIALIZED){
//			startTimer(mSensorTimer, new SensorSampleTask(), 10, mSamplingRate);
			startScheduler(mSensorScheduler, new SensorSampleSchedule(), 10, mSamplingRate);
			try{
				su = Runtime.getRuntime().exec(new String[]{"su", "-c", "system/bin/sh"});
			}catch(IOException e) {
				e.printStackTrace();
			}
			mWifiScanner = new Timer();
			mWifiScanner.scheduleAtFixedRate(new WifiScannerTask(), 0, 6000);
			setState(Shimmer.MSG_STATE_STREAMING);
//			isFirst = true;
		}
	}
	
	/**
	 * Stop streaming from a phone device
	 */
	public void stopStreaming(){
		if(mState==Shimmer.MSG_STATE_STREAMING){
//			stopTimer(mSensorTimer);
			setState(Shimmer.MSG_STATE_FULLY_INITIALIZED);
			stopScheduler(mSensorScheduler);
			mWifiScanner.cancel();
			if (mWifiScannerThread!=null) mWifiScannerThread.interrupt();
			/* ask shimmer service to clear logfile material */
			Message wmsg = mHandler.obtainMessage(Shimmer.MESSAGE_STOP_STREAMING_COMPLETE);
			Bundle wbundle = new Bundle();
			wbundle.putBoolean("Stop Streaming", true);
			wbundle.putString("Bluetooth Address", mWifiAddress);
	        wmsg.setData(wbundle);
	        mHandler.sendMessage(wmsg);
			
			Message msg = mHandler.obtainMessage(Shimmer.MESSAGE_STOP_STREAMING_COMPLETE);
	        Bundle bundle = new Bundle();
	        bundle.putBoolean("Stop Streaming", true);
	        bundle.putString("Bluetooth Address", mBluetoothAddress);
	        msg.setData(bundle);
	        mHandler.sendMessage(msg);
		}
	}
	
	/**
	 * Set the current state of local device
	 * @param state An integer defining the current device state
	 */
	private synchronized void setState(int state){
		mState = state;
		// Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Shimmer.MESSAGE_STATE_CHANGE, state, -1, new ObjectCluster(mMyName,mBluetoothAddress)).sendToTarget();
	}
	
	/**
	 * Get the current state of phone device
	 * @return An integer defining the current device state
	 */
	public synchronized int getPhoneState(){
		return mState;
	}
	
	/**
	 * Get phone device name
	 * @return phone device name 
	 */
	public String getDeviceName(){
		return mMyName;
	}
	
	/**
	 * Set phone device name, hardcoded according to bluetooth address
	 */
	private void setDeviceName(){
		if(mBluetoothAddress.equalsIgnoreCase("78:52:1A:E4:D7:24")){
			mMyName = "Hip";
		}else if(mBluetoothAddress.equalsIgnoreCase("70:F9:27:60:3C:81")){
			mMyName = "Thigh";
		}else if(mBluetoothAddress.equalsIgnoreCase("70:F9:27:60:42:43")){
			mMyName = "Arm";
		}else if(mBluetoothAddress.equalsIgnoreCase("8C:3A:E3:F0:F5:5C")){
			mMyName = "Arm";
		}else{
			mMyName = "Local";
		}
	}
	
	/**
	 * Get phone bluetooth address
	 * @return bluetooth address
	 */
	public String getDeviceAddress(){
		return mBluetoothAddress;
	}
	
	/**
	 * Get sampling rate (in Hz)
	 * @return current sampling rate
	 */
	public long getSamplingRate(){
		return 1000/mSamplingRate;
	}
	
	/**
	 * Set sampling rate
	 * @param rate new sampling rate
	 */
	public void setSamplingRate(long rate){
		mSamplingRate = rate;
	}
	
	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		switch(event.sensor.getType()){
		case Sensor.TYPE_ACCELEROMETER:
			System.arraycopy(event.values, 0, bufAcce, 0, 3);
			break;
		case Sensor.TYPE_ORIENTATION:
			System.arraycopy(event.values, 0, bufOrie, 0, 3);
			break;
		case Sensor.TYPE_GYROSCOPE:
			System.arraycopy(event.values, 0, bufGyro, 0, 3);
			break;
		case Sensor.TYPE_PRESSURE:
			System.arraycopy(event.values, 0, bufBaro, 0, 1);
			break;
		default:
			break;
	}
	}

	/**
	 * Encapsulate sampled sensor readings into objectCluster
	 * @return objectCluster contains sensor readings
	 */
	private ObjectCluster buildData(){
		ObjectCluster objectCluster=new ObjectCluster(mMyName, mBluetoothAddress);
		objectCluster.mPropertyCluster.put("Timestamp",
				new FormatCluster("CAL","mSecs",(double)System.currentTimeMillis()));
		if(((mSensorCapa&0xFFF) & Shimmer.SENSOR_ACCEL)!=0){
			objectCluster.mPropertyCluster.put("Accelerometer X",
						new FormatCluster("CAL","m/(sec^2)*",(double)bufAcce[0]));
		    objectCluster.mPropertyCluster.put("Accelerometer Y",
		    			new FormatCluster("CAL","m/(sec^2)*",(double)bufAcce[1]));
		    objectCluster.mPropertyCluster.put("Accelerometer Z",
		    			new FormatCluster("CAL","m/(sec^2)*",(double)bufAcce[2]));
		}
		if(((mSensorCapa&0xFFF) & Shimmer.SENSOR_MAG)!=0){
			objectCluster.mPropertyCluster.put("Magnetometer X",
					new FormatCluster("CAL","local*",(double)bufOrie[0]));
		    objectCluster.mPropertyCluster.put("Magnetometer Y",
		    		new FormatCluster("CAL","local*",(double)bufOrie[1]));
		    objectCluster.mPropertyCluster.put("Magnetometer Z",
		    		new FormatCluster("CAL","local*",(double)bufOrie[2]));
		}
		if(((mSensorCapa&0xFFF) & Shimmer.SENSOR_GYRO)!=0){
			objectCluster.mPropertyCluster.put("Gyroscope X",
					new FormatCluster("CAL","deg/sec*",(double)bufGyro[0]));
		    objectCluster.mPropertyCluster.put("Gyroscope Y",
		    		new FormatCluster("CAL","deg/sec*",(double)bufGyro[1]));
		    objectCluster.mPropertyCluster.put("Gyroscope Z",
		    		new FormatCluster("CAL","deg/sec*",(double)bufGyro[2]));
		}
		if(((mSensorCapa&0xFFF) & SENSOR_BARO)!=0){
			objectCluster.mPropertyCluster.put("Barometer",
					new FormatCluster("CAL","hPa*",(double)bufBaro[0]));
		}
		return objectCluster;
	}
	
	private void startScheduler(ExceptionProofExecutor aScheduler, Runnable aTimerTask, long delay, long period){
		//aScheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(10);
		aScheduler = new ExceptionProofExecutor(2);
		aScheduler.scheduleAtFixedRate(aTimerTask, delay, period, TimeUnit.MILLISECONDS);
	}
	
	private void stopScheduler(ExceptionProofExecutor aScheduler){
		if(aScheduler!=null){
			aScheduler.shutdown();
		}
	}
	
	private class SensorSampleSchedule implements Runnable{
		Throwable errorMessage;
		@Override
		public void run() {
			if(mState==Shimmer.MSG_STATE_STREAMING){
				try{
					mHandler.obtainMessage(Shimmer.MESSAGE_READ, buildData()).sendToTarget();
				}catch(Throwable e){
					e.printStackTrace();
					errorMessage = e;
				}
				throw new RuntimeException(errorMessage);
//				if(isFirst){
//					beep();
//					isFirst = false;
//				}
			}
		}
	}
	
	private class ExceptionProofExecutor extends ScheduledThreadPoolExecutor{
		public ExceptionProofExecutor(int corePoolSize){
			super(corePoolSize);
		}
		
		public ScheduledFuture scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit){
			return super.scheduleAtFixedRate(wrapRunnable(command), initialDelay, period, unit);
		}
		
		private Runnable wrapRunnable(Runnable command){
			return new LogOnExceptionRunnable(command);
		}
		
		private class LogOnExceptionRunnable implements Runnable{
			private Runnable mRunnable;
			
			public LogOnExceptionRunnable(Runnable aRunnable){
				super();
				mRunnable = aRunnable;
			}

			@Override
			public void run() {
				try{
					mRunnable.run();
				}catch (Throwable e){
					//e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Encapsulate sampled sensor readings into objectCluster
	 * @return objectCluster contains sensor readings
	 */
	private ObjectCluster buildWiFiData(String results){
		ObjectCluster objectCluster=new ObjectCluster(mMyName+"_WIFI_"+results, mWifiAddress);
		return objectCluster;
	}
	
	/**
	 * A timer task that periodically check if the continuous scanning thread is running properly
	 * if not, restart the thread
	 */
	private class WifiScannerTask extends TimerTask{
		@Override
		public void run() {
			if((mWifiScannerThread!=null)&&(mWifiScannerThread.isAlive())){
				
			}else{
				beep();
				if (mWifiScannerThread!=null) mWifiScannerThread.interrupt();
				mWifiScannerThread = new Thread(new WiFiScanningTask());
				mWifiScannerThread.start();
			}
		}
	}
	
	private class WiFiScanningTask implements Runnable {
		@Override
		public void run() {
			try{
				DataOutputStream stdin = new DataOutputStream(su.getOutputStream());
				InputStream stdout = su.getInputStream();
				while(mState==Shimmer.MSG_STATE_STREAMING){
					Log.d(TAG, "start Wifi scan");
					//stdin.writeBytes("iw dev wlan0 scan freq 2412 2417 2432 2437 2457 2462 5180 5200 5220 5240\n");
					stdin.writeBytes("iw dev wlan0 scan freq 2412 2437 2462\n");
					WifiFingerprint newFgpt = new WifiFingerprint(System.currentTimeMillis());
					byte[] buffer = new byte[4096];
					int read;
					String results = new String();
					//read method will wait forever if there is nothing in the stream
					//so we need to read it in another way than while((read=stdout.read(buffer))>0)
					while(true){
					    read = stdout.read(buffer);
					    results += new String(buffer, 0, read);
					    if(read<4096){
					        //we have read everything
					        break;
					    }
					}
					Log.d(TAG, "results: "+results);
					newFgpt.setTimeRCVD(System.currentTimeMillis());
					newFgpt.addScanObs(results);
					newFgpt.mergeObs(null);
					mHandler.obtainMessage(Shimmer.MESSAGE_READ, buildWiFiData(newFgpt.println(1))).sendToTarget();
				}
			}catch (IOException e) {
				mWifiScannerThread.interrupt();
				e.printStackTrace();
			}
		}
	}
	
	
	/**
	 * Play a sound indicating the start of logging
	 */
	private void beep(){
		try {
		    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
		    Ringtone r = RingtoneManager.getRingtone(mContext, notification);
		    r.play();
		} catch (Exception e) {
		    e.printStackTrace();
		}
	}
	
//	/**
//	 * Play a sound indicating the start of logging
//	 */
//	private void beep(){
//		try {
//		    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
//		    Ringtone r = RingtoneManager.getRingtone(mContext, notification);
//		    r.play();
//		} catch (Exception e) {
//		    e.printStackTrace();
//		}
//	}
//	
//	
//	/**
//	 * Helper function that starts a timer
//	 * @param aTimer timer to be started or created
//	 * @param aTimerTask the task to schedule
//	 * @param delay amount of time in milliseconds before first execution
//	 * @param period amount of time in milliseconds between subsequent executions
//	 */
//	private void startTimer(Timer aTimer, TimerTask aTimerTask, long delay, long period){
//		aTimer = new Timer();
//		aTimer.scheduleAtFixedRate(aTimerTask, delay, period);
//	}
//	
//	/**
//	 * Helper function that stops a timer
//	 * @param aTimer timer to be stopped
//	 */
//	private void stopTimer(Timer aTimer){
//		if(aTimer!=null){
//			aTimer.cancel();
//			aTimer.purge();
//		}
//	}
//	
//	private class SensorSampleTask extends TimerTask{
//		@Override
//		public void run() {
//			if(mState==Shimmer.MSG_STATE_STREAMING){
//				mHandler.obtainMessage(Shimmer.MESSAGE_READ, buildData()).sendToTarget();				
//			}
//		}
//	}
}
