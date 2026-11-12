package com.kingzcheung.xime.glide.neural;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.pytorch.executorch.DType;
import org.pytorch.executorch.Tensor;

/**
 * ExecuTorch Java 层没有 BOOL Tensor 子类，但原生运行时支持 bool 张量。
 *
 * 这个类只负责把 0/1 字节缓冲交给 JNI；已用 instrumentation 验证
 * FUTO 编码器的 layout_mask 可以这样传入（否则 JNI 会报 expected Bool）。
 */
public final class FutoBoolTensor extends Tensor {
    private final ByteBuffer data;

    public FutoBoolTensor(boolean[] values, long[] shape) {
        super(shape);
        ByteBuffer buffer = ByteBuffer.allocateDirect(values.length);
        buffer.order(ByteOrder.nativeOrder());
        for (boolean value : values) {
            buffer.put((byte) (value ? 1 : 0));
        }
        buffer.rewind();
        this.data = buffer;
    }

    @Override
    public DType dtype() {
        return DType.BOOL;
    }

    @Override
    public Buffer getRawDataBuffer() {
        return data;
    }
}
