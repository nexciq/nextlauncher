/**
 * NextLauncher UUID Registration Worker (Cloudflare Workers)
 *
 * Setup (one time):
 *  1. npm install -g wrangler
 *  2. wrangler login
 *  3. wrangler deploy
 *  4. wrangler secret put GITHUB_TOKEN
 *     → paste a GitHub fine-grained PAT with "Contents: Read & Write" on nexciq/nextlauncher
 *
 * After deploy, set the Worker URL in:
 *  - NLModInstaller.REGISTER_URL  (launcher)
 *  - NLUserRegistry.REGISTER_URL  (mod)
 */

const GITHUB_OWNER = "nexciq";
const GITHUB_REPO  = "nextlauncher";
const FILE_PATH    = "nl-users.json";
const BRANCH       = "main";

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default {
  async fetch(request, env) {
    // CORS preflight
    if (request.method === "OPTIONS") {
      return cors(new Response(null, { status: 204 }));
    }

    if (request.method !== "POST") {
      return cors(new Response("Method Not Allowed", { status: 405 }));
    }

    // Parse UUID from query string or JSON body
    const url = new URL(request.url);
    let uuid = url.searchParams.get("uuid");

    if (!uuid) {
      try {
        const body = await request.json();
        uuid = body.uuid;
      } catch (_) {}
    }

    if (!uuid || !UUID_REGEX.test(uuid)) {
      return cors(new Response(JSON.stringify({ error: "Invalid UUID" }), {
        status: 400,
        headers: { "Content-Type": "application/json" }
      }));
    }

    uuid = uuid.toLowerCase();

    try {
      const token = env.GITHUB_TOKEN;
      const apiBase = `https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/contents/${FILE_PATH}`;

      // Fetch current file
      const getResp = await fetch(`${apiBase}?ref=${BRANCH}`, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/vnd.github+json",
          "User-Agent": "NextLauncher-Worker/1.0"
        }
      });

      let users = [];
      let sha = null;

      if (getResp.ok) {
        const data = await getResp.json();
        sha = data.sha;
        users = JSON.parse(atob(data.content.replace(/\n/g, "")));
      }

      // Already registered?
      if (users.includes(uuid)) {
        return cors(new Response(JSON.stringify({ status: "already_registered" }), {
          headers: { "Content-Type": "application/json" }
        }));
      }

      users.push(uuid);

      // Commit updated file
      const body = {
        message: `Register NL user ${uuid}`,
        content: btoa(JSON.stringify(users, null, 2) + "\n"),
        branch: BRANCH,
        ...(sha ? { sha } : {})
      };

      const putResp = await fetch(apiBase, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/vnd.github+json",
          "Content-Type": "application/json",
          "User-Agent": "NextLauncher-Worker/1.0"
        },
        body: JSON.stringify(body)
      });

      if (!putResp.ok) {
        const err = await putResp.text();
        return cors(new Response(JSON.stringify({ error: "GitHub API error", detail: err }), {
          status: 502,
          headers: { "Content-Type": "application/json" }
        }));
      }

      return cors(new Response(JSON.stringify({ status: "registered", uuid }), {
        headers: { "Content-Type": "application/json" }
      }));

    } catch (e) {
      return cors(new Response(JSON.stringify({ error: e.message }), {
        status: 500,
        headers: { "Content-Type": "application/json" }
      }));
    }
  }
};

function cors(response) {
  const r = new Response(response.body, response);
  r.headers.set("Access-Control-Allow-Origin", "*");
  r.headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  r.headers.set("Access-Control-Allow-Headers", "Content-Type");
  return r;
}
