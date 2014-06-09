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

//Future updates needed
//- the handler should be converted to static 

package com.shimmerresearch.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.example.phonesensors.PhoneDevice;
import com.shimmerresearch.android.Shimmer;
import com.shimmerresearch.driver.*;
import com.shimmerresearch.tools.Logging;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class ShimmerService extends Service {
	private static final String TAG = "MyService";
    public Shimmer shimmerDevice1 = null;
    public Logging shimmerLog1 = null;
    private boolean mEnableLogging=false;
	private BluetoothAdapter mBluetoothAdapter = null;
	private final IBinder mBinder = new LocalBinder();
	public PhoneDevice mLocalDevice;
	public String mLocalDeviceAddr = BluetoothAdapter.getDefaultAdapter().getAddress();
	public HashMap<String, Object> mMultiShimmer = new HashMap<String, Object>(7);
	public HashMap<String, Logging> mLogShimmer = new HashMap<String, Logging>(7);
	private Handler mHandlerGraph=null;
	private boolean mGraphing=false;
	private String mLogFileName="Default";
	private Context mContext;
	private String mOperatingDevice = "";
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	public class LocalBinder extends Binder {
        public ShimmerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ShimmerService.this;
        }
    }
	
	@Override
	public void onCreate() {
		Toast.makeText(this, "My Service Created", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onCreate");
	}
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("LocalService", "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        mContext = this.getApplicationContext();
        return START_STICKY;
    }
    
	@Override
	public void onStart(Intent intent, int startid) {
		Toast.makeText(this, "My Service Started", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onStart");
	}
	
	/**
	 * Service stop
	 * JD: add local device support
	 */
	public void onStop(){
		Toast.makeText(this, "My Service Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if(stemp.getStreamingStatus()){
				stemp.stopStreaming();
			}
			stemp.stop();
		}
		if(mLocalDevice!=null){
			mLocalDevice.destory();
			mLocalDevice = null;
		}
		mMultiShimmer.clear();
		mLogShimmer.clear();
	}
	
	/**
	 * Service stop
	 * JD: add local device support
	 */
	@Override
	public void onDestroy() {
		Toast.makeText(this, "My Service Stopped", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDestroy");
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if(stemp.getStreamingStatus()){
				stemp.stopStreaming();
			}
			stemp.stop();
		}
		if(mLocalDevice!=null){
			mLocalDevice.destory();
			mLocalDevice = null;
		}
		mMultiShimmer.clear();
		mLogShimmer.clear();
		
	}
		
	/**
	 * JD: Connect to a selected device
	 * @param bluetoothAddress bluetooth address of the selected device
	 * @param selectedDevice name of the shimmer device
	 */
	public void connectShimmer(String bluetoothAddress,String selectedDevice){
		if(bluetoothAddress.equalsIgnoreCase(mLocalDeviceAddr)){
			if(mLocalDevice==null){
				Log.d("Shimmer","local Connection");
				mLocalDevice = new PhoneDevice(this,mHandler);
				mLocalDevice.connect();
			}
		}else{
			Log.d("Shimmer","net Connection");
			Shimmer shimmerDevice=new Shimmer(this, mHandler,selectedDevice,false);
			mMultiShimmer.remove(bluetoothAddress);
			if (mMultiShimmer.get(bluetoothAddress)==null){
				mMultiShimmer.put(bluetoothAddress,shimmerDevice); 
				((Shimmer) mMultiShimmer.get(bluetoothAddress)).connect(bluetoothAddress,"default");
			}
		}
	}
	
	/**
	 * JD: Disconnect to a selected device
	 * @param bluetoothAddress bluetooth address of the selected device
	 */
	public void disconnectShimmer(String bluetoothAddress){
		if(bluetoothAddress.equalsIgnoreCase(mLocalDeviceAddr)){
			if(mLocalDevice!=null){
				mLocalDevice.destory();
				mLocalDevice = null;
			}
			if(!closeAndRemoveFile(bluetoothAddress)) mLogShimmer.remove(mLocalDeviceAddr);
		}else{
			Collection<Object> colS=mMultiShimmer.values();
			Iterator<Object> iterator = colS.iterator();
			while (iterator.hasNext()) {
				Shimmer stemp=(Shimmer) iterator.next();
				if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
					stemp.stop();
				}
			}
			mLogShimmer.remove(bluetoothAddress);		
			mMultiShimmer.remove(bluetoothAddress);
		}
	}
	
	/**
	 * JD: Disconnect all devices
	 */
	public void disconnectAllDevices(){
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if(stemp.getStreamingStatus()){
				stemp.stopStreaming();
			}
			stemp.stop();
		}
		if(mLocalDevice!=null){
			mLocalDevice.destory();
			mLocalDevice = null;
		}
		mMultiShimmer.clear();
		mLogShimmer.clear();
	}
	
	/**
	 * JD: check how many devices are currently connected
	 * @return number of connected devices
	 */
	public int getDevicesConnectedCount(){
		int count = 0;
		if(mLocalDevice!=null) count++;
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED){
				count++;
			}
		}
		return count;
	}
	
	/**
	 * JD: check if a device is connected
	 * @param bluetoothAddress address of device
	 * @return true if connected
	 */
	public boolean isDevicesConnected(String bluetoothAddress){
		boolean deviceConnected=false;
		if(bluetoothAddress.equalsIgnoreCase(mLocalDeviceAddr)){
			return (mLocalDevice==null)?false:true;
		}
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				deviceConnected=true;
			}
		}
		return deviceConnected;
	}
	
	/**
	 * JD: start streaming on selected device
	 * @param bluetoothAddress bluetooth address of selected device
	 */
	public void startStreaming(String bluetoothAddress) {
		if(bluetoothAddress.equalsIgnoreCase(mLocalDeviceAddr)){
			if((mLocalDevice!=null)&&(mLocalDevice.getPhoneState()==Shimmer.MSG_STATE_FULLY_INITIALIZED)){
				mLocalDevice.startStreaming();
			}
		}else{
			Collection<Object> colS=mMultiShimmer.values();
			Iterator<Object> iterator = colS.iterator();
			while (iterator.hasNext()) {
				Shimmer stemp=(Shimmer) iterator.next();
				if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
					stemp.startStreaming();
				}
			}
		}
	}
	
	/**
	 * JD: start streaming on selected device
	 * @param bluetoothAddress bluetooth address of selected device
	 */
	public void stopStreaming(String bluetoothAddress) {
		if(bluetoothAddress.equalsIgnoreCase(mLocalDeviceAddr)){
			if((mLocalDevice!=null)&&(mLocalDevice.getPhoneState()==Shimmer.MSG_STATE_STREAMING)){
				mLocalDevice.stopStreaming();
			}
		}else{
			Collection<Object> colS=mMultiShimmer.values();
			Iterator<Object> iterator = colS.iterator();
			while (iterator.hasNext()) {
				Shimmer stemp=(Shimmer) iterator.next();
				if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
					stemp.stopStreaming();
				}
			}
		}
	}
	
	/**
	 * JD: start streaming on all devices
	 */
	public void startStreamingAllDevices() {
		if((mLocalDevice!=null)&&(mLocalDevice.getPhoneState()==Shimmer.MSG_STATE_FULLY_INITIALIZED)){
			mLocalDevice.startStreaming();
		}
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED){
				stemp.startStreaming();
			}
		}
	}
	
	/**
	 * JD: stop streaming on all devices
	 */
    public void stopStreamingAllDevices() {
    	if((mLocalDevice!=null)&&(mLocalDevice.getPhoneState()==Shimmer.MSG_STATE_STREAMING)){
			mLocalDevice.stopStreaming();
		}
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED){
				stemp.stopStreaming();
			}
		}
    }
	
	/**
	 * JD: check how many devices are currently streaming
	 * @return number of streaming devices
	 */
	public int getDevicesStreamingCount(){
		int count = 0;
		if((mLocalDevice!=null)&&(mLocalDevice.getPhoneState()==Shimmer.MSG_STATE_STREAMING)) count++;
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getStreamingStatus() == true){
				count++;
			}
		}
		return count;
	}
	
	/**
	 * JD: check if a device is streaming
	 * @param bluetoothAddress address of device
	 * @return true if streaming
	 */
	public boolean isDeviceStreaming(String bluetoothAddress){
		boolean deviceStreaming=false;
		if(bluetoothAddress.equalsIgnoreCase(mLocalDeviceAddr)){
			deviceStreaming = (mLocalDevice!=null)?(mLocalDevice.getPhoneState()==Shimmer.MSG_STATE_STREAMING):false;
		}else{
			Collection<Object> colS=mMultiShimmer.values();
			Iterator<Object> iterator = colS.iterator();
			while (iterator.hasNext()) {
				Shimmer stemp=(Shimmer) iterator.next();
				if (stemp.getStreamingStatus() == true  && stemp.getBluetoothAddress().equals(bluetoothAddress)){
					deviceStreaming=true;
				}
			}
		}
		return deviceStreaming;
	}
    
	/**
	 * JD: Set a new alias for a connected device
	 * @param bluetoothAddress address of connected device
	 * @param newName new alias, if null a default name will be assigned
	 * @return 
	 */
	public boolean setDeviceName(String bluetoothAddress, String newName){
		String devName = (newName!=null)?newName:"default";
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED  && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.setDeviceName(devName);
			}
		}
		return true;
	}
	
	/**
	 * JD: get the alias for connected device
	 * @param bluetoothAddress address of connected device
	 * @return 
	 */
	public String getDeviceName(String bluetoothAddress){
		String devName = "default";
		if(bluetoothAddress.equalsIgnoreCase(mLocalDeviceAddr)){
			if(mLocalDevice!=null) devName = mLocalDevice.getDeviceName();
		}else{
			Collection<Object> colS=mMultiShimmer.values();
			Iterator<Object> iterator = colS.iterator();
			while (iterator.hasNext()) {
				Shimmer stemp=(Shimmer) iterator.next();
				if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED  && stemp.getBluetoothAddress().equals(bluetoothAddress)){
					devName = stemp.getDeviceName();
				}
			}
		}
		return devName;
	}

	/**
	 * JD: get status of selected device
	 * @param bluetoothAddress bluetooth address of selected device
	 * @return status of selected device
	 */
	public int getShimmerState(String bluetoothAddress){
		int status = -1;
		if(bluetoothAddress.equalsIgnoreCase(mLocalDeviceAddr)){
			if(mLocalDevice!=null) status = mLocalDevice.getPhoneState();
		}else{
			Collection<Object> colS=mMultiShimmer.values();
			Iterator<Object> iterator = colS.iterator();
			while (iterator.hasNext()) {
				Shimmer stemp=(Shimmer) iterator.next();
				if (stemp.getBluetoothAddress().equals(bluetoothAddress)){
					status = stemp.getShimmerState();
					Log.d("ShimmerState",Integer.toString(status));
				}
			}
		}
		return status;
	}
	
	/**
	 * JD: stop and close log files for selected devices, and update the UI
	 * @param bluetoothAddress selected device
	 * @return true if log file is closed, false otherwise
	 */
	public boolean closeAndRemoveFile(String bluetoothAddress){		
		Logging mLogger = mLogShimmer.get(bluetoothAddress);
		if (mEnableLogging==true && mLogger!=null){
			while(mLogger.getQueueSize()>0){
				Message msg = mHandlerGraph.obtainMessage(Shimmer.MESSAGE_STOP_STREAMING_COMPLETE);
		        Bundle bundle = new Bundle();
		        bundle.putBoolean("Streaming Stopped", false);
		        bundle.putString("Bluetooth Address", bluetoothAddress);
		        bundle.putInt("Queue Size Remaining", mLogger.getQueueSize());
		        msg.setData(bundle);
		        mHandlerGraph.sendMessage(msg);
			}
			mLogger.closeFile();
			mLogShimmer.remove(bluetoothAddress);
			/* DONE, inform UI */
			Message msg = mHandlerGraph.obtainMessage(Shimmer.MESSAGE_STOP_STREAMING_COMPLETE);
	        Bundle bundle = new Bundle();
	        bundle.putBoolean("Streaming Stopped", true);
	        bundle.putString("Bluetooth Address", bluetoothAddress);
	        msg.setData(bundle);
	        mHandlerGraph.sendMessage(msg);
			return true;
		}
		return false;
	}
	
	private class CloseFileTask extends AsyncTask<String, Void, Boolean>{
		@Override
		protected Boolean doInBackground(String... arg0) {
			return closeAndRemoveFile(arg0[0]);
		}
	}
	
	public void setEnableLogging(boolean enableLogging){
		mEnableLogging=enableLogging;
		Log.d("Shimmer","Logging :" + Boolean.toString(mEnableLogging));
	}
	
	public boolean getEnableLogging(){
		return mEnableLogging;
	}
	
	public void setLoggingName(String name){
		mLogFileName=name;
	}
	
	/**
	 * JD: set the device currently selected in UI
	 * @param bluetoothAddress address of selected device
	 */
	public void setOperatingDevice(String bluetoothAddress){
	    mOperatingDevice = bluetoothAddress;
	}
		
	public final Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) { // handlers have a what identifier which is used to identify the type of msg
				case Shimmer.MESSAGE_READ:
					if ((msg.obj instanceof ObjectCluster)){	// within each msg an object can be include, objectclusters are used to represent the data structure of the shimmer device
						ObjectCluster objectCluster =  (ObjectCluster) msg.obj; 
						if (mEnableLogging==true){
							shimmerLog1= (Logging)mLogShimmer.get(objectCluster.mBluetoothAddress);
							if (shimmerLog1!=null){
								shimmerLog1.appendData(objectCluster);
							} else {
								char[] bA=objectCluster.mBluetoothAddress.toCharArray();
								String logfileName;
		            			if (mLogFileName.equals("Default")){
		            				logfileName = Long.toString(System.currentTimeMillis())+
            								"_"+bA[12]+bA[13]+bA[15]+bA[16]+"_st";
		            			} else {
		            				logfileName = Long.toString(System.currentTimeMillis())+
            								"_"+mLogFileName;
		            			}
		            			Logging shimmerLog = new Logging(logfileName, ",", mContext);
		            			mLogShimmer.remove(objectCluster.mBluetoothAddress);
		            			if (mLogShimmer.get(objectCluster.mBluetoothAddress)==null){
		            				mLogShimmer.put(objectCluster.mBluetoothAddress,shimmerLog); 
		            			}
		            			shimmerLog.setStreamState(true);
							}
						}
						if (mGraphing==true){
							if(mOperatingDevice.equals(objectCluster.mBluetoothAddress)){
								Log.d("ShimmerGraph","Sending");
								mHandlerGraph.obtainMessage(Shimmer.MESSAGE_READ, objectCluster).sendToTarget();
							}
						} 
	            	}
	                break;
				case Shimmer.MESSAGE_TOAST:
					Log.d("toast",msg.getData().getString(Shimmer.TOAST));
					Toast.makeText(getApplicationContext(), msg.getData().getString(Shimmer.TOAST),Toast.LENGTH_SHORT).show();
					if (msg.getData().getString(Shimmer.TOAST).equals("Device connection was lost")){
					}
	                break;
				case Shimmer.MESSAGE_STATE_CHANGE:
					Log.d("ShimmerGraph","Sending");
					mHandlerGraph.obtainMessage(Shimmer.MESSAGE_STATE_CHANGE, msg.arg1, -1, msg.obj).sendToTarget();
					Intent intent = new Intent("com.shimmerresearch.service.ShimmerService");
					switch (msg.arg1) {
						case Shimmer.STATE_CONNECTED:
							Log.d("Shimmer",((ObjectCluster) msg.obj).mBluetoothAddress + "  " + ((ObjectCluster) msg.obj).mMyName);
							intent.putExtra("ShimmerBluetoothAddress", ((ObjectCluster) msg.obj).mBluetoothAddress );
	                    	intent.putExtra("ShimmerDeviceName", ((ObjectCluster) msg.obj).mMyName );
	                    	intent.putExtra("ShimmerState",Shimmer.STATE_CONNECTED);
	                    	sendBroadcast(intent);
	                        break;
	                    case Shimmer.STATE_CONNECTING:
	                    	intent.putExtra("ShimmerBluetoothAddress", ((ObjectCluster) msg.obj).mBluetoothAddress );
	                    	intent.putExtra("ShimmerDeviceName", ((ObjectCluster) msg.obj).mMyName );
	                    	intent.putExtra("ShimmerState",Shimmer.STATE_CONNECTING);	                        
	                    	break;
	                    case Shimmer.STATE_NONE:
	                    	intent.putExtra("ShimmerBluetoothAddress", ((ObjectCluster) msg.obj).mBluetoothAddress );
	                    	intent.putExtra("ShimmerDeviceName", ((ObjectCluster) msg.obj).mMyName );
	                    	intent.putExtra("ShimmerState",Shimmer.STATE_NONE);
	                    	sendBroadcast(intent);
	                    	break;
					}
	                break;
				case Shimmer.MESSAGE_STOP_STREAMING_COMPLETE:
					String address =  msg.getData().getString("Bluetooth Address");
					boolean stop  =  msg.getData().getBoolean("Stop Streaming");
					if (stop==true ){
						CloseFileTask closeTask = new CloseFileTask();
               		 	closeTask.execute(address);
					}
                	break;
			}
		}
	};
	
	public void setAllEnabledSensors(int enabledSensors) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED){
				stemp.writeEnabledSensors(enabledSensors);
			}
		}
	}
	
	public void setEnabledSensors(int enabledSensors,String bluetoothAddress) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.writeEnabledSensors(enabledSensors);
			}
		}
	}

	public int getEnabledSensors(String bluetoothAddress) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		int enabledSensors=0;
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				enabledSensors = stemp.getEnabledSensors();
			}
		}
		return enabledSensors;
	}
	
	public int sensorConflictCheckandCorrection(String bluetoothAddress, int enabledSensors, int sensorToCheck) {
		// TODO Auto-generated method stub
		int newSensorBitmap = 0;
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				newSensorBitmap = stemp.sensorConflictCheckandCorrection(enabledSensors,sensorToCheck);
			}
		}
		return newSensorBitmap;
	}
	
	public List<String> getListofEnabledSensors(String bluetoothAddress) {
		// TODO Auto-generated method stub
		List<String> listofSensors = null;
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				listofSensors = stemp.getListofEnabledSensors();
			}
		}
		return listofSensors;
	}
	
	public void toggleAllLEDS(){
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED){
				stemp.toggleLed();
			}
		}
	}
	
	public void toggleLED(String bluetoothAddress) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.toggleLed();
			}
		}
	}
	
	public void setBlinkLEDCMD(String bluetoothAddress) {
		// TODO Auto-generated method stub
		Shimmer stemp=(Shimmer) mMultiShimmer.get(bluetoothAddress);
		if (stemp!=null){
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				if (stemp.getCurrentLEDStatus()==0){
					stemp.writeLEDCommand(1);
				} else {
					stemp.writeLEDCommand(0);
				}
			}
		}
				
	}
	
	public void setAllSampingRate(double samplingRate) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED){
				stemp.writeSamplingRate(samplingRate);
			}
		}
	}

	public void writeSamplingRate(String bluetoothAddress,double samplingRate) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.writeSamplingRate(samplingRate);
			}
		}
	}
	
	public double getSamplingRate(String bluetoothAddress) {
		// TODO Auto-generated method stub
		
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		double SRate=-1;
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				SRate= stemp.getSamplingRate();
			}
		}
		return SRate;
	}
	
	public void setAllAccelRange(int accelRange) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED){
				stemp.writeAccelRange(accelRange);
			}
		}
	}
	
	public void writeAccelRange(String bluetoothAddress,int accelRange) {
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.writeAccelRange(accelRange);
			}
		}
	}
	
	public int getAccelRange(String bluetoothAddress) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		int aRange=-1;
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				aRange = stemp.getAccelRange();
			}
		}
		return aRange;
	}

	public void setAllGSRRange(int gsrRange) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED){
				stemp.writeGSRRange(gsrRange);
			}
		}
	}
	
	public void writeGSRRange(String bluetoothAddress,int gsrRange) {
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.writeGSRRange(gsrRange);
			}
		}
	}
	
	public int getGSRRange(String bluetoothAddress) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		int gRange=-1;
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				gRange = stemp.getGSRRange();
			}
		}
		return gRange;
	}
	
	public void writeGyroRange(String bluetoothAddress,int range) {
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.writeGyroRange(range);
			}
		}
	}

	public void writePressureResolution(String bluetoothAddress,int resolution) {
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				//currently not supported
				stemp.writePressureResolution(resolution);
			}
		}
	}
	
	public void writeMagRange(String bluetoothAddress,int range) {
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.writeMagRange(range);
			}
		}
	}
	
	public void writePMux(String bluetoothAddress,int setBit) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.writePMux(setBit);
			}
		}
	}
	
	public int getpmux(String bluetoothAddress) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		int pmux=-1;
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				pmux = stemp.getPMux();
			}
		}
		return pmux;
	}
	
	public void write5VReg(String bluetoothAddress,int setBit) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.writeFiveVoltReg(setBit);
			}
		}
	}

	public int get5VReg(String bluetoothAddress) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		int fiveVReg=-1;
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				fiveVReg = stemp.get5VReg();
			}
		}
		return fiveVReg;
	}
	
	public boolean isLowPowerMagEnabled(String bluetoothAddress) {
		// TODO Auto-generated method stub
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		boolean enabled=false;
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				enabled = stemp.isLowPowerMagEnabled();
			}
		}
		return enabled;
	}

	public void enableLowPowerMag(String bluetoothAddress,boolean enable) {
		// TODO Auto-generated method stub
		Shimmer stemp=(Shimmer) mMultiShimmer.get(bluetoothAddress);
		if (stemp!=null){
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.enableLowPowerMag(enable);
			}
		}		
	}

	public void setBattLimitWarning(String bluetoothAddress, double limit) {
		// TODO Auto-generated method stub
		Shimmer stemp=(Shimmer) mMultiShimmer.get(bluetoothAddress);
		if (stemp!=null){
			if (stemp.getBluetoothAddress().equals(bluetoothAddress)){
				stemp.setBattLimitWarning(limit);
			}
		}		
	
	}

	public double getBattLimitWarning(String bluetoothAddress) {
		// TODO Auto-generated method stub
		double limit=-1;
		Shimmer stemp=(Shimmer) mMultiShimmer.get(bluetoothAddress);
		if (stemp!=null){
			if (stemp.getBluetoothAddress().equals(bluetoothAddress)){
				limit=stemp.getBattLimitWarning();
			}
		}		
		return limit;
	}

	public double getPacketReceptionRate(String bluetoothAddress) {
		// TODO Auto-generated method stub
		double rate=-1;
		Shimmer stemp=(Shimmer) mMultiShimmer.get(bluetoothAddress);
		if (stemp!=null){
			if (stemp.getBluetoothAddress().equals(bluetoothAddress)){
				rate=stemp.getPacketReceptionRate();
			}
		}		
		return rate;
	}
	
	public void setGraphHandler(Handler handler){
		mHandlerGraph=handler;
	}
	
	public void enableGraphingHandler(boolean setting){
		mGraphing=setting;
	}
		
	public boolean GetInstructionStatus(String bluetoothAddress){
		boolean instructionStatus=false;
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getBluetoothAddress().equals(bluetoothAddress)){
				instructionStatus=stemp.getInstructionStatus();
			}
		}
		return instructionStatus;
	}
	
	public double getFWVersion (String bluetoothAddress){
		double version=0;
		Shimmer stemp=(Shimmer) mMultiShimmer.get(bluetoothAddress);
		if (stemp!=null){
			version=stemp.getFirmwareVersion();
		}
		return version;
	}
	
	public int getShimmerVersion (String bluetoothAddress){
		int version=0;
		Shimmer stemp=(Shimmer) mMultiShimmer.get(bluetoothAddress);
		if (stemp!=null){
			version=stemp.getShimmerVersion();
		}
		return version;
	}
	
	public Shimmer getShimmer(String bluetoothAddress){
		// TODO Auto-generated method stub
		Shimmer shimmer = null;
		Collection<Object> colS=mMultiShimmer.values();
		Iterator<Object> iterator = colS.iterator();
		while (iterator.hasNext()) {
			Shimmer stemp=(Shimmer) iterator.next();
			if (stemp.getShimmerState()==Shimmer.STATE_CONNECTED && stemp.getBluetoothAddress().equals(bluetoothAddress)){
				return stemp;
			}
		}
		return shimmer;
	}
	
	public void test(){
		Log.d("ShimmerTest","Test");
	}
}
