package com.bin.android.main;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bin.android.MainPlugin.R;
import com.bin.android.utils.ApkItem;
import com.morgoo.droidplugin.pm.PluginManager;
import com.morgoo.helper.compat.PackageManagerCompat;

/**
 * @author dhunter
 * @time 2016-7-6 上午11:31:21
 */
public class StoreFragment extends ListFragment {
	private static final String TAG = StoreFragment.class.getSimpleName();
	private ArrayAdapter<ApkItem> adapter;
	final Handler handler = new Handler();
	private RemovedApkReceiver mRemovedApkBroadcastReceiver = new RemovedApkReceiver();

	public StoreFragment() {
	}

	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "---onServiceConnected---");
			startLoad();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mRemovedApkBroadcastReceiver.registerReceiver(getActivity()
				.getApplication());
		adapter = new ArrayAdapter<ApkItem>(getActivity(), 0) {
			@Override
			public View getView(final int position, View convertView,
					final ViewGroup parent) {
				if (convertView == null) {
					convertView = LayoutInflater.from(getActivity()).inflate(
							R.layout.apk_item, null);
				}
				ApkItem item = getItem(position);

				ImageView icon = (ImageView) convertView
						.findViewById(R.id.imageView);
				icon.setImageDrawable(item.icon);

				TextView title = (TextView) convertView
						.findViewById(R.id.textView1);
				title.setText(item.title);

				TextView version = (TextView) convertView
						.findViewById(R.id.textView2);
				version.setText(String.format("%s(%s)", item.versionName,
						item.versionCode));

				TextView btn3 = (TextView) convertView
						.findViewById(R.id.button3);
				btn3.setText("删除");
				btn3.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View view) {
						onListItemClick(getListView(), view, position,
								getItemId(position));
					}
				});
				TextView btn = (TextView) convertView
						.findViewById(R.id.button2);
				try {
					if (item.installing) {
						btn.setText("安装中ing");
					} else {
						if (PluginManager.getInstance().isConnected()) {
							btn.setText(PluginManager.getInstance()
									.getPackageInfo(
											item.packageInfo.packageName, 0) != null ? "已装"
									: "安装");
						} else {
							btn.setText("等待初始化服务");
						}
					}
				} catch (Exception e) {
					btn.setText("安装1");
				}
				btn.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View view) {
						onListItemClick(getListView(), view, position,
								getItemId(position));
					}
				});

				return convertView;
			}
		};

	}

	private void doUninstall(final ApkItem item) {
		AlertDialog.Builder builder = new Builder(getActivity());
		builder.setTitle("警告，你确定要删除么？");
		builder.setMessage("警告，你确定要删除" + item.title + "么？");
		builder.setNegativeButton("删除", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				new File(item.apkfile).delete();
				adapter.remove(item);
				Toast.makeText(getActivity(), "删除成功", Toast.LENGTH_SHORT)
						.show();
			}
		});
		builder.setNeutralButton("取消", null);
		builder.show();
	}

	boolean isViewCreated = false;

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		Log.i(TAG, "---onViewCreated---");
		super.onViewCreated(view, savedInstanceState);
		isViewCreated = true;
		setEmptyText("没有在sdcard找到apk");
		setListAdapter(adapter);
		setListShown(false);
		getListView().setOnItemClickListener(null);
		if (PluginManager.getInstance().isConnected()) {
			startLoad();
		} else {
			PluginManager.getInstance()
					.addServiceConnection(mServiceConnection);
		}
	}

	@Override
	public void onDestroyView() {
		isViewCreated = false;
		super.onDestroyView();
		mRemovedApkBroadcastReceiver.unregisterReceiver(getActivity()
				.getApplication());
	}

	@Override
	public void setListShown(boolean shown) {
		if (isViewCreated) {
			super.setListShown(shown);
		}
	}

	private void startLoad() {
		Log.i(TAG, "---startLoad---");
		if (ActivityCompat.checkSelfPermission(getActivity(),
				Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
			startLoadInner();
		} else {
			requestPermissions(
					new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
					0x1);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
			String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == 0x1) {
			if (permissions != null && permissions.length > 0) {
				for (int i = 0; i < permissions.length; i++) {
					String permisson = permissions[i];
					int grantResult = grantResults[i];
					if (Manifest.permission.READ_EXTERNAL_STORAGE
							.equals(permisson)) {
						if (grantResult == PackageManager.PERMISSION_GRANTED) {
							startLoadInner();
						} else {
							Toast.makeText(getActivity(), "没有授权，无法使用",
									Toast.LENGTH_SHORT).show();
						}
					}
				}
				for (String permisson : permissions) {

				}
			}
		}
	}

	private void startLoadInner() {
		Log.i(TAG, "---startLoadInner---");
		handler.post(new Runnable() {
			@Override
			public void run() {
				setListShown(true);
			}
		});
		if (!isViewCreated) {
			return;
		}
		new Thread("ApkScanner") {
			@Override
			public void run() {
				Log.i(TAG, "---ApkScanner---");
				File file = Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

				List<File> apks = new ArrayList<File>(10);
				File[] files = file.listFiles();
				if (files != null) {
					for (File apk : files) {
						if (apk.exists()
								&& apk.getPath().toLowerCase().endsWith(".apk")) {
							apks.add(apk);
						}
					}
				}

				file = new File(Environment.getExternalStorageDirectory(),
						"360Download");
				if (file.exists() && file.isDirectory()) {
					File[] files1 = file.listFiles();
					if (files1 != null) {
						for (File apk : files1) {
							if (apk.exists()
									&& apk.getPath().toLowerCase()
											.endsWith(".apk")) {
								apks.add(apk);
							}
						}
					}

				}
				PackageManager pm = getActivity().getPackageManager();
				for (final File apk : apks) {
					try {
						if (apk.exists()
								&& apk.getPath().toLowerCase().endsWith(".apk")) {
							final PackageInfo info = pm.getPackageArchiveInfo(
									apk.getPath(), 0);
							if (info != null && isViewCreated) {
								try {
									handler.post(new Runnable() {
										@Override
										public void run() {
											adapter.add(new ApkItem(
													getActivity(), info, apk
															.getPath()));//获取插件目录下每个apks对应的ApkItem信息
										}
									});
								} catch (Exception e) {
								}
							}
						}
					} catch (Exception e) {
					}
				}
			}
		}.start();
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		final ApkItem item = adapter.getItem(position);
		if (v.getId() == R.id.button2) {
			if (item.installing) {
				return;
			}
			if (!PluginManager.getInstance().isConnected()) {
				Toast.makeText(getActivity(), "插件服务正在初始化，请稍后再试。。。",
						Toast.LENGTH_SHORT).show();
			}
			try {
				if (PluginManager.getInstance().getPackageInfo(
						item.packageInfo.packageName, 0) != null) {
					Toast.makeText(getActivity(), "已经安装了，不能再安装",
							Toast.LENGTH_SHORT).show();
				} else {
					new Thread() {
						@Override
						public void run() {
							doInstall(item);
						}
					}.start();

				}
			} catch (Exception e) {
				e.printStackTrace();
				try {
					PluginManager.getInstance().installPackage(item.apkfile, 0);
				} catch (RemoteException e1) {
					e1.printStackTrace();
				}
				adapter.remove(item);
			}
		} else if (v.getId() == R.id.button3) {
			doUninstall(item);
		}
	}

	private synchronized void doInstall(ApkItem item) {
		item.installing = true;

		handler.post(new Runnable() {
			@Override
			public void run() {
				adapter.notifyDataSetChanged();
			}
		});
		try {
			final int re = PluginManager.getInstance().installPackage(
					item.apkfile, 0);
			item.installing = false;

			handler.post(new Runnable() {
				@Override
				public void run() {
					switch (re) {
					case PluginManager.INSTALL_FAILED_NO_REQUESTEDPERMISSION:
						Toast.makeText(getActivity(), "安装失败，文件请求的权限太多",
								Toast.LENGTH_SHORT).show();
						break;
					case PackageManagerCompat.INSTALL_FAILED_NOT_SUPPORT_ABI:
						Toast.makeText(getActivity(),
								"宿主不支持插件的abi环境，可能宿主运行时为64位，但插件只支持32位",
								Toast.LENGTH_SHORT).show();
						break;
					case PackageManagerCompat.INSTALL_SUCCEEDED:
						Toast.makeText(getActivity(), "安装完成",
								Toast.LENGTH_SHORT).show();
						adapter.notifyDataSetChanged();
						break;
					}

				}
			});
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy() {
		PluginManager.getInstance().removeServiceConnection(mServiceConnection);
		super.onDestroy();
	}

	public void onRefresh() {
		Log.i(TAG, "onRefresh()");
		if (isViewCreated) {
			setEmptyText("没有在sdcard找到apk");
			setListAdapter(adapter);
			setListShown(false);
			getListView().setOnItemClickListener(null);
			if (PluginManager.getInstance().isConnected()) {
				startLoad();
			} else {
				PluginManager.getInstance().addServiceConnection(
						mServiceConnection);
			}
		}
	}

	private class RemovedApkReceiver extends BroadcastReceiver {
		// 注册应用程序卸载（安装）的广播
		void registerReceiver(Context context) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(PluginManager.ACTION_PACKAGE_ADDED);
			filter.addAction(PluginManager.ACTION_PACKAGE_REMOVED);
			filter.addDataScheme("package");
			context.registerReceiver(this, filter);
		}

		void unregisterReceiver(Context context) {
			context.unregisterReceiver(this);
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			if (PluginManager.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
				int N = adapter.getCount();
				ApkItem iremovedItem = null;
				for (int i = N - 1; i >= 0; i--) {
					ApkItem item = adapter.getItem(i);
					iremovedItem = item;
					if (iremovedItem != null) {
						adapter.remove(iremovedItem);
					}
				}
				onRefresh();
			}
		}
	}

}