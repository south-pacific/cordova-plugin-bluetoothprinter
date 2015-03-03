package com.phonegap.plugins.bluetoothprinter;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class BluetoothPrinter extends CordovaPlugin {
	private static final String LOG_TAG = "BluetoothPrinter";
	private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
			.getDefaultAdapter();
	private BluetoothDevice mmDevice = null;
	private static BluetoothSocket mmSocket = null;
	private static OutputStream mmOutputStream = null;
	private static final UUID uuid = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private boolean isConnection = false;
	// 用于存放未配对蓝牙设备
	private ArrayList<BluetoothDevice> unbondDevices = new ArrayList<BluetoothDevice>();

	public BluetoothPrinter() {

	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		// 注册广播接收器，接收并处理搜索结果
		// 设置广播信息过滤
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
		intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		cordova.getActivity().registerReceiver(qinsilkBroadcastReceiver,
				intentFilter);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		cordova.getActivity().unregisterReceiver(qinsilkBroadcastReceiver);
	}

	@Override
	public boolean execute(String action, JSONArray args,
			CallbackContext callbackContext) throws JSONException {
		if (action.equals("list")) {
			listBT(callbackContext);
			return true;
		} else if (action.equals("open")) {
			String name = args.getString(0);
			if (findBT(name)) {
				openBT(callbackContext);
			} else {
				callbackContext.error("没有找到蓝牙设备：" + name);
			}
			return true;
		} else if (action.equals("print")) {
			String msg = args.getString(0);
			sendData(callbackContext, msg);
			return true;
		} else if (action.equals("close")) {
			closeBT(callbackContext);
			return true;
		} else if (action.equals("connect")) {
			String name = args.getString(0);
			connect(callbackContext, name);
			return true;
		}
		return false;
	}

	/**
	 * 获取已配对蓝牙设备信息
	 * 
	 * @param callbackContext
	 */
	void listBT(CallbackContext callbackContext) {
		try {
			if (mBluetoothAdapter == null) {
				Log.e(LOG_TAG, "No bluetooth adapter available");
				callbackContext.error("蓝牙适配器不可用");
				return;
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				cordova.getActivity()
						.startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
					.getBondedDevices();
			if (pairedDevices.size() > 0) {
				JSONArray json = new JSONArray();
				for (BluetoothDevice device : pairedDevices) {
					JSONObject jObj = new JSONObject();

					jObj.put("name", device.getName());
					jObj.put("address", device.getAddress());
					jObj.put("id", device.getAddress());

					json.put(jObj);
				}
				callbackContext.success(json);
			} else {
				callbackContext.error("没有找到已配对蓝牙设备");
			}
			// 寻找蓝牙设备，android会将查找到的设备以广播形式发出去
			mBluetoothAdapter.startDiscovery();
		} catch (Exception e) {
			Log.e(LOG_TAG, "listBT fail:", e);
			callbackContext.error("搜索蓝牙设备过程中出现错误");
		}
	}

	/**
	 * 根据地址查找蓝牙设备
	 * 
	 * @param name
	 * @return
	 */
	boolean findBT(String name) {
		if (this.isConnection && mmDevice != null
				&& name.equals(mmDevice.getAddress())) {
			Log.d(LOG_TAG,
					"Bluetooth Device Already Connect : " + mmDevice.getName());
			return true;
		}
		this.isConnection = false;
		try {
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				cordova.getActivity()
						.startActivityForResult(enableBluetooth, 0);
			}
			mmDevice = mBluetoothAdapter.getRemoteDevice(name);
			Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
			return true;
		} catch (Exception e) {
			Log.e(LOG_TAG, "findBT fail:", e);
			return false;
		}
	}

	/**
	 * 打开蓝牙连接
	 * 
	 * @param callbackContext
	 */
	void openBT(CallbackContext callbackContext) {
		if (this.isConnection) {
			callbackContext.success("蓝牙打开：" + mmDevice.getName());
			return;
		}
		try {
			mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
			mmSocket.connect();
			mmOutputStream = mmSocket.getOutputStream();
			this.isConnection = true;
			if (mBluetoothAdapter.isDiscovering()) {
				Log.d(LOG_TAG, "BluetoothAdapter closed!");
				mBluetoothAdapter.cancelDiscovery();
			}
			Log.d(LOG_TAG, "Bluetooth Opened: " + mmDevice.getName());
			callbackContext.success("蓝牙打开：" + mmDevice.getName());
		} catch (Exception e) {
			Log.e(LOG_TAG, "openBT fail:", e);
			callbackContext.error("连接蓝牙设备过程中出现错误");
		}
	}

	/**
	 * 发送打印数据
	 * 
	 * @param callbackContext
	 * @param msg
	 */
	void sendData(CallbackContext callbackContext, String msg) {
		try {
			Log.d(LOG_TAG, "Start print");
			msg += "\r";
			byte[] data = msg.getBytes("gbk");
			mmOutputStream.write(data);
			mmOutputStream.flush();
			Log.d(LOG_TAG, "Data Sent");
			callbackContext.success("打印数据发送成功");
		} catch (Exception e) {
			Log.e(LOG_TAG, "sendData fail:", e);
			callbackContext.error("发送打印数据过程中出现错误");
		}
	}

	/**
	 * 关闭蓝牙连接
	 * 
	 * @param callbackContext
	 */
	void closeBT(CallbackContext callbackContext) {
		try {
			if (mmOutputStream != null) {
				mmOutputStream.close();
			}
			if (mmSocket != null) {
				mmSocket.close();
			}
			this.isConnection = false;
			if (mBluetoothAdapter.isDiscovering()) {
				Log.d(LOG_TAG, "BluetoothAdapter closed!");
				mBluetoothAdapter.cancelDiscovery();
			}
			callbackContext.success("蓝牙连接关闭");
		} catch (Exception e) {
			callbackContext.error("蓝牙连接关闭过程中出现错误");
			Log.e(LOG_TAG, "closeBT fail:", e);
		}
	}

	/**
	 * 蓝牙配对
	 * 
	 * @param callbackContext
	 * @param name
	 */
	void connect(CallbackContext callbackContext, String name) {
		try {
			if (mBluetoothAdapter.isDiscovering()) {
				Log.d(LOG_TAG, "BluetoothAdapter closed!");
				mBluetoothAdapter.cancelDiscovery();
			}
			BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(name);
			Method createBondMethod = BluetoothDevice.class
					.getMethod("createBond");
			createBondMethod.invoke(device);
			Log.d(LOG_TAG, "Bluetooth Device Found: " + device.getName());
			callbackContext.success();
		} catch (Exception e) {
			Log.e(LOG_TAG, "connect fail:", e);
			callbackContext.error("蓝牙配对过程中出现错误");
		}
	}

	/**
	 * 蓝牙广播接收器
	 */
	private BroadcastReceiver qinsilkBroadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					if (!unbondDevices.contains(device)) {
						unbondDevices.add(device);
					}
					refreshUnbondDevices();
				}
			} else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
				for (Iterator<BluetoothDevice> it = unbondDevices.iterator(); it
						.hasNext();) {
					BluetoothDevice tempDevice = (BluetoothDevice) it.next();
					if (tempDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
						it.remove();
						String js = String
								.format("window.plugins.bluetoothPrinter.bondDeviceslist();");
						webView.sendJavascript(js);
						refreshUnbondDevices();
						break;
					}
				}
			}
		}
	};

	/**
	 * 刷新未配对设备列表数据
	 */
	public void refreshUnbondDevices() {
		try {
			JSONArray json = new JSONArray();
			for (BluetoothDevice unbondDevice : this.unbondDevices) {
				JSONObject jObj = new JSONObject();

				jObj.put("name", unbondDevice.getName());
				jObj.put("address", unbondDevice.getAddress());
				jObj.put("id", unbondDevice.getAddress());

				json.put(jObj);
			}
			String js = String.format(
					"window.plugins.bluetoothPrinter.unbondDeviceslist(%s);",
					json.toString());
			this.webView.sendJavascript(js);
		} catch (Exception e) {
			Log.e(LOG_TAG, "addUnbondDevices fail:", e);
		}
	}
}
