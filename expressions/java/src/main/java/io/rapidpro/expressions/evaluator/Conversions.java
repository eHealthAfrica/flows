package io.rapidpro.expressions.evaluator;

import io.rapidpro.expressions.EvaluationContext;
import io.rapidpro.expressions.EvaluationError;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.threeten.bp.LocalDate;
import org.threeten.bp.OffsetTime;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.temporal.Temporal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Type conversions required for expression evaluation
 */
public class Conversions {

    /**
     * Tries conversion of any value to a boolean (called a 'Logical Value' in Excel lingo)
     */
    public static boolean toBoolean(Object value, EvaluationContext ctx) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        else if (value instanceof Integer) {
            return ((Integer) value) != 0;
        }
        else if (value instanceof BigDecimal) {
            return !value.equals(BigDecimal.ZERO);
        }
        else if (value instanceof String) {
            String strVal = (String) value;
            if (strVal.equalsIgnoreCase("TRUE")) {
                return true;
            }  else if (strVal.equalsIgnoreCase("FALSE")) {
                return false;
            }
        }
        else if (value instanceof LocalDate || value instanceof OffsetTime || value instanceof ZonedDateTime) {
            return true;
        }

        throw new EvaluationError("Can't convert '" + value + "' to a boolean");
    }

    /**
     * Tries conversion of any value to an integer
     */
    public static int toInteger(Object value, EvaluationContext ctx) {
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        else if (value instanceof Integer) {
            return (Integer) value;
        }
        else if (value instanceof BigDecimal) {
            try {
                return ((BigDecimal) value).setScale(0, RoundingMode.HALF_UP).intValueExact();
            }
            catch (ArithmeticException ex) {}
        }
        else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            }
            catch (NumberFormatException e) {}
        }

        throw new EvaluationError("Can't convert '" + value + "' to an integer");
    }

    /**
     * Tries conversion of any value to a decimal
     */
    public static BigDecimal toDecimal(Object value, EvaluationContext ctx) {
        if (value instanceof Boolean) {
            return ((Boolean) value) ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        else if (value instanceof Integer) {
            return new BigDecimal((Integer) value);
        }
        else if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        else if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            }
            catch (NumberFormatException e) {}
        }

        throw new EvaluationError("Can't convert '" + value + "' to a decimal");
    }

    /**
     * Tries conversion of any value to a string
     */
    public static String toString(Object value, EvaluationContext ctx) {
        if (value instanceof Boolean) {
            return (Boolean) value ? "TRUE" : "FALSE";
        }
        else if (value instanceof Integer) {
            return String.valueOf(value);
        }
        else if (value instanceof BigDecimal) {
            return formatDecimal((BigDecimal) value);
        }
        else if (value instanceof String) {
            return (String) value;
        }
        else if (value instanceof LocalDate) {
            return ctx.getDateFormatter(false).format((LocalDate) value);
        }
        else if (value instanceof OffsetTime) {
            ZoneOffset offset = ZonedDateTime.now(ctx.getTimezone()).getOffset();
            OffsetTime inZone = ((OffsetTime) value).withOffsetSameInstant(offset);
            return DateTimeFormatter.ofPattern("HH:mm").format(inZone);
        }
        else if (value instanceof ZonedDateTime) {
            ZonedDateTime inZone = ((ZonedDateTime) value).withZoneSameInstant(ctx.getTimezone());
            return ctx.getDateFormatter(true).format(inZone);
        }

        throw new EvaluationError("Can't convert '" + value + "' to a string");
    }

    /**
     * Tries conversion of any value to a date
     */
    public static LocalDate toDate(Object value, EvaluationContext ctx) {
        if (value instanceof String) {
            Temporal temporal = ctx.getDateParser().auto((String) value);
            if (temporal != null) {
                return toDate(temporal, ctx);
            }
        }
        else if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        else if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).withZoneSameInstant(ctx.getTimezone()).toLocalDate(); // discard time
        }

        throw new EvaluationError("Can't convert '" + value + "' to a date");
    }

    /**
     * Tries conversion of any value to a date
     */
    public static ZonedDateTime toDateTime(Object value, EvaluationContext ctx) {
        if (value instanceof String) {
            Temporal temporal = ctx.getDateParser().auto((String) value);
            if (temporal != null) {
                return toDateTime(temporal, ctx);
            }
        }
        else if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay(ctx.getTimezone());
        }
        else if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).withZoneSameInstant(ctx.getTimezone());
        }

        throw new EvaluationError("Can't convert '" + value + "' to a datetime");
    }

    /**
     * Tries conversion of any value to a date or a datetime
     */
    public static Temporal toDateOrDateTime(Object value, EvaluationContext ctx) {
        if (value instanceof String) {
            Temporal temporal = ctx.getDateParser().auto((String) value);
            if (temporal != null) {
                return temporal;
            }
        }
        else if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        else if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).withZoneSameInstant(ctx.getTimezone());
        }

        throw new EvaluationError("Can't convert '" + value + "' to a date or datetime");
    }

    /**
     * Tries conversion of any value to a time
     */
    public static OffsetTime toTime(Object value, EvaluationContext ctx) {
        if (value instanceof String) {
            OffsetTime time = ctx.getDateParser().time((String) value);
            if (time != null) {
                return time;
            }
        }
        else if (value instanceof OffsetTime) {
            return (OffsetTime) value;
        }
        else if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toOffsetDateTime().toOffsetTime();
        }

        throw new EvaluationError("Can't convert '" + value + "' to a time");
    }

    /**
     * Converts a pair of arguments to their most-likely types. This deviates from Excel which doesn't auto convert values
     * but is necessary for us to intuitively handle contact fields which don't use the correct value type
     */
    public static Pair<Object, Object> toSame(Object value1, Object value2, EvaluationContext ctx) {
        if (value1.getClass().equals(value2.getClass())) {
            return new ImmutablePair<>(value1, value2);
        }

        try {
            // try converting to two decimals
            return new ImmutablePair<Object, Object>(toDecimal(value1, ctx), toDecimal(value2, ctx));
        }
        catch (EvaluationError ex) {}

        try {
            // try converting to two dates
            return new ImmutablePair<Object, Object>(toDateOrDateTime(value1, ctx), toDateOrDateTime(value2, ctx));
        }
        catch (EvaluationError ex) {}

        // try converting to two strings
        return new ImmutablePair<Object, Object>(toString(value1, ctx), toString(value2, ctx));
    }

    /**
     * Converts a value back to its representation form, e.g. x -> "x"
     */
    public static String toRepr(Object value, EvaluationContext ctx) {
        String asString = Conversions.toString(value, ctx);

        if (value instanceof String || value instanceof LocalDate || value instanceof OffsetTime || value instanceof ZonedDateTime) {
            asString = asString.replace("\"", "\"\""); // escape quotes by doubling
            asString = "\"" + asString + "\"";
        }

        return asString;
    }

    /**
     * Formats a decimal number using the same precision as Excel
     * @param decimal the decimal value
     * @return the formatted string value
     */
    private static String formatDecimal(BigDecimal decimal) {
        decimal = decimal.stripTrailingZeros();
        int intDigits = decimal.precision() - decimal.scale();  // number of non-fractional digits
        int fractionalDigits = Math.min(Math.max(10 - intDigits, 0), decimal.scale());
        decimal = decimal.setScale(fractionalDigits, RoundingMode.HALF_UP);
        return decimal.toPlainString();
    }
}
