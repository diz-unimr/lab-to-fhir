# lab-to-fhir
[![MegaLinter](https://github.com/diz-unimr/lab-to-fhir/actions/workflows/mega-linter.yml/badge.svg?branch=main)](https://github.com/diz-unimr/lab-to-fhir/actions/workflows/mega-linter.yml?query=branch%3Amain) ![java](https://github.com/diz-unimr/lab-to-fhir/actions/workflows/build.yml/badge.svg) ![docker](https://github.com/diz-unimr/lab-to-fhir/actions/workflows/release.yml/badge.svg) [![codecov](https://codecov.io/gh/diz-unimr/lab-to-fhir/branch/main/graph/badge.svg?token=ub0ZDTKwrz)](https://codecov.io/gh/diz-unimr/lab-to-fhir)

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

Observation resources which have numerical result values are mapped to LOINC and UCUM using a fixed mapping.

On startup, the _lab-to-fhir_ processor loads data from a [mapping package](https://gitlab.diz.uni-marburg.de/mapping/loinc-mapping/-/packages) which consists of a csv file and metadata.

This data is looked up on processing and results in an additional `coding` (`"system": "http://loinc.org"`) where a LOINC mapping entry exists.
The original Swisslab coding is kept in either case.

Result quantities for value and references ranges are mapped to their corresponding UCUM units.

## <a name="deploy_config"></a> Configuration

The following environment variables can be set:

| Variable                           | Default                             | Description                                                                                                                                                                                                                                  |
|------------------------------------|-------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| BOOTSTRAP_SERVERS                  | localhost:9092                      | Kafka brokers                                                                                                                                                                                                                                |
| SECURITY_PROTOCOL                  | PLAINTEXT                           | Kafka communication protocol                                                                                                                                                                                                                 |
| SSL_TRUST_STORE_LOCATION_INTERNAL  | /opt/lab-to-fhir/ssl/truststore.jks | Truststore location                                                                                                                                                                                                                          |
| SSL_TRUST_STORE_PASSWORD           |                                     | Truststore password (if using `SECURITY_PROTOCOL=SSL`)                                                                                                                                                                                       |
| SSL_KEY_STORE_LOCATION_INTERNAL    | /opt/lab-to-fhir/ssl/keystore.jks   | Keystore location                                                                                                                                                                                                                            |
| SSL_KEY_STORE_PASSWORD             |                                     | Keystore password (if using `SECURITY_PROTOCOL=SSL`)                                                                                                                                                                                         |
| SSL_TRUST_STORE_PASSWORD           |                                     | Truststore password (if using `SECURITY_PROTOCOL=SSL`)                                                                                                                                                                                       |
| INPUT_TOPIC                        | aim-lab                             | Topic to read from                                                                                                                                                                                                                           |
| OUTPUT_TOPIC                       | lab-fhir                            | Topic to store result bundles                                                                                                                                                                                                                |
| MAPPING_LOINC_VERSION              | 3.0.1                               | LOINC mapping package version: [Package Registry · mapping / loinc-mapping](https://gitlab.diz.uni-marburg.de/mapping/loinc-mapping/-/packages/))                                                                                            |
| MAPPING_LOINC_CREDENTIALS_USER     |                                     | LOINC mapping package registry user                                                                                                                                                                                                          |
| MAPPING_LOINC_CREDENTIALS_PASSWORD |                                     | LOINC mapping package registry password                                                                                                                                                                                                      |
| MAPPING_LOINC_PROXY                |                                     | Proxy server to use when pulling the package                                                                                                                                                                                                 |
| MAPPING_LOINC_LOCAL                |                                     | Name of the local LOINC mapping package file to use (see [application resources](src/main/resources)) <br /><br /> **NOTE**: This option does not pull the file from the registry and credentials and version are fixed by the local package |
| LOG_LEVEL                          | info                                | Log level (error, warn, info, debug)                                                                                                                                                                                                         |

Additional application properties can be set by overriding values form the [application.yml](src/main/resources/application.yml) by using environment variables.

## Mapping updates

In addition to the regular Kafka processor this application uses a separate
update processor to apply mapping updates to all records up until the
current offset state of the regular processor.

The update processor is a separate Kafka consumer and keeps its own offset
state in order to be able to resume unfinished updates. On completion, the
update consumer group is deleted.

On startup, the application checks the configured mapping version and
determines a diff between the mappings of the current and the last used
mapping version. This data is stored in the Kafka topic `mapping` with the key
`lab-update`.

In case there are no changes or the mapping versions used are equal, the
update processor is not started.

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

Errors which occur during serialization of records from the input topic cause the processor to stop
and move to an error state.

### Mapping errors

Records which can't be mapped are skipped.

## Deployment

This project includes a docker compose file for deployment purposes.
Environment variables can be set according to the
provided `sample.env`. Remember to replace the `IMAGE_TAG` variable according to the desired version tag. Available
tags can be found at the [Container Registry](https://github.com/orgs/diz-unimr/packages?repo_name=lab-to-fhir) or under [Releases](https://github.com/diz-unimr/lab-to-fhir/releases).

## Development

A [test setup](dev/compose.yaml) and [test data provider](dev/compose-data.yaml)
is available for development purposes.

### Builds

You can build a docker image for this processor by using the provided [Dockerfile](Dockerfile).

⚠ FHIR profiles must be installed for the build step to run successfully.

## License

[AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.en.html)
