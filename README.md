# lab-to-fhir

[![MegaLinter](https://github.com/diz-unimr/lab-to-fhir/actions/workflows/mega-linter.yml/badge.svg?branch=main)](https://github.com/diz-unimr/lab-to-fhir/actions/workflows/mega-linter.yml?query=branch%3Amain) ![java](https://github.com/diz-unimr/lab-to-fhir/actions/workflows/build.yml/badge.svg) ![docker](https://github.com/diz-unimr/lab-to-fhir/actions/workflows/release.yml/badge.svg) [![codecov](https://codecov.io/gh/diz-unimr/lab-to-fhir/branch/main/graph/badge.svg?token=ub0ZDTKwrz)](https://codecov.io/gh/diz-unimr/lab-to-fhir)

> Kafka Stream processors, transforming laboratory data to MII FHIR

This service reads lab data from Kafka topics and transforms it to
FHIR 🔥 resource bundles according to the MII laboratory module and profile
specification.

There are currently two different processors to support reading data from a
legacy input topic (aim) as well as a HL7v2 formatted topic (hl7).

These two processors can be enabled/disabled independently.

## Input data

### aim

The input data from Synedra AIM consists of FHIR resources of type
_DiagnosticReport_ and _Observation_.

## hl7

Supported HL7v2 input data are _ORU R01_ messages in pipe delimited format.
The consumer supports HL7 version _2.2_ with additional mappings for data
in repeating values in OBX-5 segments (which is strictly not part of the
specification until later versions).

## Transformations

- Mapping of `ServiceRequest`, `DiagnosticReport` and `Observation` according
  to the MII profile
- Referencing Patient and Encounter resources via logical references

## Filters

A date filter can be configured to select which messages are processed and
which are skipped. This can be used to prevent duplication of data present
in both input topics, for example.

Filters consist of a comparator (`<`,`<=`,`>`,`>=`,`=`) and a date value
(`YYYY-MM-DD`). If the filter expression matches the laboratory report's
effective date it is mapped.
Filters are configured per input topic and are optional.

E.g.:

```yml
mapping.hl7.filter: ">=2022-01-01"
```

## <a name="deploy_config"></a> Configuration

The following environment variables can be set:

| Variable                          | Default                             | Description                                                              |
|-----------------------------------|-------------------------------------|--------------------------------------------------------------------------|
| BOOTSTRAP_SERVERS                 | localhost:9092                      | Kafka brokers                                                            |
| SECURITY_PROTOCOL                 | PLAINTEXT                           | Kafka communication protocol                                             |
| SSL_TRUST_STORE_LOCATION_INTERNAL | /opt/lab-to-fhir/ssl/truststore.jks | Truststore location                                                      |
| SSL_TRUST_STORE_PASSWORD          |                                     | Truststore password (if using `SECURITY_PROTOCOL=SSL`)                   |
| SSL_KEY_STORE_LOCATION_INTERNAL   | /opt/lab-to-fhir/ssl/keystore.jks   | Keystore location                                                        |
| SSL_KEY_STORE_PASSWORD            |                                     | Keystore password (if using `SECURITY_PROTOCOL=SSL`)                     |
| SSL_TRUST_STORE_PASSWORD          |                                     | Truststore password (if using `SECURITY_PROTOCOL=SSL`)                   |
| AIM_TOPIC                         | aim-lab                             | AIM input topic                                                          |
| HL7_TOPIC                         | hl7-lab                             | HL7v2 input topic                                                        |
| OUTPUT_TOPIC                      | lab-fhir                            | Topic to store result bundles                                            |
| MAPPING_AIM_FILTER                |                                     | Filter expression for the `AIM_TOPIC`. See [Filters](#filters).          |
| MAPPING_HL7_FILTER                |                                     | Filter expression for the `HL7_TOPIC`. See [Filters](#filters).          |
| CONSUMER_CONCURRENCY              | 3                                   | Number of concurrent Kafka consumer clients                              |
| REPLICATION_FACTOR                | 3                                   | Output topic replication factor                                          |
| MIN_PARTITION_COUNT               | 3                                   | Number of minimum partitions for the output topic (if created on demand) |
| LOG_LEVEL                         | info                                | Log level (error, warn, info, debug)                                     |

Additional application properties can be set by overriding values form
the [application.yml](src/main/resources/application.yml) by using environment
variables.

## Tests

This project includes unit and integration tests.

### Setup

FHIR validation tests need the profile files used in this processor (i.e. MII
profiles). Those are managed as NPM
dependencies ([package.json](package.json)) and must be installed locally prior
to testing:

```sh
npm i
```

## Error handling

### Serialization errors

Errors which occur during serialization of records from the input topic cause
the processor to stop
and move to an error state.

### Mapping errors

Records which can't be mapped are skipped.

## Deployment

This project includes a docker compose file for deployment purposes.
Environment variables can be set according to the
provided `sample.env`. Remember to replace the `IMAGE_TAG` variable according to
the desired version tag. Available
tags can be found at
the [Container Registry](https://github.com/orgs/diz-unimr/packages?repo_name=lab-to-fhir)
or under [Releases](https://github.com/diz-unimr/lab-to-fhir/releases).

## Development

A [test setup](dev/compose.yaml) with test data is available for development
purposes.

### Builds

You can build a docker image for this processor by using the
provided [Dockerfile](Dockerfile).

⚠ FHIR profiles must be installed for the build step to run successfully.

## License

[AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.en.html)
