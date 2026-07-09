// Minimal mock of the subset of the GitHub REST API used by the mcp_http
// "github" toolkit. Requires a Bearer credential on every request -- Zilla
// forwards the caller's own JWT via options.authorization.credentials.headers
// on the mcp_http binding -- and echoes the forwarded x-user-identity header
// into the response so the credential-forwarding path is directly observable.

import express from "express";

const PORT = Number(process.env.PORT ?? 4001);

const app = express();
app.use(express.json());

let nextNumber = 101;
const pulls = new Map();

const pullKey = (owner, repo, number) => `${owner}/${repo}/${number}`;

// Seeded so the pull_by_number resource has something to read without
// requiring a prior create_pr call.
pulls.set(pullKey("acme", "widget", "42"), {
    number: 42,
    html_url: "https://github.com/acme/widget/pull/42",
    state: "open",
    title: "Seed data for the pull_by_number resource demo"
});

app.post("/repos/:owner/:repo/pulls", (req, res) =>
{
    const authorization = req.headers["authorization"] ?? "";
    if (!authorization.startsWith("Bearer "))
    {
        res.status(401).json({ message: "Bad credentials" });
        return;
    }

    const { owner, repo } = req.params;
    const { title, head, base } = req.body ?? {};
    const number = nextNumber++;
    const pull = {
        number,
        html_url: `https://github.com/${owner}/${repo}/pull/${number}`,
        state: "open",
        title,
        head,
        base,
        opened_by: req.headers["x-user-identity"] ?? "unknown"
    };

    pulls.set(pullKey(owner, repo, String(number)), pull);
    res.status(201).json(pull);
});

// Backs the mcp_http "pull_by_number" resource template
// (pr://{owner}/{repo}/{number}) -- read-only, so no credential check here,
// mirroring petstore's read operations.
app.get("/repos/:owner/:repo/pulls/:number", (req, res) =>
{
    const { owner, repo, number } = req.params;
    const pull = pulls.get(pullKey(owner, repo, number));
    if (!pull)
    {
        res.status(404).json({ message: "pull request not found" });
        return;
    }

    res.json(pull);
});

app.listen(PORT, () => console.log(`ghapi mock listening on ${PORT}`));
