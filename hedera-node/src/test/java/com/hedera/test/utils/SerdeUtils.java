package com.hedera.test.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractAliasKey;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.EvmLog;
import com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import com.hedera.services.utils.BytesComparator;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.SplittableRandom;
import java.io.UncheckedIOException;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;

public class SerdeUtils {
	public static byte[] serOutcome(ThrowingConsumer<DataOutputStream> serializer) throws Exception {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			try (SerializableDataOutputStream out = new SerializableDataOutputStream(baos)) {
				serializer.accept(out);
			}
			return baos.toByteArray();
		}
	}

	public static <T> T deOutcome(ThrowingFunction<DataInputStream, T> deserializer, byte[] repr) throws Exception {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(repr)) {
			try (SerializableDataInputStream in = new SerializableDataInputStream(bais)) {
				return deserializer.apply(in);
			}
		}
	}

	public static ThrottleDefinitions protoDefs(
			String testResource
	) throws IOException {
		try (InputStream in = ThrottlesJsonToProtoSerde.class.getClassLoader().getResourceAsStream(testResource)) {
			return ThrottlesJsonToProtoSerde.loadProtoDefs(in);
		}
	}

	public static com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions pojoDefs(
			String testResource
	) throws IOException {
		try (InputStream in = ThrottlesJsonToProtoSerde.class.getClassLoader().getResourceAsStream(testResource)) {
			return ThrottlesJsonToProtoSerde.loadPojoDefs(in);
		}
	}

	public static EvmFnResult fromGrpc(final ContractFunctionResult that) {
		return new EvmFnResult(
				that.hasContractID() ? EntityId.fromGrpcContractId(that.getContractID()) : null,
				that.getContractCallResult().isEmpty() ? EvmFnResult.EMPTY : that.getContractCallResult().toByteArray(),
				!that.getContractCallResult().isEmpty() ? that.getErrorMessage() : null,
				that.getBloom().isEmpty() ? EvmFnResult.EMPTY : that.getBloom().toByteArray(),
				that.getGasUsed(),
				that.getLogInfoList().stream().map(SerdeUtils::fromGrpc).toList(),
				that.getCreatedContractIDsList().stream().map(EntityId::fromGrpcContractId).toList(),
				that.hasEvmAddress() ? that.getEvmAddress().getValue().toByteArray() : EvmFnResult.EMPTY,
				that.getStateChangesList().stream().collect(Collectors.toMap(
						csc -> Address.wrap(Bytes.wrap(asEvmAddress(csc.getContractID()))),
						csc -> csc.getStorageChangesList().stream().collect(Collectors.toMap(
								sc -> Bytes.wrap(sc.getSlot().toByteArray()).trimLeadingZeros(),
								sc -> Pair.of(
										Bytes.wrap(sc.getValueRead().toByteArray()).trimLeadingZeros(),
										!sc.hasValueWritten() ? null :
												Bytes.wrap(
														sc.getValueWritten().getValue().toByteArray()).trimLeadingZeros()),
								(l, r) -> l,
								() -> new TreeMap<>(BytesComparator.INSTANCE)
						)),
						(l, r) -> l,
						() -> new TreeMap<>(BytesComparator.INSTANCE))),
				that.getGas(),
				that.getAmount(),
				that.getFunctionParameters().isEmpty() ? EvmFnResult.EMPTY : that.getFunctionParameters().toByteArray()
		);
	}

	public static EvmLog fromGrpc(ContractLoginfo grpc) {
		return new EvmLog(
				grpc.hasContractID() ? EntityId.fromGrpcContractId(grpc.getContractID()) : null,
				grpc.getBloom().isEmpty() ? EvmLog.MISSING_BYTES : grpc.getBloom().toByteArray(),
				grpc.getTopicList().stream().map(ByteString::toByteArray).toList(),
				grpc.getData().isEmpty() ? EvmLog.MISSING_BYTES : grpc.getData().toByteArray());
	}

	public static <T extends SelfSerializable> T deserializeFromBytes(
			final Supplier<T> factory,
			final int version,
			final byte[] serializedForm
	) {
		final var reconstruction = factory.get();

		final var bais = new ByteArrayInputStream(serializedForm);
		final var in = new SerializableDataInputStream(bais);
		try {
			reconstruction.deserialize(in, version);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return reconstruction;
	}

	public static <T extends SelfSerializable> String serializeToHex(final T source) {
		return CommonUtils.hex(serialize(source));
	}

	public static <T extends SelfSerializable> byte[] serialize(final T source) {
		final var baos = new ByteArrayOutputStream();
		final var out = new SerializableDataOutputStream(baos);
		try {
			source.serialize(out);
			out.flush();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return baos.toByteArray();
	}

	@FunctionalInterface
	public interface ThrowingConsumer<T> {
		void accept(T t) throws Exception;
	}

	@FunctionalInterface
	public interface ThrowingFunction<T, R> {
		R apply(T t) throws Exception;
	}
}
