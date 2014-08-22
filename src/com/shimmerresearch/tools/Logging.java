package com.shimmerresearch.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.google.common.collect.Multimap;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;

public class Logging {
	private static final String TAG = "SHIMMERLOG"; 
	
	boolean mFirstWrite=true;
	String[] mSensorNames;
	String[] mSensorFormats;
	String[] mSensorUnits;
	
	private Context mContext;
	private boolean isStreaming = false;
	private BlockingQueue<ObjectCluster> bq;
	private Thread mWorkerThread;
	private LogWriter mLogWriter;
	private String mFileName="";
	private String mDelimiter=","; //default is comma
	private BufferedWriter writer=null;
	private File outputFile;
	
	/**
	 * @param myName is the file name which will be used
	 */
	public Logging(String myName){
		mFileName=myName;
		File root = Environment.getExternalStorageDirectory();
		Log.d("AbsolutePath", root.getAbsolutePath());
		outputFile = new File(root, mFileName+".dat");
	}
	
	public Logging(String myName,String delimiter){
		mFileName=myName;
		mDelimiter=delimiter;
		File root = Environment.getExternalStorageDirectory();
		Log.d("AbsolutePath", root.getAbsolutePath());
		outputFile = new File(root, mFileName+".dat");
	}
		
	/**
	 * @param myName
	 * @param delimiter
	 * @param folderName will create a new folder if it does not exist
	 */
	public Logging(String myName,String delimiter, String folderName){
		mFileName=myName;
		mDelimiter=delimiter;

		 File root = new File(Environment.getExternalStorageDirectory() + "/"+folderName);

		   if(!root.exists())
		    {
		        if(root.mkdir()); //directory is created;
		    }
		   outputFile = new File(root, mFileName+".dat");
	}
	
	/**
	 * JD: Constructor
	 * @param myName filename for this recording
	 * @param delimiter delimiter for log file
	 * @param aContext application context
	 */
	public Logging(String myName, String delimiter, Context aContext){
		mFileName = myName;
		mDelimiter = delimiter;
		mContext = aContext;
		
		/* Log file */
		File root = new File(mContext.getExternalFilesDir(null).getAbsolutePath());
		if(!root.exists()){
			if(root.mkdir()); //directory is created;
		}
		outputFile = new File(root, mFileName+".txt");
		
		/* packet processing thread */
		bq = new LinkedBlockingQueue<ObjectCluster>();
		mLogWriter = new LogWriter();
		//mWorkerThread = new Thread(mLogWriter);
		//mWorkerThread.start();
	}
	
	/**
	 * JD: Set current streaming state
	 * @param streaming true if is streaming
	 */
	public synchronized void setStreamState(boolean streaming){
		isStreaming = streaming;
	}
	
	
	public String getName(){
		return mFileName;
	}
	
	public String getAbsoluteName(){
		return outputFile.getAbsolutePath();
	}
	
	/**
	 * JD: get delimiter
	 * @return delimiter
	 */
	public String getDelimiter(){
		return mDelimiter;
	}
	
	/**
	 * JD: get log queue size
	 * @return log queue size
	 */
	public int getQueueSize(){
		checkWorkerThread();
		return bq.size();
	}
	
