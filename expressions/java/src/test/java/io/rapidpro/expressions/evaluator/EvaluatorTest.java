package io.rapidpro.expressions.evaluator;

import io.rapidpro.expressions.EvaluatedTemplate;
import io.rapidpro.expressions.EvaluationContext;
import io.rapidpro.expressions.EvaluationError;
import io.rapidpro.expressions.EvaluatorBuilder;
import io.rapidpro.expressions.dates.DateStyle;
import org.junit.Assert;
import org.junit.Test;
import org.threeten.bp.ZoneId;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link Evaluator}
 */
public class EvaluatorTest {

    private Evaluator m_evaluator = new EvaluatorBuilder().build();

    @Test
    public void evaluateTemplate() {
        EvaluatedTemplate evaluated = m_evaluator.evaluateTemplate("Answer is @(2 + 3)", new EvaluationContext());
        assertThat(evaluated.getOutput(), is("Answer is 5"));
        assertThat(evaluated.getErrors(), empty());

        // with unbalanced expression
        evaluated = m_evaluator.evaluateTemplate("Answer is @(2 + 3", new EvaluationContext());
        assertThat(evaluated.getOutput(), is("Answer is @(2 + 3"));
        assertThat(evaluated.getErrors(), empty());

        // with invalid character
        evaluated = m_evaluator.evaluateTemplate("@('x')", new EvaluationContext());
        assertThat(evaluated.getOutput(), is("@('x')"));
        assertThat(evaluated.getErrors(), contains("Expression error at: '"));
    }

    @Test
    public void evaluateExpression() {
        EvaluationContext context = new EvaluationContext();
        context.putVariable("foo", 5);
        context.putVariable("bar", 3);
        
        assertThat(m_evaluator.evaluateExpression("true", context), is((Object) true));
        assertThat(m_evaluator.evaluateExpression("FALSE", context), is((Object) false));

        assertThat(m_evaluator.evaluateExpression("10", context), is((Object) new BigDecimal(10)));
        assertThat(m_evaluator.evaluateExpression("1234.5678", context), is((Object) new BigDecimal("1234.5678")));

        assertThat(m_evaluator.evaluateExpression("\"\"", context), is((Object) ""));
        assertThat(m_evaluator.evaluateExpression("\"سلام\"", context), is((Object) "سلام"));
        assertThat(m_evaluator.evaluateExpression("\"He said \"\"hi\"\" \"", context), is((Object) "He said \"hi\" "));

        assertThat(m_evaluator.evaluateExpression("-10", context), is((Object) new BigDecimal(-10)));
        assertThat(m_evaluator.evaluateExpression("1 + 2", context), is((Object) new BigDecimal(3)));
        assertThat(m_evaluator.evaluateExpression("1.3 + 2.2", context), is((Object) new BigDecimal("3.5")));
        assertThat(m_evaluator.evaluateExpression("1.3 - 2.2", context), is((Object) new BigDecimal("-0.9")));
        assertThat(m_evaluator.evaluateExpression("4 * 2", context), is((Object) new BigDecimal(8)));
        assertThat(m_evaluator.evaluateExpression("4 / 2", context), is((Object) new BigDecimal("2.0000000000")));
        assertThat(m_evaluator.evaluateExpression("4 ^ 2", context), is((Object) new BigDecimal(16)));
        assertThat(m_evaluator.evaluateExpression("4 ^ 0.5", context), is((Object) new BigDecimal(2)));
        assertThat(m_evaluator.evaluateExpression("4 ^ -1", context), is((Object) new BigDecimal("0.25")));

        assertThat(m_evaluator.evaluateExpression("\"foo\" & \"bar\"", context), is((Object) "foobar"));
        assertThat(m_evaluator.evaluateExpression("2 & 3 & 4", context), is((Object) "234"));

        // check precedence
        assertThat(m_evaluator.evaluateExpression("2 + 3 / 4 - 5 * 6", context), is((Object) new BigDecimal("-27.2500000000")));
        assertThat(m_evaluator.evaluateExpression("2 & 3 + 4 & 5", context), is((Object) "275"));

        // check associativity
        assertThat(m_evaluator.evaluateExpression("2 - -2 + 7", context), is((Object) new BigDecimal(11)));
        assertThat(m_evaluator.evaluateExpression("2 ^ 3 ^ 4", context), is((Object) new BigDecimal(4096)));
        
        assertThat(m_evaluator.evaluateExpression("FOO", context), is((Object) 5));
        assertThat(m_evaluator.evaluateExpression("foo + bar", context), is((Object) new BigDecimal(8)));

        assertThat(m_evaluator.evaluateExpression("len(\"abc\")", context), is((Object) 3));
        assertThat(m_evaluator.evaluateExpression("SUM(1, 2, 3)", context), is((Object) new BigDecimal(6)));

        assertThat(m_evaluator.evaluateExpression("FIXED(1234.5678)", context), is((Object) "1,234.57"));
        assertThat(m_evaluator.evaluateExpression("FIXED(1234.5678, 1)", context), is((Object) "1,234.6"));
        assertThat(m_evaluator.evaluateExpression("FIXED(1234.5678, 1, True)", context), is((Object) "1234.6"));
    }

    @Test
    public void evaluateTemplate_withResolveAvailableStrategy() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("foo", 5);
        variables.put("bar", "x");

        EvaluationContext context = new EvaluationContext(variables, ZoneId.of("UTC"), DateStyle.DAY_FIRST);

        EvaluatedTemplate evaluated = m_evaluator.evaluateTemplate("@(1 + 2)", context, false, Evaluator.EvaluationStrategy.RESOLVE_AVAILABLE);
        assertThat(evaluated.getOutput(), is("3"));

        evaluated = m_evaluator.evaluateTemplate("Hi @contact.name", context, false, Evaluator.EvaluationStrategy.RESOLVE_AVAILABLE);
        assertThat(evaluated.getOutput(), is("Hi @contact.name"));

        evaluated = m_evaluator.evaluateTemplate("@(foo + contact.name + bar)", context, false, Evaluator.EvaluationStrategy.RESOLVE_AVAILABLE);
        assertThat(evaluated.getOutput(), is("@(5+contact.name+\"x\")"));
    }

    @Test
    public void evaluateExpression_withErrors() {
        EvaluationContext context = new EvaluationContext();
        context.putVariable("foo", 5);

        // parser errors
        assertErrorMessage("0 /", context, "Expression is invalid");
        assertErrorMessage("\"", context, "Expression error at: \"");
        assertErrorMessage("1.1.0", context, "Expression is invalid");

        // evaluation errors
        assertErrorMessage("X", context, "Undefined variable: X");
        assertErrorMessage("2 / 0", context, "Division by zero");
        assertErrorMessage("0 / 0", context, "Division by zero");
    }

    protected void assertErrorMessage(String expression, EvaluationContext context, String expectedMessage) {
        try {
            m_evaluator.evaluateExpression(expression, context);
            Assert.fail();
        }
        catch (EvaluationError ex) {
            assertThat(ex.getMessage(), is(expectedMessage));
        }
    }
}
