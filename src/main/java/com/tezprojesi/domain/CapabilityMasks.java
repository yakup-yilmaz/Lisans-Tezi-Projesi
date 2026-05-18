package com.tezprojesi.domain;

import java.util.Set;

/**
 * Fast O(1) capability matching for routing/dispatch: product required bits must be a subset of vehicle provided bits.
 * <p>
 * Bit layout mirrors {@link ProductCapability} order (STANDARD..MEDICAL_LOCK).
 */
public final class CapabilityMasks {

  public static final int BIT_STANDARD = 1;
  public static final int BIT_COOLED = 1 << 1;
  public static final int BIT_DEEP_FREEZE = 1 << 2;
  public static final int BIT_FRAGILE = 1 << 3;
  public static final int BIT_MEDICAL = 1 << 4;

  private CapabilityMasks() {}

  public static long productCapabilityMask(Set<ProductCapability> caps) {
    long m = 0L;
    for (ProductCapability c : caps) {
      m |= productCapabilityBit(c);
    }
    return m;
  }

  public static long productCapabilityBit(ProductCapability capability) {
    return switch (capability) {
      case STANDARD -> BIT_STANDARD;
      case COOLED_BOX -> BIT_COOLED;
      case DEEP_FREEZE -> BIT_DEEP_FREEZE;
      case FRAGILE_RACK -> BIT_FRAGILE;
      case MEDICAL_LOCK -> BIT_MEDICAL;
    };
  }

  public static long vehicleCapabilityMask(Set<VehicleCapability> caps) {
    long m = 0L;
    for (VehicleCapability c : caps) {
      m |= vehicleCapabilityBit(c);
    }
    return m;
  }

  public static long vehicleCapabilityBit(VehicleCapability capability) {
    return switch (capability) {
      case STANDARD_BOX -> BIT_STANDARD;
      case COOLED_BOX -> BIT_COOLED;
      case DEEP_FREEZER -> BIT_DEEP_FREEZE;
      case FRAGILE_RACK -> BIT_FRAGILE;
      case MEDICAL_LOCK -> BIT_MEDICAL;
    };
  }

  /**
   * True iff the vehicle provides every capability required by the OR of all product requirements on the shipment.
   */
  public static boolean vehicleCoversRequiredMask(long vehicleMask, long requiredProductMask) {
    return (vehicleMask & requiredProductMask) == requiredProductMask;
  }
}
