package com.kingzcheung.xime.glide

/**
 * 完整音节组成的字母前缀树。节点 0 是根，也代表一个恰好结束在两个音节之间的前缀。
 *
 * 音节表（421 个完整拼音音节）与结构移植自水杉输入法（msime，GPL-3.0）
 * 的 `crates/engine/src/pinyin/syllables.rs` 与 `glide.rs` 的 `SyllableTrie`。
 */
object GlideSyllableTrie : GlideSpellingConstraint {
    fun syllables(): List<String> = SYLLABLES.toList()

    private val SYLLABLES: Array<String> = arrayOf(
        "a", "ai", "an", "ang", "ao", "ba", "bai", "ban", "bang", "bao", "bei", "ben",
        "beng", "bi", "bian", "biang", "biao", "bie", "bin", "bing", "bo", "bu", "ca", "cai",
        "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan", "chang", "chao", "che",
        "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun",
        "chuo", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo", "da", "dai", "dan",
        "dang", "dao", "de", "dei", "den", "deng", "di", "dia", "dian", "diao", "die", "ding",
        "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo", "e", "ei", "en", "er",
        "fa", "fan", "fang", "fei", "fen", "feng", "fiao", "fo", "fou", "fu", "ga", "gai",
        "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai",
        "guan", "guang", "gui", "gun", "guo", "ha", "hai", "han", "hang", "hao", "he", "hei",
        "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
        "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan",
        "jue", "jun", "jv", "jve", "ka", "kai", "kan", "kang", "kao", "ke", "kei", "ken",
        "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo", "la",
        "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao",
        "lie", "lin", "ling", "liu", "lo", "long", "lou", "lu", "luan", "lue", "lun", "luo",
        "lv", "lve", "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi",
        "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu", "na", "nai", "nan",
        "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian", "niang", "niao", "nie", "nin",
        "ning", "niu", "nong", "nou", "nu", "nuan", "nue", "nun", "nuo", "nv", "nve", "o",
        "ou", "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao",
        "pie", "pin", "ping", "po", "pou", "pu", "qi", "qia", "qian", "qiang", "qiao", "qie",
        "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun", "qv", "qve", "ran", "rang",
        "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "ruan", "rui", "run", "ruo",
        "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan", "shang",
        "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang",
        "shui", "shun", "shuo", "si", "song", "sou", "su", "suan", "sui", "sun", "suo", "ta",
        "tai", "tan", "tang", "tao", "te", "teng", "ti", "tian", "tiao", "tie", "ting", "tong",
        "tou", "tu", "tuan", "tui", "tun", "tuo", "wa", "wai", "wan", "wang", "wei", "wen",
        "weng", "wo", "wu", "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong",
        "xiu", "xu", "xuan", "xue", "xun", "xv", "xve", "ya", "yan", "yang", "yao", "ye",
        "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun", "yv", "yve",
        "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai", "zhan",
        "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai",
        "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong", "zou", "zu", "zuan", "zui", "zun",
        "zuo",
    )

    /** 节点 0 是根。 */
    private val children = mutableListOf(IntArray(26))
    private val intact = mutableListOf(false)
    private val masks = mutableListOf(0)

    init {
        for (syllable in SYLLABLES) {
            var node = 0
            for (ch in syllable) {
                val slot = ch - 'a'
                if (children[node][slot] == 0) {
                    children.add(IntArray(26))
                    intact.add(false)
                    masks.add(0)
                    children[node][slot] = children.size - 1
                }
                node = children[node][slot]
            }
            intact[node] = true
        }
        for (i in children.indices) {
            var mask = 0
            for (letter in 0 until 26) if (children[i][letter] != 0) mask = mask or (1 shl letter)
            masks[i] = mask
        }
    }

    fun get(): GlideSyllableTrie = this

    /** states 里某种切分后面可以接的字母（位掩码）。 */
    override fun followers(states: List<Int>): Int {
        var mask = 0
        for (s in states) mask = mask or masks[s]
        return mask
    }

    /**
     * 把 letter 接在 states 所示切分之后，每种合法切分的状态写入 next：
     * 音节还能长时是长出来的那个节点，音节完整时是根 0。无合法切分时 next 为空。
     */
    override fun extend(states: List<Int>, letter: Int, next: MutableList<Int>) {
        next.clear()
        for (state in states) {
            val child = children[state][letter]
            if (child == 0) continue
            if (intact[child] && !next.contains(0)) next.add(0)
            if (children[child].any { it != 0 } && !next.contains(child)) next.add(child)
        }
    }
}
