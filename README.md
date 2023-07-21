[![Status](https://github.com/giis-uniovi/retorch/actions/workflows/test.yml/badge.svg)](https://github.com/giis-uniovi/retorch/actions)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=my%3Aretorch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=my%3Aretorch)
[![Maven Central (annotations)](https://img.shields.io/maven-central/v/io.github.giis-uniovi/retorch-annotations)](https://central.sonatype.com/artifact/io.github.giis-uniovi/retorch-annotations)

<h1 align="center"> RETORCH: Resource-aware End-to-End Test Orchestration</h1>
<div align="center">
 <a href="https://giis.uniovi.es/?lang=en">
    <img src="https://giis.uniovi.es/icons/giis-color-medium.gif" alt="Logo" width="110" height="75">
</a >
</div>

This repository contains a series of components that compose RETORCH, an E2E test orchestration framework. It's primary
goal is to optimize E2E test execution by reducing both the execution time and the number of unnecessary resource
redeployment's.

**NOTE: In this initial version, only the annotations to identify the resources and access modes have been included.
Additional components will be added in future releases.**"

[Explore the docs](https://github.com/giis-uniovi/retorch) - [Report Bug](https://github.com/giis-uniovi/retorch/issues) -
[Request Feature](https://github.com/giis-uniovi/retorch/issues)

## Contents

- [RETORCH: Resource-aware End-to-End Test Orchestration:]()
    - [Quick Start](#quick-start)
    - [RETORCH Annotations](#retorch-annotations)
    - [Contributing](#contributing)
    - [Contact](#contact)
    - [Citing this work](#citing-this-work)
    - [Acknowledgments](#acknowledgments)

## Quick-start

[TO-DO]

[(Back to the top)](#contents)

## RETORCH Annotations

The RETORCH Tool provides a set of custom annotations to define and manage resources used in end-to-end testing. These annotations allow testers to group, schedule, and characterize resources. To execute test cases using the RETORCH Tool, each test case must be annotated with at least one access mode and resource.

The tester needs to specify the access mode using the following attributes:

- `resID`: Resource identifier for the access mode.
- `concurrency`: The upper bound of test cases that can access the resource concurrently.
- `sharing`: Allows sharing the resource between multiple test cases.
- `accessMode`: The type of access mode performed by the test case.

```java
@AccessMode(resID = "LoginService", concurrency = 10, sharing = true, accessMode = "READONLY")
```

Each access mode annotation corresponds to a specific resource, which must be annotated with the following attributes:

- `resID`: A unique identifier for the resource.
- `replaceable`: A list of resources that can replace the current one.

```java
@Resource(resID = "LoginService", replaceable = {})
```

The following code snippets illustrate a test case annotated with multiple resources and access modes:

```java
@Resource(resID = "LoginService", replaceable = {})
@AccessMode(resID = "LoginService", concurrency = 10, sharing = true, accessMode = "READONLY")
@Resource(resID = "OpenVidu", replaceable = {"OpenViduMock"})
@AccessMode(resID = "OpenVidu", concurrency = 10, sharing = true, accessMode = "NOACCESS")
@Resource(resID = "Course", replaceable = {"Forum"})
@AccessMode(resID = "Course", concurrency = 10, sharing = true, accessMode = "READONLY")
@ParameterizedTest
@MethodSource("data")
void forumLoadEntriesTest(String usermail, String password, String role) {
    this.user = setupBrowser("chrome", TJOB_NAME + "_" + TEST_NAME, usermail, WAIT_SECONDS);
    driver = user.getDriver();
    this.slowLogin(user, usermail, password);
}
```
[(back to the top)](#contents)

## Contributing

See the general contribution policies and guidelines for *giis-uniovi* at
[CONTRIBUTING.md](https://github.com/giis-uniovi/.github/blob/main/profile/CONTRIBUTING.md).

[(back to the top)](#contents)

## Contact

Cristian Augusto - [augustocristian@uniovi.es](mailto:augustocristian@uniovi.es)

[(back to the top)](#contents)

## Citing this work

RETORCH test orchestration framework:

```
Cristian Augusto, Jesús Morán, Antonia Bertolino, Claudio de la Riva, and Javier Tuya, 
“RETORCH: an approach for resource-aware orchestration of end-to-end test cases,” 
Software Quality Journal, vol. 28, no. 3, 2020.
https://doi.org/10.1007/s11219-020-09505-2
```

[Full Article available](https://link.springer.com/article/10.1007/s11219-020-09505-2) - [Authors version](https://digibuo.uniovi.es/dspace/bitstream/handle/10651/55405/RETORCHSQJExtension_BUO.pdf;jsessionid=0E661594C8732B8D2CA53636A31E4FD5?sequence=1) -
[Download citation](https://citation-needed.springer.com/v2/references/10.1007/s11219-020-09505-2?format=refman&flavour=citation)

[(back to the top)](#contents)

## Acknowledgments

This work has been developed under the TestBUS (PID2019-105455GB-C32) project supported
by the [Ministry of Science and Innovation (SPAIN)](https://www.ciencia.gob.es/)

[(back to the top)](#contents)
