package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmTokenInfoPrecompileTest {

  public static final Bytes GET_TOKEN_INFO_INPUT =
      Bytes.fromHexString(
          "0x1f69565f000000000000000000000000000000000000000000000000000000000000000a");

  @Test
  void decodeTokenInfo() {
    final var decodedInput = EvmTokenInfoPrecompile.decodeGetTokenInfo(GET_TOKEN_INFO_INPUT);

    assertTrue(decodedInput.token().length > 0);
    assertEquals(-1, decodedInput.serialNumber());
  }
}
