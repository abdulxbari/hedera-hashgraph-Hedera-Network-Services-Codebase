package com.hedera.services.legacy.core.jproto;

import static com.hedera.services.utils.EntityIdUtils.EVM_ADDRESS_SIZE;

public class JHollowKey extends JKey {

  // TODO: finish this class up

  private byte[] evmAddress;

  public byte[] getEvmAddress() {
    return evmAddress;
  }

  public JHollowKey(final byte[] evmAddress) {
    this.evmAddress = evmAddress;
  }

  @Override
  public boolean isEmpty() {
    return ((null == evmAddress) || (0 == evmAddress.length));
  }

  @Override
  public boolean isValid() {
    return !(isEmpty()
        || (evmAddress.length != EVM_ADDRESS_SIZE));
  }

  @Override
  public boolean hasHollowKey() {
    return true;
  }

  @Override
  public JHollowKey getHollowKey() {
    return this;
  }
}
