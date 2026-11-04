package com.coolskyai.modules.ai.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @Description: 向量与字节的互转
 *
 * 存 float32 小端：512 维 = 2KB/块，1e4 块约 20MB，MySQL 毫无压力。
 * 比存 JSON 省一半空间，也省掉序列化开销。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
public final class VectorCodec {

	private VectorCodec() {
	}

	public static byte[] encode(float[] vector) {
		ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (float value : vector) {
			buffer.putFloat(value);
		}
		return buffer.array();
	}

	public static float[] decode(byte[] bytes) {
		if (bytes == null || bytes.length % 4 != 0) {
			return new float[0];
		}
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		float[] vector = new float[bytes.length / 4];
		for (int i = 0; i < vector.length; i++) {
			vector[i] = buffer.getFloat();
		}
		return vector;
	}

	/**
	 * 余弦相似度；两个向量都已归一化，点积即余弦
	 */
	public static float cosine(float[] a, float[] b) {
		if (a == null || b == null || a.length == 0 || a.length != b.length) {
			return 0f;
		}
		float dot = 0f;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
		}
		return dot;
	}
}
