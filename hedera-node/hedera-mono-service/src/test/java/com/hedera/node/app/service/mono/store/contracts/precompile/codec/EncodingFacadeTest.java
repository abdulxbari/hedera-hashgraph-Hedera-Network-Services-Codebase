/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.node.app.service.mono.store.contracts.precompile.codec;

import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.junit.jupiter.api.Test;

class EncodingFacadeTest {
    private final EncodingFacade subject = new EncodingFacade();

    private static final Bytes RETURN_FUNGIBLE_MINT_FOR_10_TOKENS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                        + "0000000000000000000000000000000000000000000000000000000000"
                        + "00000a0000000000000000000000000000000000000000000000000000"
                        + "0000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                        + "0000000000000000000000000000000000000000000000000000000000"
                        + "0000020000000000000000000000000000000000000000000000000000"
                        + "00000000006000000000000000000000000000000000000000000000000"
                        + "00000000000000002000000000000000000000000000000000000000000"
                        + "00000000000000000000010000000000000000000000000000000000000000000000000000000000000002");
    private static final Bytes RETURN_BURN_FOR_49_TOKENS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                            + "0000000000000000000000000000000000000000000000000000000000000031");
    private static final Bytes MINT_FAILURE_FROM_INVALID_TOKEN_ID =
            Bytes.fromHexString(
                    "0x00000000000000000000000000000000000000000000000000000000000000a7"
                        + "0000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000"
                        + "0000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes BURN_FAILURE_FROM_TREASURY_NOT_OWNER =
            Bytes.fromHexString(
                    "0x00000000000000000000000000000000000000000000000000000000000000fc"
                            + "0000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_SUCCESS_3 =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                            + "0000000000000000000000000000000000000000000000000000000000000003");

    private static final Bytes RETURN_TRUE =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000001");

    private static final Bytes RETURN_SUCCESS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016");

    private static final Bytes RETURN_SUCCESS_TRUE =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                            + "0000000000000000000000000000000000000000000000000000000000000001");

    private static final Bytes TRANSFER_EVENT =
            Bytes.fromHexString("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");

    private static final Bytes RETURN_CREATE_SUCCESS =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016"
                            + "0000000000000000000000000000000000000000000000000000000000000008");

    private static final Bytes CREATE_FAILURE_FROM_INVALID_EXPIRATION_TIME =
            Bytes.fromHexString(
                    "0x000000000000000000000000000000000000000000000000000000000000002d"
                            + "0000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_GET_TOKEN_INFO =
            Bytes.fromHexString(
                    "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000001f40000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000d000000000000000000000000000000000000000000000000000000000000000dc00000000000000000000000000000000000000000000000000000000000000ea00000000000000000000000000000000000000000000000000000000000000ec0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a000000000000000000000000000000000000000000000000000000000000005cc00000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000003e800000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000006347f08b00000000000000000000000000000000000000000000000000000000000005cd000000000000000000000000000000000000000000000000000000000076a70000000000000000000000000000000000000000000000000000000000000000077072696d617279000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008495144584446444500000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044a554d5000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000700000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000000000036000000000000000000000000000000000000000000000000000000000000004a000000000000000000000000000000000000000000000000000000000000005e000000000000000000000000000000000000000000000000000000000000007200000000000000000000000000000000000000000000000000000000000000860000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020712681b9868a3e526507d8983486ef14c2626fc7bf170543a0668afa850059a00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200a0fde1c21c4b77e8a5fb6ddd68da4a6e6d22d9cfdb7202260f8731cc4a218c40000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020a0674fe13373a2bf9492f50435bb852c0ee34d18c205d1d75c746412bd6e6ee90000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002088f4700b5519be4cadd40bd81cd4d5e22e3cfb580631348e73e655a9ca358a8c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020696ec08bed92e0987c694971e0c978781a9abc3f7dae06fe5dfaaa68f5c5201e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002095b1ec8737e9baa2b640967f1524103b4f62e3683708a650a985afa9f510d8200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000204c577c652b075ebd79abdfa99501997035aec91b32fd058773625466bdd0ff370000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000001f400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005ce00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000050000000000000000000000000000000000000000000000000000000000000190000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005cc000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043078303300000000000000000000000000000000000000000000000000000000");

    private static final Bytes RETURN_GET_FUNGIBLE_TOKEN_INFO =
            Bytes.fromHexString(
                    "0x0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000001f40000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000d000000000000000000000000000000000000000000000000000000000000000dc00000000000000000000000000000000000000000000000000000000000000ea00000000000000000000000000000000000000000000000000000000000000ec0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a000000000000000000000000000000000000000000000000000000000000005d900000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000003e800000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000006347fc5800000000000000000000000000000000000000000000000000000000000005da000000000000000000000000000000000000000000000000000000000076a700000000000000000000000000000000000000000000000000000000000000000d46756e6769626c65546f6b656e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002465400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044a554d5000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000700000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000000000036000000000000000000000000000000000000000000000000000000000000004a000000000000000000000000000000000000000000000000000000000000005e000000000000000000000000000000000000000000000000000000000000007200000000000000000000000000000000000000000000000000000000000000860000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000201c45a31396a49d6c0925bb17ebc029b7d6abd782e0bf4028e80941be6de323f90000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020fd12cf7240b47d9637f6bc4a10a08c5690f2121a209a58f024c4221f43656ba90000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002041265a93fe962db8966d413db831e9054770fd3ed2ab9e2d331b17fb5af43c0b0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002059558acd3552e3acca2deed0f7b29effd12fdc840921dda5a521637a73c835bf0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020d6d69c2f666dae5eceacd13a209faf9219c8c3b6ab8e124a166814cd60f1f57a0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020d765fef98c141e8d249e42aa2933a496f681ee433030122f7c313d0db3c2c52c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020d561aedce1db98aee5b122d44adaddd7731be342ac81f83060d0d2565078ee6000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000028e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005db00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000050000000000000000000000000000000000000000000000000000000000000190000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005d9000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043078303300000000000000000000000000000000000000000000000000000000");

    final Address logger = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);

    @Test
    void canEncodeEip1014Address() {
        final var literalEip1014 = "0x8ff8eb31713b9ff374d893d21f3b9eb732a307a5";
        final var besuAddress = Address.fromHexString(literalEip1014);
        final var headlongAddress = EncodingFacade.convertBesuAddressToHeadlongAddress(besuAddress);
        assertEquals(literalEip1014, ("" + headlongAddress).toLowerCase());
    }

    @Test
    void decodeReturnResultForFungibleMint() {
        final var decodedResult = subject.encodeMintSuccess(10, null);
        assertEquals(RETURN_FUNGIBLE_MINT_FOR_10_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForNonFungibleMint() {
        final var decodedResult = subject.encodeMintSuccess(2, new long[] {1, 2});
        assertEquals(RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForBurn() {
        final var decodedResult = subject.encodeBurnSuccess(49);
        assertEquals(RETURN_BURN_FOR_49_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForCreateSuccess() {
        final var decodedResult = subject.encodeCreateSuccess(senderAddress);
        assertEquals(RETURN_CREATE_SUCCESS, decodedResult);
    }

    @Test
    void decodeReturnResultForCreateFailure() {
        final var decodedResult = subject.encodeCreateFailure(INVALID_EXPIRATION_TIME);
        assertEquals(CREATE_FAILURE_FROM_INVALID_EXPIRATION_TIME, decodedResult);
    }

    @Test
    void decodeReturnResultForTransfer() {
        final var decodedResult = subject.encodeEcFungibleTransfer(true);
        assertEquals(RETURN_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForApproveERC() {
        final var decodedResult = subject.encodeApprove(true);
        assertEquals(RETURN_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForApproveHAPI() {
        final var decodedResult = subject.encodeApprove(SUCCESS.getNumber(), true);
        assertEquals(RETURN_SUCCESS_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForApproveNFTHAPI() {
        final var decodedResult = subject.encodeApproveNFT(SUCCESS.getNumber());
        assertEquals(RETURN_SUCCESS, decodedResult);
    }

    @Test
    void decodeReturnResultForIsApprovedForAllHAPI() {
        final var decodedResult = subject.encodeIsApprovedForAll(SUCCESS.getNumber(), true);
        assertEquals(RETURN_SUCCESS_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForAllowanceHAPI() {
        final var decodedResult = subject.encodeAllowance(SUCCESS.getNumber(), 3);
        assertEquals(RETURN_SUCCESS_3, decodedResult);
    }

    @Test
    void decodeReturnResultForGetApprovedHAPI() {
        final var decodedResult = subject.encodeGetApproved(SUCCESS.getNumber(), senderAddress);
        assertEquals(RETURN_CREATE_SUCCESS, decodedResult);
    }

    @Test
    void logBuilderWithTopics() {
        final var log =
                EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(logger)
                        .forEventSignature(TRANSFER_EVENT)
                        .forIndexedArgument(senderAddress)
                        .forIndexedArgument(recipientAddress)
                        .build();

        final List<LogTopic> topics = new ArrayList<>();
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000008")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000006")));

        assertEquals(new Log(logger, Bytes.EMPTY, topics), log);
    }

    @Test
    void logBuilderWithTopicsWithDifferentTypes() {
        final var log =
                EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(logger)
                        .forEventSignature(TRANSFER_EVENT)
                        .forIndexedArgument(senderAddress)
                        .forIndexedArgument(20L)
                        .forIndexedArgument(BigInteger.valueOf(20))
                        .forIndexedArgument(Boolean.TRUE)
                        .forIndexedArgument(false)
                        .build();

        final List<LogTopic> topics = new ArrayList<>();
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000008")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000014")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000014")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000001")));
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0x0000000000000000000000000000000000000000000000000000000000000000")));

        assertEquals(new Log(logger, Bytes.EMPTY, topics), log);
    }

    @Test
    void logBuilderWithData() {
        final var tupleType = TupleType.parse("(address,uint256,uint256,bool,bool)");
        final var log =
                EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(logger)
                        .forEventSignature(TRANSFER_EVENT)
                        .forDataItem(senderAddress)
                        .forDataItem(9L)
                        .forDataItem(BigInteger.valueOf(9))
                        .forDataItem(Boolean.TRUE)
                        .forDataItem(false)
                        .build();

        final var dataItems = new ArrayList<>();
        dataItems.add(convertBesuAddressToHeadlongAddress(senderAddress));
        dataItems.add(BigInteger.valueOf(9));
        dataItems.add(BigInteger.valueOf(9));
        dataItems.add(true);
        dataItems.add(false);
        final var tuple = Tuple.of(dataItems.toArray());

        final List<LogTopic> topics = new ArrayList<>();
        topics.add(
                LogTopic.wrap(
                        Bytes.fromHexString(
                                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));

        assertEquals(new Log(logger, Bytes.wrap(tupleType.encode(tuple).array()), topics), log);
    }

    @Test
    void createsExpectedMintFailureResult() {
        assertEquals(
                MINT_FAILURE_FROM_INVALID_TOKEN_ID, subject.encodeMintFailure(INVALID_TOKEN_ID));
    }

    @Test
    void createsExpectedBurnFailureResult() {
        assertEquals(
                BURN_FAILURE_FROM_TREASURY_NOT_OWNER,
                subject.encodeBurnFailure(TREASURY_MUST_OWN_BURNED_NFT));
    }

    private Key initializeKey(final byte[] ed25519KeyValue) {
        return Key.newBuilder().setEd25519(ByteString.copyFrom(ed25519KeyValue)).build();
    }

    private ByteString fromHexString(final String value) {
        return ByteString.copyFrom(Bytes.fromHexString(value).toArray());
    }

    private com.esaulpaugh.headlong.abi.Address convertBesuAddressToHeadlongAddress(
            final Address addressToBeConverted) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(
                        addressToBeConverted.toBigInteger()));
    }
}
