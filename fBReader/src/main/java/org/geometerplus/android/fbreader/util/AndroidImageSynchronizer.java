/*
 * Copyright (C) 2007-2015 FBReader.ORG Limited <contact@fbreader.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.util;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import android.app.Activity;
import android.app.Service;
import android.content.*;
import android.os.IBinder;

import org.geometerplus.zlibrary.core.image.ZLImageProxy;
import org.geometerplus.zlibrary.core.image.ZLImageSimpleProxy;
import org.geometerplus.zlibrary.ui.android.image.ZLAndroidImageManager;
import org.geometerplus.zlibrary.ui.android.image.ZLBitmapImage;

import org.geometerplus.fbreader.formats.ExternalFormatPlugin;
import org.geometerplus.fbreader.formats.PluginImage;
import org.geometerplus.android.fbreader.api.FBReaderIntents;
import org.geometerplus.android.fbreader.formatPlugin.CoverReader;

/**
 * @Date:  2020-06-11
 * @Description: 图片同步
 *
 */
public class AndroidImageSynchronizer implements ZLImageProxy.Synchronizer {

	/**
	 * 服务连接对象，服务于外部支持插件
	 */
	private static final class Connection implements ServiceConnection {

		/**
		 * 单线程池
		 */
		private final ExecutorService myExecutor = Executors.newSingleThreadExecutor();

		/**
		 * 支持的外部插件
		 */
		private final ExternalFormatPlugin myPlugin;

		/**
		 * aidl 的封面读取 Bitmap 接口 readBitmap
		 */
		private volatile CoverReader Reader;

		/**
		 * 待执行的任务队列
		 */
		private final List<Runnable> myPostActions = new LinkedList<Runnable>();

		Connection(ExternalFormatPlugin plugin) {
			myPlugin = plugin;
		}

		/**
		 * 添加或执行任务
		 */
		synchronized void runOrAddAction(Runnable action) {
			if (Reader != null) {
				myExecutor.execute(action);
			} else {
				myPostActions.add(action);
			}
		}

		/**
		 * 服务连接，执行任务队列
		 */
		public synchronized void onServiceConnected(ComponentName className, IBinder binder) {
			Reader = CoverReader.Stub.asInterface(binder);
			for (Runnable action : myPostActions) {
				myExecutor.execute(action);
			}
			myPostActions.clear();
		}

		public synchronized void onServiceDisconnected(ComponentName className) {
			Reader = null;
		}
	}

	private final Context myContext;

	/**
	 * 当前建立连接的外部支持插件集合
	 */
	private final Map<ExternalFormatPlugin,Connection> myConnections =
		new HashMap<ExternalFormatPlugin,Connection>();

	public AndroidImageSynchronizer(Activity activity) {
		myContext = activity;
	}

	public AndroidImageSynchronizer(Service service) {
		myContext = service;
	}

	/**
	 * 开始图片加载
	 */
	@Override
	public void startImageLoading(ZLImageProxy image, Runnable postAction) {
		final ZLAndroidImageManager manager = (ZLAndroidImageManager)ZLAndroidImageManager.Instance();
		manager.startImageLoading(this, image, postAction);
	}

	/**
	 * 不同类型图片同步处理
	 */
	@Override
	public void synchronize(ZLImageProxy image, final Runnable postAction) {
		// Step 1: 图片加载同步完成，则执行任务
		if (image.isSynchronized()) {
			// TODO: also check if image is under synchronization
			if (postAction != null) {
				postAction.run();
			}
		} else if (image instanceof ZLImageSimpleProxy) { // Step 2: NetworkImage 或 ZLFileImageProxy,执行同步加载图片，然后执行任务（刷新ui）
			((ZLImageSimpleProxy)image).synchronize();
			if (postAction != null) {
				postAction.run();
			}
		} else if (image instanceof PluginImage) {
			// Step 3： 外部支持插件图书，建立服务连接
			final PluginImage pluginImage = (PluginImage)image;
			final Connection connection = getConnection(pluginImage.Plugin);

			// Step 4: 连接完成，执行任务
			connection.runOrAddAction(new Runnable() {
				public void run() {
					try {
						pluginImage.setRealImage(new ZLBitmapImage(connection.Reader.readBitmap(pluginImage.File.getPath(), Integer.MAX_VALUE, Integer.MAX_VALUE)));
					} catch (Throwable t) {
						t.printStackTrace();
					}
					if (postAction != null) {
						postAction.run();
					}
				}
			});
		} else {
			throw new RuntimeException("Cannot synchronize " + image.getClass());
		}
	}

	/**
	 * 清空解绑全部外部插件服务
	 */
	public synchronized void clear() {
		for (ServiceConnection connection : myConnections.values()) {
			myContext.unbindService(connection);
		}
		myConnections.clear();
	}

	/**
	 * 获取外部插件的服务连接对象
	 */
	private synchronized Connection getConnection(ExternalFormatPlugin plugin) {
		Connection connection = myConnections.get(plugin);
		// 服务未连接，则创建连接并绑定启动插件服务
		if (connection == null) {
			connection = new Connection(plugin);
			myConnections.put(plugin, connection);
			myContext.bindService(
				new Intent(FBReaderIntents.Action.PLUGIN_CONNECT_COVER_SERVICE)
					.setPackage(plugin.packageName()),
				connection,
				Context.BIND_AUTO_CREATE
			);
		}
		return connection;
	}
}
