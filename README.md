# lab-to-fhir

> Kafka Stream Processor, transforming laboratory data to FHIR

The processor reads lab data as JSON from the input topic and transforms to FHIR resource bundles according to the MII
laboratory module and profile specification.

## Input data

The input data from Synedra AIM consists of FHIR resources of type **DiagnosticReport** and
**Observation**.

## Transformations

- Mapping of `DiagnosticReport` according to the MII profile
- Mapping of `Observation` according to the MII profile
    - `valueQuantity` and `referenceRange` to LOINC and UCUM
- Creating a `ServiceRequest` resource is added which the DiagnosticReport depends on
- Creating bundle with all resources and referencing Patient and Encounter resources by id
- Pseudonymizing data via the FHIR Pseudonymizer

## Error handling

### Serialization errors

ConsumerRecords are stored in `error.[INPUT TOPIC].lab-to-fhir`.

### Processing errors

Messages which produce errors (i.e. Exceptions) while processing are send to the _ERROR_TOPIC_.

## Deployment

This project includes a docker-compose file for deployment purposes. Environment variables can be set according to the
provided `.sample.env`. Remember to replace the `IMAGE_TAG` variable according to the desired version tag. Available
tags can be found at the [Container Registry](container_registry/) or under [Releases](-/releases/).

### Quickstart

1. Copy the sample configuration
    ```sh
    cp deploy/.sample.env .env
    ```
2. Set the environment variables (see [Configuration](#deploy_config)) in `.env`
3. Start the service:
    ```sh
    docker-compose -f deploy/docker-compose.yml up -d
    ```

### <a name="deploy_config"></a> Configuration

The following environment variables can be set:

| Variable                           | Default        | Description                                                                                                                                                                                                                                  |
|------------------------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| KAFKA_BROKERS                      |                | Kafka broker hosts                                                                                                                                                                                                                           |
| SECURITY_PROTOCOL                  | PLAINTEXT      | Kafka communication protocol                                                                                                                                                                                                                 |
| SSL_KEY_STORE_PASSWORD             |                | Keystore password (if using `SECURITY_PROTOCOL=SSL`)                                                                                                                                                                                         |
| SSL_TRUST_STORE_PASSWORD           |                | Truststore password (if using `SECURITY_PROTOCOL=SSL`)                                                                                                                                                                                       |
| INPUT_TOPIC                        | aim-lab        | Topic to read from                                                                                                                                                                                                                           |
| OUTPUT_TOPIC                       | lab-fhir       | Topic to store result bundles                                                                                                                                                                                                                |
| ERROR_TOPIC                        | lab-fhir-error | Topic to store result bundles                                                                                                                                                                                                                |
| PSEUDONYMIZER_URL                  |                | FHIR endpoint of the FHIR pseudonymizer service                                                                                                                                                                                              |
| WEB_PORT                           |                | Port to map the web endpoints (health, prometheus, info, metric)                                                                                                                                                                             |
| LOG_LEVEL                          | info           | Log level (error, warn, info, debug)                                                                                                                                                                                                         |
| MAPPING_LOINC_VERSION              | 9fa7de13       | LOINC mapping package version: [Package Registry · mapping / loinc-mapping](https://gitlab.diz.uni-marburg.de/mapping/loinc-mapping/-/packages/))                                                                                            |
| MAPPING_LOINC_CREDENTIALS_USER     |                | LOINC mapping package registry user                                                                                                                                                                                                          |
| MAPPING_LOINC_CREDENTIALS_PASSWORD |                | LOINC mapping package registry password                                                                                                                                                                                                      |
| MAPPING_LOINC_PROXY                |                | Proxy server to use when pulling the package                                                                                                                                                                                                 |
| MAPPING_LOINC_LOCAL                |                | Name of the local LOINC mapping package file to use (see [application resources](src/main/resources)) <br /><br /> **NOTE**: This option does not pull the file from the registry and credentials and version are fixed by the local package |

## Development

### Quickstart

The development configuration can be deployed "as is" via the provided compose file:

```sh
docker-compose -f dev/docker-compose.yml up -d
```

This will provide a complete development environment including Kafka (broker, zookeeper, schema-registry, REST proxy)
as well as services to provide data (aim-db + connector) and which the processor depends on (
fhir-pseudonymizer, initialized gpas).

#### Start the processor

1. Check that the source topic exists by going to http://localhost:9000 (Kafdrop). The input topic is `aim-lab`.
   See [Connector Troubleshooting](#connector) to start the connector manually if the topic fails to show up after the
   environment is up.
2. Start the `lab-to-fhir` application in the IDE of your choice and wait for records to be processed.
3. Check the output topic `lab-fhir`.

You can check the contents of the input and output topic via the [Kafka Web UI](http://localhost:9000/) or by setting up
a consumer. See [Query records via the REST proxy](#query-records) to consume records via the REST proxy.

#### ⚠ Troubleshooting

The `connect-lab` service may fail with a timeout if starting the deployment services is taking too much time. You can
start it manually:

```sh
docker-compose -f docker-compose.dev.yml run connect-lab
```

### Tests

This project includes unit and end-to-end (integration) tests.

#### Setup

FHIR validation tests need the profile files used in this processor (i.e. MII profiles). Those are managed as NPM
dependencies ([package.json](package.json)) and must be installed locally prior to testing:

```sh
npm i
```

#### Integration tests

These tests use [Testcontainers](https://www.testcontainers.org) to provide a real-world test environment. This is
similar to the external development environment (with the
[compose file](dev/docker-compose.yml) under `dev`). However, it deploys docker containers by using the
underlying [docker-java](https://github.com/docker-java/docker-java) API and has the benefit of simplifying Kafka
services ([Kafka Containers](https://www.testcontainers.org/modules/kafka/)) as well as providing better means to
determine container "readyness" with
[Wait strategies](https://www.testcontainers.org/features/startup_and_waits/).

### Builds

You can build a docker image for this processor by using the provided [Dockerfile](Dockerfile).

⚠ FHIR profiles must be installed for the build step to run successfully. 
