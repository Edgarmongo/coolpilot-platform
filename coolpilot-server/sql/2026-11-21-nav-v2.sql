-- 首页 v2：导航收敛为 6 项（首页 / 技术文章 / 开源项目 / 知识产品 / 技术服务 / 关于我）
-- 技术专题与项目保留页面，只是不再占用一级导航
UPDATE nav_menu SET visible = 0 WHERE path = '/topic';
UPDATE nav_menu SET visible = 0 WHERE path = '/projects';
-- 关于我原来是 sort=60，与技术服务的 60 撞车，靠 id 排会跑到技术服务前面；
-- 设为 100 让它稳定落在最后（与「首页…技术服务…关于我」的视觉顺序一致）
UPDATE nav_menu SET visible = 1, label = '关于我', label_en = 'About', sort = 100 WHERE path = '/about';
UPDATE nav_menu SET label = '开源项目', label_en = 'Open Source' WHERE path = '/open-source';
