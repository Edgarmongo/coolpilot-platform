import { _ as __nuxt_component_0 } from './nuxt-link-DgbK9JsG.mjs';
import { defineComponent, ref, withAsyncContext, computed, unref, mergeProps, withCtx, createTextVNode, useSSRContext } from 'vue';
import { ssrRenderAttrs, ssrInterpolate, ssrRenderList, ssrRenderClass, ssrIncludeBooleanAttr, ssrRenderAttr, ssrRenderComponent } from 'vue/server-renderer';
import { _ as _export_sfc, u as useRoute, n as navigateTo } from './server.mjs';
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
  __name: "[orderNo]",
  __ssrInlineRender: true,
  async setup(__props) {
    let __temp, __restore;
    const route = useRoute();
    const { call, isLogin } = useApi();
    const busy = ref(false);
    const channel = ref("MANUAL_WECHAT");
    const channels = ref({});
    const payInfo = ref(null);
    const payerName = ref("");
    const transferTime = ref("");
    const order = ref(null);
    const items = ref([]);
    if (!isLogin.value) {
      [__temp, __restore] = withAsyncContext(() => navigateTo(`/login?redirect=${route.fullPath}`)), await __temp, __restore();
    } else {
      [__temp, __restore] = withAsyncContext(() => load()), await __temp, __restore();
      const channelRes = ([__temp, __restore] = withAsyncContext(() => call("/payment/channels").catch(() => null)), __temp = await __temp, __restore(), __temp);
      channels.value = channelRes && channelRes.data || {};
    }
    const productNo = computed(() => items.value.length ? items.value[0].productNo : "");
    async function load() {
      const res = await call(`/order/${route.params.orderNo}`);
      if (res.code === 200) {
        order.value = res.data.order;
        items.value = res.data.items || [];
      }
    }
    function statusText(status) {
      return {
        PENDING_PAY: "\u5F85\u652F\u4ED8",
        PENDING_CONFIRM: "\u5F85\u786E\u8BA4\u6536\u6B3E",
        PAID: "\u5DF2\u652F\u4ED8",
        COMPLETED: "\u5DF2\u5B8C\u6210",
        CANCELLED: "\u5DF2\u53D6\u6D88",
        CLOSED: "\u5DF2\u5173\u95ED",
        REFUNDING: "\u9000\u6B3E\u4E2D",
        REFUNDED: "\u5DF2\u9000\u6B3E"
      }[status] || status;
    }
    function price(value) {
      return Number(value || 0).toFixed(2).replace(/\.00$/, "");
    }
    return (_ctx, _push, _parent, _attrs) => {
      const _component_NuxtLink = __nuxt_component_0;
      if (unref(order)) {
        _push(`<div${ssrRenderAttrs(mergeProps({ class: "card" }, _attrs))} data-v-64923784><div class="head" data-v-64923784><div data-v-64923784><h1 class="page-title" data-v-64923784>\u6536\u94F6\u53F0</h1><p class="page-desc" data-v-64923784>\u8BA2\u5355 ${ssrInterpolate(unref(order).orderNo)}</p></div><span class="status" data-v-64923784>${ssrInterpolate(statusText(unref(order).status))}</span></div><div class="summary" data-v-64923784><!--[-->`);
        ssrRenderList(unref(items), (item) => {
          _push(`<div class="row" data-v-64923784><span data-v-64923784>${ssrInterpolate(item.titleSnapshot)}</span><span data-v-64923784>\xA5${ssrInterpolate(price(item.amount))}</span></div>`);
        });
        _push(`<!--]--><div class="row total" data-v-64923784><span data-v-64923784>\u5E94\u4ED8\u91D1\u989D</span><strong data-v-64923784>\xA5${ssrInterpolate(price(unref(order).payAmount))}</strong></div></div>`);
        if (unref(order).status === "PENDING_PAY") {
          _push(`<!--[-->`);
          if (!unref(payInfo)) {
            _push(`<div class="block" data-v-64923784><h3 data-v-64923784>\u9009\u62E9\u6536\u6B3E\u65B9\u5F0F</h3><div class="channels" data-v-64923784><!--[-->`);
            ssrRenderList(unref(channels), (name, code) => {
              _push(`<button class="${ssrRenderClass(["channel", { active: unref(channel) === code }])}" data-v-64923784>${ssrInterpolate(name)}</button>`);
            });
            _push(`<!--]--></div><button class="btn"${ssrIncludeBooleanAttr(unref(busy)) ? " disabled" : ""} data-v-64923784>${ssrInterpolate(unref(busy) ? "\u751F\u6210\u4E2D\u2026" : "\u751F\u6210\u6536\u6B3E\u4FE1\u606F")}</button></div>`);
          } else {
            _push(`<div class="block" data-v-64923784><h3 data-v-64923784>\u8BF7\u5B8C\u6210\u8F6C\u8D26</h3><ul class="info" data-v-64923784><li data-v-64923784><span data-v-64923784>\u6536\u6B3E\u65B9\u5F0F</span><b data-v-64923784>${ssrInterpolate(unref(payInfo).channelName)}</b></li><li data-v-64923784><span data-v-64923784>\u5E94\u4ED8\u91D1\u989D</span><b class="amount" data-v-64923784>\xA5${ssrInterpolate(price(unref(payInfo).amount))}</b></li><li data-v-64923784><span data-v-64923784>\u8F6C\u8D26\u5907\u6CE8</span><b data-v-64923784>${ssrInterpolate(unref(payInfo).transferRemark)}</b></li>`);
            if (unref(payInfo).payeeName) {
              _push(`<li data-v-64923784><span data-v-64923784>\u6536\u6B3E\u4EBA</span><b data-v-64923784>${ssrInterpolate(unref(payInfo).payeeName)}</b></li>`);
            } else {
              _push(`<!---->`);
            }
            _push(`</ul><p class="tip" data-v-64923784>${ssrInterpolate(unref(payInfo).tip)}</p><div class="proof" data-v-64923784><h4 data-v-64923784>\u5DF2\u8F6C\u8D26\uFF1F\u586B\u5199\u4ED8\u6B3E\u4FE1\u606F</h4><div class="form" data-v-64923784><input${ssrRenderAttr("value", unref(payerName))} placeholder="\u4ED8\u6B3E\u4EBA\u59D3\u540D" data-v-64923784><input${ssrRenderAttr("value", unref(transferTime))} type="datetime-local" data-v-64923784></div><button class="btn"${ssrIncludeBooleanAttr(unref(busy)) ? " disabled" : ""} data-v-64923784>\u63D0\u4EA4\u4ED8\u6B3E\u51ED\u8BC1</button></div></div>`);
          }
          _push(`<!--]-->`);
        } else if (unref(order).status === "PENDING_CONFIRM") {
          _push(`<div class="block" data-v-64923784><h3 data-v-64923784>\u5DF2\u6536\u5230\u4ED8\u6B3E\u4FE1\u606F</h3><p class="tip" data-v-64923784>\u6211\u4F1A\u5728 24 \u5C0F\u65F6\u5185\u6838\u5BF9\u5230\u8D26\uFF0C\u786E\u8BA4\u540E\u81EA\u52A8\u5F00\u901A\uFF0C\u65E0\u9700\u91CD\u590D\u63D0\u4EA4\u3002</p></div>`);
        } else if (unref(order).status === "COMPLETED") {
          _push(`<div class="block" data-v-64923784><h3 data-v-64923784>\u5F00\u901A\u6210\u529F</h3><p class="tip" data-v-64923784>\u6743\u76CA\u5DF2\u53D1\u653E\uFF0C\u53EF\u8FD4\u56DE\u5546\u54C1\u9875\u67E5\u770B\u5185\u5BB9\u3002</p>`);
          _push(ssrRenderComponent(_component_NuxtLink, {
            to: `/knowledge/${unref(productNo)}`,
            class: "btn"
          }, {
            default: withCtx((_, _push2, _parent2, _scopeId) => {
              if (_push2) {
                _push2(`\u53BB\u67E5\u770B\u5546\u54C1`);
              } else {
                return [
                  createTextVNode("\u53BB\u67E5\u770B\u5546\u54C1")
                ];
              }
            }),
            _: 1
          }, _parent));
          _push(`</div>`);
        } else {
          _push(`<div class="block" data-v-64923784><h3 data-v-64923784>${ssrInterpolate(statusText(unref(order).status))}</h3><p class="tip" data-v-64923784>\u8BE5\u8BA2\u5355\u5F53\u524D\u4E0D\u53EF\u652F\u4ED8\uFF0C\u5982\u6709\u7591\u95EE\u8BF7\u8054\u7CFB\u7AD9\u957F\u3002</p></div>`);
        }
        _push(`</div>`);
      } else {
        _push(`<div${ssrRenderAttrs(mergeProps({ class: "empty card" }, _attrs))} data-v-64923784>\u8BA2\u5355\u4E0D\u5B58\u5728</div>`);
      }
    };
  }
});
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("pages/order/[orderNo].vue");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const _orderNo_ = /* @__PURE__ */ _export_sfc(_sfc_main, [["__scopeId", "data-v-64923784"]]);

export { _orderNo_ as default };
//# sourceMappingURL=_orderNo_-EgBchzYl.mjs.map
