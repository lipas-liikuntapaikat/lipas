Hey! Start developing by running (dev-webapp!) .. this will start the dev system with db connection, search etc.

You can refresh all the changes with (webapp-repl/reset) .. this will also cleanup your REPL state.

(webapp-repl/db) returns the db connection if you need it

(webapp-repl/search) returns search if you need it

(webapp-repl/get-robot-user) returns a test-user with admin permissions if you need it

The tests have their own simplified system with db, search and app. Once changes are made, lipas.test-utils namespace may need to be reloaded for the tests to pick up the changes.
