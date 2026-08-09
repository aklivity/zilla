/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import static io.aklivity.zilla.runtime.common.x509.X509Fields.SUBJECT_DN;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableMap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.tls.TlsWithCertificateConfig;
import io.aklivity.zilla.config.binding.tls.TlsWithConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.common.x509.X509Fields;

/**
 * Resolves the certificate properties named by {@code routes[].with.certificate} against the
 * guarded session on the inbound application stream, yielding the property values the client
 * binding must match against its own candidate keys.
 *
 * <p>Values are either literals, which resolve to themselves, or {@code ${guarded[...]}}
 * expressions, which resolve per stream from the session identified by {@code authorization}.
 * A literal {@code subject.dn} is canonicalized once here, at configuration load, because a
 * guard renders a distinguished name canonically and a hand-written name otherwise never
 * compares equal to one.
 */
public final class TlsWithResolver
{
    private static final Pattern IDENTITY_PATTERN =
        Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]).identity\\}");
    private static final Pattern ATTRIBUTE_PATTERN =
        Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]).attributes" +
            ".([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)\\}");

    public static Set<String> extractGuardNames(
        TlsWithConfig with)
    {
        Set<String> guardNames = emptySet();

        TlsWithCertificateConfig certificate = with != null ? with.certificate : null;
        if (certificate != null)
        {
            guardNames = new LinkedHashSet<>();

            for (String value : certificate.fields.values())
            {
                Matcher identity = IDENTITY_PATTERN.matcher(value);
                while (identity.find())
                {
                    guardNames.add(identity.group(1));
                }

                Matcher attribute = ATTRIBUTE_PATTERN.matcher(value);
                while (attribute.find())
                {
                    guardNames.add(attribute.group(1));
                }
            }
        }

        return guardNames;
    }

    private final Map<String, String> fields;
    private final LongObjectBiFunction<MatchResult, String> identityReplacer;
    private final LongObjectBiFunction<MatchResult, String> attributeReplacer;
    private final Matcher identityMatcher;
    private final Matcher attributeMatcher;

    public TlsWithResolver(
        String qname,
        LongObjectBiFunction<MatchResult, String> identityReplacer,
        LongObjectBiFunction<MatchResult, String> attributeReplacer,
        TlsWithConfig with)
    {
        this.identityReplacer = identityReplacer;
        this.attributeReplacer = attributeReplacer;
        this.identityMatcher = IDENTITY_PATTERN.matcher("");
        this.attributeMatcher = ATTRIBUTE_PATTERN.matcher("");

        Map<String, String> fields = new LinkedHashMap<>();
        with.certificate.fields.forEach((name, value) -> fields.put(name, canonicalize(qname, name, value)));
        this.fields = unmodifiableMap(fields);
    }

    public Set<String> fieldNames()
    {
        return fields.keySet();
    }

    /**
     * Resolves every configured property against the guarded session, returning {@code null}
     * when any property resolves to nothing. All properties are matched together, so a single
     * unresolved property makes the whole clause unsatisfiable rather than broader.
     */
    public Map<String, String> resolve(
        long authorization)
    {
        Map<String, String> resolved = new LinkedHashMap<>();

        for (Map.Entry<String, String> field : fields.entrySet())
        {
            String value = resolveValue(authorization, field.getValue());

            if (value.isEmpty())
            {
                resolved = null;
                break;
            }

            resolved.put(field.getKey(), value);
        }

        return resolved;
    }

    /**
     * Names the first property that does not resolve, so an unresolved clause can point back
     * at the configuration that declared it.
     */
    public String unresolvedField(
        long authorization)
    {
        String unresolved = null;

        for (Map.Entry<String, String> field : fields.entrySet())
        {
            if (resolveValue(authorization, field.getValue()).isEmpty())
            {
                unresolved = field.getKey();
                break;
            }
        }

        return unresolved;
    }

    private String resolveValue(
        long authorization,
        String value)
    {
        value = findAndReplace(value, identityMatcher, r -> identityReplacer.apply(authorization, r));
        value = findAndReplace(value, attributeMatcher, r -> attributeReplacer.apply(authorization, r));

        return value;
    }

    private static String canonicalize(
        String qname,
        String name,
        String value)
    {
        String canonical = value;

        // an expression is resolved per stream from a guard, which already renders canonically
        if (SUBJECT_DN.equals(name) && !isExpression(value))
        {
            canonical = X509Fields.canonicalName(value);

            if (canonical == null)
            {
                throw new IllegalArgumentException(String.format(
                    "binding %s route with.certificate.%s is not a distinguished name: %s", qname, name, value));
            }
        }

        return canonical;
    }

    private static boolean isExpression(
        String value)
    {
        return IDENTITY_PATTERN.matcher(value).find() || ATTRIBUTE_PATTERN.matcher(value).find();
    }

    private static String findAndReplace(
        String value,
        Matcher matcher,
        Function<MatchResult, String> replacer)
    {
        matcher.reset(value);
        while (matcher.find())
        {
            value = matcher.replaceAll(replacer);
            matcher.reset(value);
        }
        return value;
    }
}
