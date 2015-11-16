package info.voxtechnica.appraisers.db.dao;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.utils.UUIDs;
import info.voxtechnica.appraisers.client.CassandraClient;
import info.voxtechnica.appraisers.model.Token;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Tokens model an OAuth2 Resource Owner Password Credentials Grant in our system.
 *
 * @see <a href="http://tools.ietf.org/html/rfc6749#section-4.3">IETF RFC 6749 Section 4.3</a>
 */
public class Tokens {
    private static final Logger LOG = LoggerFactory.getLogger(Tokens.class);
    private static boolean initialized = false;
    private static Session session;
    private static PreparedStatement psWriteToken;
    private static PreparedStatement psReadToken;
    private static PreparedStatement psDeleteToken;
    private static PreparedStatement psWriteUserToken;
    private static PreparedStatement psReadUserTokens;
    private static PreparedStatement psDeleteUserTokens;

    private static final String createTableOauthTokens = "CREATE TABLE IF NOT EXISTS oauth_tokens (\n" +
            "  oauth_token uuid,\n" +
            "  user_id text,\n" +
            "  last_use timestamp,\n" +
            "  PRIMARY KEY ((oauth_token))\n" +
            ") WITH\n" +
            "  comment='Look up User ID from OAuth Bearer Token' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableOauthTokensUser = "CREATE TABLE IF NOT EXISTS oauth_tokens_user (\n" +
            "  user_id text,\n" +
            "  oauth_token uuid,\n" +
            "  hits counter,\n" +
            "  PRIMARY KEY ((user_id), oauth_token)\n" +
            ") WITH\n" +
            "  comment='User OAuth Token History' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    public static void initialize(CassandraClient dbClient) {
        if (!initialized) {
            session = dbClient.getSession();
            // Create tables if they're missing
            if (dbClient.isCreateIfMissing()) {
                try {
                    session.execute(createTableOauthTokens);
                    session.execute(createTableOauthTokensUser);
                } catch (Exception e) {
                    LOG.error("Error creating table: {}", ExceptionUtils.getRootCauseMessage(e));
                }
            }
            // Prepare statements (invokes a call to Cassandra to validate each one):
            psWriteToken = session.prepare("INSERT INTO oauth_tokens (oauth_token, user_id, last_use) VALUES (?, ?, ?)");
            psReadToken = session.prepare("SELECT * FROM oauth_tokens WHERE oauth_token=?");
            psDeleteToken = session.prepare("DELETE FROM oauth_tokens WHERE oauth_token=?");
            psWriteUserToken = session.prepare("UPDATE oauth_tokens_user SET hits = hits + 1 WHERE user_id=? AND oauth_token=?");
            psReadUserTokens = session.prepare("SELECT oauth_token, hits FROM oauth_tokens_user WHERE user_id=? LIMIT ?");
            psDeleteUserTokens = session.prepare("DELETE FROM oauth_tokens_user WHERE user_id=?");
            initialized = true;
        } else LOG.info("Previously initialized");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static UUID createOAuthToken(@NotNull String userId) {
        UUID token = UUIDs.timeBased();
        session.executeAsync(psWriteToken.bind(token, userId, new Date(token.timestamp())));
        session.executeAsync(psWriteUserToken.bind(userId, token));
        return token;
    }

    public static Token readOAuthToken(@NotNull UUID token) {
        Row row = session.execute(psReadToken.bind(token)).one();
        if (row == null) return null;
        Token oAuthToken = new Token();
        oAuthToken.setOAuthToken(token);
        oAuthToken.setUserId(row.getString("user_id"));
        oAuthToken.setLastUse(row.getTimestamp("last_use"));
        return oAuthToken;
    }

    public static void deleteOAuthToken(@NotNull UUID token) {
        session.execute(psDeleteToken.bind(token));
    }

    public static void updateUserToken(@NotNull String userId, @NotNull UUID token) {
        session.executeAsync(psWriteToken.bind(token, userId, new Date(System.currentTimeMillis())));
        session.executeAsync(psWriteUserToken.bind(userId, token));
    }

    public static List<Token> readUserTokens(@NotNull String userId, Integer limit) {
        ArrayList<Token> tokens = new ArrayList<>();
        ResultSet resultSet = session.execute(psReadUserTokens.bind(userId, limit == null ? 100000 : limit));
        for (Row row : resultSet.all()) {
            Token token = new Token();
            token.setUserId(userId);
            token.setOAuthToken(row.getUUID("oauth_token"));
            token.setHits(row.getLong("hits"));
            tokens.add(token);
        }
        return tokens;
    }

    public static void deleteUserTokens(@NotNull String userId) {
        session.execute(psDeleteUserTokens.bind(userId));
    }
}
