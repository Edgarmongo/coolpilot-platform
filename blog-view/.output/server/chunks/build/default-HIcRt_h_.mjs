import { _ as __nuxt_component_0 } from './nuxt-link-DgbK9JsG.mjs';
import { defineComponent, mergeProps, withCtx, createVNode, createTextVNode, unref, useSSRContext } from 'vue';
import { ssrRenderAttrs, ssrRenderComponent, ssrInterpolate, ssrRenderSlot } from 'vue/server-renderer';
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
  __name: "default",
  __ssrInlineRender: true,
  setup(__props) {
    useRouter();
    const { isLogin, user } = useApi();
    return (_ctx, _push, _parent, _attrs) => {
      const _component_NuxtLink = __nuxt_component_0;
      _push(`<div${ssrRenderAttrs(mergeProps({ class: "shell" }, _attrs))} data-v-24755036><header class="header" data-v-24755036><div class="container header-inner" data-v-24755036>`);
      _push(ssrRenderComponent(_component_NuxtLink, {
        to: "/",
        class: "brand"
      }, {
        default: withCtx((_, _push2, _parent2, _scopeId) => {
          if (_push2) {
            _push2(`<span class="brand-mark" data-v-24755036${_scopeId}>CP</span><span class="brand-text" data-v-24755036${_scopeId}>CoolPilot</span>`);
          } else {
            return [
              createVNode("span", { class: "brand-mark" }, "CP"),
              createVNode("span", { class: "brand-text" }, "CoolPilot")
            ];
          }
        }),
        _: 1
      }, _parent));
      _push(`<nav class="nav" data-v-24755036>`);
      _push(ssrRenderComponent(_component_NuxtLink, {
        to: "/",
        class: "nav-item",
        "active-class": "is-active"
      }, {
        default: withCtx((_, _push2, _parent2, _scopeId) => {
          if (_push2) {
            _push2(`\u9996\u9875`);
          } else {
            return [
              createTextVNode("\u9996\u9875")
            ];
          }
        }),
        _: 1
      }, _parent));
      _push(ssrRenderComponent(_component_NuxtLink, {
        to: "/knowledge",
        class: "nav-item",
        "active-class": "is-active"
      }, {
        default: withCtx((_, _push2, _parent2, _scopeId) => {
          if (_push2) {
            _push2(`\u77E5\u8BC6\u4ED8\u8D39`);
          } else {
            return [
              createTextVNode("\u77E5\u8BC6\u4ED8\u8D39")
            ];
          }
        }),
        _: 1
      }, _parent));
      _push(ssrRenderComponent(_component_NuxtLink, {
        to: "/me",
        class: "nav-item",
        "active-class": "is-active"
      }, {
        default: withCtx((_, _push2, _parent2, _scopeId) => {
          if (_push2) {
            _push2(`\u6211\u7684`);
          } else {
            return [
              createTextVNode("\u6211\u7684")
            ];
          }
        }),
        _: 1
      }, _parent));
      _push(`</nav><div class="header-right" data-v-24755036>`);
      if (unref(isLogin)) {
        _push(`<!--[--><span class="who" data-v-24755036>${ssrInterpolate(unref(user) && (unref(user).nickname || unref(user).username))}</span><button class="link" data-v-24755036>\u9000\u51FA</button><!--]-->`);
      } else {
        _push(ssrRenderComponent(_component_NuxtLink, {
          to: "/login",
          class: "btn btn-sm"
        }, {
          default: withCtx((_, _push2, _parent2, _scopeId) => {
            if (_push2) {
              _push2(`\u767B\u5F55`);
            } else {
              return [
                createTextVNode("\u767B\u5F55")
              ];
            }
          }),
          _: 1
        }, _parent));
      }
      _push(`</div></div></header><main class="main" data-v-24755036><div class="container" data-v-24755036>`);
      ssrRenderSlot(_ctx.$slots, "default", {}, null, _push, _parent);
      _push(`</div></main><footer class="footer" data-v-24755036><div class="container" data-v-24755036><span data-v-24755036>CoolPilot \u6280\u672F\u5E73\u53F0</span><span class="dot" data-v-24755036>\xB7</span><span data-v-24755036>\u57FA\u4E8E NBlog \u4E8C\u6B21\u5F00\u53D1</span></div></footer></div>`);
    };
  }
});
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("layouts/default.vue");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const _default = /* @__PURE__ */ _export_sfc(_sfc_main, [["__scopeId", "data-v-24755036"]]);

export { _default as default };
//# sourceMappingURL=default-HIcRt_h_.mjs.map
