import { _ as __nuxt_component_0 } from './nuxt-link-DgbK9JsG.mjs';
import { defineComponent, ref, reactive, mergeProps, unref, withCtx, createTextVNode, useSSRContext } from 'vue';
import { ssrRenderAttrs, ssrRenderAttr, ssrIncludeBooleanAttr, ssrInterpolate, ssrRenderComponent } from 'vue/server-renderer';
import { _ as _export_sfc, u as useRoute, a as useRouter } from './server.mjs';
import { u as useApi } from './useApi-DfdFmTve.mjs';
import '../_/nitro.mjs';
import 'node:http';
import 'node:https';
import 'node:events';
import 'node:buffer';
import 'node:fs';
import 'node:path';
import 'node:crypto';
import 'node:url';
import '../routes/renderer.mjs';
import 'vue-bundle-renderer/runtime';
import 'unhead/server';
import 'devalue';
import 'unhead/utils';
import 'unhead/plugins';
import 'vue-router';

const _sfc_main = /* @__PURE__ */ defineComponent({
  __name: "login",
  __ssrInlineRender: true,
  setup(__props) {
    useRoute();
    useRouter();
    useApi();
    const busy = ref(false);
    const form = reactive({ username: "", password: "" });
    return (_ctx, _push, _parent, _attrs) => {
      const _component_NuxtLink = __nuxt_component_0;
      _push(`<div${ssrRenderAttrs(mergeProps({ class: "card login" }, _attrs))} data-v-303b483e><h1 class="page-title" data-v-303b483e>\u8BBF\u5BA2\u767B\u5F55</h1><p class="page-desc" data-v-303b483e>\u767B\u5F55\u540E\u53EF\u6536\u85CF\u3001\u4E0B\u5355\u4E0E\u67E5\u770B\u5DF2\u8D2D\u5185\u5BB9</p><div class="form" data-v-303b483e><input${ssrRenderAttr("value", unref(form).username)} placeholder="\u7528\u6237\u540D" autocomplete="username" data-v-303b483e><input${ssrRenderAttr("value", unref(form).password)} type="password" placeholder="\u5BC6\u7801" autocomplete="current-password" data-v-303b483e><button class="btn"${ssrIncludeBooleanAttr(unref(busy)) ? " disabled" : ""} data-v-303b483e>${ssrInterpolate(unref(busy) ? "\u767B\u5F55\u4E2D\u2026" : "\u767B\u5F55")}</button></div><p class="hint" data-v-303b483e> \u8FD8\u6CA1\u6709\u8D26\u53F7\uFF1F`);
      _push(ssrRenderComponent(_component_NuxtLink, {
        to: "/register",
        class: "link"
      }, {
        default: withCtx((_, _push2, _parent2, _scopeId) => {
          if (_push2) {
            _push2(`\u6CE8\u518C\u4E00\u4E2A`);
          } else {
            return [
              createTextVNode("\u6CE8\u518C\u4E00\u4E2A")
            ];
          }
        }),
        _: 1
      }, _parent));
      _push(`</p></div>`);
    };
  }
});
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("pages/login.vue");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const login = /* @__PURE__ */ _export_sfc(_sfc_main, [["__scopeId", "data-v-303b483e"]]);

export { login as default };
//# sourceMappingURL=login-Cu_QE0Cz.mjs.map
