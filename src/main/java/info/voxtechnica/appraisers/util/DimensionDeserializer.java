package info.voxtechnica.appraisers.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.awt.*;
import java.io.IOException;

public class DimensionDeserializer extends JsonDeserializer<Dimension> {
    @Override
    public Dimension deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        JsonNode node = jsonParser.readValueAsTree();
        JsonNode width = node.path("width");
        JsonNode height = node.path("height");
        return width.isInt() && height.isInt() ? new Dimension(width.asInt(), height.asInt()) : null;
    }
}
