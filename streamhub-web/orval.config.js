/** @type {import('orval').Config} */

/**
 * Stable, deterministic hook-name generation.
 *
 * Orval's default behaviour derives operation names from the backend
 * `operationId` (e.g. `list`, `detail_3`, `me`). Because those collide across
 * domains, orval appends auto-increment suffixes (`useList`, `useList2`, ...),
 * which shift every time a new endpoint is added and break existing imports.
 *
 * We replace that with a deterministic scheme based on the operation's
 * OpenAPI **tag** (domain) + **route** + **HTTP verb**, so the same endpoint
 * always produces the same name regardless of what else is added to the spec.
 *
 *   <domainPrefix><Action>
 *
 *   - domainPrefix: camelCase of the tag      ("Member Auth" -> memberAuth)
 *   - Action:       PascalCase of the static path segments (path params are
 *                   omitted, never interpolated into the name), with the
 *                   leading resource segment dropped when it duplicates the
 *                   domain. A CRUD verb suffix is added when the route has no
 *                   explicit action word:
 *                     GET  /x/{id}   -> Detail
 *                     GET  /x/foo    -> Foo            (collection / sub-resource)
 *                     POST /x        -> Create
 *                     PUT  /x/{id}   -> Update
 *                     DELETE /x/{id} -> Delete
 *
 * Examples: goodsList, orderDetail, dashboardSummary, contentList,
 *           subscriptionPlanUpdate, publicPosts, publicPostsDetail.
 */

const lcfirst = (s) => (s ? s[0].toLowerCase() + s.slice(1) : "");
const ucfirst = (s) => (s ? s[0].toUpperCase() + s.slice(1) : "");

// member-trend / member_trend -> MemberTrend ; index.m3u8 -> IndexM3u8
// Split on any non-alphanumeric run so generated identifiers never contain '.', '-', etc.
const pascalSeg = (s) =>
  s
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean)
    .map(ucfirst)
    .join("");

// "Member Auth" -> memberAuth ; "SubscriptionPlan" -> subscriptionPlan
const domainPrefix = (tag) =>
  tag
    .split(/\s+/)
    .map((w) => w.replace(/[^A-Za-z0-9]/g, ""))
    .map((w, i) => (i === 0 ? lcfirst(w) : ucfirst(w)))
    .join("");

const CRUD = {
  post: "Create",
  put: "Update",
  patch: "Update",
  delete: "Delete",
};

// Path segments that already describe the action -> no CRUD verb appended.
const ACTION_WORDS = new Set([
  "list",
  "upload",
  "bulk",
  "approve",
  "deny",
  "grant",
  "calendar",
  "once",
  "run-billing",
  "login",
  "logout",
  "refresh",
  "me",
  "channels",
  "categories",
  "feed",
  "summary",
  "timeseries",
  "member-trend",
  "top-contents",
  "watch-by-channel",
  "status",
  "tracking",
]);

// Route/version prefixes that carry no semantic meaning.
const DROP = new Set(["pub", "v1", "v2", "v3", "auth"]);

function operationName(operation, route, verb) {
  const tag = (operation.tags && operation.tags[0]) || "Api";
  const prefix = domainPrefix(tag);
  const tagPascal = ucfirst(prefix);
  const method = verb.toLowerCase();

  // NOTE: orval passes `route` already converted to template form, so a path
  // param arrives as `${id}` (not `{id}`). Match both forms defensively.
  const isParam = (s) => /^\$?\{.+\}$/.test(s);

  const segs = route.split("/").filter(Boolean);
  const lastRaw = segs[segs.length - 1] || "";
  const lastIsParam = isParam(lastRaw);
  const lastIsAction = !lastIsParam && ACTION_WORDS.has(lastRaw);

  // Static path segments only — path params are intentionally omitted from the
  // name (interpolating them would corrupt the generated identifier).
  const parts = [];
  for (const s of segs) {
    if (DROP.has(s)) continue;
    if (isParam(s)) continue;
    parts.push(pascalSeg(s));
  }
  // Drop the leading resource segment when it just repeats the domain prefix
  // (e.g. /v1/goods -> drop "Goods", keep cross-resource segments for tags
  // like Public that expose posts/contents/home under one domain).
  if (parts.length && parts[0] === tagPascal) parts.shift();

  let action;
  if (method === "get") {
    if (lastIsParam) {
      action = parts.join("") + "Detail"; // GET /x/{id}
    } else if (lastIsAction || parts.length) {
      action = parts.join(""); // GET /x/summary, GET /pub/contents
    } else {
      action = "Detail"; // GET /domain-root (rare)
    }
  } else if (lastIsParam || !lastIsAction) {
    action = parts.join("") + ucfirst(CRUD[method] || method);
  } else {
    action = parts.join(""); // explicit action word (list, upload, ...)
  }
  if (action === "") action = ucfirst(CRUD[method] || method);

  return lcfirst(prefix + ucfirst(action));
}

module.exports = {
  streamhub: {
    input: {
      target: "http://localhost:8080/v3/api-docs",
    },
    output: {
      mode: "tags-split",
      target: "src/apis/query",
      client: "react-query",
      prettier: true,
      override: {
        operationName,
        mutator: {
          path: "src/apis/custom-instance.ts",
          name: "customInstance",
        },
        query: {
          useQuery: true,
          useMutation: true,
        },
      },
    },
  },
};
