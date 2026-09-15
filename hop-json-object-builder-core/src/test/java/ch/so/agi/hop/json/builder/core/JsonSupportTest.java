package ch.so.agi.hop.json.builder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonSupportTest {
  @ParameterizedTest
  @ValueSource(strings = {"{}", "[]", "42", "true", "null", "\"text\""})
  void acceptsOneCompleteValueAndTrailingWhitespace(String json) throws Exception {
    assertThat(JsonSupport.parse(json + " \n\t")).isEqualTo(JsonSupport.parse(json));
    assertThat(JsonValueConversion.literalToJson(json + " \n", JsonValueType.JSON))
        .isEqualTo(JsonSupport.parse(json));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{} {}", "[] null", "42 43", "true false", "{} garbage", "null []"})
  void rejectsTrailingValuesAndGarbage(String json) {
    assertThatThrownBy(() -> JsonSupport.parse(json)).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> JsonValueConversion.literalToJson(json, JsonValueType.JSON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON");
  }
}
