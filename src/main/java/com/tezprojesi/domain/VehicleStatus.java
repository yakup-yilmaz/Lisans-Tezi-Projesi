package com.tezprojesi.domain;

public enum VehicleStatus {
  IDLE,
  EN_ROUTE,
  LOADING,
  DELIVERING,
  RETURNING,
  /** Human-in-the-loop: vehicle is ready but must not depart until approveDispatch() is called. */
  WAITING_FOR_APPROVAL
}
