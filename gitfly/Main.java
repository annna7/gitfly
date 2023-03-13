package gitfly;
import static gitfly.Utils.*;
import gitfly.Repository;

import java.io.IOException;

public class Main {
    // Usage: java gitfly.Main <command> <arg1> <arg2> ...
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            exit("Please specify a command.");
        }

        String commandName = args[0];
        String[] commandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, commandArgs, 0, commandArgs.length);

        switch (commandName) {
            case "init":
                checkNumberOfArguments(commandArgs, 0);
                Repository.init();
                break;
            case "add":
                Repository.checkIfGitflyInitialized();
                Repository.add(commandArgs);
                break;
            case "rm":
                Repository.checkIfGitflyInitialized();
                Repository.rm(commandArgs);
                break;
            case "commit":
                Repository.checkIfGitflyInitialized();
                Repository.commit(commandArgs[0]);
                break;

            default:
                exit("Unknown command: %s", commandName);
        }
    }
}
