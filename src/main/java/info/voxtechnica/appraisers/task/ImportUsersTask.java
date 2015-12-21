package info.voxtechnica.appraisers.task;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.Resources;
import info.voxtechnica.appraisers.db.dao.Tokens;
import info.voxtechnica.appraisers.db.dao.Users;
import info.voxtechnica.appraisers.model.User;
import info.voxtechnica.appraisers.util.JsonSerializer;
import io.dropwizard.servlets.tasks.Task;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Task: import User resources from JSON files. Calling the task without parameters will load static resource JSON files
 * in Jar file. Calling with query parameters will load external JSON files.
 * <p/>
 * Simple Usage: curl -X POST http://localhost:8081/tasks/import-users
 */
public class ImportUsersTask extends Task {
    private final String usage = "curl --data 'users=path/to/users.json' http://localhost:8081/tasks/import-users";

    public ImportUsersTask() {
        super("import-users");
    }

    @Override
    @Timed
    public void execute(ImmutableMultimap<String, String> parameters, PrintWriter printWriter) throws Exception {
        long startTime = System.currentTimeMillis();
        int newUserCount = 0;
        ArrayList<User> users = new ArrayList<>();

        // import static resource files if no external files supplied
        if (!parameters.containsKey("users")) {
            printWriter.print("Importing users from static resource file 'import/users.json':\n");
            printWriter.flush();
            String usersJson = Resources.toString(Resources.getResource("import/users.json"), Charsets.UTF_8);
            users = JsonSerializer.getArray(usersJson, User.class);
        }
        // TODO: support user.json file provided on the command line

        // import users if they don't already exist
        for (User user : users) {
            User existing = Users.readUser(user.getEmail());
            if (existing == null) {
                Users.createUser(user);
                UUID token = Tokens.createOAuthToken(user.getId());
                printWriter.print(String.format("Import\tUser\t%s\t%s\t%s\n", user.getId(), user.getEmail(), token));
                newUserCount++;
            } else printWriter.print(String.format("Skip\tUser\t%s\t%s\n", user.getId(), user.getEmail()));
            printWriter.flush();
        }
        printWriter.print(String.format("Imported %d of %d Users\n\n", newUserCount, users.size()));

        Long duration = System.currentTimeMillis() - startTime;
        printWriter.print(String.format("Completed in %,d ms\n", duration));
        printWriter.close();
    }
}