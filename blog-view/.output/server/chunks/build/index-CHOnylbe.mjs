import { _ as __nuxt_component_0 } from './nuxt-link-DgbK9JsG.mjs';
import { f as formatDate } from './useFormat-DUGbikLg.mjs';
import { defineComponent, withAsyncContext, computed, withCtx, createTextVNode, unref, createVNode, toDisplayString, openBlock, createBlock, createCommentVNode, useSSRContext } from 'vue';
import { ssrRenderAttrs, ssrRenderComponent, ssrInterpolate, ssrRenderList } from 'vue/server-renderer';
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
    const { data: blogRes } = ([__temp, __restore] = withAsyncContext(() => useAsyncData("home-blogs", () => call("/blogs?pageNum=1"))), __temp = await __temp, __restore(), __temp);
    const { data: productRes } = ([__temp, __restore] = withAsyncContext(() => useAsyncData("home-products", () => call("/products?pageNum=1&pageSize=1"))), __temp = await __temp, __restore(), __temp);
    const posts = computed(() => {
      var _a, _b;
      return ((_b = (_a = blogRes.value) == null ? void 0 : _a.data) == null ? void 0 : _b.list) || [];
    });
    const blogTotal = computed(() => {
      var _a, _b;
      return ((_b = (_a = blogRes.value) == null ? void 0 : _a.data) == null ? void 0 : _b.total) || posts.value.length;
    });
    const productTotal = computed(() => {
      var _a, _b;
      return ((_b = (_a = productRes.value) == null ? void 0 : _a.data) == null ? void 0 : _b.total) || 0;
    });
    return (_ctx, _push, _parent, _attrs) => {
      const _component_NuxtLink = __nuxt_component_0;
      _push(`<div${ssrRenderAttrs(_attrs)} data-v-b18bddcf><section class="hero card" data-v-b18bddcf><div class="hero-main" data-v-b18bddcf><span class="tag" data-v-b18bddcf>CoolPilot \u6280\u672F\u5E73\u53F0</span><h1 data-v-b18bddcf>\u628A\u6280\u672F\u5199\u6210\u53EF\u4EE5\u4EA4\u4ED8\u7684\u4E1C\u897F</h1><p data-v-b18bddcf>\u6587\u7AE0\u3001\u8BFE\u7A0B\u3001\u6587\u6863\u4E0E\u4EE3\u7801\u8D44\u6E90\uFF0C\u4E00\u5957\u8D26\u53F7\u6253\u901A\u9605\u8BFB\u3001\u8D2D\u4E70\u4E0E\u4E0B\u8F7D\u3002</p><div class="hero-actions" data-v-b18bddcf>`);
      _push(ssrRenderComponent(_component_NuxtLink, {
        to: "/knowledge",
        class: "btn"
      }, {
        default: withCtx((_, _push2, _parent2, _scopeId) => {
          if (_push2) {
            _push2(`\u770B\u770B\u77E5\u8BC6\u4ED8\u8D39`);
          } else {
            return [
              createTextVNode("\u770B\u770B\u77E5\u8BC6\u4ED8\u8D39")
            ];
          }
        }),
        _: 1
      }, _parent));
      _push(`<a class="btn btn-ghost" href="#latest" data-v-b18bddcf>\u6700\u65B0\u6587\u7AE0</a></div></div><div class="hero-side" data-v-b18bddcf><div class="metric" data-v-b18bddcf><strong data-v-b18bddcf>${ssrInterpolate(unref(blogTotal))}</strong><span data-v-b18bddcf>\u7BC7\u6587\u7AE0</span></div><div class="metric" data-v-b18bddcf><strong data-v-b18bddcf>${ssrInterpolate(unref(productTotal))}</strong><span data-v-b18bddcf>\u4E2A\u77E5\u8BC6\u5546\u54C1</span></div></div></section><section id="latest" class="section" data-v-b18bddcf><div class="section-head" data-v-b18bddcf><h2 data-v-b18bddcf>\u6700\u65B0\u6587\u7AE0</h2></div><div class="card" data-v-b18bddcf>`);
      if (!unref(posts).length) {
        _push(`<div class="empty" data-v-b18bddcf>\u8FD8\u6CA1\u6709\u6587\u7AE0</div>`);
      } else {
        _push(`<!---->`);
      }
      _push(`<!--[-->`);
      ssrRenderList(unref(posts), (post) => {
        _push(ssrRenderComponent(_component_NuxtLink, {
          key: post.id,
          to: `/blog/${post.id}`,
          class: "post-item"
        }, {
          default: withCtx((_, _push2, _parent2, _scopeId) => {
            if (_push2) {
              _push2(`<h3 class="post-title" data-v-b18bddcf${_scopeId}>${ssrInterpolate(post.title)}</h3><div class="post-meta" data-v-b18bddcf${_scopeId}><span data-v-b18bddcf${_scopeId}>${ssrInterpolate(("formatDate" in _ctx ? _ctx.formatDate : unref(formatDate))(post.createTime, "YYYY-MM-DD"))}</span>`);
              if (post.category) {
                _push2(`<span data-v-b18bddcf${_scopeId}>\xB7 ${ssrInterpolate(post.category.name)}</span>`);
              } else {
                _push2(`<!---->`);
              }
              _push2(`<span data-v-b18bddcf${_scopeId}>\xB7 ${ssrInterpolate(post.views || 0)} \u6B21\u9605\u8BFB</span></div>`);
            } else {
              return [
                createVNode("h3", { class: "post-title" }, toDisplayString(post.title), 1),
                createVNode("div", { class: "post-meta" }, [
                  createVNode("span", null, toDisplayString(("formatDate" in _ctx ? _ctx.formatDate : unref(formatDate))(post.createTime, "YYYY-MM-DD")), 1),
                  post.category ? (openBlock(), createBlock("span", { key: 0 }, "\xB7 " + toDisplayString(post.category.name), 1)) : createCommentVNode("", true),
                  createVNode("span", null, "\xB7 " + toDisplayString(post.views || 0) + " \u6B21\u9605\u8BFB", 1)
                ])
              ];
            }
          }),
          _: 2
        }, _parent));
      });
      _push(`<!--]--></div></section></div>`);
    };
  }
});
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("pages/index.vue");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const index = /* @__PURE__ */ _export_sfc(_sfc_main, [["__scopeId", "data-v-b18bddcf"]]);

export { index as default };
//# sourceMappingURL=index-CHOnylbe.mjs.map
