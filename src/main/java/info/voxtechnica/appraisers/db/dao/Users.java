package info.voxtechnica.appraisers.db.dao;

import com.datastax.driver.core.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import info.voxtechnica.appraisers.client.CassandraClient;
import info.voxtechnica.appraisers.client.SendGridClient;
import info.voxtechnica.appraisers.config.ApplicationConfiguration;
import info.voxtechnica.appraisers.model.User;
import info.voxtechnica.appraisers.model.Message;
import info.voxtechnica.appraisers.util.JsonSerializer;
import info.voxtechnica.appraisers.util.TuidFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;

/**
 * A ‘user’ is a human or another computer process that invokes the API. It is a focal point for access controls
 * and event logging. Users may be found in either the yaml configuration file or in the database. For rapid
 * server-to-server interaction, the configuration file is preferred for in-memory performance reasons.
 */
public class Users {
    private static final Logger LOG = LoggerFactory.getLogger(Users.class);
    private static boolean initialized = false;
    private static Session session;
    private static PreparedStatement psWriteVersion;
    private static PreparedStatement psReadAllIds;
    private static PreparedStatement psReadVersion;
    private static PreparedStatement psReadVersions;
    private static PreparedStatement psReadCurrentVersion;
    private static PreparedStatement psDeleteVersions;
    private static PreparedStatement psWriteEmailId;
    private static PreparedStatement psReadEmailId;
    private static PreparedStatement psDeleteEmailId;

