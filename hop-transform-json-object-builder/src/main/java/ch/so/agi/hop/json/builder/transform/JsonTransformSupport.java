package ch.so.agi.hop.json.builder.transform;

import ch.so.agi.hop.json.builder.core.JsonNodeSerializer;
import ch.so.agi.hop.json.builder.core.JsonSupport;
import ch.so.agi.hop.json.builder.core.JsonValueType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.IVariables;

/** Shared row/JSON conversion helpers of both JSON builder transforms. */
final class JsonTransformSupport {

  private JsonTransformSupport() {}

  static String resolve(IVariables variables, String text) {
    if (text == null) {
      return "";
    }
    return variables == null ? text : variables.resolve(text);
  }

  /**
   * Reads a field that contains a JSON document, either as native JSON value or as JSON text. A
   * null or blank value returns {@code null}.
   */
  static JsonNode readJsonField(IRowMeta rowMeta, Object[] row, int index, String fieldName)
      throws HopTransformException {
    IValueMeta valueMeta = rowMeta.getValueMeta(index);
    Object raw = row[index];
    if (raw == null) {
      return null;
    }
    try {
      if (valueMeta.getType() == IValueMeta.TYPE_JSON) {
        JsonNode node = valueMeta.getJson(raw);
        return node == null ? null : node.deepCopy();
      }
      String text = valueMeta.getString(raw);
      if (text == null || text.isBlank()) {
        return null;
      }
      return JsonSupport.parse(text);
    } catch (Exception e) {
      throw new HopTransformException(
          "Field '" + fieldName + "' does not contain valid JSON: " + e.getMessage(), e);
    }
  }

  /** Converts a non-null input field value to a JSON node using the configured value type. */
  static JsonNode fieldValue(
      IRowMeta rowMeta,
      Object[] row,
      int index,
      JsonValueType valueType,
      String fieldName,
      boolean skipMissing)
      throws HopTransformException {
    IValueMeta valueMeta = rowMeta.getValueMeta(index);
    Object raw = row[index];
    if (raw == null) {
      return null;
    }
    JsonValueType type = valueType == null ? JsonValueType.AUTO : valueType;
    try {
      if (skipMissing && isMissingInput(valueMeta, raw)) {
        return null;
      }
      return convert(valueMeta, raw, type);
    } catch (Exception e) {
      throw new HopTransformException(
          "Unable to read field '"
              + fieldName
              + "' as "
              + type.getDescription()
              + ": "
              + e.getMessage(),
          e);
    }
  }

  /** Converts an entire input row into a JSON object using the input field types. */
  static JsonNode rowObject(IRowMeta rowMeta, Object[] row, boolean skipNullFields)
      throws HopTransformException {
    ObjectNode object = JsonSupport.NODES.objectNode();
    for (int i = 0; i < rowMeta.size(); i++) {
      IValueMeta valueMeta = rowMeta.getValueMeta(i);
      Object raw = i < row.length ? row[i] : null;
      if (raw == null) {
        if (!skipNullFields) {
          object.putNull(valueMeta.getName());
        }
        continue;
      }
      try {
        if (skipNullFields && isMissingInput(valueMeta, raw)) {
          continue;
        }
        JsonNode node = convert(valueMeta, raw, JsonValueType.AUTO);
        if (skipNullFields && isMissing(node)) {
          continue;
        }
        object.set(valueMeta.getName(), node);
      } catch (Exception e) {
        throw new HopTransformException(
            "Unable to read field '" + valueMeta.getName() + "' as JSON: " + e.getMessage(), e);
      }
    }
    return object;
  }

  /** Converts the assembled JSON node into the configured output row value. */
  static Object outputValue(JsonNode node, JsonOutputType outputType, boolean prettyPrint)
      throws HopTransformException {
    if (outputType == JsonOutputType.STRING) {
      try {
        return prettyPrint ? JsonNodeSerializer.pretty(node) : JsonNodeSerializer.compact(node);
      } catch (Exception e) {
        throw new HopTransformException(
            "Unable to serialize the JSON output: " + e.getMessage(), e);
      }
    }
    return node;
  }

  /**
   * True when the value counts as missing: Java null, JSON null/missing, or an empty string value.
   */
  static boolean isMissing(JsonNode node) {
    return node == null
        || node.isNull()
        || node.isMissingNode()
        || (node.isTextual() && node.asText().isEmpty());
  }

  private static boolean isMissingInput(IValueMeta valueMeta, Object raw) throws Exception {
    if (raw == null) {
      return true;
    }
    if (valueMeta.getType() == IValueMeta.TYPE_JSON) {
      return isMissing(valueMeta.getJson(raw));
    }
    if (valueMeta.getType() == IValueMeta.TYPE_STRING) {
      String text = valueMeta.getString(raw);
      return text == null || text.isEmpty();
    }
    return false;
  }

  private static JsonNode convert(IValueMeta valueMeta, Object raw, JsonValueType type)
      throws Exception {
    return switch (type) {
      case NULL -> JsonSupport.NODES.nullNode();
      case STRING -> textNode(valueMeta.getString(raw));
      case INTEGER -> JsonSupport.NODES.numberNode(valueMeta.getInteger(raw));
      case NUMBER -> JsonSupport.NODES.numberNode(valueMeta.getNumber(raw));
      case BIGNUMBER -> JsonSupport.NODES.numberNode(valueMeta.getBigNumber(raw));
      case BOOLEAN -> JsonSupport.NODES.booleanNode(valueMeta.getBoolean(raw));
      case JSON -> jsonValue(valueMeta, raw);
      case AUTO -> autoValue(valueMeta, raw);
    };
  }

  private static JsonNode jsonValue(IValueMeta valueMeta, Object raw) throws Exception {
    if (valueMeta.getType() == IValueMeta.TYPE_JSON) {
      JsonNode node = valueMeta.getJson(raw);
      return node == null ? JsonSupport.NODES.nullNode() : node.deepCopy();
    }
    String text = valueMeta.getString(raw);
    if (text == null || text.isBlank()) {
      return JsonSupport.NODES.nullNode();
    }
    return JsonSupport.parse(text);
  }

  private static JsonNode autoValue(IValueMeta valueMeta, Object raw) throws Exception {
    return switch (valueMeta.getType()) {
      case IValueMeta.TYPE_JSON -> {
        JsonNode node = valueMeta.getJson(raw);
        yield node == null ? JsonSupport.NODES.nullNode() : node.deepCopy();
      }
      case IValueMeta.TYPE_BOOLEAN -> JsonSupport.NODES.booleanNode(valueMeta.getBoolean(raw));
      case IValueMeta.TYPE_INTEGER -> JsonSupport.NODES.numberNode(valueMeta.getInteger(raw));
      case IValueMeta.TYPE_NUMBER -> JsonSupport.NODES.numberNode(valueMeta.getNumber(raw));
      case IValueMeta.TYPE_BIGNUMBER -> JsonSupport.NODES.numberNode(valueMeta.getBigNumber(raw));
      default -> textNode(valueMeta.getString(raw));
    };
  }

  private static JsonNode textNode(String text) {
    return text == null ? JsonSupport.NODES.nullNode() : JsonSupport.NODES.textNode(text);
  }
}
