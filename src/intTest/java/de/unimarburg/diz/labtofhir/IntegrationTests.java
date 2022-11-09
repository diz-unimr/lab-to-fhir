package de.unimarburg.diz.labtofhir;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.mapper.LoincMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.LoincMap;
import de.unimarburg.diz.labtofhir.model.LoincMapEntry;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = {LabToFhirApplication.class, LoincMapper.class})
@TestPropertySource(properties = {"mapping.loinc.version=''", "mapping.loinc.credentials.user=''",
    "mapping.loinc.credentials.password=''", "mapping.loinc.local=mapping-swl-loinc.zip"})
public class IntegrationTests extends TestContainerBase {


    @Autowired
    private FhirProperties fhirProperties;

    @DynamicPropertySource
    private static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrapServers", kafka::getBootstrapServers);
    }

    @BeforeAll
    public static void setupContainers() {
        setup();
    }

    @Test
    public void bundlesAreMapped() {
        // setup test topics
        //        3:{"id":3,"fhir":"{\"code\": {\"text\": \"Laborwerte\", \"coding\": [{\"code\": \"11502-2\", \"system\": \"http://loinc.org\", \"display\": \"Laborwerte\"}]}, \"text\": {\"div\": \"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\"><div class=\\\"hapiHeaderText\\\"> Laborwerte </div><table class=\\\"hapiPropertyTable\\\"><tbody><tr><td>Status</td><td>PARTIAL</td></tr></tbody></table><table class=\\\"hapiTableOfValues\\\"><thead><tr><td>Name</td><td>Value</td><td>Interpretation</td><td>Reference Range</td><td>Status</td></tr></thead><tbody><tr class=\\\"hapiTableOfValuesRowOdd\\\"><td> Natrium </td><td>135 mmol/l</td><td></td><td> 134 mmol/l - 143 mmol/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowEven\\\"><td> Kalium </td><td>4.2 mmol/l</td><td></td><td> 3.3 mmol/l - 4.6 mmol/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowOdd\\\"><td> Calcium </td><td>2.39 mmol/l</td><td></td><td> 2.20 mmol/l - 2.66 mmol/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowEven\\\"><td> AST (GOT) </td><td>129 U/l</td><td></td><td> 15 U/l - 41 U/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowOdd\\\"><td> ALT (GPT) </td><td>104 U/l</td><td></td><td> 8 U/l - 45 U/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowEven\\\"><td> Alk. Phosphatase </td><td>225 U/l</td><td></td><td> 75 U/l - 363 U/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowOdd\\\"><td> GGT </td><td>37 U/l</td><td></td><td> 6 U/l - 30 U/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowEven\\\"><td> Kreatinin </td><td>0.34 mg/dl</td><td></td><td> 0.26 mg/dl - 0.77 mg/dl </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowOdd\\\"><td> Leukozyten </td><td>25.5 G/l</td><td></td><td> 4.5 G/l - 11.4 G/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowEven\\\"><td> Erythrozyten </td><td>2.5 T/l</td><td></td><td> 4.10 T/l - 5.55 T/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowOdd\\\"><td> Hämoglobin </td><td>92 g/l</td><td></td><td> 125 g/l - 160 g/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowEven\\\"><td> Hämatokrit </td><td>0.26 l/l</td><td></td><td> 0.37 l/l - 0.48 l/l </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowOdd\\\"><td> MCV </td><td>103 fl</td><td></td><td> 78 fl - 93 fl </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowEven\\\"><td> MCH </td><td>37 pg</td><td></td><td> 26.0 pg - 32.5 pg </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowOdd\\\"><td> MCHC </td><td>359 g/l Ery</td><td></td><td> 315 g/l Ery - 360 g/l Ery </td><td>FINAL</td></tr><tr class=\\\"hapiTableOfValuesRowEven\\\"><td> Thrombozyten </td><td>891 G/l</td><td></td><td> 170 G/l - 400 G/l </td><td>PRELIMINARY</td></tr><tr class=\\\"hapiTableOfValuesRowOdd\\\"><td> Normoblasten </td><td>folgt</td><td></td><td></td><td>REGISTERED</td></tr></tbody></table></div>\", \"status\": \"generated\"}, \"result\": [{\"reference\": \"#embedded-observation-1\"}, {\"reference\": \"#embedded-observation-2\"}, {\"reference\": \"#embedded-observation-3\"}, {\"reference\": \"#embedded-observation-4\"}, {\"reference\": \"#embedded-observation-5\"}, {\"reference\": \"#embedded-observation-6\"}, {\"reference\": \"#embedded-observation-7\"}, {\"reference\": \"#embedded-observation-8\"}, {\"reference\": \"#embedded-observation-9\"}, {\"reference\": \"#embedded-observation-10\"}, {\"reference\": \"#embedded-observation-11\"}, {\"reference\": \"#embedded-observation-12\"}, {\"reference\": \"#embedded-observation-13\"}, {\"reference\": \"#embedded-observation-14\"}, {\"reference\": \"#embedded-observation-15\"}, {\"reference\": \"#embedded-observation-16\"}, {\"reference\": \"#embedded-observation-17\"}], \"status\": \"partial\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"KLIN\"}], \"contained\": [{\"id\": \"embedded-patient\", \"name\": [{\"given\": [\"Testvorname\"], \"family\": \"Testnachname\"}], \"gender\": \"male\", \"birthDate\": \"1990-06-01\", \"identifier\": [{\"value\": \"599999\"}], \"resourceType\": \"Patient\"}, {\"id\": \"embedded-visit\", \"class\": {\"code\": \"AMB\", \"system\": \"http://terminology.hl7.org/CodeSystem/v3-ActCode\", \"display\": \"ambulatory\"}, \"status\": \"unknown\", \"identifier\": [{\"value\": \"psn-90462334\"}], \"resourceType\": \"Encounter\"}, {\"id\": \"embedded-producer\", \"identifier\": [{\"value\": \"KLIN\"}], \"resourceType\": \"Organization\"}, {\"id\": \"embedded-observation-1\", \"code\": {\"text\": \"Natrium\", \"coding\": [{\"code\": \"NA\", \"display\": \"Natrium\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Klinische Chemie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_NA\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"mmol/l\", \"value\": 135}, \"referenceRange\": [{\"low\": {\"unit\": \"mmol/l\", \"value\": 134}, \"high\": {\"unit\": \"mmol/l\", \"value\": 143}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-2\", \"code\": {\"text\": \"Kalium\", \"coding\": [{\"code\": \"K\", \"display\": \"Kalium\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Klinische Chemie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_K\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"mmol/l\", \"value\": 4.2}, \"referenceRange\": [{\"low\": {\"unit\": \"mmol/l\", \"value\": 3.3}, \"high\": {\"unit\": \"mmol/l\", \"value\": 4.6}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-3\", \"code\": {\"text\": \"Calcium\", \"coding\": [{\"code\": \"CA\", \"display\": \"Calcium\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Klinische Chemie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_CA\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"mmol/l\", \"value\": 2.39}, \"referenceRange\": [{\"low\": {\"unit\": \"mmol/l\", \"value\": 2.20}, \"high\": {\"unit\": \"mmol/l\", \"value\": 2.66}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-4\", \"code\": {\"text\": \"AST (GOT)\", \"coding\": [{\"code\": \"AST\", \"display\": \"AST (GOT)\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Klinische Chemie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_AST\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"U/l\", \"value\": 129}, \"interpretation\": [{\"coding\": [{\"code\": \"H\"}]}], \"referenceRange\": [{\"low\": {\"unit\": \"U/l\", \"value\": 15}, \"high\": {\"unit\": \"U/l\", \"value\": 41}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-5\", \"code\": {\"text\": \"ALT (GPT)\", \"coding\": [{\"code\": \"ALT\", \"display\": \"ALT (GPT)\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Klinische Chemie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_ALT\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"U/l\", \"value\": 104}, \"interpretation\": [{\"coding\": [{\"code\": \"H\"}]}], \"referenceRange\": [{\"low\": {\"unit\": \"U/l\", \"value\": 8}, \"high\": {\"unit\": \"U/l\", \"value\": 45}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-6\", \"code\": {\"text\": \"Alk. Phosphatase\", \"coding\": [{\"code\": \"AP\", \"display\": \"Alk. Phosphatase\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Klinische Chemie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_AP\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"U/l\", \"value\": 225}, \"referenceRange\": [{\"low\": {\"unit\": \"U/l\", \"value\": 75}, \"high\": {\"unit\": \"U/l\", \"value\": 363}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-7\", \"code\": {\"text\": \"GGT\", \"coding\": [{\"code\": \"GGT\", \"display\": \"GGT\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Klinische Chemie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_GGT\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"U/l\", \"value\": 37}, \"interpretation\": [{\"coding\": [{\"code\": \"H\"}]}], \"referenceRange\": [{\"low\": {\"unit\": \"U/l\", \"value\": 6}, \"high\": {\"unit\": \"U/l\", \"value\": 30}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-8\", \"code\": {\"text\": \"Kreatinin\", \"coding\": [{\"code\": \"KREA\", \"display\": \"Kreatinin\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Klinische Chemie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_KREA\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"mg/dl\", \"value\": 0.34}, \"referenceRange\": [{\"low\": {\"unit\": \"mg/dl\", \"value\": 0.26}, \"high\": {\"unit\": \"mg/dl\", \"value\": 0.77}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-9\", \"code\": {\"text\": \"Leukozyten\", \"coding\": [{\"code\": \"LEU\", \"display\": \"Leukozyten\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Hämatologie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_LEU\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"G/l\", \"value\": 25.5}, \"interpretation\": [{\"coding\": [{\"code\": \"H\"}]}], \"referenceRange\": [{\"low\": {\"unit\": \"G/l\", \"value\": 4.5}, \"high\": {\"unit\": \"G/l\", \"value\": 11.4}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-10\", \"code\": {\"text\": \"Erythrozyten\", \"coding\": [{\"code\": \"ERY\", \"display\": \"Erythrozyten\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Hämatologie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_ERY\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"T/l\", \"value\": 2.5}, \"interpretation\": [{\"coding\": [{\"code\": \"L\"}]}], \"referenceRange\": [{\"low\": {\"unit\": \"T/l\", \"value\": 4.10}, \"high\": {\"unit\": \"T/l\", \"value\": 5.55}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-11\", \"code\": {\"text\": \"Hämoglobin\", \"coding\": [{\"code\": \"HB\", \"display\": \"Hämoglobin\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Hämatologie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_HB\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"g/l\", \"value\": 92}, \"interpretation\": [{\"coding\": [{\"code\": \"L\"}]}], \"referenceRange\": [{\"low\": {\"unit\": \"g/l\", \"value\": 125}, \"high\": {\"unit\": \"g/l\", \"value\": 160}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-12\", \"code\": {\"text\": \"Hämatokrit\", \"coding\": [{\"code\": \"HK\", \"display\": \"Hämatokrit\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Hämatologie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_HK\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"l/l\", \"value\": 0.26}, \"interpretation\": [{\"coding\": [{\"code\": \"L\"}]}], \"referenceRange\": [{\"low\": {\"unit\": \"l/l\", \"value\": 0.37}, \"high\": {\"unit\": \"l/l\", \"value\": 0.48}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-13\", \"code\": {\"text\": \"MCV\", \"coding\": [{\"code\": \"MCV\", \"display\": \"MCV\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Hämatologie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_MCV\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"fl\", \"value\": 103}, \"interpretation\": [{\"coding\": [{\"code\": \"H\"}]}], \"referenceRange\": [{\"low\": {\"unit\": \"fl\", \"value\": 78}, \"high\": {\"unit\": \"fl\", \"value\": 93}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-14\", \"code\": {\"text\": \"MCH\", \"coding\": [{\"code\": \"MCH\", \"display\": \"MCH\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Hämatologie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_MCH\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"pg\", \"value\": 37}, \"interpretation\": [{\"coding\": [{\"code\": \"H\"}]}], \"referenceRange\": [{\"low\": {\"unit\": \"pg\", \"value\": 26.0}, \"high\": {\"unit\": \"pg\", \"value\": 32.5}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-15\", \"code\": {\"text\": \"MCHC\", \"coding\": [{\"code\": \"MCHC\", \"display\": \"MCHC\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Hämatologie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_MCHC\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"g/l Ery\", \"value\": 359}, \"referenceRange\": [{\"low\": {\"unit\": \"g/l Ery\", \"value\": 315}, \"high\": {\"unit\": \"g/l Ery\", \"value\": 360}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-16\", \"code\": {\"text\": \"Thrombozyten\", \"coding\": [{\"code\": \"THRO\", \"display\": \"Thrombozyten\"}]}, \"status\": \"preliminary\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Hämatologie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_THRO\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"G/l\", \"value\": 891}, \"interpretation\": [{\"coding\": [{\"code\": \"H\"}]}], \"referenceRange\": [{\"low\": {\"unit\": \"G/l\", \"value\": 170}, \"high\": {\"unit\": \"G/l\", \"value\": 400}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}, {\"id\": \"embedded-observation-17\", \"code\": {\"text\": \"Normoblasten\", \"coding\": [{\"code\": \"NRBCa\", \"display\": \"Normoblasten\"}]}, \"status\": \"registered\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Hämatologie\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_NRBCa\"}], \"valueString\": \"folgt\", \"resourceType\": \"Observation\", \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299\"}], \"resourceType\": \"DiagnosticReport\", \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}","fhir_obs":"[{\"id\": \"#embedded-observation-1\", \"code\": {\"text\": \"Natrium\", \"coding\": [{\"code\": \"NA\", \"display\": \"Natrium\"}]}, \"status\": \"final\", \"subject\": {\"reference\": \"#embedded-patient\"}, \"category\": [{\"text\": \"Klinische Chemie\"}], \"contained\": [{\"id\": \"embedded-patient\", \"name\": [{\"given\": [\"Testvorname\"], \"family\": \"Testnachname\"}], \"gender\": \"male\", \"birthDate\": \"1990-06-01\", \"identifier\": [{\"value\": \"599999\"}], \"resourceType\": \"Patient\"}, {\"id\": \"embedded-visit\", \"class\": {\"code\": \"AMB\", \"system\": \"http://terminology.hl7.org/CodeSystem/v3-ActCode\", \"display\": \"ambulatory\"}, \"status\": \"unknown\", \"identifier\": [{\"value\": \"psn-90462334\"}], \"resourceType\": \"Encounter\"}, {\"id\": \"embedded-producer\", \"identifier\": [{\"value\": \"KLIN\"}], \"resourceType\": \"Organization\"}], \"encounter\": {\"reference\": \"#embedded-visit\"}, \"performer\": [{\"reference\": \"#embedded-producer\"}], \"identifier\": [{\"value\": \"24051299_NA\"}], \"resourceType\": \"Observation\", \"valueQuantity\": {\"unit\": \"mmol/l\", \"value\": 135}, \"referenceRange\": [{\"low\": {\"unit\": \"mmol/l\", \"value\": 134}, \"high\": {\"unit\": \"mmol/l\", \"value\": 143}}], \"effectiveDateTime\": \"2019-08-13T23:18:00+02:00\"}]","effective_date_time":1565738280000,"fhir_version":4,"status_deleted":0,"inserted_when":1589814342169,"modified":1589814342169,"deleted_when":null}
        var labReport = new LaboratoryReport();
        labReport.setId(42);
        labReport.setResource(new DiagnosticReport()
            .addIdentifier(new Identifier().setValue("report-id"))
            .setSubject(new Reference(new Patient().addIdentifier(new Identifier().setValue("1"))))
            .setEncounter(
                new Reference(new Encounter().addIdentifier(new Identifier().setValue("1")))));

        labReport.setObservations(List.of(new Observation()
            .addIdentifier(new Identifier().setValue("obs-id"))
            .setCode(new CodeableConcept().setCoding(List.of(new Coding(fhirProperties
                .getSystems()
                .getLaboratorySystem(), "NA", null))))
            .setValue(new Quantity(1))));
        var loincMap = new LoincMap()
            .setSwl("NA")
            .setEntries(List.of(new LoincMapEntry()
                .setLoinc("2951-2")
                .setUcum("mmol/L")));

        KafkaHelper.addTopics("laboratory", "loinc");

        var producer = KafkaHelper.createProducer(kafka.getBootstrapServers(),
            new StringSerializer(), new JsonSerializer<>());
        producer.send(
            new ProducerRecord<>("laboratory", String.valueOf(labReport.getId()), labReport));
        producer.send(new ProducerRecord<>("loinc", loincMap.getSwl(), labReport));

        var messages = KafkaHelper.getAtLeast(
            KafkaHelper.createFhirTopicConsumer(kafka.getBootstrapServers()),
            "test-fhir-laboratory", 10);
        var resources = messages
            .stream()
            .map(Bundle.class::cast)
            .flatMap(x -> x
                .getEntry()
                .stream()
                .map(BundleEntryComponent::getResource))
            .collect(Collectors.toList());

        assertThat(resources)
            .flatExtracting(r -> r
                .getMeta()
                .getProfile())
            .extracting(PrimitiveType::getValue)
            .containsOnly(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ServiceRequestLab",
                "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/DiagnosticReportLab",
                "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab")
            .usingRecursiveComparison();
    }

    @Test
    public void messagesAreSentToUnmappedTopic() {
        var messages = KafkaHelper.getAtLeast(
            KafkaHelper.createErrorTopicConsumer(kafka.getBootstrapServers()),
            "test-fhir-laboratory-error", 1);

        assertThat(messages).isNotEmpty();
    }


}
