package com.startrail.app.astro;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/** Offline selection of naked-eye stars. Coordinates are J2000/ICRS, rounded for chart display. */
public final class StarCatalog {
    private StarCatalog() {}

    private static Star s(String id, String en, String zh, String con, double ra, double dec, double mag) {
        return new Star(id, en, zh, con, ra, dec, mag,
                zh + "是" + con + "中的亮星，视星等约 " + mag + "。位置为 J2000 赤道坐标。");
    }
    private static Star i(String id, String en, String zh, String con, double ra, double dec, double mag,
                          String description, String... aliases) {
        return new Star(id, en, zh, con, ra, dec, mag, description, aliases);
    }

    private static final List<Star> STARS = Collections.unmodifiableList(Arrays.asList(
        i("sirius","Sirius","天狼星","大犬座",6.7525,-16.7161,-1.46,"夜空中最亮的恒星，也是距离太阳约 8.6 光年的近邻双星系统。它在冬季与参宿四、南河三组成醒目的冬季大三角。","大犬座α","Alpha Canis Majoris"),
        s("canopus","Canopus","老人星","船底座",6.3992,-52.6957,-0.74),
        s("rigil_kent","Rigil Kentaurus","南门二","半人马座",14.6601,-60.8339,-0.27),
        i("arcturus","Arcturus","大角星","牧夫座",14.2610,19.1824,-0.05,"北天最亮的恒星之一，是一颗橙色巨星。沿北斗七星斗柄的弧线延伸，常能找到它。","牧夫座α"),
        i("vega","Vega","织女一","天琴座",18.6156,38.7837,0.03,"夏季大三角中最亮的一角，也是天琴座的主星。中国民间通常称它为织女星。","织女星","天琴座α"),
        s("capella","Capella","五车二","御夫座",5.2782,45.9980,0.08),
        i("rigel","Rigel","参宿七","猎户座",5.2423,-8.2016,0.13,"猎户座西南角的蓝白色超巨星，光度远高于太阳。肉眼看似单星，实际上是多星系统。","猎户座β"),
        s("procyon","Procyon","南河三","小犬座",7.6550,5.2250,0.34),
        s("achernar","Achernar","水委一","波江座",1.6286,-57.2368,0.46),
        i("betelgeuse","Betelgeuse","参宿四","猎户座",5.9195,7.4071,0.50,"猎户座肩部的红超巨星，亮度会缓慢变化。它已进入恒星演化晚期，但无法据此预测何时发生超新星爆发。","猎户座α"),
        s("hadar","Hadar","马腹一","半人马座",14.0637,-60.3730,0.61),
        i("altair","Altair","河鼓二","天鹰座",19.8464,8.8683,0.77,"夏季大三角的一角，是一颗快速自转、形状略扁的近邻恒星。中国民间通常称它为牛郎星。","牛郎星","天鹰座α"),
        s("acrux","Acrux","十字架二","南十字座",12.4433,-63.0991,0.76),
        s("aldebaran","Aldebaran","毕宿五","金牛座",4.5987,16.5093,0.86),
        i("antares","Antares","心宿二","天蝎座",16.4901,-26.4320,0.96,"天蝎座心脏位置的红超巨星，橙红色十分醒目。英文名含有“火星的对手”之意，源于它与火星相似的颜色。","大火","天蝎座α"),
        s("spica","Spica","角宿一","室女座",13.4199,-11.1613,0.98),
        s("pollux","Pollux","北河三","双子座",7.7553,28.0262,1.14),
        s("fomalhaut","Fomalhaut","北落师门","南鱼座",22.9608,-29.6222,1.16),
        i("deneb","Deneb","天津四","天鹅座",20.6905,45.2803,1.25,"夏季大三角的一角，位于天鹅座尾部。它虽然距离遥远，却因本身光度极高而成为一等亮星。","天鹅座α"),
        s("mimosa","Mimosa","十字架三","南十字座",12.7953,-59.6888,1.25),
        s("imai","Imai","十字架四","南十字座",12.2524,-58.7489,2.79),
        s("regulus","Regulus","轩辕十四","狮子座",10.1395,11.9672,1.35),
        s("adhara","Adhara","弧矢七","大犬座",6.9771,-28.9721,1.50),
        s("castor","Castor","北河二","双子座",7.5767,31.8883,1.58),
        s("shaula","Shaula","尾宿八","天蝎座",17.5601,-37.1038,1.62),
        s("gacrux","Gacrux","十字架一","南十字座",12.5194,-57.1132,1.63),
        s("bellatrix","Bellatrix","参宿五","猎户座",5.4189,6.3497,1.64),
        s("elnath","Elnath","五车五","金牛座",5.4382,28.6075,1.65),
        s("miaplacidus","Miaplacidus","南船五","船底座",9.2200,-69.7172,1.67),
        s("alnilam","Alnilam","参宿二","猎户座",5.6036,-1.2019,1.69),
        s("alnair","Alnair","鹤一","天鹤座",22.1372,-46.9610,1.74),
        s("alnitak","Alnitak","参宿一","猎户座",5.6793,-1.9426,1.74),
        s("alioth","Alioth","玉衡","大熊座",12.9005,55.9598,1.76),
        s("mirfak","Mirfak","天船三","英仙座",3.4054,49.8612,1.79),
        i("dubhe","Dubhe","天枢","大熊座",11.0621,61.7508,1.79,"北斗七星斗口外侧的指极星之一。连接天璇到天枢并继续延伸，能帮助找到北极星。","北斗一","大熊座α","北斗七星"),
        s("wezen","Wezen","弧矢一","大犬座",7.1399,-26.3932,1.83),
        s("sargas","Sargas","尾宿五","天蝎座",17.6219,-42.9978,1.86),
        s("kaus_australis","Kaus Australis","箕宿三","人马座",18.4029,-34.3846,1.79),
        s("alkaid","Alkaid","摇光","大熊座",13.7923,49.3133,1.86),
        s("avior","Avior","海石一","船底座",8.3752,-59.5095,1.86),
        s("menkalinan","Menkalinan","五车三","御夫座",5.9921,44.9474,1.90),
        s("atria","Atria","三角形三","南三角座",16.8111,-69.0277,1.91),
        s("alhena","Alhena","井宿三","双子座",6.6285,16.3993,1.93),
        s("peacock","Peacock","孔雀十一","孔雀座",20.4275,-56.7351,1.94),
        s("mirzam","Mirzam","军市一","大犬座",6.3783,-17.9559,1.98),
        s("alphard","Alphard","星宿一","长蛇座",9.4598,-8.6586,1.98),
        i("polaris","Polaris","勾陈一","小熊座",2.5303,89.2641,1.98,"目前非常接近北天极，因此在北半球可用于辨认真北方向，其地平高度也近似观测地纬度。它本身是一个多星系统。","北极星","小熊座α"),
        s("hamal","Hamal","娄宿三","白羊座",2.1196,23.4624,2.00),
        s("diphda","Diphda","土司空","鲸鱼座",0.7265,-17.9866,2.02),
        s("nunki","Nunki","斗宿四","人马座",18.9211,-26.2967,2.05),
        s("menkent","Menkent","库楼三","半人马座",14.1114,-36.3700,2.06),
        s("alpheratz","Alpheratz","壁宿二","仙女座",0.1398,29.0904,2.06),
        s("algenib","Algenib","壁宿一","飞马座",0.2206,15.1836,2.83),
        s("kochab","Kochab","帝","小熊座",14.8451,74.1555,2.08),
        s("saiph","Saiph","参宿六","猎户座",5.7959,-9.6696,2.09),
        s("denebola","Denebola","五帝座一","狮子座",11.8177,14.5721,2.14),
        s("algol","Algol","大陵五","英仙座",3.1361,40.9556,2.12),
        s("mizar","Mizar","开阳","大熊座",13.3987,54.9254,2.23),
        s("merak","Merak","天璇","大熊座",11.0307,56.3824,2.37),
        s("phecda","Phecda","天玑","大熊座",11.8972,53.6948,2.44),
        s("megrez","Megrez","天权","大熊座",12.2570,57.0326,3.31),
        s("mintaka","Mintaka","参宿三","猎户座",5.5334,-0.2991,2.23),
        s("scheat","Scheat","室宿二","飞马座",23.0629,28.0828,2.42),
        s("markab","Markab","室宿一","飞马座",23.0794,15.2053,2.49),
        s("sadalmelik","Sadalmelik","危宿一","宝瓶座",22.0964,-0.3199,2.95),
        s("rasalhague","Rasalhague","候","蛇夫座",17.5822,12.5600,2.08),
        s("etamin","Eltanin","天棓四","天龙座",17.9434,51.4889,2.24),
        s("schedar","Schedar","王良四","仙后座",0.6751,56.5373,2.24),
        s("caph","Caph","王良一","仙后座",0.1529,59.1498,2.28),
        s("gamma_cas","Navi","策","仙后座",0.9451,60.7167,2.15),
        s("ruchbah","Ruchbah","阁道三","仙后座",1.4303,60.2353,2.68),
        s("segin","Segin","阁道二","仙后座",1.9066,63.6701,3.35),
        s("mirach","Mirach","奎宿九","仙女座",1.1622,35.6206,2.05),
        s("almach","Almach","天大将军一","仙女座",2.0649,42.3297,2.10),
        s("rasalgethi","Rasalgethi","帝座","武仙座",17.2441,14.3903,3.48),
        s("unukalhai","Unukalhai","蜀","巨蛇座",15.7378,6.4256,2.63),
        s("zubenelgenubi","Zubenelgenubi","氐宿一","天秤座",14.8479,-16.0418,2.75),
        s("zubeneschamali","Zubeneschamali","氐宿四","天秤座",15.2834,-9.3831,2.61),
        s("enif","Enif","危宿三","飞马座",21.7364,9.8750,2.39),
        s("sadr","Sadr","天津一","天鹅座",20.3705,40.2567,2.23),
        s("albireo","Albireo","辇道增七","天鹅座",19.5120,27.9597,3.05),
        s("gienah_cyg","Gienah","天津九","天鹅座",20.7702,33.9703,2.48),
        s("delta_cyg","Fawaris","天津二","天鹅座",19.7496,45.1308,2.87)
    ));

