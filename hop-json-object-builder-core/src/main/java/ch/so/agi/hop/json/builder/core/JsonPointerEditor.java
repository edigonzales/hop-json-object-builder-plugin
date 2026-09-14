package ch.so.agi.hop.json.builder.core;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Inserts values into JSON documents addressed by a JSON Pointer (RFC 6901). Missing object levels
 * are created on the way; existing values are overwritten.
 *
 * <p>Only objects are created automatically. Array elements must already exist; the {@code -} token
 * can be used to append to an existing array at the very last pointer token.
 */
public final class JsonPointerEditor {

  private JsonPointerEditor() {}

  public static JsonPointer compile(String pointer) {
    if (pointer == null) {
      throw new IllegalArgumentException("JSON Pointer is missing");
    }
    try {
      return JsonPointer.compile(pointer);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Invalid JSON Pointer '" + pointer + "': " + e.getMessage(), e);
    }
  }

  /**
   * Resolves the object the pointer points to and creates missing object levels. An empty pointer
   * resolves to the root object itself.
   *
   * @throws IllegalArgumentException when the base value is null, navigation crosses a scalar value
   *     or the target is not a JSON object
   */
  public static ObjectNode resolveObject(JsonNode root, JsonPointer pointer) {
    JsonNode resolved = resolveNode(root, pointer);
    if (!resolved.isObject()) {
      throw new IllegalArgumentException(
          "JSON Pointer target is not a JSON object but a " + resolved.getNodeType() + " value");
    }
    return (ObjectNode) resolved;
  }

  /**
   * Resolves the node the pointer points to, creating missing object levels. An empty pointer
   * resolves to the root node itself.
   *
   * @throws IllegalArgumentException when the base value is null, navigation crosses a scalar value
   *     or an array element does not exist
   */
  public static JsonNode resolveNode(JsonNode root, JsonPointer pointer) {
    if (root == null || root.isNull() || root.isMissingNode()) {
      throw new IllegalArgumentException("Base JSON value is null or missing");
    }

    JsonNode current = root;
    JsonPointer remaining = pointer == null ? JsonPointer.compile("") : pointer;
    while (!remaining.matches()) {
      String property = remaining.getMatchingProperty();
      int index = remaining.getMatchingIndex();
      String token = property != null ? property : String.valueOf(index);

      if (current.isObject()) {
        JsonNode child = current.get(token);
        if (child == null || child.isMissingNode()) {
          ObjectNode created = JsonSupport.NODES.objectNode();
          ((ObjectNode) current).set(token, created);
          child = created;
        }
        current = child;
      } else if (current.isArray()) {
        if (index < 0) {
          throw new IllegalArgumentException(
              "JSON Pointer token '" + token + "' cannot address an array element");
        }
        JsonNode child = current.get(index);
        if (child == null || child.isMissingNode()) {
          throw new IllegalArgumentException(
              "JSON Pointer array index " + index + " does not exist");
        }
        current = child;
      } else {
        throw new IllegalArgumentException(
            "Cannot navigate JSON Pointer token '"
                + token
                + "' through a "
                + current.getNodeType()
                + " value");
      }
      remaining = remaining.tail();
    }
    return current;
  }

  /**
   * Sets {@code key} to {@code value} in the object addressed by the pointer, creating missing
   * object levels. A {@code null} value is written as JSON null.
   */
  public static void setValue(JsonNode root, JsonPointer pointer, String key, JsonNode value) {
    if (key == null || key.isEmpty()) {
      throw new IllegalArgumentException("JSON object key is empty");
    }
    ObjectNode target = resolveObject(root, pointer);
    target.set(key, value == null ? JsonSupport.NODES.nullNode() : value);
  }

  /**
   * Stores an array under the last pointer token, creating missing object levels. When the last
   * token addresses an array element, the element is replaced or appended ({@code -}).
   *
   * @throws IllegalArgumentException when the pointer is empty or the addressed array index does not
   *     exist
   */
  public static void setArray(JsonNode root, JsonPointer pointer, ArrayNode array) {
    if (pointer == null || pointer.matches()) {
      throw new IllegalArgumentException("JSON Pointer for array insertion is empty");
    }
    JsonNode parent = resolveNode(root, pointer.head());
    JsonPointer last = pointer.last();
    String property = last.getMatchingProperty();
    int index = last.getMatchingIndex();

    if (parent.isObject()) {
      String key = property != null ? property : String.valueOf(index);
      ((ObjectNode) parent).set(key, array);
      return;
    }

    if (parent.isArray()) {
      ArrayNode parentArray = (ArrayNode) parent;
      if (property != null && "-".equals(property)) {
        parentArray.add(array);
        return;
      }
      if (index < 0 || index > parentArray.size()) {
        throw new IllegalArgumentException("JSON Pointer array index " + index + " does not exist");
      }
      if (index == parentArray.size()) {
        parentArray.add(array);
      } else {
        parentArray.set(index, array);
      }
      return;
    }

    throw new IllegalArgumentException(
        "JSON Pointer target is not a JSON object or array but a " + parent.getNodeType() + " value");
  }
}
