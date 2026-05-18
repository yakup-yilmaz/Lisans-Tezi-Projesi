package com.tezprojesi.domain;

public enum OrderStatus {
  CREATED,
  WAITING_REPLENISHMENT,
  ASSIGNED,
  IN_TRANSIT,
  FULFILLED_FROM_MICRO,
  COMPLETED,
  FAILED
}
