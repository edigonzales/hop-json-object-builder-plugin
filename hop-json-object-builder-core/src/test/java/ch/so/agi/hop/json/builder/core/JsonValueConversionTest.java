package ch.so.agi.hop.json.builder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class JsonValueConversionTest {

  @Test
  void autoAndStringLiteralsBecomeJsonStrings() {
    assertThat(JsonValueConversion.literalToJson("hello", JsonValueType.AUTO).asText())
        .isEqualTo("hello");
    assertThat(JsonValueConversion.literalToJson("42", JsonValueType.STRING).asText())
        .isEqualTo("42");
    assertThat(JsonValueConversion.literalToJson(null, JsonValueType.STRING).asText())
        .isEqualTo("");
  }

  @Test
  void numericAndBooleanLiteralsUseTheirJsonTypes() {
    assertThat(JsonValueConversion.literalToJson("42", JsonValueType.INTEGER).isIntegralNumber())
        .isTrue();
    assertThat(JsonValueConversion.literalToJson("1.5", JsonValueType.NUMBER).isDouble()).isTrue();
    assertThat(JsonValueConversion.literalToJson("1.50", JsonValueType.BIGNUMBER).isBigDecimal())
        .isTrue();
    assertThat(JsonValueConversion.literalToJson("true", JsonValueType.BOOLEAN).booleanValue())
        .isTrue();
  }

  @Test
  void jsonLiteralsBecomeRealObjectsAndArrays() {
    JsonNode array = JsonValueConversion.literalToJson("[\"data\"]", JsonValueType.JSON);
    JsonNode object = JsonValueConversion.literalToJson("{\"href\":\"a.parquet\"}", JsonValueType.JSON);
    JsonNode nullLiteral = JsonValueConversion.literalToJson("null", JsonValueType.JSON);

    assertThat(array.isArray()).isTrue();
    assertThat(array.get(0).asText()).isEqualTo("data");
    assertThat(object.isObject()).isTrue();
    assertThat(object.get("href").asText()).isEqualTo("a.parquet");
    assertThat(nullLiteral.isNull()).isTrue();
  }

  @Test
  void nullTypeAlwaysProducesJsonNull() {
    assertThat(JsonValueConversion.literalToJson("ignored", JsonValueType.NULL).isNull()).isTrue();
  }

  @Test
  void invalidLiteralsAreRejectedWithTheConfiguredType() {
    assertThatThrownBy(() -> JsonValueConversion.literalToJson("abc", JsonValueType.INTEGER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Integer");
    assertThatThrownBy(() -> JsonValueConversion.literalToJson("yes", JsonValueType.BOOLEAN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Boolean");
    assertThatThrownBy(() -> JsonValueConversion.literalToJson("{invalid", JsonValueType.JSON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON");
  }
}
