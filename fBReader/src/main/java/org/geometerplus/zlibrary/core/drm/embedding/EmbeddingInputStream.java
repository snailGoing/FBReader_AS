/*
 * Copyright (C) 2007-2017 FBReader.ORG Limited <contact@fbreader.org>
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

package org.geometerplus.zlibrary.core.drm.embedding;

import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;

import org.geometerplus.zlibrary.core.util.InputStreamWithOffset;

/**
 * @Description: 嵌入式输入流，插入指定密文加密
 *
 * 加密算法：
 *   对输入流的前 1040 字节 byte 进行加密
 */
public class EmbeddingInputStream extends InputStreamWithOffset {

	/**
	 * 密钥
	 */
	private final byte[] myKey;

	public EmbeddingInputStream(InputStream base, String uid) throws IOException {
		super(base);
		// 信息摘要算法，用于生成散列码；相同明文经过相同算法输出相同的密文，且密文不可拟，可用于明文比较验证及加密等
		try {
			myKey = MessageDigest.getInstance("SHA").digest(uid.getBytes("utf-8"));
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	/**
	 * 读取
	 */
	@Override
	public int read() throws IOException {
		// Step 1: 当前读取偏移量
		final int o = offset();

		// Step 2: 流读取字节并更新偏移量
		final int bt = super.read();
		if (bt == -1) {
			return -1;
		}

		// Step 3:
		// 开始位置字节偏移量大于 1040 则返回实际字节值；
		// 否则，对该字节^ 异或（1和0为1，否则0）密钥（取余做索引的元素），再和255按位与（1和1为1，否则0）进行加密
		return o > 1040 ? bt : ((bt ^ myKey[o % myKey.length]) & 0xFF);
	}

	@Override
	public int read(byte[] buffer, int bOffset, int bCount) throws IOException {
		// Step 1: 记录读取开始位置
		final int o = offset();

		// Step 2: 读取多少个字节长度
		final int len = super.read(buffer, bOffset, bCount);

		// Step 3: 对于小于 1040 长度的字节进行加密
		if (o < 1040) {
			final int e = Math.min(1040 - o, len);
			for (int c = 0; c < e; ++c) {
				buffer[bOffset + c] ^= myKey[(o + c) % myKey.length];
			}
		}
		return len;
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		return read(buffer, 0, buffer.length);
	}
}
