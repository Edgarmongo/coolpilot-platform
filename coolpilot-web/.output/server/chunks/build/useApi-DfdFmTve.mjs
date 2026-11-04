import { computed, ref } from 'vue';
import { F as klona, G as parse, H as getRequestHeader, d as destr, I as isEqual, J as setCookie, K as getCookie, L as deleteCookie } from '../_/nitro.mjs';
import { b as useNuxtApp } from './server.mjs';

function useRequestEvent(nuxtApp) {
  var _a;
  nuxtApp || (nuxtApp = useNuxtApp());
  return (_a = nuxtApp.ssrContext) == null ? void 0 : _a.event;
}
const CookieDefaults = {
  path: "/",
  watch: true,
  decode: (val) => {
    const decoded = decodeURIComponent(val);
    const parsed = destr(decoded);
    if (typeof parsed === "number" && (!Number.isFinite(parsed) || String(parsed) !== decoded)) {
      return decoded;
    }
    return parsed;
  },
  encode: (val) => encodeURIComponent(typeof val === "string" ? val : JSON.stringify(val))
};
function useCookie(name, _opts) {
  var _a, _b, _c;
  const opts = { ...CookieDefaults, ..._opts };
  (_a = opts.filter) != null ? _a : opts.filter = (key) => key === name;
  const cookies = readRawCookies(opts) || {};
  let delay;
  if (opts.maxAge !== void 0) {
    delay = opts.maxAge * 1e3;
  } else if (opts.expires) {
    delay = opts.expires.getTime() - Date.now();
  }
  const hasExpired = delay !== void 0 && delay <= 0;
  const cookieValue = klona(hasExpired ? void 0 : (_c = cookies[name]) != null ? _c : (_b = opts.default) == null ? void 0 : _b.call(opts));
  const cookie = ref(cookieValue);
  {
    const nuxtApp = useNuxtApp();
    const writeFinalCookieValue = () => {
      if (opts.readonly || isEqual(cookie.value, cookies[name])) {
        return;
      }
      nuxtApp._cookies || (nuxtApp._cookies = {});
      if (name in nuxtApp._cookies) {
        if (isEqual(cookie.value, nuxtApp._cookies[name])) {
          return;
        }
      }
      nuxtApp._cookies[name] = cookie.value;
      writeServerCookie(useRequestEvent(nuxtApp), name, cookie.value, opts);
    };
    const unhook = nuxtApp.hooks.hookOnce("app:rendered", writeFinalCookieValue);
    nuxtApp.hooks.hookOnce("app:error", () => {
      unhook();
      return writeFinalCookieValue();
    });
  }
  return cookie;
}
function readRawCookies(opts = {}) {
  {
    return parse(getRequestHeader(useRequestEvent(), "cookie") || "", opts);
  }
}
function writeServerCookie(event, name, value, opts = {}) {
  if (event) {
    if (value !== null && value !== void 0) {
      return setCookie(event, name, value, opts);
    }
    if (getCookie(event, name) !== void 0) {
      return deleteCookie(event, name, opts);
    }
  }
}
function useApi() {
  const token = useCookie("cp_token", {
    maxAge: 60 * 60 * 24 * 30,
    sameSite: "lax"
  });
  const user = useCookie("cp_user", {
    maxAge: 60 * 60 * 24 * 30,
    sameSite: "lax"
  });
  async function call(path, options = {}) {
    const headers = { ...options.headers || {} };
    if (token.value) {
      headers.authorization = token.value;
    }
    return await $fetch(`/api${path}`, { ...options, headers });
  }
  function setSession(data) {
    token.value = data.token;
    user.value = data.user;
  }
  function clearSession() {
    token.value = null;
    user.value = null;
  }
  const isLogin = computed(() => !!token.value);
  return { call, token, user, isLogin, setSession, clearSession };
}

export { useApi as u };
//# sourceMappingURL=useApi-DfdFmTve.mjs.map
