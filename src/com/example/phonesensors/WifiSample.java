package com.example.phonesensors;

public class WifiSample {
	public static final String INVALID_BSSID = "FF:FF:FF:FF:FF:FF";	// dummy AP
	public static final int INVALID_RSSI = -95;						// dummy RSSI
	public static final int INVALID_FREQ = 0;
	
	private String mBSSID;	// AP BSSID
	private int mRSSI;		// RSSI 
	private int mFreq;
	private int mCount;		// appearance count
	
	public WifiSample(String aBSSID, int aRSSI, int aFreq){
		mBSSID = aBSSID;
		mRSSI = aRSSI;
		mFreq = aFreq;
		mCount = 1;
	}
	
	public String getBSSID(){
		return mBSSID;
	}
	
	public void setBSSID(String aBSSID){
		mBSSID = aBSSID;
	}

	public int getRSSI(){
		return mRSSI;
	}
	
	public void setRSSI(int aRSSI){
		mRSSI = aRSSI;
	}

	public int getFreq(){
		return mFreq;
	}
	
	public void setFreq(int aFreq){
		mFreq = aFreq;
	}
	
	public int getCount(){
		return mCount;
	}
	
	public void setCount(int aCount){
		mCount = aCount;
	}
	
	public String println(){
		return "$\t"+mBSSID+"\t"+mRSSI+"\t"+mCount+"\t"+mFreq+"\n";
	}
}
