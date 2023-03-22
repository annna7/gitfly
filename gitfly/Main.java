package gitfly;
import static gitfly.Utils.*;
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
            case "init" -> {
                checkNumberOfArguments(commandArgs, 0);
                Repository.init();
            }
            case "add" -> {
                Repository.checkIfGitflyInitialized();
                Repository.add(commandArgs);
            }
            case "rm" -> {
                Repository.checkIfGitflyInitialized();
                Repository.rm(commandArgs);
            }
            case "commit" -> {
                Repository.checkIfGitflyInitialized();
                Repository.commit(commandArgs[0]);
            }
            case "log" -> {
                Repository.checkIfGitflyInitialized();
                checkNumberOfArguments(commandArgs, 0);
                Repository.log();
            }
            case "branch" -> {
                Repository.checkIfGitflyInitialized();
                checkNumberOfArguments(commandArgs, 1);
                Repository.branch(commandArgs[0]);
            }
            case "rm-branch" -> {
                Repository.checkIfGitflyInitialized();
                checkNumberOfArguments(commandArgs, 1);
                Repository.rm_branch(commandArgs[0]);
            }
            case "checkout" -> {
                Repository.checkIfGitflyInitialized();
                Repository.checkout(commandArgs[0]);
            }
            case "status" -> {
                Repository.checkIfGitflyInitialized();
                checkNumberOfArguments(commandArgs, 0);
                Repository.status();
            }
            case "merge" -> {
                Repository.checkIfGitflyInitialized();
                checkNumberOfArguments(commandArgs, 1);
                Repository.merge(commandArgs[0]);
            }
            default -> exit("Unknown command: %s", commandName);
        }
    }
}
