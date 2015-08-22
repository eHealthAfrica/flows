package io.rapidpro.expressions.evaluator;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.*;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flexible date parser for human written dates
 */
public class DateParser {

    protected static final Map<String, Integer> monthsByAlias;
    static {
        try {
            monthsByAlias = loadMonthAliases("month.aliases");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected enum Mode {
        DATE,
        DATETIME,
        TIME,
        AUTO,
    }

    protected static final int AM = 0;
    protected static final int PM = 1;

    protected enum Component {
        YEAR,            // 99 or 1999
        MONTH,           // 1 or Jan
        DAY,
        HOUR,
        MINUTE,
        HOUR_AND_MINUTE, // e.g. 1400
        SECOND,
        AM_PM
    }

    protected static final Component[][] s_dateSequencesDayFirst = new Component[][] {
            { Component.DAY, Component.MONTH, Component.YEAR },
            { Component.MONTH, Component.DAY, Component.YEAR },
            { Component.YEAR , Component.MONTH, Component.DAY, },
            { Component.DAY, Component.MONTH },
            { Component.MONTH, Component.DAY },
            { Component.MONTH, Component.YEAR },
    };

    protected static final Component[][] s_dateSequencesMonthFirst = new Component[][] {
            { Component.MONTH, Component.DAY, Component.YEAR },
            { Component.DAY, Component.MONTH, Component.YEAR },
            { Component.YEAR , Component.MONTH, Component.DAY, },
            { Component.MONTH, Component.DAY },
            { Component.DAY, Component.MONTH },
            { Component.MONTH, Component.YEAR },
    };

    protected static final Component[][] s_timeSequences = new Component[][] {
            { Component.HOUR_AND_MINUTE },
            { Component.HOUR, Component.MINUTE },
            { Component.HOUR, Component.MINUTE, Component.AM_PM },
            { Component.HOUR, Component.MINUTE, Component.SECOND},
            { Component.HOUR, Component.MINUTE, Component.SECOND, Component.AM_PM },
    };

    protected final LocalDate m_now;
    protected final ZoneId m_timezone;
    protected final boolean m_dayFirst;

    /**
     * Creates a new date parser
     * @param now the now which parsing happens relative to
     * @param dayFirst whether dates are usually entered day first or month first
     */
    public DateParser(LocalDate now, ZoneId timezone, boolean dayFirst) {
        this.m_now = now;
        this.m_timezone = timezone;
        this.m_dayFirst = dayFirst;
    }

    /**
     * Returns a date or datetime depending on what information is available
     * @return the parsed date or datetime
     */
    public Temporal auto(String text) {
        return parse(text, Mode.AUTO);
    }

    /**
     * Tries to parse a time value from the given text
     * @param text the text
     * @return the parsed time
     */
    public OffsetTime time(String text) {
        return (OffsetTime) parse(text, Mode.TIME);
    }

    /**
     * Returns a date or datetime depending on what information is available
     * @return the parsed date or datetime
     */
    protected Temporal parse(String text, Mode mode) {
        if (StringUtils.isBlank(text)) {
            return null;
        }

        // split the text into numerical and text tokens
        Pattern pattern = Pattern.compile("([0-9]+|\\w+)", Pattern.UNICODE_CHARACTER_CLASS);
        Matcher matcher = pattern.matcher(text);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group(0));
        }

        // get the possibilities for each token
        List<Map<Component, Integer>> tokenPossibilities = new ArrayList<>();
        for (String token : tokens) {
            Map<Component, Integer> possibilities = getTokenPossibilities(token, mode);
            if (possibilities.size() > 0) {
                tokenPossibilities.add(possibilities);
            }
        }

        // see what valid sequences we can make
        List<Component[]> sequences = getPossibleSequences(mode, tokenPossibilities.size(), m_dayFirst);
        List<Map<Component, Integer>> possibleMatches = new ArrayList<>();

        outer:
        for (Component[] sequence : sequences) {
            Map<Component, Integer> match = new LinkedHashMap<>();

            for (int c = 0; c < sequence.length; c++) {
                Component component = sequence[c];
                Integer value = tokenPossibilities.get(c).get(component);
                match.put(component, value);

                if (value == null) {
                    continue outer;
                }
            }
            possibleMatches.add(match);
        }

        // find the first match that can form a valid date or datetime
        for (Map<Component, Integer> match : possibleMatches) {
            Temporal obj = makeResult(match, m_now, m_timezone);
            if (obj != null) {
                return obj;
            }
        }

        return null;
    }

    /**
     * Gets possible component sequences in the given mode
     * @param mode the mode
     * @param length the length (only returns sequences of this length)
     * @param dayFirst whether dates are usually entered day first or month first
     * @return the list of sequences
     */
    protected static List<Component[]> getPossibleSequences(Mode mode, int length, boolean dayFirst) {
        List<Component[]> sequences = new ArrayList<>();
        Component[][] dateSequences = dayFirst ? s_dateSequencesDayFirst : s_dateSequencesMonthFirst;

        if (mode == Mode.DATE || mode == Mode.AUTO) {
            for (Component[] seq : dateSequences) {
                if (seq.length == length) {
                    sequences.add(seq);
                }
            }

        } else if (mode == Mode.TIME) {
            for (Component[] seq : s_timeSequences) {
                if (seq.length == length) {
                    sequences.add(seq);
                }
            }
        }

        if (mode == Mode.DATETIME || mode == Mode.AUTO) {
            for (Component[] dateSeq : dateSequences) {
                for (Component[] timeSeq : s_timeSequences) {
                    if (dateSeq.length + timeSeq.length == length) {
                        sequences.add(ArrayUtils.addAll(dateSeq, timeSeq));
                    }
                }
            }
        }

        return sequences;
    }

