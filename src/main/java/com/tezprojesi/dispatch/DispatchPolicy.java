package com.tezprojesi.dispatch;

import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.DepotType;
import com.tezprojesi.domain.Order;
import com.tezprojesi.domain.Vehicle;
import java.util.Map;
import java.util.Objects;

public final class DispatchPolicy {

	private final long currentTick;
	private final long maxMainWaitTicks;
	private final double minMainDepartureLoadFraction;
	private final Map<String, Long> vehicleWaitingSinceAtMain;

	public DispatchPolicy(long currentTick,
												long maxMainWaitTicks,
												double minMainDepartureLoadFraction,
												Map<String, Long> vehicleWaitingSinceAtMain) {
		if (maxMainWaitTicks < 0) throw new IllegalArgumentException("maxMainWaitTicks must be >= 0");
		if (minMainDepartureLoadFraction <= 0 || minMainDepartureLoadFraction > 1.0) {
			throw new IllegalArgumentException("minMainDepartureLoadFraction must be in (0,1]");
		}
		this.currentTick = currentTick;
		this.maxMainWaitTicks = maxMainWaitTicks;
		this.minMainDepartureLoadFraction = minMainDepartureLoadFraction;
		this.vehicleWaitingSinceAtMain = Objects.requireNonNull(vehicleWaitingSinceAtMain, "vehicleWaitingSinceAtMain");
	}

	public void checkDepartureAllowed(Depot depot, Vehicle vehicle, Order order) {
		Objects.requireNonNull(depot, "depot");
		Objects.requireNonNull(vehicle, "vehicle");
		Objects.requireNonNull(order, "order");

		if (depot.getType() != DepotType.MAIN) {
			return; // constrain only MAIN depot departures
		}
		double projectedLoad = vehicle.getCurrentLoad() + order.getDemandVolume();
		double requiredLoad = minMainDepartureLoadFraction * vehicle.getCapacityVolume();
		if (projectedLoad >= requiredLoad) {
			return; // enough load
		}
		long waitingSince = vehicleWaitingSinceAtMain.getOrDefault(vehicle.getId(), currentTick);
		long waitedTicks = Math.max(0, currentTick - waitingSince);
		if (waitedTicks >= maxMainWaitTicks) {
			return; // waited long enough
		}
		throw new IllegalStateException("Departure blocked: require >= " + (int)(minMainDepartureLoadFraction*100) + "% load or >= " + maxMainWaitTicks + " ticks wait at MAIN");
	}
}

