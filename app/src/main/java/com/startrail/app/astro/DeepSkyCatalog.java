package com.startrail.app.astro;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/** A small, curated offline catalogue of prominent deep-sky targets (J2000/ICRS). */
public final class DeepSkyCatalog {
    public static final String GALAXY = "galaxy";
    public static final String OPEN_CLUSTER = "open_cluster";
    public static final String GLOBULAR_CLUSTER = "globular_cluster";
    public static final String NEBULA = "nebula";
    public static final String PLANETARY_NEBULA = "planetary_nebula";

    private DeepSkyCatalog() {}

    public static final class Entry {
        public final Star star;
        public final String kind;
        /** Short, localized type label for display. */
        public final String label;
        /** Concise, practical observing advice; integrated magnitude is not point-source brightness. */
        public final String equipment;
        /** Approximate apparent major-axis diameter in arcminutes. */
        public final double angularSizeArcmin;

        private Entry(Star star, String kind, String label, String equipment,
                      double angularSizeArcmin) {
            this.star = star;
            this.kind = kind;
            this.label = label;
            this.equipment = equipment;
            this.angularSizeArcmin = angularSizeArcmin;
        }
    }

    private static Entry e(String id, String en, String zh, String constellation,
                           double ra, double dec, double magnitude, String description,
                           String kind, String label, String equipment, double size,
                           String... aliases) {
        return new Entry(new Star(id, en, zh, constellation, ra, dec, magnitude,
                description, aliases), kind, label, equipment, size);
    }

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
        e("m31", "Andromeda Galaxy", "仙女座星系", "仙女座", 0.7123, 41.2691, 3.44,
          "本星系群的大型旋涡星系；暗空中肉眼可见的只是明亮核心。", GALAXY, "星系",
          "暗空裸眼或双筒可见；约 3° 的暗弱盘面需要低倍率广视场，城市中通常只见核心。", 177.83,
          "M 31", "NGC 224", "Andromeda"),
        e("m33", "Triangulum Galaxy", "三角座星系", "三角座", 1.5641, 30.6602, 5.79,
          "本星系群的旋涡星系，整体亮度分散在很大的盘面上。", GALAXY, "星系",
          "需要很暗的天空；用双筒或低倍率望远镜扫视，切勿按总星等期待点状亮度。", 62.1,
          "M 33", "NGC 598", "Triangulum"),
        e("m42", "Orion Nebula", "猎户座大星云", "猎户座", 5.5879, -5.3897, 4.00,
          "猎户座剑鞘中的恒星形成区，核心环绕着年轻的四边形星团。", NEBULA, "星云",
          "裸眼可辨，双筒更佳；望远镜从低倍率开始，暗适应与避开月光能显著增加云气范围。", 90.0,
          "M 42", "NGC 1976", "Great Orion Nebula"),
        e("m45", "Pleiades", "昴星团", "金牛座", 3.7913, 24.1053, 1.20,
          "明亮而疏散的年轻星团，中国传统星官为昴。", OPEN_CLUSTER, "疏散星团",
          "裸眼和双筒最佳；范围约 2.5°，普通望远镜高倍率会装不下整个星团。", 150.0,
          "M 45", "Seven Sisters", "昴宿"),
        e("m44", "Beehive Cluster", "蜂巢星团", "巨蟹座", 8.6728, 19.6721, 3.10,
          "巨蟹座中心的大型疏散星团，又称鬼宿星团。", OPEN_CLUSTER, "疏散星团",
          "暗空肉眼呈雾斑，双筒最容易分解成员星；宜用低倍率广视场。", 108.6,
          "M 44", "NGC 2632", "Praesepe", "鬼宿星团"),
        e("m13", "Hercules Globular Cluster", "武仙座大星团", "武仙座", 16.6949, 36.4613, 5.80,
          "北天著名的球状星团，聚集着数十万颗古老恒星。", GLOBULAR_CLUSTER, "球状星团",
          "双筒可见朦胧光斑；中等口径望远镜在较高倍率下可开始分解外缘恒星。", 16.5,
          "M 13", "NGC 6205", "Great Hercules Cluster"),
        e("m22", "Sagittarius Cluster", "人马座球状星团", "人马座", 18.6067, -23.9034, 6.17,
          "视直径很大的明亮球状星团，位于银河中心方向。", GLOBULAR_CLUSTER, "球状星团",
          "双筒可见；低纬地区等它升高并避开雾霾，望远镜中等倍率可分解外缘。", 12.6,
          "M 22", "NGC 6656", "Sagittarius Cluster"),
        e("omega_cen", "Omega Centauri", "半人马座欧米伽", "半人马座", 13.4461, -47.4769, 5.33,
          "银河系最大、最明亮的球状星团之一，南天低倍率观测名胜。", GLOBULAR_CLUSTER, "球状星团",
          "南方暗空肉眼可见，双筒壮观；北纬较高地区可能永不升起或贴近地平线。", 27.0,
          "Omega Centauri", "ω Centauri", "NGC 5139", "Caldwell 80"),
        e("m57", "Ring Nebula", "环状星云", "天琴座", 18.8931, 33.0286, 8.80,
          "恒星晚期抛出的电离气体壳，是典型的行星状星云。", PLANETARY_NEBULA, "行星状星云",
          "小望远镜可见但外观很小；中高倍率和稳定视宁度有助看出环形，城市中仍可尝试。", 1.27,
          "M 57", "NGC 6720", "Ring Nebula"),
        e("m27", "Dumbbell Nebula", "哑铃星云", "狐狸座", 19.9934, 22.7210, 7.40,
          "范围较大的明亮行星状星云，轮廓常呈哑铃形。", PLANETARY_NEBULA, "行星状星云",
          "双筒在暗空可见；小望远镜低到中倍率更合适，窄带星云滤镜可提升对比度。", 6.7,
          "M 27", "NGC 6853", "Dumbbell Nebula"),
        e("m8", "Lagoon Nebula", "礁湖星云", "人马座", 18.0615, -24.3802, 5.80,
          "银河深处的大型发射星云和恒星形成区，内含年轻星团 NGC 6530。", NEBULA, "星云",
          "暗空双筒即可见；低倍率广视场最合适，星云滤镜可增强云气但不会抵消严重光害。", 45.0,
          "M 8", "NGC 6523", "Lagoon Nebula"),
        e("m20", "Trifid Nebula", "三叶星云", "人马座", 18.0450, -22.9719, 8.50,
          "兼有发射、反射和暗星云结构的恒星形成区。", NEBULA, "星云",
          "比邻近 M8 更暗，需暗空和低倍率；小口径常只见淡雾，照片色彩不代表目视效果。", 28.0,
          "M 20", "NGC 6514", "Trifid Nebula"),
        e("m51", "Whirlpool Galaxy", "涡状星系", "猎犬座", 13.4980, 47.1952, 8.36,
          "与伴星系 NGC 5195 相互作用的正面旋涡星系。", GALAXY, "星系",
          "暗空下小望远镜能见双核心；旋臂需要较大口径、良好暗适应和耐心侧视。", 13.71,
          "M 51", "NGC 5194", "Whirlpool Galaxy"),
        e("m81", "Bode's Galaxy", "波德星系", "大熊座", 9.9259, 69.0653, 6.92,
          "M81 星系群中明亮的大型旋涡星系。", GALAXY, "星系",
          "双筒暗空可见；望远镜低倍率易与 M82 同框，明亮核心不等于盘面同样醒目。", 21.63,
          "M 81", "NGC 3031", "Bode's Galaxy" )
    ));

    private static final Map<String, Entry> LOOKUP = buildLookup();

    public static List<Entry> entries() { return ENTRIES; }

    /** Exact lookup over stable ID, primary names and aliases; ignores case and whitespace. */
    public static Entry find(String id) {
        String key = normalize(id);
        if (key.isEmpty()) return null;
        return LOOKUP.get(key);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (!Character.isWhitespace(c)) result.append(c);
        }
        return result.toString();
    }

    private static Map<String, Entry> buildLookup() {
        Map<String, Entry> lookup = new HashMap<>();
        for (Entry entry : ENTRIES) {
            Star star = entry.star;
            lookup.put(normalize(star.id), entry);
            lookup.put(normalize(star.name), entry);
            lookup.put(normalize(star.chineseName), entry);
            for (String alias : star.aliases) lookup.put(normalize(alias), entry);
        }
        return Collections.unmodifiableMap(lookup);
    }
}
