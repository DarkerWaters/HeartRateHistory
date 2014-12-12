package uk.co.darkerwaters.heartrateanalyser.ble;

import uk.co.darkerwaters.heartrateanalyser.MainActivity;
import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionService.ConnectionServiceListener;
import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionService.ConnectionState;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

public class BleConnectionCallback extends BluetoothGattCallback {
	/** the listener to inform of changes to, and data */
	private final  ConnectionServiceListener listener;
	
	private BluetoothDevice device;
	private String deviceName;
	/**
	 * constructor
	 * @param listener
	 */
	public BleConnectionCallback(String deviceName, BluetoothDevice device, ConnectionServiceListener listener) {
		// store the listener to inform of changes
		this.listener = listener;
		this.deviceName = deviceName;
		// set the device
		setDevice(device);
	}
	/**
	 * helper to set the device, ensuring the name is as good as it can be
	 * @param device is the device to set
	 */
	private void setDevice(BluetoothDevice device) {
		this.device = device;
		if (null != this.device && null != this.device.getName()) {
			// use the name from the device
			this.deviceName = this.device.getName();
		}
	}
	@Override
	public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
		// ensure we are informing about the correct device
		setDevice(gatt.getDevice());
		ConnectionState newConnectionState;
		switch (newState) {
		case BluetoothProfile.STATE_CONNECTED:
			newConnectionState = ConnectionState.connected;
			// once connected, request the services it provides
            gatt.discoverServices();
			break;
		case BluetoothProfile.STATE_CONNECTING:
			newConnectionState = ConnectionState.connecting;
			break;
		case BluetoothProfile.STATE_DISCONNECTING:
			newConnectionState = ConnectionState.disconnecting;
			break;
		case BluetoothProfile.STATE_DISCONNECTED:
		default:
			newConnectionState = ConnectionState.disconnected;
			break;
		}
	    gattStateChanged(newConnectionState);
	}
	public void gattStateChanged(ConnectionState newConnectionState) {
		// inform the listener of this
		this.listener.gattStateChanged(this.deviceName, this.device, newConnectionState);
	}
	@Override
	public void onServicesDiscovered(BluetoothGatt gatt, int status) {
		// ensure we are informing about the correct device
		setDevice(gatt.getDevice());
	    if (status == BluetoothGatt.GATT_SUCCESS) {
	    	// inform the listener of the services discovered
	        this.listener.gattServicesDiscovered(this.deviceName, this.device);
	    } else {
	        Log.w(MainActivity.TAG, "onServicesDiscovered received: " + status);
	    }
	}
	@Override
	public void onCharacteristicRead(BluetoothGatt gatt,
	                                 BluetoothGattCharacteristic characteristic,
	                                 int status) {
		// ensure we are informing about the correct device
		setDevice(gatt.getDevice());
	    if (status == BluetoothGatt.GATT_SUCCESS) {
	    	// inform the listener of this characteristic
	    	this.listener.gattDataAvailable(this.deviceName, this.device, characteristic);
	    } else {
	        Log.w(MainActivity.TAG, "onCharacteristicRead received: " + status);
	    }
	}
	@Override
	public void onCharacteristicChanged(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic) {
		// ensure we are informing about the correct device
		setDevice(gatt.getDevice());
		// inform the listener of this new data
		this.listener.gattDataAvailable(this.deviceName, this.device, characteristic);
	}
}
