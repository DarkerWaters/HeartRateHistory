package uk.co.darkerwaters.heartrateanalyser.ble;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import uk.co.darkerwaters.heartrateanalyser.MainActivity;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;

@SuppressLint("SimpleDateFormat")
public abstract class BleConnectionHistoryStore<T> {
	/**
	 * an interface to allow the derived classes to create the store of the proper type
	 */
	public interface Provider<T> {
		BleConnectionHistoryStore<T> createNewStore(Context context);
	}
	/*********PRODUCTION SETTINGS ********/
	/** this is the key on which to consolidate, one file per day */
	public static SimpleDateFormat consolidationFormat = new SimpleDateFormat("yyyy-MM-dd");
	/** this is the movement for the time history, to align with the consolidation period */
	public static final int K_TIMEHISTORYMOVEMENT = Calendar.DATE;
	/** the number of historic files to keep, just keep the last 30 days */
	public static final int K_MAXHISTORICFILES = 30;
	/*********TESTING SETTINGS ********/
	/** this is the key on which to consolidate, one file per minute */
//	public static SimpleDateFormat consolidationFormat = new SimpleDateFormat("yyyy-MM-dd-hhmm");
	/** this is the movement for the time history, to align with the consolidation period */
//	public static final int K_TIMEHISTORYMOVEMENT = Calendar.MINUTE;
	/** the number of historic files to keep, just keep the last 10 minutes */
//	public static final int K_MAXHISTORICFILES = 10;
	/** the interval at which we will save files for the heck of it */
	private static final long K_SAVEINTERVAL = 300000; /** five minutes */
	/** the separator to use in the filename */
	public static final String filePrefixSep = "--";
	/** this is the context that created this store, for access to android things, like files */
	private final Context context;
	/** this is the current history store in use now */
	private BleConnectionHistory<T> currentHistory;
	/** the last seen item of data */
	private T lastData = null;
	/** the last time we saved a file for the heck of it - incase there is a crash */
	private long lastSavePerformed;
	/**
	 * an interface to a class that will package data in this store into a series of data bins
	 */
	public interface StorePackager<T> {
		public T dataFromString(String data);
		public int getNoBins();
		public int getBinIndex(T data);
		public String getBinName(int binIndex);
		public String getFilePrefix();
		public int getBinColour(int binIndex);
	}
	/** member to perform the consolidation binning task for the data */
	private final StorePackager<T> packager;
	/** the list of historic collections we are to keep / show */
	private final ArrayList<BleConnectionHistory<T>> historicStore;
	/**
	 * constructor
	 * @param context
	 * @param packager
	 */
	public BleConnectionHistoryStore(Context context, StorePackager<T> packager) {
		// set the members
		this.context = context;
		this.packager = packager;
		this.currentHistory = null;
		this.historicStore = new ArrayList<BleConnectionHistory<T>>(K_MAXHISTORICFILES);
		// try to load all the files from the device at this point
		loadHistories();
		this.lastSavePerformed = System.currentTimeMillis();
	}
	/**
	 * @return the store packager implementation
	 */
	public StorePackager<T> getPackager() {
		return this.packager;
	}
	/**
	 * @return the last seen value - null if nothing
	 */
	public T getLastData() {
		return this.lastData;
	}
	/**
	 * helper
	 * @return the oldest date we want to store data for in this store
	 */
	private Date getOldestPermissableDate() {
		// get now as a calendar
		Calendar now = Calendar.getInstance();
		// move the "now" time back the number of movements into the past
		now.add(K_TIMEHISTORYMOVEMENT, -1 * K_MAXHISTORICFILES);
		// this is the oldest permissable date we want to keep
		return now.getTime();
	}
	/**
	 * helper to load the history files on creation of this store
	 */
	private void loadHistories(){
		File filesDir = this.context.getFilesDir();
		if (null == filesDir) {
			// no files dir, probably in edit demo mode, fine
			return;
		}
		Date oldest = getOldestPermissableDate();
		// now go through all the files and load up the data
		for (File file : filesDir.listFiles()) {
			// for each file, load it if the date is to be summarized
			Date fileDate = BleConnectionHistory.getDateOfFile(this.packager, file);
			if (null == fileDate) {
				// this is not a valid file, ignore it
			}
			else if (fileDate.before(oldest)) {
				// this is out-of-date, remove this file
				if (false == file.delete()) {
					Log.e(MainActivity.TAG, "Failed to delete the out-of-date consolidated file: " + file.getName());
				}
				else {
					Log.i(MainActivity.TAG, "Deleted the out-of-date consolidated file: " + file.getName());
				}
				// and just move on to the next file
			}
			else {
				// OK then, this is a file we want, load it up
				BleConnectionHistory<T> history = new BleConnectionHistory<T>(fileDate, this.context, this);
				BleConnectionHistory<T> existingData = getHistoryData(history.getFileDateKey());
				if (null == existingData) {
					// and add to our list
					synchronized (this.historicStore) {
						this.historicStore.add(history);
					}
					Log.i(MainActivity.TAG, "Loaded file: " + file.getName());
				}
				else {
					// merge this data
					existingData.addDataFrom(history);
					Log.i(MainActivity.TAG, "Merged file: " + file.getName());
				}
			}
		}
		synchronized (this.historicStore) {
			// we want the list to be sorted so we can just pop the oldest each time
			Collections.sort(this.historicStore);
		}
	}
	
