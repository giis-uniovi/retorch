[![Status](https://github.com/giis-uniovi/retorch/actions/workflows/build.yml/badge.svg)](https://github.com/giis-uniovi/retorch/actions)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=my%3Aretorch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=my%3Aretorch)
[![Maven Central (annotations)](https://img.shields.io/maven-central/v/io.github.giis-uniovi/retorch-annotations)](https://central.sonatype.com/artifact/io.github.giis-uniovi/retorch-annotations)

# RETORCH: Resource-aware End-to-End Test Orchestration

This repository contains a series of components of the RETORCH End-to-End (E2E) test orchestration framework. It's primary
goal is to optimize E2E test execution by reducing both the execution time and the number of unnecessary Resource[^1]
redeployment's.

NOTE: The repository is a work in progress, the initial version only made available the annotations, and currently we're 
migrating the orchestration generator module.
Additional components will be added in future releases.

[^1]: Henceforth, we will use the term "Resources" (capitalized) when referring to the ones required by the E2E test suite.

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
- Configure the E2E test suite as indicated below
- Execute the orchestration generator and generate the pipelining-scripting code
- [TO-DO]

## RETORCH Annotations

The RETORCH framework provides a set of custom annotations to define and manage Resources used in end-to-end testing. These
annotations allow testers to group, schedule, and characterize Resources. To execute test cases using RETORCH,
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
- `replaceable`: A list of Resources that can replace the current one.

```java
@Resource(resID = "LoginService", replaceable = {})
```

The following code snippets illustrate a test case annotated with multiple Resources and access modes:

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
The RETORCH framework provides a generator that creates the Execution Plan, along with the required pipelining and script 
files for execution in a CI environment. The generation of scripts and pipelining code is based on the Access Modes 
annotated within the test cases and the Resource information specified in `retorchfiles/configurations/[SUT_NAME]SystemResources.json`.

The RETORCH orchestration generator requires 4 inputs:
- The annotated E2E test cases with the [RETORCH access modes](#retorch-annotations) into a **single module** Maven project.
- A file with the Resources in JSON format.
- A properties file with the Environment configuration.
- A custom `docker-compose.yml` file.

Given these inputs, the generator gives as output the necessary scripting code and the `Jenkinsfile` to execute the E2E test
suite into a Continuous Integration system.

### Prepare the E2E Test suite
The first step is to create several folders to store the configurations and place the `docker-compose.yml`
in the single module project root.
The resulting directory tree might look like as:
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
  - `configurations/`: stores the Resources and CI configuration files.
  - `customscriptscode/`: stores the different script snippets for the tear down, set up and environment.
- The `docker-compose.yml` in the root of the project.
- The different project directories and files.

The following subsections explain how to create each configuration file and how to prepare the `docker-compose.yml` file.

#### Create the Resource.json file
The Resource file must be placed in the `retorchfiles/configurations/` and named with the system or test suite name, followed
by `SystemResources.json` . This file contains a map with a series of Resources, using their unique ResourceID as a key. For each
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
- `dockerImage`: String with the concatenation of the placeholder name in the docker-compose, using `;` as separator between  placeholder and the image name.

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
    "dockerImage": "userservice;wigo4it/identityserver4:latest"
},
  "frontend": {
    "hierarchyParent": [], "replaceable": [],
    "elasticityModel": {"elasticityID": "elasmodelfrontend", "elasticity": 1, "elasticityCost": 300.0},
    "resourceType": "LOGICAL", "resourceID": "frontend",
    "minimalCapacities": [
      {"name": "memory", "quantity": 2}, 
      {"name": "processor", "quantity": 1}, 
      {"name": "storage", "quantity": 0.88}],
    "dockerImage": "frontend;nginx:latest"
  }
  
}
```

#### Create the retorchCI.properties file
The CI file must be placed in `retorchfiles/configurations/`, namely `retorchCI.properties` containing several parameters 
related to the SUT and the Continuous Integration Infrastructure, these parameters are the following:
- `agentCIName`: the specific Jenkins agent used to execute the test suite.
- `sut-wait-html`: state in the frontend (HTML displayed) when the SUT is ready to execute the test SUITE.
- `sut-location`: location of the `docker-compose.yml` file used to deploy the SUT.
- `docker-frontend-name`: ID of the container used as frontend .
- `docker-frontend-port`: PORT on which the frontend container is available.
- `external-binded-port`: EXTERNAL PORT where the frontend is made available (if its available).
- `external-frontend-url`: EXTERNAL URI where the frontend is made available.
- `testsBasePath`: Path to the Java project root.

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
The orchestration generator also requires to parametrize the `docker-compose.yml` used to deploy the application by means including the
necessary environment variables in the containers names and URIs, as well as the placeholders of the images specified above.
Examples of the necessary changes in the `docker-compose.yml` can consulted in the FullTeaching and eShopOnContainers repositories:

- FullTeaching:
  - [Original `docker-compose.yml`](https://github.com/elastest/full-teaching/blob/master/application/docker-compose/docker-compose.yml)
  - [RETORCH `docker-compose.yml`](https://github.com/giis-uniovi/retorch-st-fullteaching/blob/main/docker-compose.yml)
- EshopContainers:
  - [Original `docker-compose.yml`](https://github.com/erjain/eShopOnContainers/blob/dev/src/docker-compose.yml) and [ `docker-compose.prod.yml`](https://github.com/erjain/eShopOnContainers/blob/dev/src/docker-compose.prod.yml)
  - [RETORCH `docker-compose.yml`](https://github.com/giis-uniovi/retorch-st-eShopContainers/blob/main/sut/src/docker-compose.yml)

#### (Optional) Specify script snippets to include in the set-up tear-down and environment
The RETORCH orchestration generator allows to specify scripting code/commands to be included in the generated set up, tear down, and 
the environment declaration of each TJob. To include it, the tester must create the following files in `retorch/customscriptscode`: 
- `custom-tjob-setup`: Contains the custom set up code (e.g. declare some environment variable specific for each TJob) or custom logging systems.
- `custom-tjob-teardown`: Contains the custom tear down code (e.g. save some generated outputs).
- `custom.env`: Contains  configurations and environment variables common to all TJobs.

Examples of the three snippets files can be consulted in [FullTeaching Test Suite](https://github.com/giis-uniovi/retorch-st-fullteaching)
and [eShopOnContainers](https://github.com/giis-uniovi/retorch-st-eShopContainers).

Once created the different properties and configuration files, the single module directory tree might look like:

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

### Executing the Orchestration generator
Once all the files created and the `docker-compose.yml` is prepared, to execute the generator we only need to call the
main class `giis.retorch.orchestration.main.OrchestationGeneratorMainClass`. The calling of this class must be done in
the same package of the annotated E2E test cases, in order to be capable to access to the generated java classes through
the Java class loader and extract the RETORCH `@AccessMode` annotations.
The call of the main method must be done with the following 3 parameters as arguments:
- `rootPackageNameTests`: String that specifies the root package name where tests are located.
- `systemName`: String that specifies the system name, must correspond with the name used in the [Resources JSON file](#create-the-resourcejson-file).
- `jenkinsFilePath`: String with the location where the `Jenkinsfile` will be created, it must be the project root.

For example, in the [FullTeaching test suite](https://github.com/giis-uniovi/retorch-st-fullteaching) the `rootPackageNameTests` is `com.fullteaching.e2e.no_elastest.functional.test`,
the `systemName` is `FullTeaching` and the `jenkinsFilePath` is `./`. The `OrchestationGeneratorMainClass` must be called under 
the test package, e.g. `com.fullteaching.e2e.no_elastest`


### RETORCH Orchestration generator outputs
The generator provides four different outputs: the pipelining code, the necessary scripts to set up, tear down and execute the TJobs(`retorchfiles/scripts/tjoblifecycles`),
the infrastructure(`retorchfiles/scripts/coilifecycles`) and the different environment files of each TJob (`retorchfiles/envfiles`) :
- `Jenkinsfile`: located in the root of the project, contains the pipelining code with the different stages in sequential-parallel 
that perform the different TJob lifecycle stages.
- `retorchfiles/scripts/tjoblifecycles` and `retorchfiles/scripts/coilifecycles` contains the set up, execution, and tear down scripts for the TJobs and infrastructure
- `retorchfiles/envfiles`: contains the generated custom environment of each TJob.

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

RETORCH*: A Cost and Resource aware Model for E2E Testing in the Cloud:

- Cristian Augusto, Jesús Morán, Antonia Bertolino, Claudio de la Riva, and Javier Tuya,
  “RETORCH*: A Cost and Resource aware Model for E2E Testing in the Cloud”,
  *Journal of Systems and Software*, vol. 221 , pages. 112237, 2025.
  https://doi.org/10.1016/j.jss.2024.112237 - [Full Paper available](https://www.sciencedirect.com/science/article/pii/S0164121224002814?via%3Dihub) - [Authors version](https://hdl.handle.net/10651/75794) -
  [Download citation](https://www.sciencedirect.com/science/article/pii/S0164121224002814?via%3Dihub#:~:text=Export%20citation%20to%20text)

## Acknowledgments

This work has been developed under the TestBUS (PID2019-105455GB-C32) and project supported
by the [Ministry of Science and Innovation (SPAIN)](https://www.ciencia.gob.es/)

