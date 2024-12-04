[![Status](https://github.com/giis-uniovi/retorch/actions/workflows/build.yml/badge.svg)](https://github.com/giis-uniovi/retorch/actions)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=my%3Aretorch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=my%3Aretorch)
[![Maven Central (annotations)](https://img.shields.io/maven-central/v/io.github.giis-uniovi/retorch-annotations)](https://central.sonatype.com/artifact/io.github.giis-uniovi/retorch-annotations)

# RETORCH: Resource-aware End-to-End Test Orchestration

This repository contains a series of components of the RETORCH End-to-End (E2E) test orchestration framework. It's primary
goal is to optimize E2E test execution by reducing both the execution time and the number of unnecessary resource
redeployment's.

NOTE: In this initial version, only the annotations to identify the resources and access modes have been included.
Additional components will be added in future releases.

## Contents

- [RETORCH: Resource-aware End-to-End Test Orchestration:]()
    - [Quick Start](#quick-start)
    - [RETORCH Annotations](#retorch-annotations)
    - [RETORCH Orchestration](#retorch-orchestration)
    - [Contributing](#contributing)
    - [Contact](#contact)
    - [Citing this work](#citing-this-work)
    - [Acknowledgments](#acknowledgments)

## Quick-start

- Add the dependency 
  [`io.github.giis-uniovi:retorch-annotations`](https://central.sonatype.com/artifact/io.github.giis-uniovi/retorch-annotations) to the pom.xml of your SUT.
- Add the annotations to the test classes as indicated below
- [TO-DO]

## RETORCH Annotations

The RETORCH framework provides a set of custom annotations to define and manage resources used in end-to-end testing. These
annotations allow testers to group, schedule, and characterize resources. To execute test cases using RETORCH,
each test case must be annotated with at least one access mode and resource.

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
void forumLoadEntriesTest(String usermail,String password,String role){
        this.user=setupBrowser("chrome",TJOB_NAME+"_"+TEST_NAME,usermail,WAIT_SECONDS);
        driver=user.getDriver();
        this.slowLogin(user,usermail,password);
        }
```
## RETORCH Orchestration
The RETORCH framework provides a tool that generates an Execution Plan, along with the required pipelining and script 
files for execution in a CI environment. The generation of scripts and pipelining code is based on the Access Modes 
annotated within the test cases and the Resource information specified in `/retorchfiles/[SUT_NAME]SystemResources.json`.

The RETORCH orchestration tool requires 4 inputs:
- The annotated E2E test cases with the [RETORCH access modes](#retorch-annotations).
- A file with the Resources in JSON format.
- A properties file with the Environment configuration.
- A custom `docker-compose.yml` file.

Given these inputs, the tool generates as output the necessary scripting code and the `Jenkinsfile` to execute the E2E test
suite into a Continuous Integration system.

### Prepare the E2E Test suite
Execute the RETORCH Orchestration tool and generate the script and pipelining code requires to perform a series of configurations
into the test suite. The first step is to create several folders to store the configurations and place the `docker-compose.yml`
in the project repository.
The resulting directory tree should look like as:
```
.
├── src
├── docker-compose.yml
├── retorchfiles/
│   ├── configurations/
│   └── customscriptscode/

```
- The `retorchfiles/` directory would contain all the configuration files and scripting snippets that would be used to generate the
pipelining code and the scripts to set up, deploy, and tear down the different Resources and TJob. Contains two subdirectories:
  - `configurations/`: stores the Resource and CI configuration files.
  - `customscriptscode/`: stores the different script snippets for the tear-down, set-up and environment.
- The `docker-compose.yml` in the root of the directory.
- The different project directories and files.

The following subsections explain how to create each configuration file and how to prepare the docker-compose.yml file.

#### Create the Resource.json file
The Resource file must be placed in the `retorchfiles/configurations/` and named with the system or test suite name, followed
by `SystemResources.json`. This file contains a map with a series of resources, using their unique ResourceID as a key. For each
Resource the tester needs to specify the following attributes:
- `resourceID`: A unique identifier for the Resource.
- `replaceable`: A list of Resources that can replace the current one.
- `hierarchyParent`: A resourceID of the hierarchical parent of the Resource.
- `elasticityModel`: The elasticity model of the Resource, is composed by the following attributes:
  - `elasticityID`: A unique identifier for the elasticity model.
  - `elasticity`: Integer with the available Resources.
  - `elasticityCost`: Instantiation cost of each Resource.
- `resourceType`:  String with the type of the Resource(e.g. LOGICAL, PHYSICAL or COMPUTATIONAL).
- `minimalCapacities`: List with the Minimal Capacities required by the Resource; each Capacity is composed by:
  - `name`: String between "memory", "processor" and "storage".
  - `quantity`: float with the amount of Capacity Required.
- `dockerImage`: String with the concatenation of the placeholder name in the docker-compose, "[IMG:]" and the image name.

The following snippet shows an example of two Resources declared in the JSON file:

```json
{ 
  "userservice": {
  "hierarchyParent": ["mysql"],
  "replaceable": [],
  "elasticityModel": {"elasticityID": "elasmodeluserservice", "elasticity": 5, "elasticityCost": 30.0},
  "resourceType": "LOGICAL", "resourceID": "userservice",
  "minimalCapacities": [
    {"name": "memory", "quantity": 0.2929}, 
    {"name": "processor", "quantity": 0.2}, 
    {"name": "storage", "quantity": 0.5}],
    "dockerImage": "userservice[IMG:]wigo4it/identityserver4:latest"
},
  "frontend": {
    "hierarchyParent": [], "replaceable": [],
    "elasticityModel": {"elasticityID": "elasmodelfrontend", "elasticity": 1, "elasticityCost": 300.0},
    "resourceType": "LOGICAL", "resourceID": "frontend",
    "minimalCapacities": [
      {"name": "memory", "quantity": 2}, 
      {"name": "processor", "quantity": 1}, 
      {"name": "storage", "quantity": 0.88}],
    "dockerImage": "frontend[IMG:]nginx:latest"
  }
  
}
```

#### Create the retorchCI.properties file
The CI file must be placed in `retorchfiles/configurations/`, namely `retorchCI.properties` containing several parameters 
related to the SUT and the Continuous Integration Infrastructure has the following parameters:
- `agentCIName`: the specific Jenkins agent used to execute the test suite.
- `sut-wait-html`: State in the frontend (html displayed) when the SUT is ready to execute the test SUITE.
- `sut-location`: Location of the docker-compose file used to deploy the SUT.
- `docker-frontend-name`: ID of the container used as frontend .
- `docker-frontend-port`: PORT on which the frontend is available.
- `external-binded-port`: EXTERNAL PORT where the frontend is made available (if its available).
- `external-frontend-url`: EXTERNAL URI where the frontend is made available.
- `testsBasePath`: Path to the java project.

The following snippet provides an example of how this file looks like:

```properties
  agentCIName=any
  sut-wait-html=<title>Hello World</title>
  sut-location=$WORKSPACE
  docker-frontend-name=https://sutexample-
  docker-frontend-port=5000
  external-binded-port=
  external-frontend-url=
  testsBasePath=./
```

#### Preparing the docker-compose.yml file
The RETORCH tool also requires to parametrize the docker-compose used to deploy the application by means including the
necessary environment variables in the containers names and URIs, as well as the placeholders of the images specified above.
The following snippet present how it was done in one of the services of the [FullTeaching Test Suite](https://github.com/giis-uniovi/retorch-st-fullteaching):

```diff
services:
  full-teaching-mysql:
-   container_name: full-teaching-mysql
+   container_name: full-teaching-mysql-${tjobname}
     ...
+    networks:
+      - jenkins_network

  full-teaching-openvidu-server-kms:
-    container_name: full-teaching-openvidu-server-kms
-    image: openvidu/openvidu-server-kms:1.7.0
+    container_name: full-teaching-${tjobname}-openvidu-server-kms
+    image: ${mediaserver}
    ...
    environment:
-     - openvidu.publicurl=https://full-teaching-openvidu-server-kms:8443
+     - openvidu.publicurl=https://full-teaching-${tjobname}-openvidu-server-kms:8443
+    networks:
+      - jenkins_network

  full-teaching:
-   container_name: full-teaching
+   container_name: full-teaching-${tjobname}
    ...
    environment:
-     - WAIT_HOSTS=full-teaching-mysql:3306
+     - WAIT_HOSTS=full-teaching-mysql-${tjobname}:3306
-     - MYSQL_PORT_3306_TCP_ADDR=full-teaching-mysql
+     - MYSQL_PORT_3306_TCP_ADDR=full-teaching-mysql-${tjobname}
-     - openvidu.url=https://full-teaching-openvidu-server-kms:8443
+     - openvidu.url=https://full-teaching-${tjobname}-openvidu-server-kms:8443
      - ...
+    networks:
+      - jenkins_network

+ networks:
+  jenkins_network:
+    external: true
```

#### (Optional) Specify script snippets to include in the set-up tear-down and environment
The RETORCH orchestration tool allows to specify scripting code/commands to be included in the generated set-up, tear-down, and 
the environment declaration of each TJob. To include it, the tester must create the following files in `retorch\customscriptscode` 
- `custom-tjob-setup`: Contains the custom set-up code (e.g. deploy the SUT) or custom logging systems.
- `custom-tjob-teardown`: Contains the custom tear-down code (e.g. tear-down the SUT)
- `custom.env`: Contains environment variables common to all TJobs

Examples of the three snippets files can be consulted in [FullTeaching Test Suite](https://github.com/giis-uniovi/retorch-st-fullteaching)
and [eShopOnContainers](https://github.com/giis-uniovi/retorch-st-eShopContainers)

Once created the different properties and configuration files, the tree directory might look like:

```
.
├── src
├── docker-compose.yml
├── retorchfiles/
│   ├── configurations/
│   │   ├── [SUTNAME]SystemResource.json
│   │   └── retorchCI.properties
│   └── customscriptscode/
        ├── custom-tjob-setup
        ├── custom-tjob-teardown
        └── custom.env
```

### Executing the Orchestration tool
[TO-DO] Pending to decide-implement the final version

### RETORCH Orchestration Tool outputs
The tool provides four different outputs: the pipelining code, the necessary scripts to set up, tear down and execute the TJobs(`/retorchfiles/tjoblifecycles`),
the infrastructure(`/retorchfiles/tjoblifecycles`) and the different environment files of each TJob (`/retorchfiles/envfiles`) :
- `Jenkinsfile`: located in the root of the project, contains the pipelining code with the different stages in sequential-parallel 
that perform the different TJob lifecycle stages.
- `/retorchfiles/tjoblifecycles` and `/retorchfiles/coilifecycles` contains the set up, execution, and tear down scripts for the TJobs and infrastructure
- `/retorchfiles/envfiles`: contains the generated custom environment of each TJob.


## Contributing

See the general contribution policies and guidelines for *giis-uniovi* at
[CONTRIBUTING.md](https://github.com/giis-uniovi/.github/blob/main/profile/CONTRIBUTING.md).

## Contact

Cristian Augusto - [augustocristian@uniovi.es](mailto:augustocristian@uniovi.es) - 
[Software Engineering Research Group (GIIS) - University of Oviedo, ES](https://giis.uniovi.es)

## Citing this work

RETORCH E2E Test Orchestration framework:

- Cristian Augusto, Jesús Morán, Antonia Bertolino, Claudio de la Riva, and Javier Tuya,
  “RETORCH: an approach for resource-aware orchestration of end-to-end test cases,”
  *Software Quality Journal*, vol. 28, no. 3, 2020.
  https://doi.org/10.1007/s11219-020-09505-2 - [Full Article available](https://link.springer.com/article/10.1007/s11219-020-09505-2) - [Authors version](https://digibuo.uniovi.es/dspace/bitstream/handle/10651/55405/RETORCHSQJExtension_BUO.pdf;jsessionid=0E661594C8732B8D2CA53636A31E4FD5?sequence=1) -
  [Download citation](https://citation-needed.springer.com/v2/references/10.1007/s11219-020-09505-2?format=refman&flavour=citation)
## Acknowledgments

This work has been developed under the TestBUS (PID2019-105455GB-C32) project supported
by the [Ministry of Science and Innovation (SPAIN)](https://www.ciencia.gob.es/)

