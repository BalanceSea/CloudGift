package cn.cloudgift.storage;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public interface ClaimRepository extends AutoCloseable {

    ClaimAttempt attemptClaim(UUID playerId, String giftId, long lastClaimCutoff, int maxClaims, long now)
            throws SQLException;

    Map<String, ClaimRecord> loadClaims(UUID playerId) throws SQLException;

    void reset(UUID playerId, String giftId) throws SQLException;

    @Override
    void close();
}
