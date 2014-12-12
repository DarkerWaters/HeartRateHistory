package uk.co.darkerwaters.heartrateanalyser;

import java.util.ArrayList;

import uk.co.darkerwaters.heartrateanalyser.ble.BleConnectionService.ConnectionState;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateConnection;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateConnection.HeartRateListener;
import uk.co.darkerwaters.heartrateanalyser.ble.HeartRateDataStore;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends Activity {

	public static final String TAG = "HeartRateAnalyser";
	
	private HeartRateConnection connection = null;

	private final ArrayList<FragmentBase> fragments = new ArrayList<FragmentBase>();
	
	private FragmentBase activeFragment = null;

	private final HeartRateListener connectionListener;
	
	public MainActivity() {
		// create the listener for connection changes
		this.connectionListener = new HeartRateListener() {
			@Override
			public void displayData(String deviceName, BluetoothDevice device, ConnectionState connectionState, HeartRateDataStore data) {
				// display the data on the active fragment
				if (null != activeFragment) {
					activeFragment.displayData(deviceName, device, connectionState, data);
				}
			}
		};
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// connect to our service for data
		createServiceConnection();		
		// add the fragment to our activity
		if (savedInstanceState == null) {
			showFragment(FragmentHome.class);
		}
	}
	
	private void createServiceConnection() {
		// create the service connection
		this.connection = new HeartRateConnection(this);
		// now have this listen to the service connection
		this.connection.addListener(this.connectionListener);
	}

	@Override
	public void onBackPressed() {
		// if we are not on the home activity, go back to the home activity instead
		if (null != this.activeFragment && false == activeFragment instanceof FragmentHome) {
			// show the fragment
			showFragment(FragmentHome.class);
			// and create the menu
			invalidateOptionsMenu();
		}
		else {
			// let the app back out
			super.onBackPressed();
		}
	}
	
	void setupFragment(FragmentBase fragment) {
		synchronized (this.fragments) {
			if (false == this.fragments.contains(fragment)) {
				// add this to the list of fragments
				this.fragments.add(fragment);
			}
		}
		// remember the active fragment
		this.activeFragment = fragment;
		Log.d(TAG, "Active Fragment is a " + this.activeFragment.getTag());
		// create the new menu for this active fragment
		invalidateOptionsMenu();
	}

	@Override
	protected void onPause() {
		if (null != this.connection) {
			// stop the connection by killing it
			this.connection.removeListener(this.connectionListener);
			this.connection.destroy();
			this.connection = null;
		}
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		if (null == this.connection) {
			// recreate the service connection
			createServiceConnection();
		}
		super.onResume();
	}
	
	@Override
	protected void onDestroy() {
		if (null != this.connection) {
			this.connection.removeListener(this.connectionListener);
			this.connection.destroy();
			this.connection = null;
		}
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (null != this.activeFragment) {
			// this is the devices fragment, show the menu for this
			this.activeFragment.onCreateOptionsMenu(menu, getMenuInflater());
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		else if (id == R.id.action_devices) {
			// show the devices fragment
			showFragment(FragmentDevices.class); 
			return true;
		}/*
		else if (id == R.id.action_device) {
			// show the devices fragment
			showFragment(FragmentDevice.class); 
			return true;
		}*/
		else if (id == R.id.action_exit) {
			// properly exiting, kill the service
			if (null != this.connection) {
				this.connection.removeListener(this.connectionListener);
				this.connection.stopService();
				this.connection.destroy();
				this.connection = null;
			}
			finish();
			return true;
		}
		else if (null != this.activeFragment) {
			// let the fragment create it's own menu
			return this.activeFragment.onOptionsItemSelected(item);
		}
		else {
			return super.onOptionsItemSelected(item);
		}
	}

	FragmentBase showFragment(Class<? extends FragmentBase> fragmentClass) {
		if (null != activeFragment && fragmentClass.isInstance(this.activeFragment)) {
			// no change, already showing this...
			return this.activeFragment;
		}
		else {
			// try to find the fragment of this type first
			FragmentBase extantFragment = null;
			synchronized (this.fragments) {
				for (FragmentBase fragment : this.fragments) {
					if (fragmentClass.isInstance(fragment)) {
						// this is it
						extantFragment = fragment;
						break;
					}
				}
			}
			if (null == extantFragment) {
				// else create a new one
				try {
					extantFragment = fragmentClass.newInstance();
				} catch (InstantiationException e) {
					Log.e(TAG, "Failed to create the new fragment", e);
				} catch (IllegalAccessException e) {
					Log.e(TAG, "Failed to create the new fragment", e);
				}
			}
			FragmentTransaction transaction = getFragmentManager().beginTransaction();
			if (null != this.activeFragment) {
				// replace the active one, first set the data on the fragment to be as on the current one
				extantFragment.setData(this.activeFragment.getDeviceName(), 
						this.activeFragment.getDevice(), 
						this.activeFragment.getConnectionState(), 
						this.activeFragment.getData());
				// and setup the transaction to show this fragment
				transaction.setCustomAnimations(R.anim.slidein, R.anim.slideout);
				transaction.replace(R.id.home_view_container, extantFragment, fragmentClass.getCanonicalName());
			}
			else {
				// make the first one
				transaction.add(R.id.home_view_container, extantFragment, fragmentClass.getCanonicalName());
			}
			// do the work to create this new fragment
			transaction.commit();
			// return the newly shown active fragment
			return extantFragment;
		}
	}

	public void connectToDevice(BluetoothDevice device) {
		if (null != device && null != this.connection) {
			// connect the device
			this.connection.connectDevice(device.getName(), device.getAddress());
			// change to the home fragment to show this new connection
			showFragment(FragmentHome.class);
        }
	}

	public void disconnectDevice() {
		if (null != this.connection) {
			// connect the device
			this.connection.disconnectDevice();
        }
	}
}
