package com.example.phonesensors;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import com.shimmerresearch.tools.Logging;

public class WifiFingerprint {
	private long mTime;							// time stamp
	private long mTimeRcvd;
	private double mCoordinate[];				// location coordinate
	private ArrayList<WifiSample> mObservation;	// WiFi observation
	private int mManuMkr;						// manual marker
	private int mAutoMkr;						// automatic marker
	private int mSensorIdx; 					// latest sensor index in sensor trace when current observation is recorded	
	private String mTraceId;

	/**
	 * Constructor
	 * @param aTime time stamp
	 */
	public WifiFingerprint(long aTime){
		mTime = aTime;
		mCoordinate = new double[]{-1,-1};
		mObservation = new ArrayList<WifiSample>();
		mManuMkr = 0;
		mAutoMkr = 0;
		mSensorIdx = -1;
		mTraceId = "null";
	}
	
	/**
	 * Get observation record time
	 * @return observation record time
	 */
	public long getTime(){
		return mTime;
	}
	
	public void setTimeRCVD(long aTimeRcvd){
		mTimeRcvd = aTimeRcvd;
	}
	
	/**
	 * Get observation received time 
	 * @return observation received time
	 */
	public long getTimeRcvd(){
		return mTimeRcvd;
	}
	
	/**
	 * Get location of this fingerprint
	 * @return coordinate of current location
	 */
	public double[] getCoordinate(){
		return new double[]{mCoordinate[0], mCoordinate[1]};
	}
	
	/**
	 * Set location to specified coordinate
	 * @param aCoordinate coordinate of current location
	 */
	public void setCoordinate(double aCoordinate[]){
		mCoordinate[0] = aCoordinate[0];
		mCoordinate[1] = aCoordinate[1];
	}
	
	/**
	 * Get manual marker index of this fingerprint
	 * @return manual marker index
	 */
	public int getManuMkr(){
		return mManuMkr;
	}
	
	/**
	 * Mark this fingerprint as a manual marker and assign an index to it
	 * @param aManuMkr new manual marker index
	 */
	public void setManuMkr(int aManuMkr){
		mManuMkr = aManuMkr;
	}
	
	/**
	 * Get automatic marker index of this fingerprint
	 * @return automatic marker index
	 */
	public int getAutoMkr(){
		return mAutoMkr;
	}
	
	/**
	 * Mark this fingerprint as a automatic marker and assign an index to it
	 * @param aAutoMkr new automatic marker index
	 */
	public void setAutoMkr(int aAutoMkr){
		mAutoMkr = aAutoMkr;
	}
	
	/**
	 * Get the sensor index range between current and previous WiFi fingerprint
	 * @return sensor index range
	 */
	public int getSensorIdx(){
		return mSensorIdx;
	}
	
	/**
	 * Set sensor index range
	 * @param aSensorIdx index range 
	 */
	public void setSensorIdx(int aSensorIdx){
		mSensorIdx = aSensorIdx;
	}
	
	/**
	 * Get the trace index 
	 * @return trace index
	 */
	public String getTraceId(){
		return mTraceId;
	}
	
	/**
	 * Set trace index
	 * @param aTraceIdx trace index 
	 */
	public void setTraceId(String aTraceId){
		mTraceId = aTraceId;
	}
	
	/**
	 * Get a copy of the WiFi observation 
	 * @return WiFi observation
	 */
	public ArrayList<WifiSample> getObs(){
		return new ArrayList<WifiSample>(mObservation);
	}
	
	/**
	 * Add a WiFi sample to the end of observation list
	 * @param aSample new WiFi sample
	 */
	public void appendObs(WifiSample aSample){
		mObservation.add(aSample);
	}
	
