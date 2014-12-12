package uk.co.darkerwaters.heartrateanalyser.ble;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import uk.co.darkerwaters.heartrateanalyser.MainActivity;
import uk.co.darkerwaters.heartrateanalyser.R;
import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionService.ConnectionServiceListener;
import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionService.ConnectionState;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

public class HeartRateConnection implements ConnectionServiceListener {
	/** the device name to connect to */
	private String deviceName;
	/** the device address to connect to */
    private String deviceAddress;
    /** the connection service we can connect to for data */
    private BleConnectionService bleConnectionService = null;
    /** this is the current notification characteristic - heart-rate */
    private BluetoothGattCharacteristic currentNotifyCharacteristic;
    /** the defined UUID to which to connect */
    public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    /** the defined UUID to which to set to receive heart-rate data */
    public final static UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    /** preferences to use to get the device name and address to connect to */
	private SharedPreferences preferences;
	/** the main activity controlling this connection */
	private MainActivity mainActivity;
	/** listeners to changed in the connection */
	private final ArrayList<HeartRateListener> listeners = new ArrayList<HeartRateListener>();
	/**
	 * an interface to use to listen to the heart rate being received
	 */
	public interface HeartRateListener {
		/**
		 * override to display the heart rate data received
		 * @param deviceName 
		 * @param device
		 * @param connectionState
		 * @param heartRate
		 */
		public void displayData(String deviceName, BluetoothDevice device, ConnectionState connectionState, HeartRateDataStore data);
	}
	