    private static final String createTableUsers = "CREATE TABLE IF NOT EXISTS users (\n" +
            "  id text,\n" +
            "  update_id text,\n" +
            "  json text,\n" +
            "  PRIMARY KEY ((id), update_id)\n" +
            ") WITH\n" +
            "  comment='User Version History' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    private static final String createTableUserIdsEmail = "CREATE TABLE IF NOT EXISTS user_ids_email (\n" +
            "  email text,\n" +
            "  id text,\n" +
            "  PRIMARY KEY ((email))\n" +
            ") WITH\n" +
            "  comment='Look up User ID from Email Address' AND\n" +
            "  compaction={'class': 'LeveledCompactionStrategy'} AND\n" +
            "  compression={'sstable_compression': 'LZ4Compressor'};\n";

    public static void initialize(CassandraClient dbClient) {
        if (!initialized) {
            session = dbClient.getSession();
            // Create tables if they're missing
            if (dbClient.isCreateIfMissing()) {
                try {
                    session.execute(createTableUsers);
                    session.execute(createTableUserIdsEmail);
                } catch (Exception e) {
                    LOG.error("Error creating table: {}", ExceptionUtils.getRootCauseMessage(e));
                }
            }
            // Prepare statements (invokes a call to Cassandra to validate each one):
            // regular object persistence
            psWriteVersion = session.prepare("INSERT INTO users (id, update_id, json) VALUES (?, ?, ?)");
            psReadAllIds = session.prepare("SELECT DISTINCT id FROM users LIMIT ?");
            psReadVersion = session.prepare("SELECT * FROM users WHERE id=? AND update_id=?");
            psReadVersions = session.prepare("SELECT * FROM users WHERE id=? ORDER BY update_id ASC");
            psReadCurrentVersion = session.prepare("SELECT * FROM users WHERE id=? ORDER BY update_id DESC LIMIT 1");
            psDeleteVersions = session.prepare("DELETE FROM users WHERE id=?");
            // look up user from email address
            psWriteEmailId = session.prepare("INSERT INTO user_ids_email (email, id) VALUES (?, ?)");
            psReadEmailId = session.prepare("SELECT id FROM user_ids_email WHERE email=?");
            psDeleteEmailId = session.prepare("DELETE FROM user_ids_email WHERE email=?");
            initialized = true;
        } else LOG.info("Previously initialized");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Create a new User object
     *
     * @param user User object
     * @return the updated, persisted User
     * @throws JsonProcessingException
     */
    public static User createUser(User user) throws JsonProcessingException, MissingResourceException {
        // set IDs
        if (user.getId() == null) user.setId(TuidFactory.getId());
        user.setUpdateId(user.getId()); // updateId matches id on first version
        // validate email address
        Boolean validEmailProvided = user.validEmail();
        if (user.getEmail() != null && !validEmailProvided)
            throw new MissingResourceException("Email address invalid", User.class.getSimpleName(), "email");
        if (validEmailProvided && readUserId(user.getEmail()) != null)
            throw new MissingResourceException("Email address not available", User.class.getSimpleName(), "email");
        // provide a default 'pending' status if necessary
        if (user.getStatus() == null) user.setStatus(User.Status.PENDING);
        // persist record(s)
        BatchStatement batch = new BatchStatement();
        batch.add(psWriteVersion.bind(user.getId(), user.getUpdateId(), JsonSerializer.getJson(user)));
        if (validEmailProvided) batch.add(psWriteEmailId.bind(user.getEmail(), user.getId()));
        session.execute(batch);
        return user;
    }

    /**
     * Create an new version of (update) the provided User
     *
     * @param user User object
     * @return the User, with a new updateId
     * @throws JsonProcessingException
     */
    public static User updateUser(User user) throws IOException, MissingResourceException {
        if (user.getId() == null) throw new MissingResourceException("Missing ID", User.class.getSimpleName(), "id");
        user.setUpdateId(TuidFactory.getId());
        // is the user asking for an address that's already taken?
        String idNewEmail = readUserId(user.getEmail());
        if (idNewEmail != null && !idNewEmail.equals(user.getId()))
            throw new MissingResourceException("Email address not available", User.class.getSimpleName(), "email");
        // get the previous version and check for email address update
        BatchStatement batch = new BatchStatement();
        User oldUser = readUser(user.getId());
        if (oldUser != null && oldUser.validEmail() && !oldUser.getEmail().equals(user.getEmail()))
            batch.add(psDeleteEmailId.bind(oldUser.getEmail()));
        // write a new version of the user
        batch.add(psWriteVersion.bind(user.getId(), user.getUpdateId(), JsonSerializer.getJson(user)));
        if (user.validEmail()) batch.add(psWriteEmailId.bind(user.getEmail(), user.getId()));
        session.execute(batch);
        return user;
    }

    /**
     * Delete all versions of the User, specified by ID or email address
     *
     * @param idOrEmail user ID or email address
     * @throws IOException
     */
    public static void deleteUser(String idOrEmail) throws IOException {
        User user = readUser(idOrEmail);
        if (user != null) {
            BatchStatement batch = new BatchStatement();
            if (user.getEmail() != null) batch.add(psDeleteEmailId.bind(user.getEmail()));
            batch.add(psDeleteVersions.bind(user.getId()));
            session.execute(batch);
        }
    }

    /**
     * Get User ID from email address. This can be useful for avoiding duplicate accounts.
     *
     * @param email Email address
     * @return User ID if available, otherwise null
     */
    public static String readUserId(String email) {
        if (email == null || !email.contains("@")) return null;
        Row row = session.execute(psReadEmailId.bind(email)).one();
        if (row == null) return null;
        return row.getString("id");
    }

    /**
     * Get the current version of the specified User
     *
     * @param idOrEmail user ID or email address
     * @return User object
     */
    public static User readUser(String idOrEmail) throws IOException {
        if (idOrEmail == null) return null;
        String userId = idOrEmail.contains("@") ? readUserId(idOrEmail) : idOrEmail;
        if (userId == null) return null;
        Row row = session.execute(psReadCurrentVersion.bind(userId)).one();
        if (row == null) return null;
        return JsonSerializer.getObject(row.getString("json"), User.class);
    }

    /**
     * Get the specified version of the specified User
     *
     * @param id       user ID
     * @param updateId update (version) ID
     * @return User object
     * @throws IOException
     */
    public static User readUserVersion(String id, String updateId) throws IOException {
        if (id == null || updateId == null) return null;
        Row row = session.execute(psReadVersion.bind(id, updateId)).one();
        if (row == null) return null;
        return JsonSerializer.getObject(row.getString("json"), User.class);
    }

    /**
     * Get the complete revision history for the specified User
     *
     * @param idOrEmail user ID or email address
     * @return Chronological List of User versions
     * @throws IOException
     */
    public static List<User> readUserVersions(String idOrEmail) throws IOException {
        if (idOrEmail == null) return null;
        String userId = idOrEmail.contains("@") ? readUserId(idOrEmail) : idOrEmail;
        if (userId == null) return null;
        ArrayList<User> versions = new ArrayList<>();
        ResultSet resultSet = session.execute(psReadVersions.bind(userId));
        for (Row row : resultSet.all()) {
            versions.add(JsonSerializer.getObject(row.getString("json"), User.class));
        }
        return versions;
    }

    private static List<String> readIds(BoundStatement query, String idField) {
        ArrayList<String> ids = new ArrayList<>();
        ResultSet resultSet = session.execute(query);
        for (Row idRow : resultSet.all())
            ids.add(idRow.getString(idField));
        return ids;
    }

    public static SortedSet<User> readUsers(List<String> ids, Comparator<User> order) throws IOException, ExecutionException, InterruptedException {
        // Send asynchronous queries to Cassandra
        List<ResultSetFuture> futures = Lists.newArrayListWithExpectedSize(ids.size());
        for (String id : ids) if (id != null) futures.add(session.executeAsync(psReadCurrentVersion.bind(id)));
        // Process the results as they come in, deserializing JSON to pojos in sorted order
        ConcurrentSkipListSet<User> users = new ConcurrentSkipListSet<>(order);
        for (ListenableFuture<ResultSet> future : Futures.inCompletionOrder(futures)) {
            Row row = future.get().one();
            if (row != null) users.add(JsonSerializer.getObject(row.getString("json"), User.class));
        }
        return users;
    }

    /**
     * Get the current version of all users, up to the specified limit
     *
     * @param limit (default 10,000)
     * @return List of Users
     * @throws IOException
     */
    public static SortedSet<User> readUsers(Integer limit) throws IOException, ExecutionException, InterruptedException {
        return readUsers(readIds(psReadAllIds.bind(limit == null ? 10000 : limit), "id"), User.Alphabetical);
    }

    // TODO: Rate limit sending new tokens and don't use old tokens
    public static Message sendResetToken(String id) throws IOException, ExecutionException, InterruptedException {
        // If the User doesn't already exist, create a new account (self-provisioning)
        Boolean isNewUser = false;
        User user = readUser(id);
        if (user == null) {
            user = new User();
            user.setEmail(id);
            if (!user.validEmail()) return null;
            user = createUser(user);
            isNewUser = true;
        }
        user.setResetToken(TuidFactory.getId());
        updateUser(user);
        // Send Password Reset Message
        String url = ApplicationConfiguration.get().getWebBaseUrl() + (isNewUser ? "?create_password" : "?reset_password") + "&email=" + user.getEmail() + "&token=" + user.getResetToken();
        Message message = new Message();
        message.setFrom(SendGridClient.getFrom());
        message.addRecipient(user.getEmail());
        message.setSubject(SendGridClient.getFromName() + " User Account");
        message.setBody(String.format("Please use the following URL to enter a new password:\nURL: %s\nUser: %s\nToken: %s\n", url, user.getEmail(), user.getResetToken()));
        return Messages.createMessage(message);
    }
}