/*
 * Copyright 2021-2024 Aklivity Inc
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
module io.aklivity.zilla.runtime.binding.pgsql
{
    requires io.aklivity.zilla.runtime.engine;
    requires org.antlr.antlr4.runtime;

    exports io.aklivity.zilla.runtime.binding.pgsql.parser;
    exports io.aklivity.zilla.runtime.binding.pgsql.parser.listener;
    exports io.aklivity.zilla.runtime.binding.pgsql.parser.model;

    provides io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi
        with io.aklivity.zilla.runtime.binding.pgsql.internal.PgsqlBindingFactorySpi;
}
