package uk.co.darkerwaters.heartrateanalyser.ble;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.LinkedList;

import uk.co.darkerwaters.heartrateanalyser.MainActivity;
import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionHistoryStore.StorePackager;
import android.content.Context;
import android.util.Log;

public class BleConnectionHistory<T> implements Comparable<BleConnectionHistory<T>> {
	/** current file version */
	private static final int K_VERSION = 1;
	/** the size of the recent memory to store */
	public static int K_MEMORYSPAN = 500;
	/** the store in which this is kept */
	private final BleConnectionHistoryStore<T> store;
	/** the time for which this history is created - start time */
	private Date dataTime;
	/** the key for the data time, nearest to interval for which data is stored */
	private String dataTimeKey;
	/** member to perform the consolidation binning task for the data */
	private final StorePackager<T> packager;
	/** if false all the data matches that in it's file, no need to save */
	private boolean isDirtyFromFile = false;
	/** this is a small memory of recent data entries */
	private final LinkedList<T> recentValues = new LinkedList<T>();
	/**
	 * small helper class to hold the bin frequency data
	 */
	private static class Bin {
		final String binName;
		int frequency;
		Bin(String binName) {
			this.binName = binName;
			this.frequency = 0;
		}
	}
	private final Bin[] dataBins;
	
	public BleConnectionHistory(Date dataTime, Context context, BleConnectionHistoryStore<T> store) {
		this.store = store;
		this.packager = store.getPackager();
		this.dataTime = dataTime;
		this.dataTimeKey = BleConnectionHistoryStore.consolidationFormat.format(this.dataTime);
		this.dataBins = new Bin[packager.getNoBins()];
		if (false == loadConsolidatedData(context)) {
			// there is no data to load, initialise the bins to be empty
			synchronized (this.dataBins) {
				for (int i = 0; i < this.dataBins.length; ++i) {
					// create the initial bin file
					this.dataBins[i] = new Bin(packager.getBinName(i));
				}
			}
			this.isDirtyFromFile = true;
		}
	}
	
	public void addDataFrom(BleConnectionHistory<T> data) {
		// add the data from the passed history to our data
		synchronized (this.dataBins) {
			for (int i = 0; i < this.dataBins.length && i < data.dataBins.length; ++i) {
				this.dataBins[i].frequency += data.dataBins[i].frequency;
			}
		}
	}

	public void clearAllHistoricData() {
		// clear all the binned historic data from this store
		synchronized (this.dataBins) {
			for (Bin bin : this.dataBins) {
				bin.frequency = 0;
			}
		}
		this.isDirtyFromFile = true;
	}
	
	@Override
	public int compareTo(BleConnectionHistory<T> another) {
		if (this.dataTime.after(another.dataTime)) {
			return 1;
		}
		else if (this.dataTime.before(another.dataTime)) {
			return -1;
		}
		else {
			return 0;
		}
	}
	
	public boolean isDirtyFromFile() {
		return this.isDirtyFromFile;
	}

	public String getFileDateKey() {
		return this.dataTimeKey;
	}

	public String getFilename() {
		return this.packager.getFilePrefix() + BleConnectionHistoryStore.filePrefixSep + this.dataTimeKey;
	}
	
	public int addData(T data, int frequency) {
		// add this data to the recent memory
		synchronized (this.recentValues) {
			this.recentValues.addLast(data);
			while (this.recentValues.size() > K_MEMORYSPAN) {
				this.recentValues.removeFirst();
			}
		}
		int newValue = -1;
		int binIndex = this.packager.getBinIndex(data);
		synchronized (this.dataBins) {
			if (binIndex >= 0 && binIndex < this.dataBins.length) {
				// this is valid, add this data
				this.dataBins[binIndex].frequency += frequency;
				newValue = this.dataBins[binIndex].frequency;
			}
		}
		this.isDirtyFromFile = true;
		return newValue;
	}
	
	public static Date getDateOfFile(StorePackager<?> packager, File file) {
		Date fileDate = null;
		if (file.exists() && file.isFile()) {
			String[] split = file.getName().split(BleConnectionHistoryStore.filePrefixSep);
			if (null == split || split.length != 2) {
				Log.e(MainActivity.TAG, "File does not have a date " + file.getName());
			}
			else if (split[0].equals(packager.getFilePrefix())) {
				// this is a consolidated file, should we still have this?
				try {
					fileDate = BleConnectionHistoryStore.consolidationFormat.parse(split[1]);
				}
				catch (Exception e) {
					// fine, just not a valid file is all...
				}
			}
		}
		return fileDate;
	}
	
