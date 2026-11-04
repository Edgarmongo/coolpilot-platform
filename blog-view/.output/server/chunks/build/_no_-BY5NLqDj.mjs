import { defineComponent, ref, withAsyncContext, computed, unref, mergeProps, useSSRContext } from 'vue';
import { ssrRenderAttrs, ssrInterpolate, ssrIncludeBooleanAttr, ssrRenderList } from 'vue/server-renderer';
import { _ as _export_sfc, u as useRoute, a as useRouter } from './server.mjs';
import { u as useApi } from './useApi-DfdFmTve.mjs';
import { u as useAsyncData } from './asyncData-QV7p65wP.mjs';
import { u as useHead } from './v3-BgpqbPVE.mjs';
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
import 'perfect-debounce';

const _sfc_main = /* @__PURE__ */ defineComponent({
  __name: "[no]",
  __ssrInlineRender: true,
  async setup(__props) {
    var _a;
    let __temp, __restore;
    const route = useRoute();
    useRouter();
    const { call, isLogin } = useApi();
    const buying = ref(false);
    const owned = ref(false);
    const { data } = ([__temp, __restore] = withAsyncContext(() => useAsyncData(`product-${route.params.no}`, () => call(`/product/${route.params.no}`))), __temp = await __temp, __restore(), __temp);
    const product = computed(() => {
      var _a2, _b;
      return ((_b = (_a2 = data.value) == null ? void 0 : _a2.data) == null ? void 0 : _b.product) || null;
    });
    const detailHtml = computed(() => {
      var _a2, _b;
      return ((_b = (_a2 = data.value) == null ? void 0 : _a2.data) == null ? void 0 : _b.detailHtml) || "";
    });
    const resources = computed(() => {
      var _a2, _b;
      return ((_b = (_a2 = data.value) == null ? void 0 : _a2.data) == null ? void 0 : _b.resources) || [];
    });
    if (product.value) {
      useHead({ title: `${product.value.title} - CoolPilot` });
      if (isLogin.value) {
        const access = ([__temp, __restore] = withAsyncContext(() => call(`/resource/access?productNo=${route.params.no}`).catch(() => null)), __temp = await __temp, __restore(), __temp);
        owned.value = !!((_a = access == null ? void 0 : access.data) == null ? void 0 : _a.accessible);
      }
    }
    function typeLabel(type) {
      return { COURSE: "\u89C6\u9891\u8BFE\u7A0B", DOC: "\u4ED8\u8D39\u6587\u6863", COLUMN: "\u77E5\u8BC6\u4E13\u680F", CODE: "\u4EE3\u7801\u8D44\u6E90", SERVICE: "\u6280\u672F\u670D\u52A1" }[type] || type;
    }
    function price(value) {
      return Number(value || 0).toFixed(2).replace(/\.00$/, "");
    }
    return (_ctx, _push, _parent, _attrs) => {
      var _a2;
      if (unref(product)) {
        _push(`<div${ssrRenderAttrs(mergeProps({ class: "card" }, _attrs))} data-v-ca556b59><div class="head" data-v-ca556b59><div class="head-main" data-v-ca556b59><span class="tag" data-v-ca556b59>${ssrInterpolate(typeLabel(unref(product).productType))}</span><h1 class="page-title" data-v-ca556b59>${ssrInterpolate(unref(product).title)}</h1><p class="page-desc" data-v-ca556b59>${ssrInterpolate(unref(product).subtitle)}</p><div class="post-meta" data-v-ca556b59><span data-v-ca556b59>${ssrInterpolate(unref(product).salesCount || 0)} \u4EBA\u5DF2\u8D2D</span><span data-v-ca556b59>\xB7 ${ssrInterpolate(unref(product).viewCount || 0)} \u6B21\u6D4F\u89C8</span></div></div><div class="buy" data-v-ca556b59><div class="price" data-v-ca556b59>\xA5${ssrInterpolate(price(unref(product).price))}</div><button class="btn buy-btn"${ssrIncludeBooleanAttr(unref(buying)) ? " disabled" : ""} data-v-ca556b59>${ssrInterpolate(unref(owned) ? "\u5DF2\u62E5\u6709" : unref(buying) ? "\u5904\u7406\u4E2D\u2026" : "\u7ACB\u5373\u8D2D\u4E70")}</button><p class="muted tip" data-v-ca556b59>\u4EBA\u5DE5\u786E\u8BA4\u5230\u8D26\u540E\u81EA\u52A8\u5F00\u901A</p></div></div><div class="article" data-v-ca556b59>${(_a2 = unref(detailHtml)) != null ? _a2 : ""}</div>`);
        if (unref(resources).length) {
          _push(`<div class="resources" data-v-ca556b59><h3 data-v-ca556b59>\u914D\u5957\u8D44\u6E90</h3><!--[-->`);
          ssrRenderList(unref(resources), (item) => {
            _push(`<p class="resource" data-v-ca556b59><span data-v-ca556b59>${ssrInterpolate(item.displayName)}</span><span class="muted" data-v-ca556b59>${ssrInterpolate(item.version)}${ssrInterpolate(unref(owned) ? "" : " \xB7 \u8D2D\u4E70\u540E\u53EF\u4E0B\u8F7D")}</span></p>`);
          });
          _push(`<!--]--></div>`);
        } else {
          _push(`<!---->`);
        }
        _push(`</div>`);
      } else {
        _push(`<div${ssrRenderAttrs(mergeProps({ class: "empty card" }, _attrs))} data-v-ca556b59>\u5546\u54C1\u4E0D\u5B58\u5728\u6216\u5DF2\u4E0B\u67B6</div>`);
      }
    };
  }
});
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("pages/knowledge/[no].vue");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const _no_ = /* @__PURE__ */ _export_sfc(_sfc_main, [["__scopeId", "data-v-ca556b59"]]);

export { _no_ as default };
//# sourceMappingURL=_no_-BY5NLqDj.mjs.map
