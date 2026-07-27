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
package io.aklivity.zilla.runtime.engine.router;

import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

/**
 * Construction-tier context supplied to a {@link Router} during its setup.
 * <p>
 * Where {@code EngineContext} is the over-engine surface available to bindings and other
 * consumers, {@code RouteableContext} is the under-engine surface available to a router
 * that contributes to engine services. The router uses this context to obtain the engine's
 * current default stream factory (which it may wrap) and to inject synthesized namespaces
 * alongside operator-authored ones.
 * </p>
 *
 * @see Router
 * @see RouterContext
 */
public interface RouteableContext
{
    /**
     * Returns the engine {@link Configuration} for option access.
     *
     * @return the engine configuration
     */
    Configuration config();

    /**
     * Returns the engine's current default {@link BindingHandler} stream factory.
     * <p>
     * The router may call this method during its own construction to obtain the reference,
     * and may hold the reference for runtime use. Callers must not invoke
     * {@code newStream(...)} on this handler until after engine startup completes.
     * </p>
     *
     * @return the current stream factory
     */
    BindingHandler streamFactory();

    /**
     * Attaches a synthesized {@link NamespaceConfig} to the engine alongside operator-authored
     * namespaces.
     *
     * @param composite  the namespace configuration to attach
     */
    void attachComposite(
        NamespaceConfig composite);

    /**
     * Detaches a previously attached synthesized {@link NamespaceConfig} from the engine.
     *
     * @param composite  the namespace configuration to detach
     */
    void detachComposite(
        NamespaceConfig composite);
}
