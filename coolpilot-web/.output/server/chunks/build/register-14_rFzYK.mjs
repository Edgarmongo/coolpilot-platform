import { _ as __nuxt_component_0 } from './nuxt-link-DgbK9JsG.mjs';
import { defineComponent, ref, reactive, mergeProps, unref, withCtx, createTextVNode, useSSRContext } from 'vue';
import { ssrRenderAttrs, ssrRenderAttr, ssrIncludeBooleanAttr, ssrInterpolate, ssrRenderComponent } from 'vue/server-renderer';
import { _ as _export_sfc, a as useRouter } from './server.mjs';
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
  __name: "register",
  __ssrInlineRender: true,
  setup(__props) {
    useRouter();
    useApi();
    const busy = ref(false);
    const form = reactive({ username: "", password: "", nickname: "", email: "" });
    return (_ctx, _push, _parent, _attrs) => {
      const _component_NuxtLink = __nuxt_component_0;
      _push(`<div${ssrRenderAttrs(mergeProps({ class: "card login" }, _attrs))} data-v-c8f3fdd0><h1 class="page-title" data-v-c8f3fdd0>\u6CE8\u518C\u8BBF\u5BA2\u8D26\u53F7</h1><p class="page-desc" data-v-c8f3fdd0>\u4EC5\u7528\u4E8E\u6536\u85CF\u3001\u4E0B\u5355\u4E0E\u67E5\u770B\u5DF2\u8D2D\u5185\u5BB9</p><div class="form" data-v-c8f3fdd0><input${ssrRenderAttr("value", unref(form).username)} placeholder="\u7528\u6237\u540D" autocomplete="username" data-v-c8f3fdd0><input${ssrRenderAttr("value", unref(form).password)} type="password" placeholder="\u5BC6\u7801\uFF08\u81F3\u5C11 6 \u4F4D\uFF09" autocomplete="new-password" data-v-c8f3fdd0><input${ssrRenderAttr("value", unref(form).nickname)} placeholder="\u6635\u79F0\uFF08\u53EF\u9009\uFF09" data-v-c8f3fdd0><input${ssrRenderAttr("value", unref(form).email)} placeholder="\u90AE\u7BB1\uFF08\u53EF\u9009\uFF0C\u7528\u4E8E\u627E\u56DE\uFF09" data-v-c8f3fdd0><button class="btn"${ssrIncludeBooleanAttr(unref(busy)) ? " disabled" : ""} data-v-c8f3fdd0>${ssrInterpolate(unref(busy) ? "\u63D0\u4EA4\u4E2D\u2026" : "\u6CE8\u518C")}</button></div><p class="hint" data-v-c8f3fdd0>\u5DF2\u6709\u8D26\u53F7\uFF1F`);
      _push(ssrRenderComponent(_component_NuxtLink, {
        to: "/login",
        class: "link"
      }, {
        default: withCtx((_, _push2, _parent2, _scopeId) => {
          if (_push2) {
            _push2(`\u53BB\u767B\u5F55`);
          } else {
            return [
              createTextVNode("\u53BB\u767B\u5F55")
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
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("pages/register.vue");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const register = /* @__PURE__ */ _export_sfc(_sfc_main, [["__scopeId", "data-v-c8f3fdd0"]]);

export { register as default };
//# sourceMappingURL=register-14_rFzYK.mjs.map
