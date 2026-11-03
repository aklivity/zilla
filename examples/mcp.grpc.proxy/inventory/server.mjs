// Minimal mock gRPC inventory service. Zilla's mcp-grpc binding derives the MCP
// tools get_item and list_items from the same etc/protos/inventory.proto this
// service loads, so neither side re-declares the message shapes. Every call must
// carry the Bearer credential Zilla forwards from the caller's session as gRPC
// metadata via options.authorization, otherwise it is UNAUTHENTICATED.

import grpc from "@grpc/grpc-js";
import loader from "@grpc/proto-loader";

const PORT = Number(process.env.PORT ?? 4004);
const PROTO = process.env.PROTO ?? "/protos/inventory.proto";

const definition = loader.loadSync(PROTO, { longs: String, defaults: true });
const { InventoryService } = grpc.loadPackageDefinition(definition).acme.inventory.v1;

const items = [
    { sku: "sku-1", name: "Widget", category: "tools", quantity: 12 },
    { sku: "sku-2", name: "Gadget", category: "tools", quantity: 3 },
    { sku: "sku-3", name: "Gizmo", category: "toys", quantity: 40 }
];

const authenticated = (handler) => (call, callback) =>
{
    const [credential] = call.metadata.get("authorization");
    const [identity] = call.metadata.get("x-user-identity");

    if (typeof credential === "string" && credential.startsWith("Bearer "))
    {
        console.log(`caller ${identity}`);
        handler(call, callback);
    }
    else
    {
        callback({ code: grpc.status.UNAUTHENTICATED, message: "missing credential" });
    }
};

const server = new grpc.Server();

server.addService(InventoryService.service,
{
    getItem: authenticated((call, callback) =>
    {
        const item = items.find(i => i.sku === call.request.sku);
        console.log(`GetItem ${JSON.stringify(call.request)}`);
        if (item)
        {
            callback(null, item);
        }
        else
        {
            callback({ code: grpc.status.NOT_FOUND, message: `unknown sku ${call.request.sku}` });
        }
    }),
    listItems: authenticated((call, callback) =>
    {
        const { category } = call.request;
        console.log(`ListItems ${JSON.stringify(call.request)}`);
        callback(null, { items: items.filter(i => !category || i.category === category) });
    })
});

server.bindAsync(`0.0.0.0:${PORT}`, grpc.ServerCredentials.createInsecure(), (error) =>
{
    if (error)
    {
        throw error;
    }
    console.log(`inventory gRPC listening on :${PORT}`);
});
