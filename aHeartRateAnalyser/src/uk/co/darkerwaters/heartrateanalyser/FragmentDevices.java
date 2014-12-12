package uk.co.darkerwaters.heartrateanalyser;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionService.ConnectionState;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateDataStore;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * A fragment containing a the home views.
 */
public class FragmentDevices extends FragmentBase {
	
	private DeviceListAdapter deviceListAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private boolean isScanning;
    private Handler handler;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after some seconds.
    private long scanStartTime;
    private static final long SCAN_PERIOD = 15000;
    
    private TextView deviceAddressText;
	private TextView deviceNameText;
    private Button deviceActionButton;
    private ProgressBar deviceConnectionProgress = null;
    
    private ListView listView = null;
    private ProgressBar deviceScanProgress = null;
	private final Timer deviceScanTimer;
	private TimerTask deviceScanTimerTask = null;

	public FragmentDevices() {
        this.deviceScanTimer = new Timer();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_devices, container, false);
		
		// setup the device portion
		this.deviceAddressText = (TextView) rootView.findViewById(R.id.device_address);
		this.deviceNameText = (TextView) rootView.findViewById(R.id.device_name);
		this.deviceConnectionProgress = (ProgressBar) rootView.findViewById(R.id.device_progress);
		this.deviceActionButton = (Button) rootView.findViewById(R.id.deviceActionButton);
		this.deviceActionButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				switch(FragmentDevices.this.connectionState) {
				case connecting :
				case connected :
					FragmentDevices.this.mainActivity.disconnectDevice();
					break;
				case disconnected:
				case disconnecting :
					FragmentDevices.this.mainActivity.connectToDevice(getDevice());
					break;
		    	}
			}
		});
		
		// find the members
		this.deviceScanProgress = (ProgressBar) rootView.findViewById(R.id.deviceScanProgress);
		this.listView = (ListView) rootView.findViewById(R.id.connectDeviceList);
		this.listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final BluetoothDevice device = deviceListAdapter.getDevice(position);
				if (null != device) {
					// connect to this device
			        mainActivity.connectToDevice(device);
			        if (isScanning) {
			            bluetoothAdapter.stopLeScan(scanCallback);
			            isScanning = false;
			        }
				}
			}
		});
        //getActionBar().setTitle(R.string.title_devices);
        this.handler = new Handler();
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!this.mainActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(MainActivity.TAG, "Bluetooth NOT AVAILABLE!!!");
        }
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager) this.mainActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (bluetoothAdapter == null) {
        	Log.e(MainActivity.TAG, "Bluetooth NOT SUPPORTED!!!");
        }
		// update our view now
		updateDisplayData(this.deviceName, this.device, this.connectionState, this.heartData);
        // start scanning for devices
        scanForDevices(true);
		return rootView;
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// set the action bar title
		this.mainActivity.getActionBar().setTitle(R.string.connect_hrm);
		inflater.inflate(R.menu.devices, menu);
		if (false == isScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
	}
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	boolean result = false;
        switch (item.getItemId()) {
            case R.id.menu_scan:
                deviceListAdapter.clear();
                scanForDevices(true);
                result = true;
                break;
            case R.id.menu_stop:
                scanForDevices(false);
                result = true;
                break;
        }
        return result;
    }
    
    @Override
	protected void updateDisplayData(String deviceName, BluetoothDevice device, ConnectionState connectionState, HeartRateDataStore heartData) {
    	super.updateDisplayData(deviceName, device, connectionState, heartData);
    	// update the button text
    	int deviceConnectProgress = View.INVISIBLE;
    	switch(this.connectionState) {
		case connecting :
			deviceConnectProgress = View.VISIBLE;
		case connected :
			this.deviceActionButton.setText(R.string.action_disconnect);
			break;
		case disconnecting:
			deviceConnectProgress = View.VISIBLE;
		case disconnected :
			this.deviceActionButton.setText(R.string.action_connect);
			break;
    	}
    	// show the progress icon if we are doing something
    	this.deviceConnectionProgress.setVisibility(deviceConnectProgress);
    	// update the display of the device
    	if (null != device) {
	    	this.deviceNameText.setText(device == null ? "" : device.getName());
	    	this.deviceAddressText.setText(device == null ? "" : device.getAddress());
	    	this.deviceActionButton.setClickable(true);
	    	this.deviceActionButton.setEnabled(true);
    	}
    	else {
    		this.deviceNameText.setText(getString(R.string.device_not_connected));
	    	this.deviceAddressText.setText("");
	    	this.deviceActionButton.setClickable(false);
	    	this.deviceActionButton.setEnabled(false);
    	}
    }
	
    @Override
    public void onResume() {
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!bluetoothAdapter.isEnabled()) {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        // Initializes list view adapter.
        deviceListAdapter = new DeviceListAdapter();
        listView.setAdapter(deviceListAdapter);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            //finish();
        	//TODO show BT is not on 
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onPause() {
        super.onPause();
        scanForDevices(false);
    }

    private void scanForDevices(final boolean enable) {
    	this.isScanning = enable;
    	this.mainActivity.invalidateOptionsMenu();
        if (this.isScanning) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanForDevices(false);
                }
            }, SCAN_PERIOD);
        }
        // perform the scan operation in a thread to release the UI
        new Thread(new Runnable() {
            public void run() {
            	if (null != bluetoothAdapter && null != scanCallback) {
	            	if (enable) {
	            		bluetoothAdapter.startLeScan(scanCallback);
	            	}
	            	else {
	            		bluetoothAdapter.stopLeScan(scanCallback);
	            	}
            	}
            } 
        }, "deviceScan").start();
        if (null != this.deviceScanTimerTask) {
        	this.deviceScanTimerTask.cancel();
        	this.deviceScanTimerTask = null;
        }
        if (this.isScanning) {
	        // also update the progress bar;
        	this.scanStartTime = System.currentTimeMillis();
        	this.deviceScanTimerTask = new TimerTask() {
    			@Override
    			public void run() {
    				// update the timer scan progress bar
    				mainActivity.runOnUiThread(new Runnable() {
    					@Override
    					public void run() {
    						if (null != deviceScanProgress) {
    							double delta = System.currentTimeMillis() - scanStartTime;
    							int progress = (int)((delta / (double)SCAN_PERIOD) * 100.0);
    							deviceScanProgress.setProgress(progress);
    						}
    					}
    				});
    			}
    		};
	        this.deviceScanTimer.schedule(this.deviceScanTimerTask, 100, 100);
        }
        else {
        	this.mainActivity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (null != deviceScanProgress) {
						deviceScanProgress.setProgress(0);
					}
				}
			});
        }
    }

    // Adapter for holding devices found through scanning.
    private class DeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> devices;
        private LayoutInflater layoutInflator;

        private class ViewHolder {
            TextView deviceName;
            TextView deviceAddress;
        }

        public DeviceListAdapter() {
            super();
            devices = new ArrayList<BluetoothDevice>();
            layoutInflator = mainActivity.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!devices.contains(device)) {
                devices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return devices.get(position);
        }

        public void clear() {
            devices.clear();
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int i) {
            return devices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = layoutInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = devices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
            }
            else {
                viewHolder.deviceName.setText(R.string.unknown_device);
            }
            // set the text for the address
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    deviceListAdapter.addDevice(device);
                    deviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };
}
