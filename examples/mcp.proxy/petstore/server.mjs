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
const FEATURED_IDS = new Set([1]);
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

// Static resource: no path params, so mcp_openapi exposes this as a single
// fixed-URI entry in resources/list rather than a resources/templates entry.
app.get("/pets/featured", (req, res) => res.json(pets.filter((pet) => FEATURED_IDS.has(pet.id))));

// Dynamic resource: the {petId} path param makes mcp_openapi expose this as
// a resources/templates entry, read with a concrete URI per pet.
app.get("/pets/:petId", (req, res) =>
{
    const pet = pets.find((candidate) => String(candidate.id) === req.params.petId);
    if (!pet)
    {
        res.status(404).json({ message: "pet not found" });
        return;
    }

    res.json(pet);
});

app.listen(PORT, () => console.log(`petstore mock listening on ${PORT}`));
