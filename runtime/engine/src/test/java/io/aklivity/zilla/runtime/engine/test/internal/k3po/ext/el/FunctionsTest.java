/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.el;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertNotEquals;
import static org.kaazing.k3po.lang.internal.el.ExpressionFactoryUtils.newExpressionFactory;

import javax.el.ExpressionFactory;
import javax.el.ValueExpression;

import org.junit.Test;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

public final class FunctionsTest
{
    @Test
    public void shouldInvokeId() throws Exception
    {
        ExpressionFactory factory = newExpressionFactory();
        ExpressionContext environment = new ExpressionContext();

        String expressionText = "${zilla:id(\"test\")}";
        ValueExpression expression = factory.createValueExpression(environment, expressionText, int.class);

        Object id = expression.getValue(environment);

        assertThat(id, instanceOf(Integer.class));
        assertNotEquals(0, Integer.class.cast(id).intValue());
    }
}