	public BleConnectionHistory<T> getHistoryData(Date date) {
		String fileDateKey = consolidationFormat.format(new Date());
		return getHistoryData(fileDateKey);
	}
	
	public BleConnectionHistory<T> getHistoryData(String fileDateKey) {
		BleConnectionHistory<T> toReturn = null;
		synchronized (this.historicStore) {
			for (BleConnectionHistory<T> history : this.historicStore) {
				if (history.getFileDateKey().equals(fileDateKey)) {
					toReturn = history;
					break;
				}
			}
		}
		if (null == toReturn) {
			Log.i(MainActivity.TAG, "No history found for " + fileDateKey);
		}
		return toReturn;
	}
	
	public String[] getHistoricFileDates() {
		String[] dateKeys;
		synchronized (this.historicStore) {
			dateKeys = new String[this.historicStore.size()];
			for (int i = 0; i < dateKeys.length; ++i) {
				dateKeys[i] = this.historicStore.get(i).getFileDateKey();
			}
		}
		return dateKeys;
	}

	public void storeData(T value, int frequency) {
		// store the latest data
		this.lastData = value;
		// so lets store this data, for "now", format the time to the nearest file time though
		String fileDateKey = consolidationFormat.format(new Date());
		if (null == this.currentHistory || false == this.currentHistory.getFileDateKey().equals(fileDateKey)) {
			// need to create a new history store
			try {
				/// try to get an existing one if there is one
				this.currentHistory = getHistoryData(fileDateKey);
				if (null == this.currentHistory) {
					// create the new history store
					this.currentHistory = new BleConnectionHistory<T>(consolidationFormat.parse(fileDateKey), this.context, this);
					// now go through our store and get rid of out-of-date ones and save used ones
					saveStoreContents(true);
					// add our new current history to the store
					synchronized (this.historicStore) {
						this.historicStore.add(this.currentHistory);
					}
				}
			} catch (ParseException e) {
				Log.e(MainActivity.TAG, "Failed to store data in the history file", e);
				return;
			}
		}
		//else the current history is valid so we can just use that
		this.currentHistory.addData(value, frequency);
		if (System.currentTimeMillis() - this.lastSavePerformed > K_SAVEINTERVAL) {
			// time to back things up to be safe
			saveStoreContents(true);
		}
	}
	
	private void saveStoreContents(boolean isRemoveOld) {
		// remember when we last saved this all
		this.lastSavePerformed = System.currentTimeMillis();
		// now save everything
		Date oldest = getOldestPermissableDate();
		ArrayList<BleConnectionHistory<T>> toRemove = new ArrayList<BleConnectionHistory<T>>();
		synchronized (this.historicStore) {
			for (BleConnectionHistory<T> history : this.historicStore) {
				// save the file if it changed
				if (history.isDirtyFromFile()) {
					// save this
					history.saveDataToFile(context);
				}
				try {
					Date fileDate = consolidationFormat.parse(history.getFileDateKey());
					if (fileDate.before(oldest)) {
						// this data is no longer required, bin it
						toRemove.add(history);					
					}
				} catch (ParseException e) {
					Log.e(MainActivity.TAG, "Failed to store data in the history file", e);
					return;
				}
			}
			if (isRemoveOld) {
				// remove all the out-of-date histories in the store
				for (BleConnectionHistory<T> history : toRemove) {
					this.historicStore.remove(history);
				}
			}
		}
	}
	
	public void closeStore() {
		saveStoreContents(false);
		synchronized (this.historicStore) {
			this.historicStore.clear();
		}
	}
	public abstract void handleGattData(BluetoothDevice device, BluetoothGattCharacteristic characteristic);
}
