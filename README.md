<img src="/paidy.png?raw=true" width=300 style="background-color:white;">

# Paidy Take-Home Coding Exercises

## Introduction

This repository contains my solution to the Paidy Take-Home Coding Exercises.

## Table of Contents

1. [Requirements](#requirements)
2. [How to Run](#how-to-run)
    - [Tests](#tests)
    - [Application](#application)
3. [Configuration](#configuration)
4. [Libraries and Tools](#libraries-and-tools)
5. [Code Structure](#code-structure)
6. [Unit Tests](#unit-tests)
7. [Conclusion](#conclusion)

## Requirements

Ensure you have the following installed:

- [sbt](https://www.scala-sbt.org/)

## How to Run

### Tests

To run the tests, execute the following command in the console:

```bash
sbt test
```

### Application
To run the application, execute the following command:

```bash
sbt run
```
The default port is 3233, and the default URI for OneFrame is http://localhost:8080 with the token 10dc303535874aeccc86a8251e6992f5. You can change these values using environment variables or by modifying the src/main/resources/application.conf file.

## Configuration

The application can be configured using the following environment variables:

- `$PORT`: Specifies the port for the application.
- `$BASE_URI`: Specifies the base URI for OneFrame.
- `$SCHEDULER_MODE`: Is scheduler mode enabled.
- `$TOKEN`: Specifies the token for authentication.

Alternatively, you can modify the values in the `src/main/resources/application.conf` file.

## Libraries and Tools

I utilized the following libraries and tools in my solution:

- [Cats](https://typelevel.org/cats/): A library for functional programming in Scala.
- [Http4s](https://http4s.org/): A functional HTTP library for Scala.
- [Scaffeine](https://github.com/blemale/scaffeine): A Scala caching library.
- [PureConfig](https://pureconfig.github.io/): A library for loading configuration files.

## Code Structure

The code is organized in a functional programming style, leveraging Cats and Http4s. The implementation includes a local proxy for currency exchange rates.

## Unit Tests

Unit tests are included to ensure the correctness of the implemented functionality. You can run them using the `sbt test` command.

## Conclusion

Thank you for reviewing my solution. If you have any questions or feedback, please feel free to reach out. I look forward to discussing my approach during the interview.