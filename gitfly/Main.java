package gitfly;
import static gitfly.Utils.*;
import gitfly.Repository;

import java.awt.image.ReplicateScaleFilter;
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
            case "log":
                Repository.checkIfGitflyInitialized();
                checkNumberOfArguments(commandArgs, 0);
                Repository.log();
                break;
            case "branch":
                Repository.checkIfGitflyInitialized();
                checkNumberOfArguments(commandArgs, 1);
                Repository.branch(commandArgs[0]);
                break;
            case "rm-branch":
                Repository.checkIfGitflyInitialized();
                checkNumberOfArguments(commandArgs, 1);
                Repository.rm_branch(commandArgs[0]);
                break;
            case "checkout":
                Repository.checkIfGitflyInitialized();
                Repository.checkout(commandArgs[0]);
                break;
            case "status":
                Repository.checkIfGitflyInitialized();
                checkNumberOfArguments(commandArgs, 0);
                Repository.status();
                break;
            case "merge":
                Repository.checkIfGitflyInitialized();
                checkNumberOfArguments(commandArgs, 1);
                Repository.merge(commandArgs[0]);
                break;
            case "ancestors":
                Repository.checkIfGitflyInitialized();
                System.out.println(Repository.getAncestorsOfCommit(Repository.getCurrentCommitID()));
                break;
            case "printIndex":
                Repository.checkIfGitflyInitialized();
                Stage.printIndex();
                break;
            default:
                exit("Unknown command: %s", commandName);
        }
    }
}
