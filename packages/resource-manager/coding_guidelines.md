# Coding guidelines

To increase the likelihood that your contributions will be accepted
try to follow these guidelines when you are contributing.

The guidelines aims to make all code look and "feel" the same. This
simplifies understanding code and speeds up code reading.

We have a set of general guidelines and guidelines specific to what
programming language you are developing in. Please read through the
general guidelines first and then take a look at the specific
programming language guidelines.

## General guidelines

* Keep the lines 80 characters long
* Follow the "style" of existing code when extending
* Do not commit commented-out code
* Make sure your comments are correct and helpful

* Use the ```ncs-make-package``` and ```ncs-project``` tools when
  applicable. These tools creates the framework you need to easily use
  your code with NSO.

For more information about how to develop NSO applications read the
NSO development guide (the file is named
nso_development-<nso-version>.pdf).

## Python coding guidelines

Please make sure that your code at least adheres to the PEP8
guidelines. We recommend using
[flake8](https://bitbucket.org/tarek/flake8/overview) together with
the pep8-naming plugin. Together it checks both pep8, pyflakes and
pep8 naming conventions.

To install flake8 using pip:
```$ pip install flake8```
```$ pip install pep8-naming```

Verify installation
```$ flake8```
```2.0 (pep8: 1.4.3, pyflakes: 0.6.1, naming: 0.2)```

For Other Installation options, check the link above.

See more information about pep8 at:
[pep8](https://www.python.org/dev/peps/pep-0008/)
and pyflakes at:
[pyflakes](https://pypi.python.org/pypi/pyflakes)


## Java coding guidelines

Use Oracles [Java Code
Conventions](http://www.oracle.com/technetwork/java/codeconventions-150003.pdf)


## Erlang coding guidelines

In general
* Use types
* Do not "export all"

Take a look at [Inaka's
guidelines](https://github.com/inaka/erlang_guidelines) .