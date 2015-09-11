package io.rapidpro.expressions.functions;

import io.rapidpro.expressions.EvaluationContext;
import io.rapidpro.expressions.evaluator.Conversions;
import io.rapidpro.expressions.functions.annotations.BooleanDefault;
import io.rapidpro.expressions.functions.annotations.IntegerDefault;
import io.rapidpro.expressions.functions.annotations.StringDefault;
import io.rapidpro.expressions.utils.ExpressionUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Library of supported custom functions.
 */
public class CustomFunctions {

    /**
     * Reference a field in string separated by a delimiter
     */
    public static String field(EvaluationContext ctx, Object text, Object index, @StringDefault(" ") Object delimiter) {
        String _text = Conversions.toString(text, ctx);
        int _index = Conversions.toInteger(index, ctx);
        String _delimiter = Conversions.toString(delimiter, ctx);

        String[] splits = StringUtils.splitByWholeSeparator(_text, _delimiter);

        if (_index < 1) {
            throw new RuntimeException("Field index cannot be less than 1");
        }

        if (_index <= splits.length) {
            return splits[_index - 1];
        } else {
            return "";
        }
    }

    /**
     * Returns the first word in the given text string
     */
    public static String first_word(EvaluationContext ctx, Object text) {
        // In Excel this would be IF(ISERR(FIND(" ",A2)),"",LEFT(A2,FIND(" ",A2)-1))
        return word(ctx, text, 1, false);
    }

    /**
     * Formats a number as a percentage
     */
    public static String percent(EvaluationContext ctx, Object number) {
        BigDecimal percent = Conversions.toDecimal(number, ctx).multiply(new BigDecimal(100));
        return Conversions.toInteger(percent, ctx) + "%";
    }

    /**
     * Formats digits in text for reading in TTS
     */
    public static String read_digits(EvaluationContext ctx, Object text) {
        String _text = Conversions.toString(text, ctx).trim();
        if (StringUtils.isEmpty(_text)) {
            return "";
        }

        // trim off the plus for phone numbers
        if (_text.startsWith("+")) {
            _text = _text.substring(1);
        }

        if (_text.length() == 9) { // SSN
            return StringUtils.join(_text.substring(0, 3).toCharArray(), ' ')
                    + " , " + StringUtils.join(_text.substring(3, 5).toCharArray(), ' ')
                    + " , " + StringUtils.join(_text.substring(5).toCharArray(), ' ');
        }
        else if (_text.length() % 3 == 0 && _text.length() > 3) { // triplets, most international phone numbers
            List<String> chunks = chunk(_text, 3);
            return StringUtils.join(StringUtils.join(chunks, ',').toCharArray(), ' ');
        }
        else if (_text.length() % 4 == 0) { // quads, credit cards
            List<String> chunks = chunk(_text, 4);
            return StringUtils.join(StringUtils.join(chunks, ',').toCharArray(), ' ');
        }
        else {
            // otherwise, just put a comma between each number
            return StringUtils.join(_text.toCharArray(), ',');
        }
    }

    /**
     * Removes the first word from the given text string
     */
    public static String remove_first_word(EvaluationContext ctx, Object text) {
        String _text = StringUtils.stripStart(Conversions.toString(text, ctx), null);
        String firstWord = first_word(ctx, _text);

        if (StringUtils.isNotEmpty(firstWord)) {
            return StringUtils.stripStart(_text.substring(firstWord.length()), null);
        } else {
            return "";
        }
    }

    /**
     * Extracts the nth word from the given text string
     */
    public static String word(EvaluationContext ctx, Object text, Object number, @BooleanDefault(false) Object bySpaces) {
        return word_slice(ctx, text, number, Conversions.toInteger(number, ctx) + 1, bySpaces);
    }

    /**
     * Returns the number of words in the given text string
     */
    public static int word_count(EvaluationContext ctx, Object text, @BooleanDefault(false) Object bySpaces) {
        String _text = Conversions.toString(text, ctx);
        boolean _bySpaces = Conversions.toBoolean(bySpaces, ctx);
        return getWords(_text, _bySpaces).size();
    }

    /**
     * Extracts a substring spanning from start up to but not-including stop
     */
    public static String word_slice(EvaluationContext ctx, Object text, Object start, @IntegerDefault(0) Object stop, @BooleanDefault(false) Object bySpaces) {
        String _text = Conversions.toString(text, ctx);
        int _start = Conversions.toInteger(start, ctx);
        Integer _stop = Conversions.toInteger(stop, ctx);
        boolean _bySpaces = Conversions.toBoolean(bySpaces, ctx);

        if (_start == 0) {
            throw new RuntimeException("Start word cannot be zero");
        } else if (_start > 0) {
            _start -= 1;  // convert to a zero-based offset
        }

        if (_stop == 0) {  // zero is treated as no end
            _stop = null;
        } else if (_stop > 0) {
            _stop -= 1; // convert to a zero-based offset
        }

        List<String> words = getWords(_text, _bySpaces);
        List<String> selection = ExpressionUtils.slice(words, _start, _stop);

        // re-combine selected words with a single space
        return StringUtils.join(selection, ' ');
    }

    /************************************************************************************
     * Helper (not available in expressions)
     ************************************************************************************/

    /**
     * Helper function which splits the given text string into words. If by_spaces is false, then text like '01-02-2014'
     * will be split into 3 separate words. For backwards compatibility, this is the default for all expression functions.
     * @param text the text to split
     * @param bySpaces whether words should be split only by spaces or by punctuation like '-', '.' etc
     * @return the words as a list of strings
     */
    private static List<String> getWords(String text, boolean bySpaces) {
        if (bySpaces) {
            List<String> words = new ArrayList<>();
            for (String split : text.split("\\s+")) {
                if (StringUtils.isNotEmpty(split)) {
                    words.add(split);
                }
            }
            return words;
        } else {
            return Arrays.asList(ExpressionUtils.tokenize(text));
        }
    }

    /**
     * Splits a string into equally sized chunks
     * @param text the text to split
     * @param size the chunk size
     * @return the list of chunks
     */
    private static List<String> chunk(String text, int size) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(StringUtils.substring(text, i, i + size));
        }
        return chunks;
    }
}
