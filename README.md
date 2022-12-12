# lab-to-fhir

> Kafka Stream Processor, transforming laboratory data to MII FHIR

The processor reads lab data as JSON from the input topic and transforms to FHIR resource bundles according to the MII
laboratory module and profile specification.

## Input data

The input data from Synedra AIM consists of FHIR resources of type **DiagnosticReport** and
**Observation**.

## Transformations

- Mapping of `DiagnosticReport` and `Observation` according to the MII profile
- Adding a `ServiceRequest` resource which the DiagnosticReport depends on
- Referencing Patient and Encounter resources by id
- Adding LOINC / UCUM codings

### LOINC mapping

Observation resources which have numerical result values are mapped to LOINC and UCUM using the `loinc` Kafka topic 
(see [loinc-kafka](https://gitlab.diz.uni-marburg.de/etl/loinc-kafka.git) for details).

This results in an additional `coding` with `"system": "http://loinc.org"` where a LOINC mapping entry exists. 
The original Swisslab coding is kept in either case.

Result quantities for value and references ranges are mapped to their corresponding UCUM units.

#### Updates

Changes in the `loinc` topic's mapping data trigger a reprocessing of the laboratory data 
from the input topic where the Swisslab code of Observations correspond to the changed Swisslab 
code of the mapping data.

In order for this to work dynamically, laboratory data is split into single entry FHIR bundles before
joining them with the `loinc` topic. The Foreign-key extractor selects the Observation's Swisslab code
or a generated UUID for DiagnosticReport and ServiceRequest resources (which will never be joined but send to
the output topic anyway).

## <a name="deploy_config"></a> Configuration

The following environment variables can be set:

| Variable                          | Default                             | Description                                                      |
|-----------------------------------|-------------------------------------|------------------------------------------------------------------|
| BOOTSTRAP_SERVERS                 | localhost:9092                      | Kafka brokers                                                    |
| SECURITY_PROTOCOL                 | PLAINTEXT                           | Kafka communication protocol                                     |
| SSL_TRUST_STORE_LOCATION_INTERNAL | /opt/lab-to-fhir/ssl/truststore.jks | Truststore location                                              |
| SSL_TRUST_STORE_PASSWORD          |                                     | Truststore password (if using `SECURITY_PROTOCOL=SSL`)           |
| SSL_KEY_STORE_LOCATION_INTERNAL   | /opt/lab-to-fhir/ssl/keystore.jks   | Keystore location                                                |
| SSL_KEY_STORE_PASSWORD            |                                     | Keystore password (if using `SECURITY_PROTOCOL=SSL`)             |
| SSL_TRUST_STORE_PASSWORD          |                                     | Truststore password (if using `SECURITY_PROTOCOL=SSL`)           |
| INPUT_TOPIC                       | aim-lab                             | Topic to read from                                               |
| MAPPING_TOPIC                     | loinc                               | Mapping topic to join                                            |
| OUTPUT_TOPIC                      | lab-fhir                            | Topic to store result bundles                                    |
| WEB_PORT                          |                                     | Port to map the web endpoints (health, prometheus, info, metric) |
| LOG_LEVEL                         | info                                | Log level (error, warn, info, debug)                             |

Additional application properties can be set by overriding values form the [application.yml](application.yml) by using environment variables. 

## Tests

This project includes unit and integration tests.

### Setup

FHIR validation tests need the profile files used in this processor (i.e. MII profiles). Those are managed as NPM
dependencies ([package.json](package.json)) and must be installed locally prior to testing:

```sh
npm i
```

## Error handling

### Serialization errors

ConsumerRecords are stored in `error.[INPUT TOPIC].lab-to-fhir`.

## Deployment

This project includes a docker-compose file for deployment purposes. Environment variables can be set according to the
provided `sample.env`. Remember to replace the `IMAGE_TAG` variable according to the desired version tag. Available
tags can be found at the [Container Registry](container_registry/) or under [Releases](-/releases/).

## Development

A [test setup](dev/docker-compose.yml) and [test data provider](dev/docker-compose-data.yml) 
is available for development purposes.

### Builds

You can build a docker image for this processor by using the provided [Dockerfile](Dockerfile).

⚠ FHIR profiles must be installed for the build step to run successfully. 
