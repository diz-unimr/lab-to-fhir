package de.unimarburg.diz.labtofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.unimarburg.diz.labtofhir.mapper.MiiLabReportMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.MappingContainer;
import de.unimarburg.diz.labtofhir.model.MappingResult;
import de.unimarburg.diz.labtofhir.serializer.FhirDeserializer;
import de.unimarburg.diz.labtofhir.serializer.FhirSerializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.TestPropertySource;


@SpringBootTest(classes = {LabToFhirProcessor.class})
@TestPropertySource(properties = {
    "spring.cloud.stream.bindings.process-out-0.error=lab-fhir-error"})
public class LabToFhirProcessorTests {

    @Autowired
    private LabToFhirProcessor processor;

    @MockBean
    private MiiLabReportMapper mapper;

    @Test
    public void process_sendsMappedReportToOutputTopic() {
        // mapper will return this bundle
        var bundleId = "42";
        var resultBundle = new Bundle();
        resultBundle.setId(bundleId);

        when(mapper.apply(any(LaboratoryReport.class))).thenReturn(
            new MappingContainer<>(new LaboratoryReport(), resultBundle));

        // build stream
        var builder = new StreamsBuilder();
        var input = builder.stream("aim-lab", Consumed.with(Serdes.String(),
            Serdes.serdeFrom(new JsonSerializer<>(),
                new JsonDeserializer<>(LaboratoryReport.class))));
        processor
            .process()
            .apply(input)
            .to("lab-fhir", Produced.with(Serdes.String(),
                Serdes.serdeFrom(new FhirSerializer<>(), new FhirDeserializer<>(Bundle.class))));

        try (var driver = new TopologyTestDriver(builder.build())) {

            // create topics
            var inputTopic = driver.createInputTopic("aim-lab", new StringSerializer(),
                new JsonSerializer<>());
            var outputTopic = driver.createOutputTopic("lab-fhir", new StringDeserializer(),
                new FhirDeserializer<>(Bundle.class));

            // create test report
            var report = new LaboratoryReport();
            report.setResource(new DiagnosticReport());

            // send report to input topic
            inputTopic.pipeInput("test", report);

            // assert (mapped) bundle in output topic
            var actual = outputTopic.readKeyValue();
            assertThat(actual.key).isEqualTo(bundleId);
            assertThat(actual.value.getId()).isEqualTo(bundleId);
        }
    }

    @Test
    public void process_sendsErroneousReportToErrorTopic() {

        // return error on mapping
        when(mapper.apply(any(LaboratoryReport.class))).thenAnswer(
            i -> new MappingContainer<>(i.getArguments()[0], null).withResultType(
                MappingResult.ERROR));

        // build stream
        var builder = new StreamsBuilder();
        var input = builder.stream("aim-lab", Consumed.with(Serdes.String(),
            Serdes.serdeFrom(new JsonSerializer<>(),
                new JsonDeserializer<>(LaboratoryReport.class))));
        processor
            .process()
            .apply(input)
            .to("lab-fhir", Produced.with(Serdes.String(),
                Serdes.serdeFrom(new FhirSerializer<>(), new FhirDeserializer<>(Bundle.class))));

        try (var driver = new TopologyTestDriver(builder.build())) {

            // create topics
            var inputTopic = driver.createInputTopic("aim-lab", new StringSerializer(),
                new JsonSerializer<>());
            var outputTopic = driver.createOutputTopic("lab-fhir", new StringDeserializer(),
                new FhirDeserializer<>(Bundle.class));
            var errorTopic = driver.createOutputTopic("lab-fhir-error", new IntegerDeserializer(),
                new JsonDeserializer<>(LaboratoryReport.class));

            // create test report
            var report = new LaboratoryReport();
            report.setId(-1);
            report.setResource(new DiagnosticReport());

            // send report to input topic
            inputTopic.pipeInput("test", report);

            // assert record in error topic
            var errorRecord = errorTopic.readKeyValue();
            assertThat(errorRecord.key).isEqualTo(report.getId());
            assertThat(errorRecord.value.getId()).isEqualTo(report.getId());
            assertThat(outputTopic.isEmpty()).isTrue();
        }
    }

}
