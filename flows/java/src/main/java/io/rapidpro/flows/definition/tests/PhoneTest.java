package io.rapidpro.flows.definition.tests;

import com.google.gson.JsonObject;
import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import io.rapidpro.expressions.EvaluationContext;
import io.rapidpro.flows.definition.Flow;
import io.rapidpro.flows.definition.FlowParseException;
import io.rapidpro.flows.runner.RunState;

/**
 * Test that returns whether the text contains the a valid phone number
 */
public class PhoneTest extends Test {

    /**
     * @see Test#fromJson(JsonObject, Flow.DeserializationContext)
     */
    public static PhoneTest fromJson(JsonObject obj, Flow.DeserializationContext context) throws FlowParseException {
        return new PhoneTest();
    }

    @Override
    public Result evaluate(RunState run, EvaluationContext context, String text) {
        String country = run.getOrg().getCountry();
        PhoneNumberUtil numberUtil = PhoneNumberUtil.getInstance();

        // try to find a phone number in the text we have been sent
        Iterable<PhoneNumberMatch> matches = numberUtil.findNumbers(text, country);

        // try it as an international number if we failed
        if (!matches.iterator().hasNext()) {
            matches = numberUtil.findNumbers("+" + text, country);
        }

        if (matches.iterator().hasNext()) {
            Phonenumber.PhoneNumber number = matches.iterator().next().number();
            return Result.textMatch(numberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164));
        } else {
            return Result.NO_MATCH;
        }
    }
}
