-- 首页 v2：导航收敛为 6 项（首页 / 技术文章 / 开源项目 / 知识产品 / 技术服务 / 关于我）
-- 技术专题与项目保留页面，只是不再占用一级导航
UPDATE nav_menu SET visible = 0 WHERE path = '/topic';
UPDATE nav_menu SET visible = 0 WHERE path = '/projects';
UPDATE nav_menu SET visible = 1, label = '关于我', label_en = 'About' WHERE path = '/about';
UPDATE nav_menu SET label = '开源项目', label_en = 'Open Source' WHERE path = '/open-source';
