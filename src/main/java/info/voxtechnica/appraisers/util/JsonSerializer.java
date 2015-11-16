package info.voxtechnica.appraisers.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for serializing Model objects to JSON and back.
 */
public class JsonSerializer {
    private static final Logger LOG = LoggerFactory.getLogger(JsonSerializer.class);
    static ObjectMapper objectMapper = null;

    static public ObjectMapper setObjectMapper(final ObjectMapper mapper) {
        objectMapper = mapper;
        return objectMapper;
    }

    static public ObjectMapper configureObjectMapper(final ObjectMapper mapper) {
        mapper.setSerializationInclusion(Include.NON_NULL);
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // add custom (de)serializers. Example here for java.awt.Dimension:
        SimpleModule globalSerializers = new SimpleModule();
        globalSerializers.addSerializer(Dimension.class, new DimensionSerializer());
        globalSerializers.addDeserializer(Dimension.class, new DimensionDeserializer());
        mapper.registerModule(globalSerializers);
        LOG.info("Configured ObjectMapper");
        return mapper;
    }

    static public String getJson(final Object val) throws JsonProcessingException {
        if (val == null) return null;
        return getObjectMapper().writeValueAsString(val);
    }

    static public <T> T getObject(final String json, final Class<T> type) throws IOException {
        if (json == null || type == null) return null;
        return type.cast(getObjectMapper().readValue(json, type));
    }

    static public <T> T getObject(final Path path, final Class<T> type) throws IOException {
        InputStream stream = new FileInputStream(path.toString());
        return getObject(stream, type);
    }

    public static <T> T getObject(final Path path, final TypeReference<T> typeReference) throws IOException {
        InputStream stream = new FileInputStream(path.toString());
        return getObject(stream, typeReference);
    }

    static public <T> T getObject(final InputStream is, final Class<T> type) throws IOException {
        if (is == null || type == null) return null;
        return type.cast(getObjectMapper().readValue(is, type));
    }

    static public <T> T getObject(final String json, final TypeReference<T> type) throws IOException {
        if (json == null || type == null) return null;
        return getObjectMapper().readValue(json, type);
    }

    static public <T> T getObject(final InputStream is, final TypeReference<T> type) throws IOException {
        if (is == null || type == null) return null;
        return getObjectMapper().readValue(is, type);
    }

    static public <T> T getObjectFromFile(final File file, final Class<T> type) throws IOException {
        return getObjectMapper().readValue(file, type);
    }

    static public <T> T getObjectFromResource(final String path, final Class<T> type) throws IOException {
        return getObjectMapper().readValue(Thread.currentThread().getContextClassLoader().getResourceAsStream(path), type);
    }

    static public <T> List<T> getArray(final Path path, final Class<T> targetClass) throws IOException {
        InputStream stream = new FileInputStream(path.toString());
        TypeReference<List<T>> typeRef = new TypeReference<List<T>>() {
        };
        return getObject(stream, typeRef);
    }

    static public <T> List<T> getArrayFromResource(final String path, final Class<T> targetClass) throws IOException {
        TypeFactory typeFactory = getObjectMapper().getTypeFactory();
        return getObjectMapper().readValue(Thread.currentThread().getContextClassLoader().getResourceAsStream(path), typeFactory.constructCollectionType(List.class, targetClass));
    }

    static public <T> ArrayList<T> getArray(final String jsonString, final Class<T> targetClass) throws IOException {
        TypeFactory typeFactory = getObjectMapper().getTypeFactory();
        return getObjectMapper().readValue(jsonString, typeFactory.constructCollectionType(List.class, targetClass));
    }

    static public ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            setObjectMapper(new ObjectMapper());
            configureObjectMapper(objectMapper);
        }
        return objectMapper;
    }
}