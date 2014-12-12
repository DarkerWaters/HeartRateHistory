package uk.co.darkerwaters.heartrateanalyser.ble;

import java.util.ArrayList;
import java.util.List;

import uk.co.darkerwaters.heartrateanalyser.MainActivity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BleConnectionService extends Service {
	/** the bluetooth manager */
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice device;
    private BluetoothGatt bluetoothGatt;
    
    public enum ConnectionState {
    	connecting,
    	connected,
    	disconnected,
    	disconnecting
    }
    
    private ConnectionState connectionState = ConnectionState.disconnected;

    public interface ConnectionServiceListener {
    	public void gattStateChanged(String deviceName, BluetoothDevice device, ConnectionState state);
    	public void gattServicesDiscovered(String deviceName, BluetoothDevice device);
    	public void gattDataAvailable(String deviceName, BluetoothDevice device, BluetoothGattCharacteristic characteristic);
    }
    
    public class LocalBinder extends Binder {
        BleConnectionService getService() {
            return BleConnectionService.this;
        }
    }
    private final IBinder binder = new LocalBinder();
    
    private final ArrayList<ConnectionServiceListener> listeners = new ArrayList<BleConnectionService.ConnectionServiceListener>();

    private BleConnectionCallback gattCallback;

    private BleConnectionHistoryStore<?> dataStore;
	private Context context;
	private BleConnectionHistoryStore.Provider<?> dataStoreProvider;

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

	@Override
    public void onDestroy() {
		// close the gatt connection
    	closeServiceConnections();
    	super.onDestroy();
    }

	public boolean initialiseService(Context context, BleConnectionHistoryStore.Provider<?> dataStoreProvider) {
    	this.context = context;
    	this.dataStoreProvider = dataStoreProvider;
    	// ensure we have a data store right away, do this by getting one
    	getStore();
        // For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager.
        if (this.bluetoothManager == null) {
        	this.bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (null == this.bluetoothManager) {
                Log.e(MainActivity.TAG, "Unable to initialize BluetoothManager.");
            }
        }
        if (null != this.bluetoothManager) {
	        this.bluetoothAdapter = bluetoothManager.getAdapter();
	        if (null == this.bluetoothAdapter) {
	            Log.e(MainActivity.TAG, "Unable to obtain a BluetoothAdapter.");
	        }
        }
        // return success of this
        return null != this.bluetoothAdapter;
    }

	/**
	 * helper to close all the connections to this service
	 */
    public void closeServiceConnections() {
		if (null != this.bluetoothGatt) {
			this.bluetoothGatt.disconnect();
			this.bluetoothGatt.close();
			this.bluetoothGatt = null;
		}
    	// save all our data
    	synchronized (this) {
    		if (null != this.dataStore) {
        		this.dataStore.closeStore();
        		this.dataStore = null;
        	}
		}
	}
    
    public BluetoothDevice connect(String deviceName, String deviceAddress) {
    	BluetoothDevice device = null;
    	if (null != deviceAddress && false == deviceAddress.isEmpty()) {
    		device = bluetoothAdapter.getRemoteDevice(deviceAddress);
    		if (null != device) {
    			// call the function to connect to this device
    			connect(deviceName, device);
    		}
    		else {
    			Log.w(MainActivity.TAG, "Device \"" + deviceAddress + "\" was not found.  Unable to connect.");
    		}
    	}
    	else {
    		// no address
    		Log.w(MainActivity.TAG, "Device of no address cannot be connected to.");
    	}
    	return this.device;
    }
    
    public BluetoothDevice connect(final String deviceName, final BluetoothDevice deviceToConnect) {
    	// create the gatt callback to inform the listener of these changes
    	this.gattCallback = new BleConnectionCallback(deviceName, deviceToConnect, new ConnectionServiceListener() {
			@Override
			public void gattStateChanged(String deviceName, BluetoothDevice device, ConnectionState state) {
				// store the current state
				BleConnectionService.this.connectionState = state;
				// inform our listeners
				synchronized(BleConnectionService.this.listeners) {
					for (ConnectionServiceListener listener : BleConnectionService.this.listeners) {
						listener.gattStateChanged(deviceName, BleConnectionService.this.device, state);
					}
				}
			}
			@Override
			public void gattServicesDiscovered(String deviceName, BluetoothDevice device) {
				// inform our listeners
				synchronized(BleConnectionService.this.listeners) {
					for (ConnectionServiceListener listener : BleConnectionService.this.listeners) {
						listener.gattServicesDiscovered(deviceName, device);
					}
				}
			}
			@Override
			public void gattDataAvailable(String deviceName, BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
				// put this data in the store to handle the data creation
				BleConnectionHistoryStore<?> store = getStore();
				if (null != store) {
					store.handleGattData(device, characteristic);
				}
				// inform our listeners
				synchronized(BleConnectionService.this.listeners) {
					for (ConnectionServiceListener listener : BleConnectionService.this.listeners) {
						listener.gattDataAvailable(deviceName, device, characteristic);
					}
				}
			}
		});
        if (this.bluetoothAdapter == null || deviceToConnect == null || deviceToConnect.getAddress().isEmpty()) {
        	// cannot connect to nothing
            Log.w(MainActivity.TAG, "BluetoothAdapter not initialized or unspecified address.");
        }
        else if (this.device != null && 
        		this.connectionState == ConnectionState.connected &&
        		deviceToConnect.getAddress().equals(this.device.getAddress()) && 
        		this.bluetoothGatt != null) {
        	// already connected to this very same device, send a message but basically ignore
        	this.gattCallback.gattStateChanged(connectionState);
        }
        else {
        	// this is a change in the connection, so try to connect to it
	        Log.d(MainActivity.TAG, "Trying to create a new connection to " + deviceToConnect.getName() + " at " + deviceToConnect.getAddress());
	        // remember we are connecting to this device
	        this.device = deviceToConnect;
	        try {
		        // connect to the device
		        this.bluetoothGatt = this.device.connectGatt(this, false, gattCallback);
		        // inform we are connecting this
		        this.gattCallback.gattStateChanged(ConnectionState.connecting);
	        }
	        catch (IllegalArgumentException e) {
	        	Log.e(MainActivity.TAG, "Failed to connect to the specified device");
	        }
        }
        // return the success of this
        return this.device;
    }
    
    public ConnectionState getCurrentState() {
    	return this.connectionState;
    }

	public BleConnectionHistoryStore<?> getStore() {
		synchronized (this) {
			if (null == this.dataStore) {
	    		// we don't have a store, so create one with the provider
	    		this.dataStore = this.dataStoreProvider.createNewStore(this.context);
	    		if (null != this.gattCallback) {
		    		// inform any listeners of this data
		    		this.gattCallback.gattStateChanged(this.connectionState);
	    		}
	    	}
			return this.dataStore;
		}
	}

    public void disconnect() {
    	if (this.bluetoothGatt == null) {
            Log.w(MainActivity.TAG, "BluetoothAdapter not initialized so cannot disconnect");
        }
        else {
        	// close our service connections, will inform the listener of this disconnection
        	// through the callback
        	closeServiceConnections();
	        // inform we are disconnecting this
	        this.gattCallback.gattStateChanged(ConnectionState.disconnected);
        }
    	Log.i(MainActivity.TAG, "Service disconnected");
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (this.bluetoothGatt == null) {
            Log.w(MainActivity.TAG, "BluetoothAdapter not initialized so cannot read the characteristic");
        }
        else {
        	this.bluetoothGatt.readCharacteristic(characteristic);
        }
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
    	if (this.bluetoothGatt == null) {
            Log.w(MainActivity.TAG, "BluetoothAdapter not initialized so cannot set the characteristic notification");
        }
        else {
        	this.bluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        }
    }

	public void writeGattDescriptor(BluetoothGattDescriptor descriptor) {
		if (this.bluetoothGatt == null) {
            Log.w(MainActivity.TAG, "BluetoothAdapter not initialized so cannot write the gatt descriptor");
        }
        else {
        	this.bluetoothGatt.writeDescriptor(descriptor);
        }
	}
	
	public boolean addListener(ConnectionServiceListener listener) {
		boolean success = false;
		synchronized (this.listeners) {
			if (false == this.listeners.contains(listener)) {
				success = this.listeners.add(listener);
			}
		}
		// upon adding a listener, lets take the opportunity to immediately inform the listener of what is going on
		if (null != this.gattCallback) {
			// there is a callback to inform any listeners of this data
    		this.gattCallback.gattStateChanged(this.connectionState);
		}
		return success;
	}
	
	public boolean removeListener(ConnectionServiceListener listener) {
		synchronized (this.listeners) {
			return this.listeners.remove(listener);
		}
	}

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (null != this.bluetoothGatt) {
        	return this.bluetoothGatt.getServices();
        }
        else {
        	// return none
        	return new ArrayList<BluetoothGattService>();
        }
    }
}