	/**
	 * Insert/update a WiFi sample into observation 
	 * in ascending order in terms of BSSID
	 * @param aBSSID BSSID of new WiFi sample
	 * @param aRSSI RSSI of new WiFi sample
	 */
	public void insertObs(String aBSSID, int aRSSI, int aFreq){
		int pos; 
		int strcmp = -1;
		for(pos = 0; pos < mObservation.size(); pos++){
			strcmp = aBSSID.compareToIgnoreCase(mObservation.get(pos).getBSSID());
			if(strcmp<=0){
				/* new AP has smaller or same address? at the right position */
				break;
			}else{
				/* new AP has larger address? move to next */
			}
		}
		if(strcmp==0){
			/* sample from same AP exists, update average RSSI */
			WifiSample curr = mObservation.get(pos);
			int avgRSSI = (curr.getRSSI()*curr.getCount()+aRSSI)/(curr.getCount()+1);
			curr.setRSSI(avgRSSI);
			curr.setCount(curr.getCount()+1);
		}else{
			/* smaller address, insert to current position */
			mObservation.add(pos, new WifiSample(aBSSID, aRSSI, aFreq));
		}
	}
	
	/**
	 * Merge WiFi samples that may come from virtual APs of the same physical AP, 
	 * If two sample in same band 
	 * with bssids only differ at the last digit, 
	 * and the difference is within 3,
	 * they are considered as virtual APs from same physical AP
	 * @param obs specified sorted observation; null if obs for current fingerprint is merged
	 */
	public void mergeObs(ArrayList<WifiSample> obs){
		int idx1, idx2, cmp;
		WifiSample sample1, sample2;
		if(obs==null) obs = mObservation;
		
		idx1 = 0;
		while(idx1 < (obs.size()-1)){
			idx2 = idx1+1;
			sample1 = obs.get(idx1);
			sample2 = obs.get(idx2);
			cmp = sample1.getBSSID().substring(0, 16).
					compareToIgnoreCase(sample2.getBSSID().substring(0, 16));
			if(cmp == 0){
				if(Math.abs(Integer.parseInt(sample1.getBSSID().substring(15, 17),16)-
						Integer.parseInt(sample2.getBSSID().substring(15, 17),16))<3){
					if(Math.abs(sample1.getFreq()-sample2.getFreq())<1000){
						sample1.setRSSI((sample1.getRSSI()+sample2.getRSSI())/2);
						sample1.setCount(sample1.getCount()+sample2.getCount());
						obs.remove(idx2);
					}else{
						idx1++;
					}
				}else{
					idx1++;
				}
			}else{
				idx1++;
			}
		}
	}
	
	/**
	 * Convert iwscan result into internal fingerprint format
	 * @param results iwscan result
	 */
	public void addScanObs(String results){
		String scanline[] = results.split("\n");
		String bss = WifiSample.INVALID_BSSID;
		int freq = WifiSample.INVALID_FREQ;
		int rssi = WifiSample.INVALID_RSSI;
		for(int i = 0; i < scanline.length; i++){
			if(scanline[i].startsWith("BSS")){
				bss = scanline[i].substring(4, 21);
			}
			if(scanline[i].contains("freq:")){
				String freqbrk[] = scanline[i].split(" ");
				freq = Integer.parseInt(freqbrk[1]);
			}
			if(scanline[i].contains("signal")){
				String signalbrk[] = scanline[i].split(" ");
				rssi = (int)Double.parseDouble(signalbrk[1]);
				if((bss!=WifiSample.INVALID_BSSID)&&(freq!=WifiSample.INVALID_FREQ)&&(rssi!=WifiSample.INVALID_RSSI)){
					this.insertObs(bss, rssi, freq);
				}
				bss = WifiSample.INVALID_BSSID;
				freq = WifiSample.INVALID_FREQ;
				rssi = WifiSample.INVALID_RSSI;
			}
		}
	}
	
