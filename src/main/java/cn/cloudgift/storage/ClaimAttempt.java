package cn.cloudgift.storage;

public record ClaimAttempt(Status status, long lastClaimAt, int claimCount) {

    public enum Status {
        ACCEPTED,
        COOLDOWN,
        LIMIT_REACHED
    }

    public static ClaimAttempt accepted(long claimedAt, int claimCount) {
        return new ClaimAttempt(Status.ACCEPTED, claimedAt, claimCount);
    }

    public static ClaimAttempt cooldown(long lastClaimAt, int claimCount) {
        return new ClaimAttempt(Status.COOLDOWN, lastClaimAt, claimCount);
    }

    public static ClaimAttempt limitReached(long lastClaimAt, int claimCount) {
        return new ClaimAttempt(Status.LIMIT_REACHED, lastClaimAt, claimCount);
    }

    public boolean accepted() {
        return status == Status.ACCEPTED;
    }
}
