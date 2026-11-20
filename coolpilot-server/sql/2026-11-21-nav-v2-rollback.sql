-- 回滚：恢复 v1 导航
UPDATE nav_menu SET visible = 1 WHERE path = '/topic';
UPDATE nav_menu SET visible = 1 WHERE path = '/projects';
UPDATE nav_menu SET visible = 0, label = '关于', label_en = 'About' WHERE path = '/about';
UPDATE nav_menu SET label = '开源商业观察', label_en = 'Insights' WHERE path = '/open-source';
