package cn.cloudgift.storage;

public record ClaimAttempt(boolean accepted, long lastClaimAt) {

    public static ClaimAttempt accepted(long claimedAt) {
        return new ClaimAttempt(true, claimedAt);
    }

    public static ClaimAttempt rejected(long lastClaimAt) {
        return new ClaimAttempt(false, lastClaimAt);
    }
}
