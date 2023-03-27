# Gitfly

## Table of Contents
- [Introduction](#introduction)
- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [Commands](#commands)
- [License](#license)

## Introduction
Command-line-driven **version control system** from scratch that mirrors Git’s core features,
such as **file tracking**, **branching** and **merging**.

Implemented in **Java**.
## Features
- Leveraged the **directed acyclic graph** structure of Git commits to **optimize storage** and **retrieval of
repository data**.
- Employed advanced graph theory concepts, such as **Lowest Common Ancestor**, to efficiently manage and
traverse the commit history tree and resolve **merge conflicts**.
- Ensured low memory usage and minimal storage duplication by using the
**SHA-1 hash function.**

## Installation
1. Clone the repository.
2. Add the path to the repository to your `PATH` environment variable: `export CLASSPATH=.../out/production/gitfly`
3. Run `gitfly` in your terminal.

## Usage
`java gitfly.Main <command-name> <args>`
## Commands
- `init`: Initialize a new Gitfly repository.
  - Sets up the necessary Gitfly files and directories.
  - Creates a new commit with the initial commit message and sets it as the current commit.
  - Creates a new branch called `master`.
  - Sets the current branch to `master`.
- `add`: Add files to the staging area.
    - Updates the current index with the new file versions.
    - If the added file was in a merge conflict, adds final version to the index and resolves the conflict.
- `rm`: Remove files from the staging area and from disk.
    - Removes the file from the current index.
    - Removes the file from disk if it's the case.
- `commit`: Commit the files in the staging area.
  - Creates a new commit with the given commit message.
  - Computes SHA1 for the new commit object from the index tree.
  - Sets the new commit as the current commit and sets current branch to point at it.
  - Clears the staging area.
- `checkout`: Checkouts a branch or a commit hash.
  - Modifies the HEAD pointer.
  - Updates the working directory to match its state from the given commit.
- `log`: Prints the commit history of the current branch.
- `branch`: Creates a new branch with the given name.
- `rm-branch`: Removes the branch with the given name, if it exists.
- `status`: Prints the current status of the repository.
  - Branch names and current branch.
  - Files found in merge conflict.
  - Untracked files.
  - Files with changes to be committed.
  - Files with changes not staged for commit.
- `merge`: Merges the giver branch into the current branch.
  - Aborts if the giver branch is the current branch/doesn't exist.
  - There are three main cases:
    1. The giver branch is an ancestor of the current branch => Already up-to-date, no merge is needed.
    2. The receiver branch is an ancestor of the giver branch => Fast-forwarded, the commit history isn't changed, only the current branch is moved to the giver branch.
    3. The receiver branch and the giver branch are not related. Merge conflicts can be encountered. Perform eight steps:
       1. Create a new commit with the given commit message.
       2. Find the LCA of the giver and the receiver branches. This will be the base commit.
       3. Get contents of the receiver, giver and base.
       4. Generate a diff that specifies the status of each file analyzing the contents of the receiver, giver and base.
          - If the file is different in all three commits, then it is a merge conflict.
       5. Apply changes to the working directory.
          - If there are any files found in conflict, write both versions (receiver and giver) to the working directory (invoke getContentOfConflictedFile function).
       6. Write the contents of the working directory to the index.
          - If there are any files found in conflict, write three versions in the index: 1 - SHA1 of base contents, 2 - SHA1 of receiver contents, 3 - SHA1 of giver contents

          The following step differs fundamentally on whether merge conflicts were found.
       7. No conflict:
          - Create a commit with the given index.
          
          Conflict:
          - When a user adds a conflicted file, the other index entries for the respective filename (which indicate conflict) get removed. Wait until all merge conflicts are resolved.
          - User makes a new commit. Gitfly sees that a merge is ongoing (MERGE_HEAD exists) and checks that there are no more conflicted files. A new simple commit is created.
         8. Delete MERGE_HEAD and update current branch.

## License
[MIT](https://choosealicense.com/licenses/mit/)