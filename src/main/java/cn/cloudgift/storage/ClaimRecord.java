package cn.cloudgift.storage;

/** A player's persisted claim state for a single gift. */
public record ClaimRecord(long lastClaimAt, int claimCount) {
}
