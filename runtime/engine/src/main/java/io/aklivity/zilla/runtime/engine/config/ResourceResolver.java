/*
 * Copyright 2021-2023 Aklivity Inc.
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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.vault.Vault;

public class ResourceResolver
{
    private static final String FILESYSTEM = "filesystem";

    private final Catalog fsCatalog;
    private final Vault fsVault;

    public ResourceResolver(
        Collection<Catalog> catalogs,
        Collection<Vault> vaults)
    {
        Catalog fsCatalog = null;
        for (Catalog catalog : catalogs)
        {
            if (FILESYSTEM.equals(catalog.name()))
            {
                fsCatalog = catalog;
                break;
            }
        }
        this.fsCatalog = fsCatalog;

        Vault fsVault = null;
        for (Vault vault : vaults)
        {
            if (FILESYSTEM.equals(vault.name()))
            {
                fsVault = vault;
                break;
            }
        }
        this.fsVault = fsVault;
    }

    public List<String> resolve(
        NamespaceConfig namespace)
    {
        requireNonNull(namespace);
        List<String> result = new LinkedList<>();
        if (fsCatalog != null && namespace.catalogs != null)
        {
            for (CatalogConfig catalog : namespace.catalogs)
            {
                if (FILESYSTEM.equals(catalog.type))
                {
                    result.addAll(catalog.options.resources);
                }
            }
        }
        if (fsVault != null && namespace.vaults != null)
        {
            for (VaultConfig vault : namespace.vaults)
            {
                if (FILESYSTEM.equals(vault.type))
                {
                    result.addAll(vault.options.resources);
                }
            }
        }
        return result;
    }
}