	/**
	 * This function takes an object cluster and logs all the data within it. User should note that the function will write over prior files with the same name.
	 * @param objectClusterLog data which will be written into the file
	 */
	public void logData(ObjectCluster objectCluster){
		ObjectCluster objectClusterLog = objectCluster;
		if(objectCluster.mMyName.contains("WIFI")){
			try{
				if (mFirstWrite==true) {
					writer = new BufferedWriter(new FileWriter(outputFile,false));
					mFirstWrite=false;
				}
				String results[] = objectCluster.mMyName.split("_");
				writer.write(results[2]);
			}catch (IOException e){
				e.printStackTrace();
				Log.e(TAG,"Error with bufferedwriter");
			}
		}else{
			/* write ordered version */
			try {
				if (mFirstWrite==true) {
					//writer = new BufferedWriter(new FileWriter(outputFile,true));
					//First retrieve all the unique keys from the objectClusterLog
					Multimap<String, FormatCluster> m = objectClusterLog.mPropertyCluster;
					int size = m.size();
					System.out.print(size);
					mSensorNames=new String[size];
					mSensorFormats=new String[size];
					mSensorUnits=new String[size];
					int i=0;
					int p=0;
					for(String key : m.keys()) {
						//first check that there are no repeat entries
						if(compareStringArray(mSensorNames, key) == true) {
							for(FormatCluster formatCluster : m.get(key)) {
								mSensorFormats[p]=formatCluster.mFormat;
								mSensorUnits[p]=formatCluster.mUnits;
								//Log.d("Shimmer",key + " " + mSensorFormats[p] + " " + mSensorUnits[p]);
								p++;
							}
						}	
						mSensorNames[i]=key;
						i++;				 
					}
					// write header to a file
					writer = new BufferedWriter(new FileWriter(outputFile,false));
					String header = "#Time(mSecs)"+mDelimiter+
							"AcceX(m/(sec^2))"+mDelimiter+"AcceY(m/(sec^2))"+mDelimiter+"AcceZ(m/(sec^2))"+mDelimiter+
							"MagnX"+mDelimiter+"MagnY"+mDelimiter+"MagnZ"+mDelimiter+
							"GyroX(deg/sec)"+mDelimiter+"GyroY(deg/sec)"+mDelimiter+"GyroZ(deg/sec)"+mDelimiter+
							"Baro(hPa)"+mDelimiter+"COSMED"+mDelimiter+"Anno"+"\n";
					writer.write(header);
					Log.d(TAG,"Header Written");
					mFirstWrite=false;
				}
			
				//now print data
				String timestamp = "";
				String data[] = new String[10];
				for (int r=0;r<mSensorNames.length;r++) {
					Collection<FormatCluster> dataFormats = objectClusterLog.mPropertyCluster.get(mSensorNames[r]);  
					FormatCluster formatCluster = (FormatCluster) returnFormatCluster(dataFormats,mSensorFormats[r],mSensorUnits[r]);  // retrieve the calibrated data
					//Log.d(TAG,"Data : " +mSensorNames[r] + formatCluster.mData + " "+ formatCluster.mUnits);
					//TODO: clear data buffer? 
					if(mSensorFormats[r].contains("CAL")){
						if(mSensorNames[r].contains("Time")||mSensorNames[r].contains("Timestamp")){
							timestamp = Long.toString((long)formatCluster.mData);
						}else if(mSensorNames[r].contains("AcceX")||mSensorNames[r].contains("Accelerometer X")){
							data[0] = formatfloat(formatCluster.mData);
						}else if(mSensorNames[r].contains("AcceY")||mSensorNames[r].contains("Accelerometer Y")){
							data[1] = formatfloat(formatCluster.mData);
						}else if(mSensorNames[r].contains("AcceZ")||mSensorNames[r].contains("Accelerometer Z")){
							data[2] = formatfloat(formatCluster.mData);
						}else if(mSensorNames[r].contains("MagnX")||mSensorNames[r].contains("Magnetometer X")){
							data[3] = formatfloat(formatCluster.mData);
						}else if(mSensorNames[r].contains("MagnY")||mSensorNames[r].contains("Magnetometer Y")){
							data[4] = formatfloat(formatCluster.mData);
						}else if(mSensorNames[r].contains("MagnZ")||mSensorNames[r].contains("Magnetometer Z")){
							data[5] = formatfloat(formatCluster.mData);
						}else if(mSensorNames[r].contains("GyroX")||mSensorNames[r].contains("Gyroscope X")){
							data[6] = formatfloat(formatCluster.mData);
						}else if(mSensorNames[r].contains("GyroY")||mSensorNames[r].contains("Gyroscope Y")){
							data[7] = formatfloat(formatCluster.mData);
						}else if(mSensorNames[r].contains("GyroZ")||mSensorNames[r].contains("Gyroscope Z")){
							data[8] = formatfloat(formatCluster.mData);
						}else if(mSensorNames[r].contains("Baro")){
							data[9] = formatfloat(formatCluster.mData);
						}
					}
				}
				String value = timestamp+mDelimiter+
						data[0]+mDelimiter+data[1]+mDelimiter+data[2]+mDelimiter+
						data[3]+mDelimiter+data[4]+mDelimiter+data[5]+mDelimiter+
						data[6]+mDelimiter+data[7]+mDelimiter+data[8]+mDelimiter+
						data[9]+mDelimiter+0+mDelimiter+1+"\n";
				writer.write(value);
			}catch (IOException e){
				e.printStackTrace();
				Log.e(TAG,"Error with bufferedwriter");
			}
		}
	}
	
	/**
	 * JD: A wrapper function that adds incoming packets into processing queue
	 * @param objectCluster new packet
	 */
	public void appendData(ObjectCluster objectCluster){
		try{
			bq.put(objectCluster);
			checkWorkerThread();
//			Log.d(TAG, "append to queue: "+bq.size());
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Check if worker thread is working properly
	 */
	private void checkWorkerThread(){
		/* in case the worker thread is accidentally killed, restart it */
		if((isStreaming)&&((mWorkerThread==null)||(!mWorkerThread.isAlive()))){
			Log.d(TAG, "Worker thread is gone!");
			if(mWorkerThread==null) mWorkerThread = new Thread(mLogWriter);
			if(!mWorkerThread.isAlive()) mWorkerThread.start();
		}
	}
	
	/**
	 * JD: close log queue and files 
	 */
	public void closeFile(){
		mWorkerThread.interrupt();
		bq.clear();
		if (writer != null){
			try {
				writer.flush();
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * JD: Helper function formats float point values to two decimals
	 * @param value float point values
	 * @return formatted values
	 */
	public static String formatfloat(double value){
		DecimalFormat df = new DecimalFormat("###.##");
		return df.format(value);
	}
	
	private class LogWriter implements Runnable{				
		@Override
		public void run() {
			try{
				while(isStreaming|!bq.isEmpty()){
					Log.d(TAG, "write data from queue "+bq.size());
					logData(bq.take());
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	private boolean compareStringArray(String[] stringArray, String string){
		boolean uniqueString=true;
		int size = stringArray.length;
		for (int i=0;i<size;i++){
			if (stringArray[i]==string){
				uniqueString=false;
			}			
		}
		return uniqueString;
	}
	
	private FormatCluster returnFormatCluster(Collection<FormatCluster> collectionFormatCluster, String format, String units){
	   	Iterator<FormatCluster> iFormatCluster=collectionFormatCluster.iterator();
	   	FormatCluster formatCluster;
	   	FormatCluster returnFormatCluster = null;
	   	
	   	while(iFormatCluster.hasNext()){
	   		formatCluster=(FormatCluster)iFormatCluster.next();
	   		if (formatCluster.mFormat==format && formatCluster.mUnits==units){
	   			returnFormatCluster=formatCluster;
	   		}
	   	}
		return returnFormatCluster;
	}
}


