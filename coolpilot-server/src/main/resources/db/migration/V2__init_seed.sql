-- ============================================================
-- V2：基础数据（站点配置、管理员、关于页、定时任务、分类、标签）
--
-- 「站点能正常打开」的最小数据集：
--   site_setting  36 项 —— 站点名、标题后缀、备案、社交链接、收款码等
--   user          1 条  —— admin 账号（BCrypt）
--   about         4 项  —— 关于页标题/正文/音乐
--   schedule_job  2 条  —— 访客统计等定时任务
--   category/tag  3/1   —— 演示内容依赖
--
-- 演示文章（demo-data.sql）与演示商品（07-seed-product.sql）不进迁移：
-- 那是「开发时看着有内容」的数据，正式环境不该自动生成；
-- 需要的话手动执行 coolpilot-server/demo-data.sql。
--
-- 已有库通过 baseline-version=2 跳过本文件。
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO `user` VALUES (1,'Admin','$2a$10$4wnwMW8Z4Bn6wR4K1YlbquQunlHM/4rvudVBX8oyfx16xeVtI6i7C','Admin','/img/avatar.jpg','admin@coolskyai.com','2020-09-21 16:47:18','2020-09-21 16:47:22','ROLE_admin');
INSERT INTO `site_setting` VALUES (1,'blogName','博客名称','CoolSkyAI',1);
INSERT INTO `site_setting` VALUES (2,'webTitleSuffix','网页标题后缀',' - CoolSkyAI',1);
INSERT INTO `site_setting` VALUES (3,'footerImgTitle','页脚图片标题','手机看本站',1);
INSERT INTO `site_setting` VALUES (4,'footerImgUrl','页脚图片路径','/img/qr.png',1);
INSERT INTO `site_setting` VALUES (5,'copyright','Copyright','{\"title\":\"Copyright © 2026\",\"siteName\":\"COOLSKYAI\'S BLOG\"}',1);
INSERT INTO `site_setting` VALUES (6,'beian','ICP备案号','',1);
INSERT INTO `site_setting` VALUES (7,'reward','赞赏码','/img/reward.jpg',1);
INSERT INTO `site_setting` VALUES (8,'commentAdminFlag','博主评论标识','咕咕',1);
INSERT INTO `site_setting` VALUES (9,'playlistServer','播放器平台','netease',1);
INSERT INTO `site_setting` VALUES (10,'playlistId','播放器歌单','3071528549',1);
INSERT INTO `site_setting` VALUES (11,'avatar','头像','/img/avatar.jpg',2);
INSERT INTO `site_setting` VALUES (12,'name','昵称','CoolSkyAI',2);
INSERT INTO `site_setting` VALUES (13,'rollText','滚动个签','\"云鹤当归天，天不迎我妙木仙；\",\"游龙当归海，海不迎我自来也。\"',2);
INSERT INTO `site_setting` VALUES (14,'github','GitHub','https://github.com/Edgarmongo',2);
INSERT INTO `site_setting` VALUES (15,'telegram','Telegram','',2);
INSERT INTO `site_setting` VALUES (16,'qq','QQ','http://sighttp.qq.com/authd?IDKEY=',2);
INSERT INTO `site_setting` VALUES (17,'bilibili','bilibili','https://space.bilibili.com/',2);
INSERT INTO `site_setting` VALUES (18,'netease','网易云音乐','https://music.163.com/#/user/home?id=',2);
INSERT INTO `site_setting` VALUES (19,'email','email','mailto:admin@coolskyai.com',2);
INSERT INTO `site_setting` VALUES (20,'favorite','自定义','{\"title\":\"最喜欢的动漫 📺\",\"content\":\"异度侵入、春物语、NO GAME NO LIFE、实力至上主义的教室、辉夜大小姐、青春猪头少年不会梦到兔女郎学姐、路人女主、Re0、魔禁、超炮、俺妹、在下坂本、散华礼弥、OVERLORD、慎勇、人渣的本愿、白色相簿2、死亡笔记、DARLING in the FRANXX、鬼灭之刃\"}',2);
INSERT INTO `site_setting` VALUES (21,'favorite','自定义','{\"title\":\"最喜欢我的女孩子们 🤤\",\"content\":\"芙兰达、土间埋、食蜂操祈、佐天泪爷、樱岛麻衣、桐崎千棘、02、亚丝娜、高坂桐乃、五更琉璃、安乐冈花火、一色彩羽、英梨梨、珈百璃、时崎狂三、可儿那由多、和泉纱雾、早坂爱\"}',2);
INSERT INTO `site_setting` VALUES (22,'favorite','自定义','{\"title\":\"最喜欢玩的游戏 🎮\",\"content\":\"Stellaris、巫师、GTA、荒野大镖客、刺客信条、魔兽争霸、LOL、PUBG\"}',2);
INSERT INTO `site_setting` VALUES (23,'badge','徽标','{\"title\":\"本博客已开源于 GitHub\",\"url\":\"https://github.com/Edgarmongo/coolpilot-platform\",\"subject\":\"CoolSkyAI\",\"value\":\"Open Source\",\"color\":\"brightgreen\"}',3);
INSERT INTO `site_setting` VALUES (24,'badge','徽标','{\"title\":\"由 Spring Boot 强力驱动\",\"url\":\"https://spring.io/projects/spring-boot/\",\"subject\":\"Powered\",\"value\":\"Spring Boot\",\"color\":\"blue\"}',3);
INSERT INTO `site_setting` VALUES (25,'badge','徽标','{\"title\":\"Vue.js 客户端渲染\",\"url\":\"https://cn.vuejs.org/\",\"subject\":\"SPA\",\"value\":\"Vue.js\",\"color\":\"brightgreen\"}',3);
INSERT INTO `site_setting` VALUES (26,'badge','徽标','{\"title\":\"UI 框架 Semantic-UI\",\"url\":\"https://semantic-ui.com/\",\"subject\":\"UI\",\"value\":\"Semantic-UI\",\"color\":\"semantic-ui\"}',3);
INSERT INTO `site_setting` VALUES (27,'badge','徽标','{\"title\":\"阿里云提供服务器及域名相关服务\",\"url\":\"https://www.aliyun.com/\",\"subject\":\"VPS & DNS\",\"value\":\"Aliyun\",\"color\":\"blueviolet\"}',3);
INSERT INTO `site_setting` VALUES (28,'badge','徽标','{\"title\":\"静态资源托管于 GitHub\",\"url\":\"https://github.com/\",\"subject\":\"OSS\",\"value\":\"GitHub\",\"color\":\"github\"}',3);
INSERT INTO `site_setting` VALUES (29,'badge','徽标','{\"title\":\"jsDelivr 加速静态资源\",\"url\":\"https://www.jsdelivr.com/\",\"subject\":\"CDN\",\"value\":\"jsDelivr\",\"color\":\"orange\"}',3);
INSERT INTO `site_setting` VALUES (30,'badge','徽标','{\"color\":\"lightgray\",\"subject\":\"CC\",\"title\":\"本站点采用 CC BY 4.0 国际许可协议进行许可\",\"url\":\"https://creativecommons.org/licenses/by/4.0/\",\"value\":\"BY 4.0\"}',3);
INSERT INTO `site_setting` VALUES (31,'friendContent','友链页面信息','随机排序，不分先后。欢迎交换友链~(￣▽￣)~*\n\n* 昵称：CoolSkyAI\n* 一句话：游龙当归海，海不迎我自来也。\n* 网址：[https://coolskyai.com](https://coolskyai.com)\n* 头像URL：[https://coolskyai.com/img/avatar.jpg](https://coolskyai.com/img/avatar.jpg)\n\n仅凭个人喜好添加友链，请在收到我的回复邮件后再于贵站添加本站链接。原则上已添加的友链不会删除，如果你发现自己被移除了，恕不另行通知，只需和我一样做就好。\n\n',4);
INSERT INTO `site_setting` VALUES (32,'friendCommentEnabled','友链页面评论开关','1',4);
INSERT INTO `site_setting` VALUES (33,'wechatQr','å¾®ä¿¡æ”¶æ¬¾ç ','/img/wechat-qr.png',5);
INSERT INTO `site_setting` VALUES (34,'alipayQr','æ”¯ä»˜å®æ”¶æ¬¾ç ','/img/alipay-qr.png',5);
INSERT INTO `site_setting` VALUES (35,'paypalUrl','PayPal æ”¶æ¬¾é“¾æŽ¥','https://paypal.me/yourname',5);
INSERT INTO `site_setting` VALUES (36,'payeeName','æ”¶æ¬¾äººå§“å','',5);
INSERT INTO `about` VALUES (1,'title','标题','About CoolSkyAI');
INSERT INTO `about` VALUES (2,'musicId','网易云歌曲ID','423015580');
INSERT INTO `about` VALUES (3,'content','正文Markdown','');
INSERT INTO `about` VALUES (4,'commentEnabled','评论开关','true');
INSERT INTO `schedule_job` VALUES (1,'redisSyncScheduleTask','syncBlogViewsToDatabase','','0 0 1 * * ?',1,'每天凌晨一点，从Redis将博客浏览量同步到数据库','2020-11-17 23:45:42');
INSERT INTO `schedule_job` VALUES (2,'visitorSyncScheduleTask','syncVisitInfoToDatabase','','0 0 0 * * ?',1,'清空当天Redis访客标识，记录当天的PV和UV，更新当天所有访客的PV和最后访问时间，更新城市新增访客UV数','2021-02-05 08:14:28');
INSERT INTO `category` VALUES (3,'AI工具集');
INSERT INTO `category` VALUES (1,'文章');
INSERT INTO `category` VALUES (2,'知识库');
INSERT INTO `tag` VALUES (1,'AI','teal');