    /** class to manage the service connection */
    private ServiceConnection serviceConnection = null; 
	/**
	 * constructor
	 * @param mainActivity is the parent activity
	 */
	public HeartRateConnection(MainActivity mainActivity) {
		// store the parent activity
		this.mainActivity = mainActivity;
        this.preferences = mainActivity.getSharedPreferences(mainActivity.getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        // load the name and address from the previous settings
        this.deviceName = this.preferences.getString(mainActivity.getString(R.string.saved_device_name), "");
        this.deviceAddress = this.preferences.getString(mainActivity.getString(R.string.saved_device_address), "");
        // fire up the service
        connectToService();
	}
	/**
	 * helper to connect to the service and connect the device
	 */
	private void connectToService() {
		// Calling startService() first prevents it from being killed on unbind()
		this.mainActivity.startService(new Intent(this.mainActivity, BleConnectionService.class));
		// Now connect to it
		this.serviceConnection = new ServiceConnection() {
	        @Override
	        public void onServiceConnected(ComponentName componentName, IBinder serviceBinder) {
	        	BleConnectionService service = ((BleConnectionService.LocalBinder)serviceBinder).getService();
	            if (false == service.initialiseService(mainActivity, new HeartRateDataStore.HeartRateStoreProvider())) {
	                Log.e(MainActivity.TAG, "Unable to initialize Bluetooth");
	            }
	            else {
	            	setService(service);
	            }
	            // Automatically connects to the device upon successful start-up initialization.
	            connectDevice(deviceName, deviceAddress);
	        }
	        @Override
	        public void onServiceDisconnected(ComponentName componentName) {
	        	setService(null);
	        }
	    };
	    // bind to our service with the created connection
		boolean result = this.mainActivity.bindService(
				new Intent(this.mainActivity, BleConnectionService.class),
				this.serviceConnection, Context.BIND_AUTO_CREATE);
		if (false == result) {
			// this is serious - throw
			throw new RuntimeException("Unable to bind with service.");
		}
	}
	/**
	 * wrapped in a function so the pointer cannot become null half way through using it
	 * @param service is the service to use, can be null
	 */
	protected void setService(BleConnectionService service) {
		synchronized (this) {
			if (null != this.bleConnectionService) {
				// remove us as a listener too
				this.bleConnectionService.removeListener(this);
			}
    		this.bleConnectionService = service;
    		if (null != this.bleConnectionService) {
	    		// add us as a listener to this
	    		this.bleConnectionService.addListener(this);
    		}
		}
	}
	/**
	 * wrapped in a function so the pointer cannot becomme null half way through using it
	 * @return the connection service for use
	 */
	protected BleConnectionService getService() {
		synchronized (this) {
    		return this.bleConnectionService;
		}
	}
	/**
	 * stop the service, to be called explicitly else will live on in the background
	 */
	public void stopService() {
		synchronized (this) {
    		this.bleConnectionService.closeServiceConnections();
		}
		// If we no longer need it, kill the service
		this.mainActivity.stopService(new Intent(this.mainActivity, BleConnectionService.class));
	}
	/**
	 * @return the current connection state of the service
	 */
	public ConnectionState getCurrentState() {
		BleConnectionService service = getService();
		if (null == service) {
			return ConnectionState.disconnected;
		}
		else {
			return service.getCurrentState();
		}
	}
    
	@Override
	public void gattStateChanged(String deviceName, BluetoothDevice device, ConnectionState state) {
		BleConnectionHistoryStore<?> store = this.bleConnectionService.getStore();
		synchronized (this.listeners) {
			for (HeartRateListener listener : this.listeners) {
				listener.displayData(deviceName, device, state, (HeartRateDataStore) store);
			}
		}
	}
	
	public boolean connectDevice(String name, String address) {
		BleConnectionService service = getService();
		boolean success = false;
		if (null != address && false == address.isEmpty() && null != service) {
			if (service.getCurrentState() != ConnectionState.connected ||
				null == this.deviceAddress ||
				null == this.deviceName ||
				false == this.deviceName.equals(name) ||
				false == this.deviceAddress.equals(address)) {
				// there is a change in the state if we connect, so connect
				this.deviceName = name;
				this.deviceAddress = address;
				// now we can connect to this device, first put this in the prefs to remember
	    		SharedPreferences.Editor editor = this.preferences.edit();
	    		editor.putString(this.mainActivity.getString(R.string.saved_device_name), this.deviceName);
	    		editor.putString(this.mainActivity.getString(R.string.saved_device_address), this.deviceAddress);
	    		editor.commit();
	    		// and connect
	    		success = null != service.connect(this.deviceName, this.deviceAddress);
			}
			else {
				// service is already connected
			}
		}
		return success;
	}

	public void disconnectDevice() {
		BleConnectionService service = getService();
		if (null != service) {
			service.disconnect();
		}
	}

	public void destroy() {
		// clear listeners
		synchronized (this.listeners) {
			this.listeners.clear();
		}
		// un-bind from the service
		if (null != this.serviceConnection) {
			this.mainActivity.unbindService(this.serviceConnection);
		}
		setService(null);
	}
	/**
	 * @return the currently connected device name
	 */
	public String getDeviceName() {
		return this.deviceName;
	}
	/**
	 * @return the currently connected device address
	 */
	public String getDeviceAddress() {
		return this.deviceName;
	}

	@Override
	public void gattServicesDiscovered(String deviceName, BluetoothDevice device) {
		BleConnectionService service = getService();
		if (null != service) {
			// services are discovered - subscribe to heart-rate updates
			List<BluetoothGattService> supportedGattServices = service.getSupportedGattServices();
			boolean isSupported = false;
	        // connect to the heart-rate service to get the data we want...
	        for (BluetoothGattService gattService : supportedGattServices) {
	        	for (BluetoothGattCharacteristic characteristic : gattService.getCharacteristics()) {
		        	if (characteristic.getUuid().equals(UUID_HEART_RATE_MEASUREMENT)) {
		        		// supports HRM, ask for this data from the service
		        		if (null != currentNotifyCharacteristic) {
		        		    // If there is an active notification on a characteristic, clear
		                    // it first so it doesn't update the data field on the user interface.
		        			service.setCharacteristicNotification(this.currentNotifyCharacteristic, false);
		                    this.currentNotifyCharacteristic = null;
		                }
		        		// and read the new one
		        		service.setCharacteristicNotification(characteristic, true);
		        		// This is specific to Heart Rate Measurement, turn on the notification value too
		                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
	                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
	                    service.writeGattDescriptor(descriptor);
	                    // Heart-rate done, stop looking at the characteristics
	                    isSupported = true;
	                    break;
		        	}
	        	}
	        }
	        if (false == isSupported) {
	        	Log.w(MainActivity.TAG, "Heart-rate measurements not supported by device " + this.deviceName);
	        	disconnectDevice();
	        }
		}
	}

	@Override
	public void gattDataAvailable(String deviceName, BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
        // there is new data available, show this, get the data store from the service
        BleConnectionHistoryStore<?> store = this.bleConnectionService.getStore();
        if (null != store && store instanceof HeartRateDataStore) {
            // show this data
            displayHeartRateData(deviceName, device, (HeartRateDataStore)store);
        }
        else {
        	Log.e(MainActivity.TAG, "Returned store is not a heartratestore instead is " + store);
        }
	}

	private void displayHeartRateData(String deviceName, BluetoothDevice device, HeartRateDataStore dataStore) {
		// display the data by passing on to the listeners
		ConnectionState state;
		BleConnectionService service = getService();
		if (null != service) {
			state = service.getCurrentState();
		}
		else {
			state = ConnectionState.disconnected;
		}
		synchronized (this.listeners) {
			for (HeartRateListener listener : this.listeners) {
				listener.displayData(deviceName, device, state, dataStore);
			}
		}
	}
	
	public boolean addListener(HeartRateListener listener) {
		synchronized (this.listeners) {
			if (this.listeners.contains(listener)) {
				return false;
			}
			else {
				return this.listeners.add(listener);
			}
		}
	}
	
	public boolean removeListener(HeartRateListener listener) {
		synchronized (this.listeners) {
			return this.listeners.remove(listener);
		}
	}
}
