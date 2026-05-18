package com.tezprojesi.simulation;

public final class DispatchConstants {
    private DispatchConstants() {}

    // Multistopping Logic
    public static final double MULTI_STOP_DEVIATION_RATIO = 0.35;
    public static final double CARGO_VALUE_DIVISOR = 100.0;
    public static final double BASE_BYPASS_KM = 15.0;
    public static final int MAX_STOPS_PER_TRIP = 3; // Fixed for all vehicles

    // Inventory
    public static final int MAX_PRODUCT_CAPACITY = 100;
    public static final int AUTO_REPLENISH_TRIGGER = 40;
    public static final int AUTO_REPLENISH_TARGET = 80;

    // Fuel Multipliers (Tez Specs)
    public static final double ELECTRIC_FUEL_MULTIPLIER = 0.30;
    public static final double DIESEL_FUEL_MULTIPLIER = 0.55;
    public static final double GASOLINE_FUEL_MULTIPLIER = 0.75;
}
