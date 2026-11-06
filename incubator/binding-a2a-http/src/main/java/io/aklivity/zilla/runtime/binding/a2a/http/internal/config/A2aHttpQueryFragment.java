/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.a2a.http.internal.config;

public final class A2aHttpQueryFragment
{
    public final String template;
    public final String expression;
    public final String name;

    public static A2aHttpQueryFragment required(
        String template)
    {
        return new A2aHttpQueryFragment(template, null, null);
    }

    public static A2aHttpQueryFragment optional(
        String expression,
        String name)
    {
        return new A2aHttpQueryFragment(null, expression, name);
    }

    public boolean optional()
    {
        return template == null;
    }

    private A2aHttpQueryFragment(
        String template,
        String expression,
        String name)
    {
        this.template = template;
        this.expression = expression;
        this.name = name;
    }
}
