package dev.luxoru.tunnelvision.source.list;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlainListSourceTest {
    @Test
    void parsesEntriesAndSkipsComments() {
        String body = """
            # A comment
            1.2.3.4
              5.6.7.0/24   ; trailing comment
            8.8.8.8 some description
            9.9.9.9,other

            ; another comment
            2001:db8::/32 # trailing
            """;
        assertThat(PlainListSource.parse(body))
            .containsExactly("1.2.3.4", "5.6.7.0/24", "8.8.8.8", "9.9.9.9", "2001:db8::/32");
    }
}
