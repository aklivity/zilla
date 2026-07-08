// Minimal mock Petstore REST API. Its OpenAPI 3.1 description (inlined in
// etc/zilla.yaml) declares "create_pet" as requiring the "pets:write" scope
// via a bearerAuth security scheme; Zilla enforces that scope requirement at
// the mcp_openapi binding before a request ever reaches this service, so no
// authorization check is duplicated here.

import express from "express";

const PORT = Number(process.env.PORT ?? 4002);

const app = express();
app.use(express.json());

const pets = [
    { id: 1, name: "Bramble", tag: "dog" },
    { id: 2, name: "Whiskers", tag: "cat" }
];
let nextId = 3;

app.get("/pets", (req, res) => res.json(pets));

app.post("/pets", (req, res) =>
{
    const { name, tag } = req.body ?? {};
    if (!name)
    {
        res.status(400).json({ message: "name is required" });
        return;
    }

    const pet = { id: nextId++, name, tag };
    pets.push(pet);
    res.status(201).json(pet);
});

app.listen(PORT, () => console.log(`petstore mock listening on ${PORT}`));
