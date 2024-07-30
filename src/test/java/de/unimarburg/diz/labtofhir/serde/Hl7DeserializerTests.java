package de.unimarburg.diz.labtofhir.serde;

import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.serializer.Hl7Deserializer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class Hl7DeserializerTests {

    @Test
    public void deserializeHl7() {
        var msg = """
            MSH|^~\\&|SWISSLAB|KLIN|DBSERV||20220702120811|LAB|ORU^R01|test-msg.000010|P|2.2|||AL|NE
            PID|||123456||Tester^Test||19811029000000|M
            PV1|||GYN||||||||||||||||23651159
            ORC|RE|20220702_88888888|||IP||||20220702120811
            OBR|1|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|ST|USTA^Urinstatus||||||||F
            NTE|1||Urin-Sedimentanalytik aktuell nicht möglich.
            OBR|2|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|ST|ULEU^Leuko (Stix)||++||negativ|A|||F
            OBR|3|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|ST|UNIT^Nitrit (Stix)||negativ||negativ||||F
            OBR|4|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|NM|UPH^pH (Stix)||5.5||4.5 - 8.0||||F
            OBR|5|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|ST|UTP^Protein (Stix)||negativ||negativ||||F
            OBR|6|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|ST|UZ^Glukose im Urin (Stix)||norm||norm||||F
            OBR|7|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|ST|UKET^Ketone (Stix)||negativ||negativ||||F
            OBR|8|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|ST|URO^Urobilinogen (Stix)||norm||norm||||F
            OBR|9|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|ST|UHB^Hb/Ery (Stix)||s.Bem.||negativ|A|||F
            NTE|1||Auftr.Nr.: 88888888, Hb/Ery (Stix)
            NTE|2||grenzwertig
            OBR|10|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|ST|UBIL^Bilirubin (Stix)||negativ||negativ||||F
            OBR|11|20220702_88888888|||||20220702120811||||||||||||||||||F
            OBX|1|NM|USg^Harndichte (Stix)||1.022|g/cm3|1.002 - 1.040||||F""";

        var message = new Hl7Deserializer().deserialize(null,
            msg.getBytes(StandardCharsets.UTF_8));

        if (message instanceof ORU_R01 labMsg) {
            labMsg.getMSH().getMsh10_MessageControlID();
        }
    }

}