    /**
     * Returns all possible component types of a token without regard to its context. For example "26" could be year,
     * date or minute, but can't be a month or an hour.
     * @param token the token to classify
     * @return the map of possible types and values if token was of that type
     */
    protected static Map<Component, Integer> getTokenPossibilities(String token, Mode mode) {
        token = token.toLowerCase().trim();
        Map<Component, Integer> possibilities = new HashMap<>();
        try {
            int asInt = Integer.parseInt(token);

            if (mode != Mode.TIME) {
                if (asInt >= 1 && asInt <= 9999 && (token.length() == 2 || token.length() == 4)) {
                    possibilities.put(Component.YEAR, asInt);
                }
                if (asInt >= 1 && asInt <= 12) {
                    possibilities.put(Component.MONTH, asInt);
                }
                if (asInt >= 1 && asInt <= 31) {
                    possibilities.put(Component.DAY, asInt);
                }
            }

            if (mode != Mode.DATE) {
                if (asInt >= 0 && asInt <= 23) {
                    possibilities.put(Component.HOUR, asInt);
                }
                if (asInt >= 0 && asInt <= 59) {
                    possibilities.put(Component.MINUTE, asInt);
                }
                if (asInt >= 0 && asInt <= 59) {
                    possibilities.put(Component.SECOND, asInt);
                }
                if (token.length() == 4) {
                    int hour = asInt / 100;
                    int minute = asInt - (hour * 100);
                    if (hour >= 1 && hour <= 24 && minute >= 1 && minute <= 59) {
                        possibilities.put(Component.HOUR_AND_MINUTE, asInt);
                    }
                }
            }
        }
        catch (NumberFormatException ex) {
            if (mode != Mode.TIME) {
                // could it be a month alias?
                Integer month = monthsByAlias.get(token);
                if (month != null) {
                    possibilities.put(Component.MONTH, month);
                }
            }

            if (mode != Mode.DATE) {
                // could it be an AM/PM marker?
                boolean isAmMarker = token.equals("am");
                boolean isPmMarker = token.equals("pm");
                if (isAmMarker || isPmMarker) {
                    possibilities.put(Component.AM_PM, isAmMarker ? AM : PM);
                }
            }
        }

        return possibilities;
    }

    /**
     * Makes a date or datetime or time object from a map of component values
     * @param values the component values
     * @param timezone the current timezone
     * @return the date, datetime, time or null if values are invalid
     */
    protected static Temporal makeResult(Map<Component, Integer> values, LocalDate now, ZoneId timezone) {
        LocalDate date = null;
        LocalTime time = null;

        if (values.containsKey(Component.MONTH)) {
            int year = yearFrom2Digits(values.getOrDefault(Component.YEAR, now.getYear()), now.getYear());
            int month = values.get(Component.MONTH);
            int day = values.getOrDefault(Component.DAY, 1);
            try {
                date = LocalDate.of(year, month, day);
            } catch (DateTimeException ex) {
                return null;  // not a valid date
            }
        }

        if ((values.containsKey(Component.HOUR) && values.containsKey(Component.MINUTE)) || values.containsKey(Component.HOUR_AND_MINUTE)) {
            int hour, minute, second;
            if (values.containsKey(Component.HOUR_AND_MINUTE)) {
                int combined = values.get(Component.HOUR_AND_MINUTE);
                hour = combined / 100;
                minute = combined - (hour * 100);
                second = 0;
            }
            else {
                hour = values.get(Component.HOUR);
                minute = values.get(Component.MINUTE);
                second = values.getOrDefault(Component.SECOND, 0);

                if (hour <= 12 && values.getOrDefault(Component.AM_PM, AM) == PM) {
                    hour += 12;
                }
            }

            try {
                time = LocalTime.of(hour, minute, second);
            }
            catch (DateTimeException ex) {
                return null;  // not a valid time
            }
        }

        if (date != null && time != null) {
            return ZonedDateTime.of(date, time, timezone);
        } else if (date != null) {
            return date;
        } else if (time != null) {
            return ZonedDateTime.of(now, time, timezone).toOffsetDateTime().toOffsetTime();
        } else {
            return null;
        }
    }

    /**
     * Converts a relative 2-digit year to an absolute 4-digit year
     * @param shortYear the relative year
     * @return the absolute year
     */
    protected static int yearFrom2Digits(int shortYear, int currentYear) {
        if (shortYear < 100) {
            shortYear += currentYear - (currentYear % 100);
            if (Math.abs(shortYear - currentYear) >= 50) {
                if (shortYear < currentYear) {
                    return shortYear + 100;
                } else {
                    return shortYear - 100;
                }
            }
        }
        return shortYear;
    }

    /**
     * Loads month aliases from the given resource file
     */
    protected static Map<String, Integer> loadMonthAliases(String file) throws IOException {
        InputStream in = DateParser.class.getClassLoader().getResourceAsStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        Map<String, Integer> map = new HashMap<>();
        String line;
        int month = 1;
        while ((line = reader.readLine()) != null) {
            for (String alias : line.split(",")) {
                map.put(alias, month);
            }
            month++;
        }
        reader.close();
        return map;
    }
}