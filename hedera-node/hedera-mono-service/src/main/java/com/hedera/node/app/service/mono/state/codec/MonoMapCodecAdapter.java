package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.DataBuffer;
import com.hedera.pbj.runtime.io.DataInput;
import com.hedera.pbj.runtime.io.DataInputStream;
import com.hedera.pbj.runtime.io.DataOutput;
import com.hedera.pbj.runtime.io.DataOutputStream;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * A temporary utility that constructs a {@link Codec} for a {@link SelfSerializable}, {@link VirtualKey},
 * or {@link VirtualValue} type. This is only useful for adapting parts of the {@code mono-service} Merkle
 * tree, since we expect key and value types in {@code hedera-app} to enjoy protobuf serialization.
 *
 * <p>Note that {@code fastEquals()} is not implemented in any case; and only the {@link VirtualKey}
 * codec needs to implement {@code measure()} and {@code typicalSize()}.
 *
 * <p>Also note the {@link SelfSerializable} codec are only usable with a
 * {@code SerializableDataInputStream} and {@code SerializableDataOutputStream}.
 */
public class MonoMapCodecAdapter {
    private MonoMapCodecAdapter() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T extends SelfSerializable> Codec<T> codecForSelfSerializable(
            final int version, final Supplier<T> factory) {
        return new Codec<>() {
            @NonNull
            @Override
            public T parse(final @NonNull DataInput input) throws IOException {
                final var item = factory.get();
                if (input instanceof DataInputStream in) {
                    item.deserialize(new SerializableDataInputStream(in), version);
                } else {
                    throw new IllegalArgumentException("Expected a DataInputStream, but found: " + input.getClass());
                }
                return item;
            }

            @NonNull
            @Override
            public T parseStrict(@NonNull DataInput dataInput) throws IOException {
                return parse(dataInput);
            }

            @Override
            public void write(final @NonNull T item, final @NonNull DataOutput output) throws IOException {
                if (output instanceof DataOutputStream out) {
                    item.serialize(new SerializableDataOutputStream(out));
                } else {
                    throw new IllegalArgumentException("Expected a DataOutputStream, but found: " + output.getClass());
                }
            }

            @Override
            public int measure(final @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int measureRecord(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(@NonNull T item, @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static <T extends VirtualKey<?>> Codec<T> codecForVirtualKey(
            final int version, final Supplier<T> factory, final KeySerializer<T> keySerializer) {
        return new Codec<>() {
            @NonNull
            @Override
            public T parse(final @NonNull DataInput input) throws IOException {
                final var item = factory.get();
                if (input instanceof DataInputStream in) {
                    item.deserialize(new SerializableDataInputStream(in), version);
                } else if (input instanceof DataBuffer dataBuffer) {
                    // TODO: Is it possible to get direct access to the underlying ByteBuffer here?
                    final var byteBuffer = ByteBuffer.allocate(dataBuffer.getCapacity());
                    dataBuffer.readBytes(byteBuffer);
                    // TODO: Remove the following line once this was fixed in DataBuffer
                    dataBuffer.skip(dataBuffer.getRemaining());
                    byteBuffer.rewind();
                    item.deserialize(byteBuffer, version);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataInput type: " + input.getClass().getName());
                }
                return item;
            }

            @NonNull
            @Override
            public T parseStrict(@NonNull DataInput dataInput) throws IOException {
                return parse(dataInput);
            }

            @Override
            public void write(final @NonNull T item, final @NonNull DataOutput output) throws IOException {
                if (output instanceof DataOutputStream out) {
                    item.serialize(new SerializableDataOutputStream(out));
                } else if (output instanceof DataBuffer dataBuffer) {
                    // TODO: Is it possible to get direct access to the underlying ByteBuffer here?
                    final var byteBuffer = ByteBuffer.allocate(dataBuffer.getCapacity());
                    item.serialize(byteBuffer);
                    byteBuffer.rewind();
                    dataBuffer.writeBytes(byteBuffer);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataOutput type: " + output.getClass().getName());
                }
            }

            @Override
            public int measure(final @NonNull DataInput input) {
                return keySerializer.getSerializedSize();
            }

            @Override
            public int measureRecord(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(@NonNull T item, @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static <T extends VirtualValue> Codec<T> codecForVirtualValue(
            final int version, final Supplier<T> factory) {
        return new Codec<>() {
            @NonNull
            @Override
            public T parse(final @NonNull DataInput input) throws IOException {
                final var item = factory.get();
                if (input instanceof DataInputStream in) {
                    item.deserialize(new SerializableDataInputStream(in), version);
                } else if (input instanceof DataBuffer dataBuffer) {
                    // TODO: Is it possible to get direct access to the underlying ByteBuffer here?
                    final var byteBuffer = ByteBuffer.allocate(dataBuffer.getCapacity());
                    dataBuffer.readBytes(byteBuffer);
                    // TODO: Remove the following line once this was fixed in DataBuffer
                    dataBuffer.skip(dataBuffer.getRemaining());
                    byteBuffer.rewind();
                    item.deserialize(byteBuffer, version);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataInput type: " + input.getClass().getName());
                }
                return item;
            }

            @NonNull
            @Override
            public T parseStrict(@NonNull DataInput dataInput) throws IOException {
                return parse(dataInput);
            }

            @Override
            public void write(final @NonNull T item, final @NonNull DataOutput output) throws IOException {
                if (output instanceof DataOutputStream out) {
                    item.serialize(new SerializableDataOutputStream(out));
                } else if (output instanceof DataBuffer dataBuffer) {
                    // TODO: Is it possible to get direct access to the underlying ByteBuffer here?
                    final var byteBuffer = ByteBuffer.allocate(dataBuffer.getCapacity());
                    item.serialize(byteBuffer);
                    byteBuffer.rewind();
                    dataBuffer.writeBytes(byteBuffer);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported DataOutput type: " + output.getClass().getName());
                }
            }

            @Override
            public int measure(final @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int measureRecord(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(final @NonNull T item, final @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }
}