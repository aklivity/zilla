/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import java.util.Set;

import org.agrona.collections.ObjectHashSet;

import io.aklivity.zilla.runtime.binding.grpc.config.GrpcMethodConfig;
import io.aklivity.zilla.runtime.binding.grpc.config.GrpcServiceConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.parser.Protobuf3BaseListener;
import io.aklivity.zilla.runtime.binding.grpc.internal.parser.Protobuf3Parser;

public class GrpcServiceDefinitionListener extends Protobuf3BaseListener
{
    private final Set<GrpcServiceConfig> services;
    private String package_;

    public GrpcServiceDefinitionListener(
        Set<GrpcServiceConfig> services)
    {
        this.services = services;
    }

    @Override
    public void enterPackageStatement(
        Protobuf3Parser.PackageStatementContext ctx)
    {
        package_ = ctx.fullIdent().getText();
    }

    @Override
    public void exitServiceDef(
        Protobuf3Parser.ServiceDefContext ctx)
    {
        String serviceName = String.format("%s.%s", package_, ctx.serviceName().getText());
        final ObjectHashSet<GrpcMethodConfig> methods = new ObjectHashSet<>();

        ctx.serviceElement().forEach(element ->
        {
            Protobuf3Parser.RpcContext rpc = element.rpc();

            String method = rpc.rpcName().getText();
            methods.add(new GrpcMethodConfig(method));
        });
        final GrpcServiceConfig service = new GrpcServiceConfig(serviceName, methods);
        services.add(service);
    }

}
