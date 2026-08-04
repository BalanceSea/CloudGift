package cn.cloudgift.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class JdbcClaimRepository implements ClaimRepository {

    private final HikariDataSource dataSource;
    private final String table;

    public JdbcClaimRepository(JavaPlugin plugin) throws SQLException {
        FileConfiguration config = plugin.getConfig();
        String prefix = config.getString("storage.table-prefix", "cloudgift_");
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("storage.table-prefix 只能包含字母、数字和下划线");
        }
        this.table = prefix + "claims";
        this.dataSource = new HikariDataSource(createPoolConfig(plugin, config));
        try {
            createTable(config.getString("storage.type", "sqlite"));
            migrateClaimCount();
        } catch (SQLException | RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }

    @Override
    public ClaimAttempt attemptClaim(UUID playerId, String giftId, long cooldownMillis, int maxClaims, long now)
            throws SQLException {
        long cutoff = now - Math.min(now, Math.max(0L, cooldownMillis));
        // maxClaims <= 0 means unlimited; use a cap the counter can never reach.
        long limit = maxClaims > 0 ? maxClaims : Long.MAX_VALUE;
        String token = UUID.randomUUID().toString();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // The usage limit takes priority: only accept when the count is still below the
                // limit AND the cooldown has elapsed. Both checks live in the same atomic UPDATE.
                String updateSql = "UPDATE " + table
                        + " SET last_claim = ?, claim_token = ?, claim_count = claim_count + 1"
                        + " WHERE player_uuid = ? AND gift_id = ? AND claim_count < ? AND last_claim <= ?";
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    statement.setLong(1, now);
                    statement.setString(2, token);
                    statement.setString(3, playerId.toString());
                    statement.setString(4, giftId);
                    statement.setLong(5, limit);
                    statement.setLong(6, cutoff);
                    if (statement.executeUpdate() == 1) {
                        connection.commit();
                        return ClaimAttempt.accepted(now, currentCount(connection, playerId, giftId));
                    }
                }

                ClaimRecord existing = findRecord(connection, playerId, giftId);
                if (existing != null) {
                    connection.commit();
                    return reject(existing, maxClaims);
                }

                String insertSql = "INSERT INTO " + table
                        + " (player_uuid, gift_id, last_claim, claim_token, claim_count) VALUES (?, ?, ?, ?, 1)";
                try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, giftId);
                    statement.setLong(3, now);
                    statement.setString(4, token);
                    statement.executeUpdate();
                }
                connection.commit();
                return ClaimAttempt.accepted(now, 1);
            } catch (SQLException exception) {
                connection.rollback();
                ClaimRecord winner = findRecord(connection, playerId, giftId);
                if (winner != null) {
                    return reject(winner, maxClaims);
                }
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private ClaimAttempt reject(ClaimRecord record, int maxClaims) {
        // Limit check first so a fully-used gift never reports a misleading cooldown.
        if (maxClaims > 0 && record.claimCount() >= maxClaims) {
            return ClaimAttempt.limitReached(record.lastClaimAt(), record.claimCount());
        }
        return ClaimAttempt.cooldown(record.lastClaimAt(), record.claimCount());
    }

    private int currentCount(Connection connection, UUID playerId, String giftId) throws SQLException {
        ClaimRecord record = findRecord(connection, playerId, giftId);
        return record == null ? 0 : record.claimCount();
    }

    @Override
    public Map<String, ClaimRecord> loadClaims(UUID playerId) throws SQLException {
        String sql = "SELECT gift_id, last_claim, claim_count FROM " + table + " WHERE player_uuid = ?";
        Map<String, ClaimRecord> claims = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    claims.put(
                            resultSet.getString("gift_id"),
                            new ClaimRecord(resultSet.getLong("last_claim"), resultSet.getInt("claim_count")));
                }
            }
        }
        return claims;
    }

    @Override
    public void reset(UUID playerId, String giftId) throws SQLException {
        String sql = "DELETE FROM " + table + " WHERE player_uuid = ? AND gift_id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, giftId);
            statement.executeUpdate();
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private ClaimRecord findRecord(Connection connection, UUID playerId, String giftId) throws SQLException {
        String sql = "SELECT last_claim, claim_count FROM " + table + " WHERE player_uuid = ? AND gift_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, giftId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? new ClaimRecord(resultSet.getLong(1), resultSet.getInt(2))
                        : null;
            }
        }
    }

    private HikariConfig createPoolConfig(JavaPlugin plugin, FileConfiguration config) {
        String type = config.getString("storage.type", "sqlite").toLowerCase();
        HikariConfig pool = new HikariConfig();
        pool.setPoolName("CloudGift-Database");
        pool.setConnectionTimeout(config.getLong("storage.pool.connection-timeout-ms", 5000L));
        pool.setMaxLifetime(config.getLong("storage.pool.max-lifetime-ms", 1_800_000L));

        if (type.equals("mysql") || type.equals("mariadb")) {
            String host = config.getString("storage.mysql.host", "127.0.0.1");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "minecraft");
            String parameters = config.getString("storage.mysql.parameters", "useSSL=false");
            String separator = parameters == null || parameters.isBlank() ? "" : "?" + parameters;
            pool.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + separator);
            pool.setUsername(config.getString("storage.mysql.username", "root"));
            pool.setPassword(config.getString("storage.mysql.password", ""));
            pool.setDriverClassName("com.mysql.cj.jdbc.Driver");
            int maximumPoolSize = Math.max(2, config.getInt("storage.pool.maximum-pool-size", 10));
            int minimumIdle = Math.max(0, config.getInt("storage.pool.minimum-idle", 2));
            pool.setMaximumPoolSize(maximumPoolSize);
            pool.setMinimumIdle(Math.min(maximumPoolSize, minimumIdle));
            pool.addDataSourceProperty("cachePrepStmts", "true");
            pool.addDataSourceProperty("prepStmtCacheSize", "250");
            pool.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else if (type.equals("sqlite")) {
            File database = new File(plugin.getDataFolder(), "data.db");
            pool.setJdbcUrl("jdbc:sqlite:" + database.getAbsolutePath());
            pool.setDriverClassName("org.sqlite.JDBC");
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(1);
            pool.addDataSourceProperty("busy_timeout", "5000");
        } else {
            throw new IllegalArgumentException("不支持的 storage.type: " + type);
        }
        return pool;
    }

    private void createTable(String storageType) throws SQLException {
        String type = storageType == null ? "sqlite" : storageType.toLowerCase();
        String sql;
        if (type.equals("mysql") || type.equals("mariadb")) {
            sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "player_uuid CHAR(36) NOT NULL,"
                    + "gift_id VARCHAR(128) NOT NULL,"
                    + "last_claim BIGINT NOT NULL,"
                    + "claim_token CHAR(36) NOT NULL,"
                    + "claim_count INT NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (player_uuid, gift_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "player_uuid TEXT NOT NULL,"
                    + "gift_id TEXT NOT NULL,"
                    + "last_claim INTEGER NOT NULL,"
                    + "claim_token TEXT NOT NULL,"
                    + "claim_count INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (player_uuid, gift_id)"
                    + ")";
        }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    /** Adds the claim_count column to tables created before usage limits existed. */
    private void migrateClaimCount() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (columnExists(connection, "claim_count")) {
                return;
            }
            String sql = "ALTER TABLE " + table + " ADD COLUMN claim_count INTEGER NOT NULL DEFAULT 0";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }
    }

    private boolean columnExists(Connection connection, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        for (String candidate : new String[] {table, table.toUpperCase(), table.toLowerCase()}) {
            try (ResultSet columns = metaData.getColumns(connection.getCatalog(), null, candidate, null)) {
                while (columns.next()) {
                    if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
