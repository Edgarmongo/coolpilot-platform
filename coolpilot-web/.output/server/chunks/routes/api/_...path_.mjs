import { c as defineEventHandler, u as useRuntimeConfig, g as getRouterParam, e as getMethod, f as getQuery, r as readBody, h as getHeader, i as setResponseStatus } from '../../_/nitro.mjs';
import 'node:http';
import 'node:https';
import 'node:events';
import 'node:buffer';
import 'node:fs';
import 'node:path';
import 'node:crypto';
import 'node:url';

const ____path_ = defineEventHandler(async (event) => {
  const config = useRuntimeConfig();
  const path = getRouterParam(event, "path") || "";
  const method = getMethod(event);
  const query = getQuery(event);
  const body = ["POST", "PUT", "PATCH"].includes(method) ? await readBody(event).catch(() => void 0) : void 0;
  const headers = {};
  const authorization = getHeader(event, "authorization");
  if (authorization) {
    headers.authorization = authorization;
  }
  const contentType = getHeader(event, "content-type");
  if (contentType) {
    headers["content-type"] = contentType;
  }
  const target = `${config.apiBase}/${path}`.replace(/([^:]\/)\/+/g, "$1");
  try {
    return await $fetch(target, { method, query, body, headers });
  } catch (error) {
    const response = error && error.response;
    if (response && response._data) {
      setResponseStatus(event, response.status || 500);
      return response._data;
    }
    setResponseStatus(event, 502);
    return { code: 502, msg: "\u540E\u7AEF\u670D\u52A1\u4E0D\u53EF\u7528", data: null };
  }
});

export { ____path_ as default };
//# sourceMappingURL=_...path_.mjs.map
