import { _ as __nuxt_component_0 } from './nuxt-link-DgbK9JsG.mjs';
import { defineComponent, withAsyncContext, computed, unref, withCtx, createVNode, toDisplayString, openBlock, createBlock, createCommentVNode, useSSRContext } from 'vue';
import { ssrRenderAttrs, ssrRenderList, ssrRenderComponent, ssrInterpolate } from 'vue/server-renderer';
import { u as useApi } from './useApi-DfdFmTve.mjs';
import { u as useAsyncData } from './asyncData-QV7p65wP.mjs';
import { _ as _export_sfc } from './server.mjs';
import '../_/nitro.mjs';
import 'node:http';
import 'node:https';
import 'node:events';
import 'node:buffer';
import 'node:fs';
import 'node:path';
import 'node:crypto';
import 'node:url';
import 'perfect-debounce';
import '../routes/renderer.mjs';
import 'vue-bundle-renderer/runtime';
import 'unhead/server';
import 'devalue';
import 'unhead/utils';
import 'unhead/plugins';
import 'vue-router';

const _sfc_main = /* @__PURE__ */ defineComponent({
  __name: "index",
  __ssrInlineRender: true,
  async setup(__props) {
    let __temp, __restore;
    const { call } = useApi();
    const { data } = ([__temp, __restore] = withAsyncContext(() => useAsyncData("products", () => call("/products?pageNum=1&pageSize=12"))), __temp = await __temp, __restore(), __temp);
    const products = computed(() => {
      var _a, _b;
      return ((_b = (_a = data.value) == null ? void 0 : _a.data) == null ? void 0 : _b.list) || [];
    });
    function typeLabel(type) {
      return { COURSE: "\u89C6\u9891\u8BFE\u7A0B", DOC: "\u4ED8\u8D39\u6587\u6863", COLUMN: "\u77E5\u8BC6\u4E13\u680F", CODE: "\u4EE3\u7801\u8D44\u6E90", SERVICE: "\u6280\u672F\u670D\u52A1" }[type] || type;
    }
    function price(value) {
      return Number(value || 0).toFixed(2).replace(/\.00$/, "");
    }
    return (_ctx, _push, _parent, _attrs) => {
      const _component_NuxtLink = __nuxt_component_0;
      _push(`<div${ssrRenderAttrs(_attrs)} data-v-aa2a7d77><h1 class="page-title" data-v-aa2a7d77>\u77E5\u8BC6\u4ED8\u8D39</h1><p class="page-desc" data-v-aa2a7d77>\u6587\u6863\u3001\u8BFE\u7A0B\u3001\u4E13\u680F\u4E0E\u4EE3\u7801\u8D44\u6E90\uFF0C\u8D2D\u4E70\u540E\u957F\u671F\u53EF\u770B</p>`);
      if (!unref(products).length) {
        _push(`<div class="empty card" data-v-aa2a7d77>\u6682\u65E0\u5546\u54C1</div>`);
      } else {
        _push(`<!---->`);
      }
      _push(`<div class="grid" data-v-aa2a7d77><!--[-->`);
      ssrRenderList(unref(products), (product) => {
        _push(ssrRenderComponent(_component_NuxtLink, {
          key: product.id,
          to: `/knowledge/${product.productNo}`,
          class: "card item"
        }, {
          default: withCtx((_, _push2, _parent2, _scopeId) => {
            if (_push2) {
              _push2(`<div class="item-head" data-v-aa2a7d77${_scopeId}><span class="tag" data-v-aa2a7d77${_scopeId}>${ssrInterpolate(typeLabel(product.productType))}</span>`);
              if (product.salesCount) {
                _push2(`<span class="muted" data-v-aa2a7d77${_scopeId}>${ssrInterpolate(product.salesCount)} \u4EBA\u5DF2\u8D2D</span>`);
              } else {
                _push2(`<!---->`);
              }
              _push2(`</div><h3 data-v-aa2a7d77${_scopeId}>${ssrInterpolate(product.title)}</h3><p class="item-desc" data-v-aa2a7d77${_scopeId}>${ssrInterpolate(product.summary || product.subtitle)}</p><div class="item-foot" data-v-aa2a7d77${_scopeId}><strong data-v-aa2a7d77${_scopeId}>\xA5${ssrInterpolate(price(product.price))}</strong>`);
              if (product.originalPrice && product.originalPrice > product.price) {
                _push2(`<s data-v-aa2a7d77${_scopeId}>\xA5${ssrInterpolate(price(product.originalPrice))}</s>`);
              } else {
                _push2(`<!---->`);
              }
              _push2(`</div>`);
            } else {
              return [
                createVNode("div", { class: "item-head" }, [
                  createVNode("span", { class: "tag" }, toDisplayString(typeLabel(product.productType)), 1),
                  product.salesCount ? (openBlock(), createBlock("span", {
                    key: 0,
                    class: "muted"
                  }, toDisplayString(product.salesCount) + " \u4EBA\u5DF2\u8D2D", 1)) : createCommentVNode("", true)
                ]),
                createVNode("h3", null, toDisplayString(product.title), 1),
                createVNode("p", { class: "item-desc" }, toDisplayString(product.summary || product.subtitle), 1),
                createVNode("div", { class: "item-foot" }, [
                  createVNode("strong", null, "\xA5" + toDisplayString(price(product.price)), 1),
                  product.originalPrice && product.originalPrice > product.price ? (openBlock(), createBlock("s", { key: 0 }, "\xA5" + toDisplayString(price(product.originalPrice)), 1)) : createCommentVNode("", true)
                ])
              ];
            }
          }),
          _: 2
        }, _parent));
      });
      _push(`<!--]--></div></div>`);
    };
  }
});
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("pages/knowledge/index.vue");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const index = /* @__PURE__ */ _export_sfc(_sfc_main, [["__scopeId", "data-v-aa2a7d77"]]);

export { index as default };
//# sourceMappingURL=index-Dw5L9Y8w.mjs.map
