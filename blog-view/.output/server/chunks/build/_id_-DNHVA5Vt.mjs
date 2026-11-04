import { f as formatDate } from './useFormat-DUGbikLg.mjs';
import { defineComponent, withAsyncContext, computed, mergeProps, unref, useSSRContext } from 'vue';
import { ssrRenderAttrs, ssrInterpolate } from 'vue/server-renderer';
import { _ as _export_sfc, u as useRoute } from './server.mjs';
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
  __name: "[id]",
  __ssrInlineRender: true,
  async setup(__props) {
    let __temp, __restore;
    const route = useRoute();
    const { call } = useApi();
    const { data } = ([__temp, __restore] = withAsyncContext(() => useAsyncData(`blog-${route.params.id}`, () => call(`/blog?id=${route.params.id}`))), __temp = await __temp, __restore(), __temp);
    const blog = computed(() => {
      var _a;
      return ((_a = data.value) == null ? void 0 : _a.data) || null;
    });
    if (blog.value) {
      useHead({ title: `${blog.value.title} - CoolPilot` });
    }
    return (_ctx, _push, _parent, _attrs) => {
      var _a;
      _push(`<div${ssrRenderAttrs(mergeProps({ class: "card" }, _attrs))} data-v-c1174ed3>`);
      if (unref(blog)) {
        _push(`<!--[--><h1 class="page-title" data-v-c1174ed3>${ssrInterpolate(unref(blog).title)}</h1><div class="post-meta meta" data-v-c1174ed3><span data-v-c1174ed3>${ssrInterpolate(("formatDate" in _ctx ? _ctx.formatDate : unref(formatDate))(unref(blog).createTime, "YYYY-MM-DD"))}</span>`);
        if (unref(blog).category) {
          _push(`<span data-v-c1174ed3>\xB7 ${ssrInterpolate(unref(blog).category.name)}</span>`);
        } else {
          _push(`<!---->`);
        }
        _push(`<span data-v-c1174ed3>\xB7 ${ssrInterpolate(unref(blog).views || 0)} \u6B21\u9605\u8BFB</span></div><div class="article" data-v-c1174ed3>${(_a = unref(blog).content) != null ? _a : ""}</div><!--]-->`);
      } else {
        _push(`<div class="empty" data-v-c1174ed3>\u6587\u7AE0\u4E0D\u5B58\u5728\u6216\u5DF2\u88AB\u5220\u9664</div>`);
      }
      _push(`</div>`);
    };
  }
});
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("pages/blog/[id].vue");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const _id_ = /* @__PURE__ */ _export_sfc(_sfc_main, [["__scopeId", "data-v-c1174ed3"]]);

export { _id_ as default };
//# sourceMappingURL=_id_-DNHVA5Vt.mjs.map
