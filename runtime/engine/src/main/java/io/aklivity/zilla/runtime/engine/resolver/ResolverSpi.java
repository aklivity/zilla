/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.resolver;

/**
 * Resolves a variable name to its string value within a named expression context.
 * <p>
 * Zilla supports expression substitution in {@code zilla.yaml} using the syntax
 * {@code ${{context.varName}}}. The engine splits the expression at the first dot: the
 * left-hand part ({@code context}) selects the {@code ResolverSpi} implementation via
 * its {@link ResolverFactorySpi#type()} name; the right-hand part ({@code varName}) is
 * passed to {@link #resolve(String)}.
 * </p>
 * <p>
 * For example, given the expression {@code ${{env.MY_VAR}}}, the engine invokes
 * {@code resolve("MY_VAR")} on the {@code ResolverSpi} registered under the
 * type name {@code "env"}, which looks up the value from the process environment.
 * </p>
 * <p>
 * Implementations are created by a corresponding {@link ResolverFactorySpi} and discovered
 * via {@link java.util.ServiceLoader}.
 * </p>
 *
 * @see ResolverFactorySpi
 */
public interface ResolverSpi
{
    /**
     * Resolves the given variable name to its string value.
     * <p>
     * Returns {@code null} if the variable is not defined in this resolver's backing store.
     * The engine substitutes an empty string in place of {@code null} to avoid propagating
     * unresolved expressions into the parsed configuration.
     * </p>
     *
     * @param var  the variable name to resolve (the portion after the dot in the expression,
     *             e.g. {@code "MY_VAR"} from {@code ${{env.MY_VAR}}})
     * @return the resolved value, or {@code null} if the variable is not defined
     */
    String resolve(
        String var);
}