	/**
	 * Merge observation of current fingerprint with that of another fingerprint. 
	 * It is to combine consecutive fingerprints for obtaining the comprehensive list. 
	 * Properties, such as time and location, will not change for current fingerprint
	 * @param nextFgpt fingerprint to be merged 
	 */
	public void mergeFgpts(WifiFingerprint nextFgpt){
		int idx1, idx2, cmp; 
		ArrayList<WifiSample> obs1 = mObservation;
		ArrayList<WifiSample> obs2 = nextFgpt.getObs();
		ArrayList<WifiSample> newObs = new ArrayList<WifiSample>();
		WifiSample sample1, sample2, newSample;
		WifiSample sentinel = 
				new WifiSample(WifiSample.INVALID_BSSID, WifiSample.INVALID_RSSI, WifiSample.INVALID_FREQ);
		
		idx1 = idx2 = 0;
		while((idx1<obs1.size())||(idx2<obs2.size())){
			sample1 = (idx1<obs1.size())?obs1.get(idx1):sentinel;
			sample2 = (idx2<obs2.size())?obs2.get(idx2):sentinel;
			/* MUST use compareToIgnoreCase since sentinel starts with 
			 * "FF" which is always smaller than lower case */
			cmp = sample1.getBSSID().compareToIgnoreCase(sample2.getBSSID());
			if(cmp == 0){
				newSample = new WifiSample(sample1.getBSSID(),
					(sample1.getRSSI()*sample1.getCount()+sample2.getRSSI()*sample2.getCount())/
					(sample1.getCount()+sample2.getCount()), sample1.getFreq());
				newSample.setCount(sample1.getCount()+sample2.getCount());		
				idx1++;
				idx2++;
			}else if(cmp < 0){
				newSample = new WifiSample(sample1.getBSSID(),sample1.getRSSI(), sample1.getFreq());
				newSample.setCount(sample1.getCount());
				idx1++;
			}else{
				/* use a new pointer all the time, 
				 * otherwise the merger at next fingerprint will be affected */
				newSample = new WifiSample(sample2.getBSSID(),sample2.getRSSI(), sample2.getFreq());
				newSample.setCount(sample2.getCount());
				idx2++;
			}
			newObs.add(newSample);
		}
		mergeObs(newObs);
		mObservation = newObs;
	}
	
	/**
	 * Calculate the signal distance from current fingerprint to another
	 * IMPORTANT: fingerprints must be merged first before being processed
	 * @param aFgpt target fingerprint
	 * @return signal distance from current fingerprint to aFgpt
	 */
	public double calcSignalDistEuclidean(WifiFingerprint aFgpt){
		int idx1, idx2, num, cmp; 
		double dist;
		ArrayList<WifiSample> obs1 = mObservation;
		ArrayList<WifiSample> obs2 = aFgpt.getObs();
		WifiSample sample1, sample2;
		WifiSample sentinel = 
				new WifiSample(WifiSample.INVALID_BSSID, WifiSample.INVALID_RSSI, WifiSample.INVALID_FREQ);
		
		dist = idx1 = idx2 = 0;
		num = 1;
		while((idx1<obs1.size())||(idx2<obs2.size())){
			sample1 = (idx1<obs1.size())?obs1.get(idx1):sentinel;
			sample2 = (idx2<obs2.size())?obs2.get(idx2):sentinel;
			/* MUST use compareToIgnoreCase since sentinel starts with 
			 * "FF" which is always smaller than lower case */
			cmp = sample1.getBSSID().substring(0, 16).
					compareToIgnoreCase(sample2.getBSSID().substring(0, 16));
			if(cmp == 0){
				/* fingerprints after merging may capture different virtual APs 
				 * to represent physical AP, sort out cases for vap */
				if((Math.abs(Integer.parseInt(sample1.getBSSID().substring(15, 17),16)-
						Integer.parseInt(sample2.getBSSID().substring(15, 17),16))<3)&&
						(Math.abs(sample1.getFreq()-sample2.getFreq())<1000)){
					dist += Math.pow(Math.abs(sample1.getRSSI()-sample2.getRSSI()), 2);
					idx1++;
					idx2++;
					num++;
				}else{
					cmp = sample1.getBSSID().compareToIgnoreCase(sample2.getBSSID());
					if(cmp < 0){
						dist += (sample1.getCount()/3)*
								Math.pow(Math.abs(sample1.getRSSI()-WifiSample.INVALID_RSSI), 2);
						idx1++;
					}else{
						dist += (sample2.getCount()/3)*
								Math.pow(Math.abs(sample2.getRSSI()-WifiSample.INVALID_RSSI), 2);
						idx2++;
					}
				}
			}else if(cmp < 0){
				dist += (sample1.getCount()/3)*
						Math.pow(Math.abs(sample1.getRSSI()-WifiSample.INVALID_RSSI), 2);
				idx1++;
			}else{
				dist += (sample2.getCount()/3)*
						Math.pow(Math.abs(sample2.getRSSI()-WifiSample.INVALID_RSSI), 2);
				idx2++;
			}
		}
		return Math.sqrt(dist/num);
	}
	