    private static ConstellationLine l(String c, String a, String b) { return new ConstellationLine(c,a,b); }
    private static final List<ConstellationLine> LINES = Collections.unmodifiableList(Arrays.asList(
        l("猎户座","betelgeuse","bellatrix"), l("猎户座","bellatrix","mintaka"),
        l("猎户座","mintaka","alnilam"), l("猎户座","alnilam","alnitak"),
        l("猎户座","betelgeuse","alnitak"), l("猎户座","alnitak","saiph"), l("猎户座","saiph","rigel"), l("猎户座","rigel","bellatrix"),
        l("大熊座","dubhe","merak"), l("大熊座","merak","phecda"), l("大熊座","phecda","megrez"),
        l("大熊座","megrez","alioth"), l("大熊座","alioth","mizar"), l("大熊座","mizar","alkaid"), l("大熊座","megrez","dubhe"),
        l("南十字座","acrux","gacrux"), l("南十字座","mimosa","imai"),
        l("仙女座","alpheratz","mirach"), l("仙女座","mirach","almach"),
        l("飞马座","alpheratz","scheat"), l("飞马座","scheat","markab"), l("飞马座","markab","algenib"), l("飞马座","algenib","alpheratz"),
        l("天鹅座","deneb","sadr"), l("天鹅座","sadr","albireo"), l("天鹅座","sadr","gienah_cyg"), l("天鹅座","sadr","delta_cyg"),
        l("仙后座","caph","schedar"), l("仙后座","schedar","gamma_cas"), l("仙后座","gamma_cas","ruchbah"), l("仙后座","ruchbah","segin")
    ));

    public static List<Star> stars() { return STARS; }
    public static List<ConstellationLine> lines() { return LINES; }

    public static Star findById(String id) {
        for (Star star : STARS) if (star.id.equals(id)) return star;
        return null;
    }

    /** Case-insensitive search over IDs, primary names, constellation and common aliases. */
    public static List<Star> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return STARS;
        List<Star> matches = new ArrayList<>();
        for (Star star : STARS) {
            if (contains(star.id, q) || contains(star.name, q) || contains(star.chineseName, q)
                    || contains(star.constellation, q)) {
                matches.add(star);
                continue;
            }
            for (String alias : star.aliases) if (contains(alias, q)) { matches.add(star); break; }
        }
        return Collections.unmodifiableList(matches);
    }

    private static boolean contains(String text, String lowerQuery) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }
}
