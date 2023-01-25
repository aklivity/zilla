package io.aklivity.zilla.runtime.engine.expression;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExpressionResolverTest
{
    @Test
    public void shouldLoadAndResolve()
    {
        ExpressionResolver expressions = ExpressionResolver.instantiate();
        String actual = expressions.resolve("${{test.PASSWORD}}");

        assertEquals("ACTUALPASSWORD", actual);
    }
}
