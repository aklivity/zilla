/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.config;

import static java.util.Objects.requireNonNull;

import java.util.List;

public class Binding
{
    public transient long id;

    public final NamespacedRef vault;
    public final String entry;
    public final String type;
    public final Role kind;
    public final Options options;
    public final List<Route> routes;
    public final Route exit;

    public Binding(
        NamespacedRef vault,
        String entry,
        String type,
        Role kind,
        Options options,
        List<Route> routes,
        Route exit)
    {
        this.vault = vault;
        this.entry = entry;
        this.type = requireNonNull(type);
        this.kind = requireNonNull(kind);
        this.options = options;
        this.routes = routes;
        this.exit = exit;
    }
}
