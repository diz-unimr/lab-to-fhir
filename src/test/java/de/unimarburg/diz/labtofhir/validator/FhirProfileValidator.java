package de.unimarburg.diz.labtofhir.validator;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import java.util.Collection;
import java.util.stream.Collectors;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FhirProfileValidator {

    private final static Logger log = LoggerFactory.getLogger(FhirProfileValidator.class);

    private static Collection<StructureDefinition> loadedProfiles;
    private static Collection<CodeSystem> loadedCodeSystems;
    private static Collection<ValueSet> loadedValueSets;

    public static FhirValidator create(FhirContext ctx) {
        var validator = ctx.newValidator();

        // Create a PrePopulatedValidationSupport and load it with our custom structures
        var customValidation = new PrePopulatedValidationSupport(ctx);

        // Load StructureDefinitions, ValueSets, CodeSystems, etc.
        getProfiles(ctx).forEach(customValidation::addStructureDefinition);

        // TODO valuesets
        //        getCodeSystems(ctx).forEach(customValidation::addCodeSystem);
        //        getValueSets(ctx).forEach(customValidation::addValueSet);

        // We'll create a chain that includes both the pre-populated and default. We put
        // the pre-populated (custom) support module first so that it takes precedence
        var validationSupportChain = new ValidationSupportChain(customValidation,
            new SnapshotGeneratingValidationSupport(ctx), new DefaultProfileValidationSupport(ctx),
            new InMemoryTerminologyServerValidationSupport(ctx),
            new CommonCodeSystemsTerminologyService(ctx));

        var validatorModule = new FhirInstanceValidator(validationSupportChain);
        validator.registerValidatorModule(validatorModule);

        return validator;
    }

    private static Collection<StructureDefinition> getProfiles(FhirContext ctx) {
        // get profiles
        if (loadedProfiles == null || loadedProfiles.isEmpty()) {
            loadedProfiles = FhirResourceLoader.loadFromDirectory(ctx, "node_modules", "*.json")
                .stream()
                .filter(StructureDefinition.class::isInstance)
                .map(StructureDefinition.class::cast)
                .collect(Collectors.toList());
        }
        return loadedProfiles;
    }

    private static Collection<CodeSystem> getCodeSystems(FhirContext ctx) {
        // get code systems
        if (loadedCodeSystems == null || loadedCodeSystems.isEmpty()) {
            loadedCodeSystems = FhirResourceLoader.loadFromDirectory(ctx, "codesystems")
                .stream()
                .filter(CodeSystem.class::isInstance)
                .map(CodeSystem.class::cast)
                .collect(Collectors.toList());
        }
        return loadedCodeSystems;
    }

    private static Collection<ValueSet> getValueSets(FhirContext ctx) {
        // get value sets
        if (loadedValueSets == null || loadedValueSets.isEmpty()) {
            loadedValueSets = FhirResourceLoader.loadFromDirectory(ctx, "valueset")
                .stream()
                .filter(ValueSet.class::isInstance)
                .map(ValueSet.class::cast)
                .collect(Collectors.toList());
        }
        return loadedValueSets;
    }

    public static void prettyPrint(Logger log, ValidationResult validationResult) {
        validationResult.getMessages()
            .forEach(message -> {

                switch (message.getSeverity()) {
                    case ERROR:
                        log.error((char) 27 + "[31mFHIR Validation" + (char) 27 + "[0m" + ": "
                            + message.getLocationString() + " - " + message.getMessage());
                        break;
                    case WARNING:
                        log.warn((char) 27 + "[33mFHIR Validation" + (char) 27 + "[0m" + ": "
                            + message.getLocationString() + " - " + message.getMessage());
                        break;
                    case INFORMATION:
                        log.info((char) 27 + "[34mFHIR Validation" + (char) 27 + "[0m" + ": "
                            + message.getLocationString() + " - " + message.getMessage());
                        break;
                    default:
                        log.debug("Validation issue " + message.getSeverity() + " - "
                            + message.getLocationString() + " - " + message.getMessage());
                }
            });
    }
}
