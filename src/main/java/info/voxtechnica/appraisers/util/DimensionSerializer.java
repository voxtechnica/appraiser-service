package info.voxtechnica.appraisers.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.awt.*;
import java.io.IOException;

public class DimensionSerializer extends com.fasterxml.jackson.databind.JsonSerializer<Dimension> {
    @Override
    public void serialize(Dimension dimension, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeNumberField("width", dimension.width);
        jsonGenerator.writeNumberField("height", dimension.height);
        jsonGenerator.writeEndObject();
    }
}
