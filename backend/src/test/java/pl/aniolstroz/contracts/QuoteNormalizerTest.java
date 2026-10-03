package pl.aniolstroz.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuoteNormalizerTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
        "Zażółć gęślą jaźń|zazolc gesla jazn",
        "ŁÓDŹ|lodz",
        "Łukasz|lukasz",
        "wypłać złoto|wyplac zloto",
        "Nikomu nie mów, proszę!|nikomu nie mow prosze",
        "   wielokrotne     spacje  |wielokrotne spacje",
        "kod BLIK: 123-456|kod blik 123 456",
        "to zostaje między nami — proszę|to zostaje miedzy nami prosze",
        "bezpieczne–konto|bezpieczne konto",
        "...!!! ?|''",
        "ąęćńóśźż ĄĘĆŃÓŚŹŻ|aecnoszz aecnoszz",
        "Kurier odbierze 5000 zł|kurier odbierze 5000 zl"
    })
    void normalizes(String input, String expected) {
        assertThat(QuoteNormalizer.normalize(input)).isEqualTo(expected.equals("''") ? "" : expected);
    }

    @ParameterizedTest
    @CsvSource({"'café Zoé',cafe zoe", "'kod blik',kod blik"})
    void handlesDecomposedAndNonBreakingSpace(String input, String expected) {
        assertThat(QuoteNormalizer.normalize(input)).isEqualTo(expected);
    }
}
