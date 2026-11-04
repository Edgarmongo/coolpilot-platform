import { _ as __nuxt_component_0 } from './nuxt-link-DgbK9JsG.mjs';
import { f as formatDate } from './useFormat-DUGbikLg.mjs';
import { defineComponent, ref, withAsyncContext, unref, withCtx, createVNode, toDisplayString, useSSRContext } from 'vue';
import { ssrRenderAttrs, ssrRenderClass, ssrRenderList, ssrRenderComponent, ssrInterpolate } from 'vue/server-renderer';
import { u as useApi } from './useApi-DfdFmTve.mjs';
import { _ as _export_sfc, n as navigateTo } from './server.mjs';
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
  __name: "me",
  __ssrInlineRender: true,
  async setup(__props) {
    var _a;
    let __temp, __restore;
    const { call, isLogin } = useApi();
    const tab = ref("order");
    const orders = ref([]);
    const entitlements = ref([]);
    if (!isLogin.value) {
      [__temp, __restore] = withAsyncContext(() => navigateTo("/login?redirect=/me")), await __temp, __restore();
    } else {
      const [orderRes, entRes] = ([__temp, __restore] = withAsyncContext(() => Promise.all([
        call("/orders?pageNum=1&pageSize=50").catch(() => null),
        call("/entitlements").catch(() => null)
      ])), __temp = await __temp, __restore(), __temp);
      orders.value = ((_a = orderRes == null ? void 0 : orderRes.data) == null ? void 0 : _a.list) || [];
      entitlements.value = (entRes == null ? void 0 : entRes.data) || [];
    }
    function price(value) {
      return Number(value || 0).toFixed(2).replace(/\.00$/, "");
    }
    function statusText(status) {
      return {
        PENDING_PAY: "\u5F85\u652F\u4ED8",
        PENDING_CONFIRM: "\u5F85\u786E\u8BA4",
        PAID: "\u5DF2\u652F\u4ED8",
        COMPLETED: "\u5DF2\u5B8C\u6210",
        CANCELLED: "\u5DF2\u53D6\u6D88",
        CLOSED: "\u5DF2\u5173\u95ED",
        REFUNDING: "\u9000\u6B3E\u4E2D",
        REFUNDED: "\u5DF2\u9000\u6B3E"
      }[status] || status;
    }
    return (_ctx, _push, _parent, _attrs) => {
      const _component_NuxtLink = __nuxt_component_0;
      _push(`<div${ssrRenderAttrs(_attrs)} data-v-a8763c44><div class="head" data-v-a8763c44><div data-v-a8763c44><h1 class="page-title" data-v-a8763c44>\u6211\u7684</h1><p class="page-desc" data-v-a8763c44>\u8BA2\u5355\u4E0E\u5DF2\u8D2D\u5185\u5BB9</p></div></div><div class="tabs" data-v-a8763c44><button class="${ssrRenderClass(["tab", { active: unref(tab) === "order" }])}" data-v-a8763c44>\u8BA2\u5355\u8BB0\u5F55</button><button class="${ssrRenderClass(["tab", { active: unref(tab) === "ent" }])}" data-v-a8763c44>\u6211\u7684\u6743\u76CA</button></div><div class="card" data-v-a8763c44>`);
      if (unref(tab) === "order") {
        _push(`<!--[-->`);
        if (!unref(orders).length) {
          _push(`<div class="empty" data-v-a8763c44>\u8FD8\u6CA1\u6709\u8BA2\u5355</div>`);
        } else {
          _push(`<!---->`);
        }
        _push(`<!--[-->`);
        ssrRenderList(unref(orders), (order) => {
          _push(`<div class="item" data-v-a8763c44>`);
          _push(ssrRenderComponent(_component_NuxtLink, {
            to: `/order/${order.orderNo}`,
            class: "item-main"
          }, {
            default: withCtx((_, _push2, _parent2, _scopeId) => {
              if (_push2) {
                _push2(`<strong data-v-a8763c44${_scopeId}>${ssrInterpolate(order.titleSummary || order.orderNo)}</strong><span class="muted" data-v-a8763c44${_scopeId}>${ssrInterpolate(("formatDate" in _ctx ? _ctx.formatDate : unref(formatDate))(order.createTime))}</span>`);
              } else {
                return [
                  createVNode("strong", null, toDisplayString(order.titleSummary || order.orderNo), 1),
                  createVNode("span", { class: "muted" }, toDisplayString(("formatDate" in _ctx ? _ctx.formatDate : unref(formatDate))(order.createTime)), 1)
                ];
              }
            }),
            _: 2
          }, _parent));
          _push(`<div class="item-side" data-v-a8763c44><span class="amount" data-v-a8763c44>\xA5${ssrInterpolate(price(order.payAmount))}</span><span class="status" data-v-a8763c44>${ssrInterpolate(statusText(order.status))}</span></div></div>`);
        });
        _push(`<!--]--><!--]-->`);
      } else {
        _push(`<!--[-->`);
        if (!unref(entitlements).length) {
          _push(`<div class="empty" data-v-a8763c44>\u8FD8\u6CA1\u6709\u5DF2\u8D2D\u5185\u5BB9</div>`);
        } else {
          _push(`<!---->`);
        }
        _push(`<!--[-->`);
        ssrRenderList(unref(entitlements), (ent) => {
          _push(`<div class="item" data-v-a8763c44>`);
          _push(ssrRenderComponent(_component_NuxtLink, {
            to: `/knowledge/${ent.productNo}`,
            class: "item-main"
          }, {
            default: withCtx((_, _push2, _parent2, _scopeId) => {
              if (_push2) {
                _push2(`<strong data-v-a8763c44${_scopeId}>${ssrInterpolate(ent.productTitle)}</strong><span class="muted" data-v-a8763c44${_scopeId}>${ssrInterpolate(ent.expireTime ? "\u6709\u6548\u671F\u81F3 " + ("formatDate" in _ctx ? _ctx.formatDate : unref(formatDate))(ent.expireTime, "YYYY-MM-DD") : "\u6C38\u4E45\u6709\u6548")}</span>`);
              } else {
                return [
                  createVNode("strong", null, toDisplayString(ent.productTitle), 1),
                  createVNode("span", { class: "muted" }, toDisplayString(ent.expireTime ? "\u6709\u6548\u671F\u81F3 " + ("formatDate" in _ctx ? _ctx.formatDate : unref(formatDate))(ent.expireTime, "YYYY-MM-DD") : "\u6C38\u4E45\u6709\u6548"), 1)
                ];
              }
            }),
            _: 2
          }, _parent));
          _push(`<div class="item-side" data-v-a8763c44><span class="status" data-v-a8763c44>\u53EF\u8BBF\u95EE</span></div></div>`);
        });
        _push(`<!--]--><!--]-->`);
      }
      _push(`</div></div>`);
    };
  }
});
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("pages/me.vue");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const me = /* @__PURE__ */ _export_sfc(_sfc_main, [["__scopeId", "data-v-a8763c44"]]);

export { me as default };
//# sourceMappingURL=me-aeReZJ4z.mjs.map
