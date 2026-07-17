package cn.cloudgift.storage;

import java.sql.SQLException;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

public interface ClaimRepository extends AutoCloseable {

    ClaimAttempt attemptClaim(UUID playerId, String giftId, long cooldownMillis, long now) throws SQLException;

    Map<String, Long> loadClaims(UUID playerId) throws SQLException;

    OptionalLong findLastClaim(UUID playerId, String giftId) throws SQLException;

    void reset(UUID playerId, String giftId) throws SQLException;

    @Override
    void close();
}
