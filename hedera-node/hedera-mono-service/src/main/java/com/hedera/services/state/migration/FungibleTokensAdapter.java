package com.hedera.services.state.migration;

import com.hedera.services.state.merkle.HederaToken;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.entities.FungibleOnDiskToken;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.crypto.Hash;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang3.NotImplementedException;

public class FungibleTokensAdapter {

  /** Pointer to the underlying MerkleMap to interface with. null if unavailable. */
  @Nullable
  private final MerkleMap<EntityNum, MerkleToken> merkleMap;

  /** Pointer to the underlying VirtualMap to interface with. null if unavailable. */
  @Nullable private final VirtualMap<EntityNumVirtualKey, FungibleOnDiskToken> virtualMap;

  /** True if {@link #virtualMap} is set. False if {@link #merkleMap} is set. */
  private final boolean isVirtual;

  public static FungibleTokensAdapter newInMemory() {
    return fromInMemory(new MerkleMap<>());
  }

  public static FungibleTokensAdapter newOnDisk() {
    return fromOnDisk(new VirtualMap<>());
  }

  public static FungibleTokensAdapter fromInMemory(MerkleMap<EntityNum, MerkleToken> tokenStorage) {
    return new FungibleTokensAdapter(tokenStorage);
  }

  public static FungibleTokensAdapter fromOnDisk(
      VirtualMap<EntityNumVirtualKey, FungibleOnDiskToken> tokenStorage) {
    return new FungibleTokensAdapter(tokenStorage);
  }

  FungibleTokensAdapter(final VirtualMap<EntityNumVirtualKey, FungibleOnDiskToken> virtualMap) {
    isVirtual = true;
    this.virtualMap = virtualMap;
    this.merkleMap = null;
  }

  FungibleTokensAdapter(final MerkleMap<EntityNum, MerkleToken> merkleMap) {
    isVirtual = false;
    this.merkleMap = merkleMap;
    this.virtualMap = null;
  }

  public void archive() {
    if(true)throw new NotImplementedException();
    if (!isOnDisk()) {
      //save
    }
  }

  private boolean isOnDisk() {
    throw new NotImplementedException();
  }

  public long size() {
    throw new NotImplementedException();
  }

  public HederaToken getForModify(EntityNum fromTokenId) {
    throw new NotImplementedException();
  }

  public boolean containsKey(EntityNum eId) {
    throw new NotImplementedException();
  }

  public void put(EntityNum eId, HederaToken token) {
    throw new NotImplementedException();
  }

  public void remove(EntityNum fromTokenId) {
    throw new NotImplementedException();
  }

  public Set<EntityNum> keySet() {
    throw new NotImplementedException();
  }

  public HederaToken get(EntityNum fromTokenId) {
    throw new NotImplementedException();
  }

  public HederaToken getOrDefault(EntityNum tokenNum, HederaToken removedToken) {
    throw new NotImplementedException();
  }

  public Hash getHash() {
    return isVirtual ? virtualMap.getHash() : merkleMap.getHash();
  }
}
