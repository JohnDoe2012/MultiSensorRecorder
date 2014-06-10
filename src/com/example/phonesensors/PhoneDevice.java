package com.example.phonesensors;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.driver.*;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class PhoneDevice implements SensorEventListener{
	private static final String TAG = "LOCAL";
	public static final int SAMPLING_INTRVL = 20; 	//ms
	public static final int SENSOR_BARO = 0x100;
	
	private Context mContext;
	private Handler mHandler;
	private String mBluetoothAddress;
	private String mMyName;
	private int mState;
	
//	private Timer mSensorTimer;
	private ScheduledThreadPoolExecutor mSensorScheduler;
	private SensorManager mSensorManager;
	private int mSensorCapa = 0;
	private float bufAcce[];
	private float bufOrie[];
	private float bufGyro[];
	private float bufBaro[];
	private long mSamplingRate = SAMPLING_INTRVL;	//ms
//	private boolean isFirst = false;
	
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
					SensorManager.SENSOR_DELAY_FASTEST);
			}
			if(((mSensorCapa&0xFFF) & Shimmer.SENSOR_MAG)!=0){
				bufOrie = new float[3];
				mSensorManager.registerListener(this, 
					mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), 
					SensorManager.SENSOR_DELAY_FASTEST);
			}
			if(((mSensorCapa&0xFFF) & Shimmer.SENSOR_GYRO)!=0){
				bufGyro = new float[3];
				mSensorManager.registerListener(this, 
					mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 
					SensorManager.SENSOR_DELAY_FASTEST);
			}
			if(((mSensorCapa&0xFFF) & SENSOR_BARO)!=0){
				bufBaro = new float[1];
				mSensorManager.registerListener(this, 
					mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), 
					SensorManager.SENSOR_DELAY_FASTEST);
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
			startScheduler(mSensorScheduler, new SensorSampleSchedule(), 0, mSamplingRate);
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
			/* ask shimmer service to clear logfile material */
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
	
	private void startScheduler(ScheduledThreadPoolExecutor aScheduler, Runnable aTimerTask, long delay, long period){
		aScheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(10);
		aScheduler.scheduleAtFixedRate(aTimerTask, delay, period, TimeUnit.MILLISECONDS);
	}
	
	private void stopScheduler(ScheduledThreadPoolExecutor aScheduler){
		if(aScheduler!=null){
			aScheduler.shutdown();
		}
	}
	
	private class SensorSampleSchedule implements Runnable{
		@Override
		public void run() {
			if(mState==Shimmer.MSG_STATE_STREAMING){
				mHandler.obtainMessage(Shimmer.MESSAGE_READ, buildData()).sendToTarget();
//				if(isFirst){
//					beep();
//					isFirst = false;
//				}
			}
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
