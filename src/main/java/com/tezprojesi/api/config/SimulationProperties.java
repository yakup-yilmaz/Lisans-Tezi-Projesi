package com.tezprojesi.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulation")
public class SimulationProperties {
	private int maxTicks = 10000;
	private double orderRate = 1.0;
	private double minOrderVolume = 1.0;
	private double maxOrderVolume = 10.0;
	private double minTrafficFactor = 0.8;
	private double maxTrafficFactor = 1.4;
	private boolean autostart = true;
	private int tickDurationMinutes = 1; // 1 tick = 1 minute
	private long liveTickDelayMs = 2000L;
	private double minMainDepartureLoadFraction = 0.7;
	private double maxMainWaitHours = 5.0;
	private boolean dispatchApprovalRequired = false;
	/** Mikro depoda stok, baseline'ın bu kesrine düşünce otomatik ikmal talebi (örn. 0.30 ≈ yüzde 30). */
	private double reorderPointFraction = 0.50;

	public int getMaxTicks() {
		return maxTicks;
	}

	public void setMaxTicks(int maxTicks) {
		this.maxTicks = maxTicks;
	}

	public double getOrderRate() {
		return orderRate;
	}

	public void setOrderRate(double orderRate) {
		this.orderRate = orderRate;
	}

	public double getMinOrderVolume() {
		return minOrderVolume;
	}

	public void setMinOrderVolume(double minOrderVolume) {
		this.minOrderVolume = minOrderVolume;
	}

	public double getMaxOrderVolume() {
		return maxOrderVolume;
	}

	public void setMaxOrderVolume(double maxOrderVolume) {
		this.maxOrderVolume = maxOrderVolume;
	}

	public double getMinTrafficFactor() {
		return minTrafficFactor;
	}

	public void setMinTrafficFactor(double minTrafficFactor) {
		this.minTrafficFactor = minTrafficFactor;
	}

	public double getMaxTrafficFactor() {
		return maxTrafficFactor;
	}

	public void setMaxTrafficFactor(double maxTrafficFactor) {
		this.maxTrafficFactor = maxTrafficFactor;
	}

	public boolean isAutostart() {
		return autostart;
	}

	public void setAutostart(boolean autostart) {
		this.autostart = autostart;
	}

	public int getTickDurationMinutes() {
		return tickDurationMinutes;
	}

	public void setTickDurationMinutes(int tickDurationMinutes) {
		this.tickDurationMinutes = tickDurationMinutes;
	}

	public long getLiveTickDelayMs() {
		return liveTickDelayMs;
	}

	public void setLiveTickDelayMs(long liveTickDelayMs) {
		this.liveTickDelayMs = liveTickDelayMs;
	}

	public double getMinMainDepartureLoadFraction() {
		return minMainDepartureLoadFraction;
	}

	public void setMinMainDepartureLoadFraction(double minMainDepartureLoadFraction) {
		this.minMainDepartureLoadFraction = minMainDepartureLoadFraction;
	}

	public double getMaxMainWaitHours() {
		return maxMainWaitHours;
	}

	public void setMaxMainWaitHours(double maxMainWaitHours) {
		this.maxMainWaitHours = maxMainWaitHours;
	}

	public boolean isDispatchApprovalRequired() {
		return dispatchApprovalRequired;
	}

	public void setDispatchApprovalRequired(boolean dispatchApprovalRequired) {
		this.dispatchApprovalRequired = dispatchApprovalRequired;
	}

	public double getReorderPointFraction() {
		return reorderPointFraction;
	}

	public void setReorderPointFraction(double reorderPointFraction) {
		if (reorderPointFraction <= 0 || reorderPointFraction > 1) {
			throw new IllegalArgumentException("reorderPointFraction must be in (0,1]");
		}
		this.reorderPointFraction = reorderPointFraction;
	}
}

