/**
 * NextLauncher UUID Registration Worker (Cloudflare Workers)
 * Uses GitHub Actions workflow_dispatch to update nl-users.json
 * so that only the workflow's GITHUB_TOKEN (with write access) touches the file.
 */

const GITHUB_OWNER = "nexciq";
const GITHUB_REPO  = "nextlauncher";
const WORKFLOW_ID  = "register-user.yml";
const BRANCH       = "main";

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));
    if (request.method !== "POST") return cors(new Response("Method Not Allowed", { status: 405 }));

    const url = new URL(request.url);
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

    try {
      // Trigger the GitHub Actions workflow to add the UUID
      const resp = await fetch(
        `https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/actions/workflows/${WORKFLOW_ID}/dispatches`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${env.GITHUB_TOKEN}`,
            Accept: "application/vnd.github+json",
            "Content-Type": "application/json",
            "User-Agent": "NextLauncher-Worker/1.0",
          },
          body: JSON.stringify({ ref: BRANCH, inputs: { uuid } }),
        }
      );

      if (resp.status === 204) {
        return cors(new Response(JSON.stringify({ status: "queued", uuid }), {
          headers: { "Content-Type": "application/json" }
        }));
      }

      const err = await resp.text();
      return cors(new Response(JSON.stringify({ error: "GitHub error", detail: err }), {
        status: 502, headers: { "Content-Type": "application/json" }
      }));

    } catch (e) {
      return cors(new Response(JSON.stringify({ error: e.message }), {
        status: 500, headers: { "Content-Type": "application/json" }
      }));
    }
  }
};

function cors(response) {
  const r = new Response(response.body, response);
  r.headers.set("Access-Control-Allow-Origin", "*");
  r.headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  return r;
}
