/**
 * NextLauncher UUID Registration Worker
 * Uses Cloudflare KV for storage — no GitHub token needed.
 *
 * Endpoints:
 *   POST /register?uuid=<uuid>   Register a player UUID
 *   GET  /users                  Return JSON array of all registered UUIDs
 */

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const KV_KEY = "uuids";

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));

    const url = new URL(request.url);

    // GET /users — return full UUID list
    if (request.method === "GET" && url.pathname === "/users") {
      const users = await env.NL_USERS.get(KV_KEY, "json") || [];
      return cors(new Response(JSON.stringify(users), {
        headers: { "Content-Type": "application/json" }
      }));
    }

    // POST /register?uuid=<uuid>
    if (request.method === "POST") {
      let uuid = url.searchParams.get("uuid");
      if (!uuid) {
        try { uuid = (await request.json()).uuid; } catch (_) {}
      }

      if (!uuid || !UUID_REGEX.test(uuid)) {
        return cors(new Response(JSON.stringify({ error: "Invalid UUID" }), {
          status: 400, headers: { "Content-Type": "application/json" }
        }));
      }

      uuid = uuid.toLowerCase();

      const users = await env.NL_USERS.get(KV_KEY, "json") || [];

      if (users.includes(uuid)) {
        return cors(new Response(JSON.stringify({ status: "already_registered" }), {
          headers: { "Content-Type": "application/json" }
        }));
      }

      users.push(uuid);
      await env.NL_USERS.put(KV_KEY, JSON.stringify(users));

      return cors(new Response(JSON.stringify({ status: "registered", uuid }), {
        headers: { "Content-Type": "application/json" }
      }));
    }

    return cors(new Response("Not Found", { status: 404 }));
  }
};

function cors(response) {
  const r = new Response(response.body, response);
  r.headers.set("Access-Control-Allow-Origin", "*");
  r.headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  return r;
}
