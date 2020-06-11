/*
 * Copyright (C) 2010-2015 FBReader.ORG Limited <contact@fbreader.org>
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

package org.geometerplus.zlibrary.core.image;

/**
 * @Date:  2020-06-11
 * @Description: 图片代理抽象类
 *
 * 子类：
 *   ｜
 *    -- {@link ZLImageSimpleProxy}
 *   |
 *    -- {@link org.geometerplus.fbreader.formats.PluginImage}
 */
public abstract class ZLImageProxy implements ZLImage {

	/**
	 * 同步器接口
	 */
	public interface Synchronizer {

		/**
		 * 开始图片加载
		 */
		void startImageLoading(ZLImageProxy image, Runnable postAction);

		/**
		 * 同步
		 */
		void synchronize(ZLImageProxy image, Runnable postAction);
	}

	private volatile boolean myIsSynchronized;

	/**
	 * 是否已经同步
	 */
	public final boolean isSynchronized() {
		if (myIsSynchronized && isOutdated()) {
			myIsSynchronized = false;
		}
		return myIsSynchronized;
	}

	protected final void setSynchronized() {
		myIsSynchronized = true;
	}

	/**
	 * 超期
	 */
	protected boolean isOutdated() {
		return false;
	}

	/**
	 * 开始同步
	 *
	 * 用途：加载图片，完成后执行 postAction 任务
	 * Example： {@link org.geometerplus.fbreader.network.NetworkImage} 图片，下载后执行刷新显示到View UI
	 * @see org.geometerplus.android.fbreader.network.NetworkBookInfoActivity#setupCover
	 */
	public void startSynchronization(Synchronizer synchronizer, Runnable postAction) {
		synchronizer.startImageLoading(this, postAction);
	}

	public static enum SourceType {
		FILE,
		NETWORK,
		SERVICE;
	};
	public abstract SourceType sourceType();

	public abstract ZLImage getRealImage();
	public abstract String getId();

	@Override
	public String toString() {
		return getClass().getName() + "[" + getId() + "; synchronized=" + isSynchronized() + "]";
	}
}