	private boolean loadConsolidatedData(Context context) {
		// what would the filename for this time be?...
		String filename = getFilename();
		FileInputStream inputStream;
		char[] inputBuffer= new char[2048];
		boolean success = false;
		try {
			// open a stream to this file
			inputStream = context.openFileInput(filename);
			InputStreamReader inReader= new InputStreamReader(inputStream);
			// read in the data in nice blocks
			int charRead;
			StringBuilder builder = new StringBuilder();
			while ((charRead=inReader.read(inputBuffer)) > 0) {
				// while data is loaded, add to the builder
				String readstring=String.copyValueOf(inputBuffer,0,charRead);
				builder.append(readstring); 
			}
			inReader.close();
			inputStream.close();
			// create the data
			success = setDataFromFileContents(builder.toString());
		}
		catch (Exception e) {
			// fine, file not there is all...
		}
		return success;
	}
	
	private boolean setDataFromFileContents(String fileString) {
		// initialise the bins from the passed file string
		boolean isSuccess = false;
		int stringIndex = -1;
		try {
			String[] strings = fileString.split(",");
			// this is all the data in the file, get the version and load the data
			int version = Integer.parseInt(strings[++stringIndex]); 
			switch (version) {
			case 1 :
				// VERSION 1
				this.dataTime = BleConnectionHistoryStore.consolidationFormat.parse(strings[++stringIndex]);
				this.dataTimeKey = BleConnectionHistoryStore.consolidationFormat.format(this.dataTime);
				// the rest of the data is data in pairs, offset and data
				int binIndex = 0;
				synchronized (this.dataBins) {
					for (int i = ++stringIndex; i < strings.length && binIndex < this.dataBins.length; i += 2) {
						// create the data item and add to the list
						Bin dataBin = new Bin(strings[i]);
						dataBin.frequency = Integer.parseInt(strings[i + 1]);
						this.dataBins[binIndex++] = dataBin;
					}
				}
				isSuccess = true;
				break;
			default:
				Log.e(MainActivity.TAG, "unknown version number " + strings[0]);
				break;
			}
		} catch (Exception e) {
			Log.e(MainActivity.TAG, "Failed to get some data from the file at string " + stringIndex, e);
		}
		return isSuccess;
	}
	
	private String toFileString() {
		StringBuilder builder = new StringBuilder();
		builder.append(K_VERSION);
		builder.append(',');
		builder.append(BleConnectionHistoryStore.consolidationFormat.format(this.dataTime));
		builder.append(',');
		synchronized (this.dataBins) {
			for (Bin bin : this.dataBins) {
				builder.append(bin.binName);
				builder.append(",");
				builder.append(bin.frequency);
				builder.append(",");
			}
		}
		return builder.toString();
	}

	public boolean saveDataToFile(Context context) {
		boolean isSaved = false;
		FileOutputStream outputStream;
		try { 
			outputStream = context.openFileOutput(getFilename(), Context.MODE_PRIVATE);
			outputStream.write(toFileString().getBytes());
			outputStream.close();
			this.isDirtyFromFile = false;
			isSaved = true;
			Log.d(MainActivity.TAG, "Saved the file " + getFilename());
		} catch (Exception e) {
			Log.e(MainActivity.TAG, "Failed to store the file", e);
		}
		return isSaved;
	}
	
	@SuppressWarnings("unchecked")
	public LinkedList<T> getRecentValues() {
		synchronized (this.recentValues) {
			return (LinkedList<T>) this.recentValues.clone();
		}
	}

	public int getNoBins() {
		synchronized (this.dataBins) {
			return this.dataBins.length;
		}
	}
	
	public int getBinFrequency(int binIndex) {
		synchronized (this.dataBins) {
			return this.dataBins[binIndex].frequency;
		}
	}
	
	public String getBinName(int binIndex) {
		return this.packager.getBinName(binIndex);
	}

	public int getBinColour(int binIndex) {
		return this.packager.getBinColour(binIndex);
	}

	public int getBinIndex(T data) {
		return this.packager.getBinIndex(data);
	}

	public BleConnectionHistoryStore<T> getStore() {
		return this.store;
	}
}
