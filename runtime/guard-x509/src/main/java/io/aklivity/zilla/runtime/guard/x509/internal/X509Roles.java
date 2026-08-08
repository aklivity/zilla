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
package io.aklivity.zilla.runtime.guard.x509.internal;

import static java.util.Collections.emptyList;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.guard.x509.X509MatchConfig;

// Evaluates the operator-declared role mapping against the fields of a verified chain.
// Matches within a role are OR'd, fields within a match are AND'd, a multi-valued field
// matches existentially, an absent field never matches, and roles are a set union.
final class X509Roles
{
    private final List<X509Role> roles;

    X509Roles(
        Map<String, List<X509MatchConfig>> roles)
    {
        List<X509Role> compiled = new LinkedList<>();

        if (roles != null)
        {
            roles.forEach((name, matches) -> compiled.add(new X509Role(name, matches)));
        }

        this.roles = compiled;
    }

    List<String> resolve(
        Map<String, List<String>> fields)
    {
        List<String> resolved = emptyList();

        for (X509Role role : roles)
        {
            if (role.matches(fields))
            {
                if (resolved.isEmpty())
                {
                    resolved = new LinkedList<>();
                }
                resolved.add(role.name);
            }
        }

        return resolved;
    }

    private static Pattern asPattern(
        String glob)
    {
        StringBuilder pattern = new StringBuilder("^");
        StringBuilder literal = new StringBuilder();

        for (int index = 0; index < glob.length(); index++)
        {
            char ch = glob.charAt(index);

            if (ch == '*' || ch == '?')
            {
                if (literal.length() != 0)
                {
                    pattern.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                pattern.append(ch == '*' ? ".*" : ".");
            }
            else
            {
                literal.append(ch);
            }
        }

        if (literal.length() != 0)
        {
            pattern.append(Pattern.quote(literal.toString()));
        }

        pattern.append('$');

        return Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static final class X509Role
    {
        private final String name;
        private final List<X509Match> matches;

        private X509Role(
            String name,
            List<X509MatchConfig> matches)
        {
            this.name = name;
            this.matches = new LinkedList<>();

            if (matches != null)
            {
                matches.forEach(match -> this.matches.add(new X509Match(match)));
            }
        }

        private boolean matches(
            Map<String, List<String>> fields)
        {
            boolean matched = false;

            for (X509Match match : matches)
            {
                if (match.matches(fields))
                {
                    matched = true;
                    break;
                }
            }

            return matched;
        }
    }

    private static final class X509Match
    {
        private final List<X509Condition> conditions;

        private X509Match(
            X509MatchConfig match)
        {
            this.conditions = new LinkedList<>();

            if (match.fields != null)
            {
                match.fields.forEach((field, glob) -> conditions.add(new X509Condition(field, glob)));
            }
        }

        private boolean matches(
            Map<String, List<String>> fields)
        {
            boolean matched = !conditions.isEmpty();

            for (X509Condition condition : conditions)
            {
                if (!condition.matches(fields))
                {
                    matched = false;
                    break;
                }
            }

            return matched;
        }
    }

    private static final class X509Condition
    {
        private final String field;
        private final Matcher matcher;

        private X509Condition(
            String field,
            String glob)
        {
            this.field = field;
            this.matcher = asPattern(glob).matcher("");
        }

        private boolean matches(
            Map<String, List<String>> fields)
        {
            List<String> values = fields.get(field);

            boolean matched = false;

            if (values != null)
            {
                for (String value : values)
                {
                    if (matcher.reset(value).matches())
                    {
                        matched = true;
                        break;
                    }
                }
            }

            return matched;
        }
    }
}
