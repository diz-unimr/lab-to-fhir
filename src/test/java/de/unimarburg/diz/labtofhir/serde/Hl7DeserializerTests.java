package de.unimarburg.diz.labtofhir.serde;

import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.serializer.Hl7Deserializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class Hl7DeserializerTests {


    @SuppressWarnings("checkstyle:LineLength")
    @Test
    public void deserializeHl7() {
        var msg = """
            MSH|^~\\&|SWISSLAB|KLIN|DBSERV||20220702120811|LAB|ORU^R01|test-msg.000010|P|2.2|||AL|NE\r
            PID|||123456||Tester^Test||19811029000000|M\r
            PV1|||GYN||||||||||||||||23651159\r
            ORC|RE|20220702_88888888|||IP||||20220702120811\r
            OBR|1|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|ST|USTA^Urinstatus||||||||F\r
            NTE|1||Urin-Sedimentanalytik aktuell nicht möglich.\r
            OBR|2|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|ST|ULEU^Leuko (Stix)||++||negativ|A|||F\r
            OBR|3|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|ST|UNIT^Nitrit (Stix)||negativ||negativ||||F\r
            OBR|4|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|NM|UPH^pH (Stix)||5.5||4.5 - 8.0||||F\r
            OBR|5|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|ST|UTP^Protein (Stix)||negativ||negativ||||F\r
            OBR|6|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|ST|UZ^Glukose im Urin (Stix)||norm||norm||||F\r
            OBR|7|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|ST|UKET^Ketone (Stix)||negativ||negativ||||F\r
            OBR|8|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|ST|URO^Urobilinogen (Stix)||norm||norm||||F\r
            OBR|9|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|ST|UHB^Hb/Ery (Stix)||s.Bem.||negativ|A|||F\r
            NTE|1||Auftr.Nr.: 88888888, Hb/Ery (Stix)\r
            NTE|2||grenzwertig\r
            OBR|10|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|ST|UBIL^Bilirubin (Stix)||negativ||negativ||||F\r
            OBR|11|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|NM|USg^Harndichte (Stix)||1.022|g/cm3|1.002 - 1.040||||F\r""";

        try (var deserializer = new Hl7Deserializer<>()) {
            var actual = deserializer.deserialize(null,
                msg.getBytes(StandardCharsets.UTF_8));

            Consumer<ORU_R01> oruReq = oru -> {
                assertThat(oru.getMSH()
                    .getMsh10_MessageControlID()
                    .getValue()).isEqualTo("test-msg.000010");
                assertThat(oru.getPATIENT_RESULT()
                    .getORDER_OBSERVATION()
                    .getOBR()
                    .getPlacerOrderNumber()
                    .getCm_placer1_UniquePlacerId()
                    .getValue()).isEqualTo("20220702_88888888");
            };

            assertThat(actual).isInstanceOfSatisfying(ORU_R01.class, oruReq);
        }
    }

    @Test
    public void deserializeNull() {
        try (var deserializer = new Hl7Deserializer<>()) {
            var actual = deserializer.deserialize(null,
                null);

            assertThat(actual).isNull();
        }
    }
}
