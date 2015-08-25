package io.rapidpro.flows.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.rapidpro.flows.definition.Flow;
import org.threeten.bp.*;
import org.threeten.bp.chrono.IsoChronology;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.DateTimeFormatterBuilder;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.threeten.bp.format.ResolverStyle.*;
import static org.threeten.bp.temporal.ChronoField.MILLI_OF_SECOND;
import static org.threeten.bp.temporal.ChronoField.NANO_OF_SECOND;

/**
 * JSON utility methods
 */
public class JsonUtils {

    /**
     * Gets the named member as a string, returning null if it's null of it doesn't exist
     * @param obj the parsed JSON object
     * @param memberName the object member name
     * @return the string value or null
     */
    public static String getAsString(JsonObject obj, String memberName) {
        JsonElement member = obj.get(memberName);
        return (member == null || member.isJsonNull()) ? null : member.getAsString();
    }

    /**
     * Instantiates a new object instance by calling a static fromJson method on its class.
     * @param obj the JSON object passed to fromJson
     * @param context the deserialization context
     * @param clazz the class to instantiate
     * @return the new object instance
     */
    public static <T> T fromJson(JsonObject obj, Flow.DeserializationContext context, Class<T> clazz) {
        try {
            Method method = clazz.getDeclaredMethod("fromJson", JsonObject.class, Flow.DeserializationContext.class);
            return (T) method.invoke(null, obj, context);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adapter for ZoneId instances to serialize as id string, e.g. "Africa/Kigali"
     */
    public static class TimezoneAdapter extends TypeAdapter<ZoneId> {
        @Override
        public void write(JsonWriter out, ZoneId zoneId) throws IOException {
            out.value(zoneId.getId());
        }

        @Override
        public ZoneId read(JsonReader in) throws IOException {
            return ZoneId.of(in.nextString());
        }
    }

    /**
     * Adapter for Instant instances to serialize as ISO8601 in UTC, with millisecond precision,
     * e.g. "2014-10-03T01:41:12.790Z"
     */
    public static class InstantAdapter extends TypeAdapter<Instant> {
        protected static DateTimeFormatter s_formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        @Override
        public void write(JsonWriter out, Instant instant) throws IOException {
            if (instant != null) {
                out.value(s_formatter.format(instant.atOffset(ZoneOffset.UTC)));
            } else {
                out.nullValue();
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            return LocalDateTime.parse(in.nextString(), s_formatter).atOffset(ZoneOffset.UTC).toInstant();
        }
    }
}