	/**
	 * Calculate the signal distance from current fingerprint to another
	 * IMPORTANT: fingerprints must be merged first before being processed
	 * @param aFgpt target fingerprint
	 * @return signal distance from current fingerprint to aFgpt
	 */
	public double calcSignalDist(WifiFingerprint aFgpt){
		int idx1, idx2, num, cmp; 
		double dist, dist1, dist2;
		ArrayList<WifiSample> obs1 = mObservation;
		ArrayList<WifiSample> obs2 = aFgpt.getObs();
		WifiSample sample1, sample2;
		WifiSample sentinel = 
				new WifiSample(WifiSample.INVALID_BSSID, WifiSample.INVALID_RSSI, WifiSample.INVALID_FREQ);
		
		dist2 = dist1 = dist = idx1 = idx2 = 0;
		num = 1;
		while((idx1<obs1.size())||(idx2<obs2.size())){
			sample1 = (idx1<obs1.size())?obs1.get(idx1):sentinel;
			sample2 = (idx2<obs2.size())?obs2.get(idx2):sentinel;
			/* MUST use compareToIgnoreCase since sentinel starts with 
			 * "FF" which is always smaller than lower case */
			cmp = sample1.getBSSID().substring(0, 16).
					compareToIgnoreCase(sample2.getBSSID().substring(0, 16));
			if(cmp == 0){
				/* fingerprints after merging may capture different virtual APs 
				 * to represent physical AP, sort out cases for vap */
				if((Math.abs(Integer.parseInt(sample1.getBSSID().substring(15, 17),16)-
						Integer.parseInt(sample2.getBSSID().substring(15, 17),16))<3)&&
						(Math.abs(sample1.getFreq()-sample2.getFreq())<1000)){
					dist += (sample1.getRSSI()-WifiSample.INVALID_RSSI)*(sample2.getRSSI()-WifiSample.INVALID_RSSI);
					idx1++;
					idx2++;
					num++;
				}else{
					cmp = sample1.getBSSID().compareToIgnoreCase(sample2.getBSSID());
					if(cmp < 0){
						idx1++;
					}else{
						idx2++;
					}
				}
			}else if(cmp < 0){
				idx1++;
			}else{
				idx2++;
			}
		}
		dist1 = calcSignalSumsq(obs1);
		dist2 = calcSignalSumsq(obs2);
		return 1-(dist/(dist1+dist2-dist));
	}
	
	public static double calcSignalSumsq(ArrayList<WifiSample> aObservation){
		double dist = 0;
		for(int i = 0; i < aObservation.size(); i++){
			dist += Math.pow((aObservation.get(i).getRSSI()-WifiSample.INVALID_RSSI),2);
		}
		return dist;
	}
	
	/**
	 * Calculate the 2D Euclidean distance from current fingerprint to another
	 * @param aFgpt target fingerprint
	 * @return Euclidean distance from current fingerprint to aFgpt
	 */
	public double calcGeoDist(WifiFingerprint aFgpt){
		double pos1[] = mCoordinate;
		double pos2[] = aFgpt.getCoordinate();
		return Math.sqrt(Math.pow(pos1[0]-pos2[0], 2)+Math.pow(pos1[1]-pos2[1], 2));
	}
	
	/**
	 * Print WiFi fingerprint
	 * @param complete 0 if short record is printed; 1 if complete record 
	 * @return WiFi fingerprint
	 */
	public String println(int complete){
		long duration = mTimeRcvd-mTime;
		String newline = "$$\t"+mTime+"\t"+duration+"\t"+
							Logging.formatfloat(mCoordinate[0])+"\t"+
							Logging.formatfloat(mCoordinate[1])+"\t"+
							mObservation.size()+"\t"+mSensorIdx+"\t"+
							mManuMkr+"\t"+mAutoMkr+"\t"+"\n";
		if(complete>0){
			for(int i = 0; i < mObservation.size(); i++){
				newline += mObservation.get(i).println();
			}
		}
		return newline;
	}
	
	public static void printTrace(String output, ArrayList<WifiFingerprint> trace, int detail){
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(output,true));
			String newFgpt = "";
			for(int i = 0; i < trace.size(); i++){
				newFgpt = trace.get(i).println(detail);
				writer.write(newFgpt);
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
