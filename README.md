[![Status](https://github.com/giis-uniovi/retorch/actions/workflows/test.yml/badge.svg)](https://github.com/giis-uniovi/retorch/actions)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=my%3Aretorch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=my%3Aretorch)
[![Maven Central (annotations)](https://img.shields.io/maven-central/v/io.github.giis-uniovi/retorch-annotations)](https://central.sonatype.com/artifact/io.github.giis-uniovi/retorch-annotations)

<a name="readme-top"></a>     

<!-- PROJECT LOGO -->
<br />
<div align="center">
  <a href="https://giis.uniovi.es/?lang=en">
    <img src="https://giis.uniovi.es/icons/giis-color-medium.gif" alt="Logo" width="110" height="75">
  </a>

<h3 align="center">RETORCH: Resource-aware End-to-End Test Orchestration</h3>

  <p align="center">
    This repository contains a series of components that compose RETORCH, a E2E test orchestration rchestration framework which aims
        to optimize E2E test execution reducing the execution time and the number of unnecessary resource
        redeployment's.

**NOTE: In this initial version, only the annotations to identify the resources and access modes have been included;
        additional components will be added in future releases.**
    <br />
    <a href="https://github.com/giis-uniovi/retorch"><strong>Explore the docs »</strong></a>
    <br />
    <br />
    <a href="https://github.com/giis-uniovi/retorch">View Demo</a>
    ·
    <a href="https://github.com/giis-uniovi/retorch/issues">Report Bug</a>
    ·
    <a href="https://github.com/giis-uniovi/retorch/issues">Request Feature</a>
  </p>
</div>


<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#quick-start">Quick-start</a></li>
 <li><a href="#retorch-annotations">RETORCH Annotations</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#contact">Contact</a></li>
    <li><a href="#citing-this-work">Citing this work</a></li>
    <li><a href="#acknowledgments">Acknowledgments</a></li>
  </ol>
</details>

## Quick-start

[TO-DO]

## RETORCH annotations

RETORCH Tool provides a series of custom annotations to characterize the resources employed in end-to-end testing,
group, and schedule them. To use RETORCH Tool to execute the test cases, each test case needs to be annotated with one
access mode and resource at least. The tester needs to specify the access mode with:

- resID: Resource that belongs to access mode
- concurrency: upper bound of test cases that can access concurrently to the Resource
- sharing: allows sharing the resource between several test cases
- accessMode: type of access mode performed by the test case

```java
    @AccessMode(resID = "LoginService", concurrency = 10, sharing = true, accessMode = "READONLY")
```

Each access mode annotation belongs to a concrete resource, the resource needs to be annotated with the following
attributes:

- resID: unique resource identifier
- replaceable: list of resources that can replace the current ones

```java
    @Resource(resID = "LoginService", replaceable = {}) 
```

The following code snippets illustrates a tes case annotated with several resources and access modes:

```java
    @Resource(resID = "LoginService", replaceable = {})
@AccessMode(resID = "LoginService", concurrency = 10, sharing = true, accessMode = "READONLY")
@Resource(resID = "OpenVidu", replaceable = {"OpenViduMock"})
@AccessMode(resID = "OpenVidu", concurrency = 10, sharing = true, accessMode = "NOACCESS")
@Resource(resID = "Course", replaceable = {"Forum"})
@AccessMode(resID = "Course", concurrency = 10, sharing = true, accessMode = "READONLY")
@ParameterizedTest
@MethodSource("data")
    void forumLoadEntriesTest(String usermail,String password,String role){


            this.user=setupBrowser("chrome",TJOB_NAME+"_"+TEST_NAME,usermail,WAIT_SECONDS);
            driver=user.getDriver();
            this.slowLogin(user,usermail,password);//24 lines
```
<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Contributing

See the general contribution policies and guidelines for *giis-uniovi* at
[CONTRIBUTING.md](https://github.com/giis-uniovi/.github/blob/main/profile/CONTRIBUTING.md).
<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Contact

Cristian Augusto - [augustocristian@uniovi.es](mailto:augustocristian@uniovi.es)

Project
Link: [https://github.com/giis-uniovi/retorch](https://github.com/giis-uniovi/retorch)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

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

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Acknowledgments

This work has been developed under the TestBUS (PID2019-105455GB-C32) and EQUAVEL (PID2022-137646OB-C32) projects supported by [Ministry of Science and Innovation (SPAIN)](https://www.ciencia.gob.es/)



<p align="right">(<a href="#readme-top">back to top</a>)</p>
