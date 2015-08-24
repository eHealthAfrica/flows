package io.rapidpro.flows.definition.tests.numeric;

import com.google.gson.JsonObject;
import io.rapidpro.flows.definition.Flow;
import io.rapidpro.flows.definition.FlowParseException;
import io.rapidpro.flows.definition.tests.Test;

import java.math.BigDecimal;

/**
 * Test which returns whether input is numerically less than or equal to a value
 */
public class LessThanOrEqualTest extends NumericComparisonTest {

    public LessThanOrEqualTest(String test) {
        super(test);
    }

    /**
     * @see Test#fromJson(JsonObject, Flow.DeserializationContext)
     */
    public static LessThanOrEqualTest fromJson(JsonObject obj, Flow.DeserializationContext context) throws FlowParseException {
        return new LessThanOrEqualTest(obj.get("test").getAsString());
    }

    /**
     * @see NumericComparisonTest#doComparison(BigDecimal, BigDecimal)
     */
    @Override
    protected boolean doComparison(BigDecimal input, BigDecimal test) {
        return input.compareTo(test) <= 0;
    }
}
