package com.hedera.services.state.virtual;

import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.virtual.IterableMappingUtils.addMapping;
import static com.hedera.services.state.virtual.IterableMappingUtils.removeMapping;
import static com.hedera.services.store.contracts.SizeLimitedStorage.ZERO_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IterableMappingUtilsTest {
	@Mock
	private VirtualMap<ContractKey, ContractValue> storage;

	private final ContractValue rootValue = new ContractValue(rootEvmValue.toArray());
	private final ContractValue nextValue = new ContractValue(nextEvmValue.toArray());
	private final ContractValue targetValue = new ContractValue(targetEvmValue.toArray());

	@Test
	void canInsertToEmptyList() {
		final var newRoot = addMapping(
				targetKey, targetValue,
				null, null,
				storage);

		verify(storage).put(targetKey, targetValue);

		assertSame(targetKey, newRoot);
	}

	@Test
	void canInsertWithUnknownRootValue() {
		given(storage.getForModify(rootKey)).willReturn(rootValue);

		final var newRoot = addMapping(
				targetKey, targetValue,
				rootKey, null,
				storage);

		verify(storage).put(targetKey, targetValue);

		assertSame(targetKey, newRoot);
		assertNull(targetValue.getPrevKeyScopedTo(contractNum));
		assertEquals(rootKey, targetValue.getNextKeyScopedTo(contractNum));
		assertEquals(newRoot, rootValue.getPrevKeyScopedTo(contractNum));
	}

	@Test
	void canInsertWithPrefetchedValue() {
		final var newRoot = addMapping(
				targetKey, targetValue,
				rootKey, rootValue,
				storage);

		assertSame(targetKey, newRoot);
		assertNull(targetValue.getPrevKeyScopedTo(contractNum));
		assertEquals(rootKey, targetValue.getNextKeyScopedTo(contractNum));
		assertEquals(newRoot, rootValue.getPrevKeyScopedTo(contractNum));

		verify(storage).put(targetKey, targetValue);
		verify(storage, never()).getForModify(rootKey);
	}

	@Test
	void canRemoveFromRootWithNextValue() {
		rootValue.setNextKey(nextKey.getKey());
		nextValue.setPrevKey(rootKey.getKey());
		given(storage.getForModify(nextKey)).willReturn(nextValue);
		given(storage.get(rootKey)).willReturn(rootValue);

		final var newRoot = removeMapping(rootKey, rootKey, storage);

		assertEquals(nextKey, newRoot);
		assertNull(nextValue.getPrevKeyScopedTo(contractNum));

		verify(storage).put(rootKey, ZERO_VALUE);
	}

	@Test
	void canRemoveFromNonRootWithNextValue() {
		rootValue.setNextKey(targetKey.getKey());
		targetValue.setPrevKey(rootKey.getKey());
		targetValue.setNextKey(nextKey.getKey());
		nextValue.setPrevKey(targetKey.getKey());
		given(storage.getForModify(nextKey)).willReturn(nextValue);
		given(storage.getForModify(rootKey)).willReturn(rootValue);
		given(storage.get(targetKey)).willReturn(targetValue);

		final var newRoot = removeMapping(targetKey, rootKey, storage);

		assertEquals(rootKey, newRoot);
		assertEquals(rootKey, nextValue.getPrevKeyScopedTo(contractNum));
		assertEquals(nextKey, rootValue.getNextKeyScopedTo(contractNum));

		verify(storage).put(targetKey, ZERO_VALUE);
	}

	@Test
	void canRemoveFromNonRootEnd() {
		rootValue.setNextKey(targetKey.getKey());
		targetValue.setPrevKey(rootKey.getKey());
		given(storage.getForModify(rootKey)).willReturn(rootValue);
		given(storage.get(targetKey)).willReturn(targetValue);

		final var newRoot = removeMapping(targetKey, rootKey, storage);

		assertEquals(rootKey, newRoot);
		assertNull(rootValue.getNextKeyScopedTo(contractNum));

		verify(storage).put(targetKey, ZERO_VALUE);
	}

	@Test
	void canRemoveOnlyValue() {
		given(storage.get(rootKey)).willReturn(rootValue);

		final var newRoot = removeMapping(rootKey, rootKey, storage);

		assertNull(newRoot);

		verify(storage).put(rootKey, ZERO_VALUE);
	}

	private static final long contractNum = 1234;
	private static final AccountID contractId = AccountID.newBuilder().setAccountNum(contractNum).build();
	private static final UInt256 targetEvmKey = UInt256.fromHexString("0xaabbcc");
	private static final UInt256 rootEvmKey = UInt256.fromHexString("0xbbccdd");
	private static final UInt256 nextEvmKey = UInt256.fromHexString("0xffeedd");
	private static final ContractKey targetKey = ContractKey.from(contractId, targetEvmKey);
	private static final ContractKey rootKey = ContractKey.from(contractId, rootEvmKey);
	private static final ContractKey nextKey = ContractKey.from(contractId, nextEvmKey);
	private static final UInt256 targetEvmValue =
			UInt256.fromHexString("0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060");
	private static final UInt256 rootEvmValue =
			UInt256.fromHexString("0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563");
	private static final UInt256 nextEvmValue =
			UInt256.fromHexString("0x210aeca1542b62a2a60345a122326fc24ba6bc15424002f6362f13160ef3e563");
}