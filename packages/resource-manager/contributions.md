# Contributing

We appreciate when we get feature requests, bug reports and
suggestions for improvements. If you are extending a project or fixing
a bug, please contribute back in the form of a pull request or a bug
report. Your contributions may help others who will face the same need
or problem.

## Reporting bugs

Please contact support@tail-f.com with your issue.

Before you report a new issue, please

* Check if your issue is already reported before creating a new one.
* Check if there already is a pull request open aiming to solve the problem.
* Check the version of the component, are there known issues for the component?
* Check the NCS version, are there known issues for the version you are using?

In the bug report, please provide

* NCS Version
* Component version
* Versions of all packages being used
* provide the output from the NCS cli command: ```show packages```
* A description of the error
* If applicable a description of a way to reproduce the issue

## Suggest a new feature

You can also use the support channel to create a suggestion for a new
feature.

## Contributing code

We happily accept pull requests for new features and bug reports.

### How to contribute

Create a new branch, prefix it with the issue number if you have
created one or received one, then create a PR from your branch to the
develop branch.

E.g. You've contacted support and gotten an issue PG-36 created, the
     branch name is then PG-36-my-issue

Work against the 'develop' branch, i.e. the PR target should be the
develop branch.

* Add a description of the problem the PR is aiming to solve.
* Create one PR for each feature or bug fix.
* Make sure there are reviewers automatically added, otherwise send us
  an email with a link to the PR.
* Follow the coding guidelines.

* Comment your code, all public interfaces should have docstrings, if
  available.
* For new features, always add a test case that tests the changes in
  the PR.
* For new features, also add at least one line of documentation
  describing the feature.
* Make sure to have run all test cases and having them pass before
  creating the PR
```
         make -C test all
```
## Documentation
### General

The general documentation for all NSO Component should use the docbook
xml format.
