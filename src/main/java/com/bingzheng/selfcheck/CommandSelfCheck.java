package com.bingzheng.selfcheck;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 指令层自检（只在 {@code -Dfabric.development=true} 时运行）。
 *
 * <p>做法：在模组初始化结束时，<b>手动触发一次命令注册回调</b>（三个监听器都只操作
 * 传进来的 dispatcher，不碰任何全局状态），从而在<b>不需要服务器、不需要玩家</b>的情况下
 * 拿到「真实注册出来的指令树」，再对它做<b>解析级</b>断言：
 * 哪些输入能解析成可执行命令、哪些必定被拒。
 *
 * <p>覆盖内容：三组命令是否真的挂在同一个 {@code /bingzhengmod} 根下、英文/拼音参数是否可用、
 * 中文参数是否被拒（这才是死代码清理的真实证据）、数值越界是否被拒、未知子命令是否被拒、
 * {@code *_debug} 的 OP 门槛是否生效、两条查询命令的参数对照表文案是否两种语言都在。
 *
 * <p><b>不覆盖</b>：命令真正的执行副作用（改数值、发聊天栏文本）—— 那属于实体层，
 * 本自检只到「解析通过、能定位到可执行命令」为止，不去执行它。
 */
public class CommandSelfCheck implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("bingzhengmod");

    /** 权限 0：普通玩家（非 OP）。 */
    private static final int LEVEL_GUEST = 0;
    /** 权限 4：创造模式单人存档 / 管理员。 */
    private static final int LEVEL_OP = 4;

    private static int passed;
    private static int failed;
    private static int skipped;
    private static final List<String> FAILURES = new ArrayList<>();

    /** 各组当前是否装载（由指令树探测得出，不依赖任何跨模块引用）。 */
    private static boolean kernelInstalled;
    private static boolean emotionInstalled;
    private static boolean wuzangInstalled;

    private static CommandDispatcher<ServerCommandSource> dispatcher;
    private static ServerCommandSource guest;
    private static ServerCommandSource op;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("fabric.development")) {
            return;
        }
        run();
    }

    public static void run() {
        passed = 0;
        failed = 0;
        skipped = 0;
        FAILURES.clear();

        dispatcher = new CommandDispatcher<>();
        CommandRegistrationCallback.EVENT.invoker()
                .register(dispatcher, null, CommandManager.RegistrationEnvironment.ALL);
        guest = source(LEVEL_GUEST);
        op = source(LEVEL_OP);

        // 用「指令树里有没有这一组」判断该拓展是否装载 ——
        // 纯调度器探测，不需要引用任何模块的类（拆包之后同样有效）。
        kernelInstalled = commandExists("yingwei");
        emotionInstalled = commandExists("emotion");
        wuzangInstalled = commandExists("wuzang");

        LOGGER.info("【病证·指令自检】开始，共 18 项。");
        checkRootMerged();
        checkYingwei();
        checkEmotionBusiness();
        checkFatigueBusiness();
        checkEmotionKeysEnglish();
        checkEmotionKeyChineseRejected();
        checkWuzangBusiness();
        checkWuzangZangEnglish();
        checkWuzangZangPinyin();
        checkWuzangZangChineseRejected();
        checkWuzangPartStrict();
        checkRangeRejected();
        checkUnknownRejected();
        checkDebugHiddenForGuest();
        checkFatigueWriteGated();
        checkDebugVisibleForOp();
        checkBusinessIgnoresPermission();
        checkUsageLangKeysPresent();

        for (String f : FAILURES) {
            LOGGER.error("【病证·指令自检】[FAIL] {}", f);
        }
        LOGGER.info("【病证·指令自检】完成：通过 {} 项，跳过 {} 项（对应拓展未装载），失败 {} 项。",
                passed, skipped, failed);
        if (failed > 0) {
            LOGGER.error("【病证·指令自检】存在 {} 项失败！", failed);
        }
    }

    // ---------------- 基础设施 ----------------

    /**
     * 构造一个只用于解析的指令源。
     *
     * <p>只用到它的权限等级（{@code hasPermissionLevel} 就是读那个 int 字段），
     * 其余参数（输出、世界、服务器、实体）全部可以留 null ——
     * 本自检只解析、不执行，永远不会碰这些字段。
     */
    private static ServerCommandSource source(int level) {
        return new ServerCommandSource(null, Vec3d.ZERO, new Vec2f(0.0F, 0.0F), null, level,
                "selfcheck", Text.literal("selfcheck"), null, null);
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            LOGGER.info("【病证·指令自检】[OK] {}", name);
        } else {
            failed++;
            FAILURES.add(name + " —— " + detail);
        }
    }

    /** 指令树里是否存在该路径（用于判断某拓展是否装载）。 */
    private static boolean commandExists(String path) {
        var node = dispatcher.getRoot().getChild("bingzhengmod");
        for (String segment : path.split(" ")) {
            node = node == null ? null : node.getChild(segment);
        }
        return node != null;
    }

    /** 该组未装载就记为「跳过」而不是「失败」—— 自检只对已装载的东西负责。 */
    private static boolean skipUnless(boolean installed, String name) {
        if (installed) {
            return true;
        }
        skipped++;
        LOGGER.info("【病证·指令自检】[SKIP] {}（对应拓展未装载）", name);
        return false;
    }

    /** @return null 表示解析成功且能定位到可执行命令；否则返回失败原因。 */
    private static String probe(ServerCommandSource source, String input) {
        ParseResults<ServerCommandSource> results = dispatcher.parse(input, source);
        if (!results.getExceptions().isEmpty()) {
            return "解析被拒：" + results.getExceptions().values().iterator().next().getMessage();
        }
        if (!results.getReader().getRemaining().isBlank()) {
            return "输入未被完全消费：\"" + results.getReader().getRemaining() + "\"";
        }
        if (results.getContext().getCommand() == null) {
            return "只解析到中间节点，没有可执行命令";
        }
        return null;
    }

    /** 逐个输入要求「必须解析成功」，返回第一个失败说明。 */
    private static String firstFailure(ServerCommandSource source, String... inputs) {
        for (String input : inputs) {
            String reason = probe(source, input);
            if (reason != null) {
                return "\"" + input + "\" → " + reason;
            }
        }
        return null;
    }

    /** 逐个输入要求「必须解析失败」，返回第一个竟然成功的说明。 */
    private static String firstUnexpectedSuccess(ServerCommandSource source, String... inputs) {
        for (String input : inputs) {
            if (probe(source, input) == null) {
                return "\"" + input + "\" 竟然解析成功了";
            }
        }
        return null;
    }

    // ---------------- 1 根节点合并 ----------------

    private static void checkRootMerged() {
        List<String> paths = new ArrayList<>();
        if (kernelInstalled) {
            paths.add("yingwei");
            paths.add("yingwei set");
        }
        if (emotionInstalled) {
            paths.add("emotion");
            paths.add("emotion set");
            paths.add("emotion add");
            paths.add("emotion clear");
            paths.add("emotion debug");
            paths.add("fatigue");
            paths.add("fatigue set spirit");
            paths.add("fatigue set body");
            paths.add("fatigue reset");
            paths.add("fatigue debug");
        }
        if (wuzangInstalled) {
            paths.add("wuzang");
            paths.add("wuzang set");
            paths.add("wuzang add");
            paths.add("wuzang clear");
            paths.add("wuzang debug");
        }
        var root = dispatcher.getRoot().getChild("bingzhengmod");
        StringBuilder missing = new StringBuilder();
        if (root == null) {
            check("三组命令挂在同一个 /bingzhengmod 根下（" + paths.size() + " 条子命令路径全部已注册）", false,
                    "根节点 /bingzhengmod 不存在");
            return;
        }
        for (String path : paths) {
            var node = root;
            for (String segment : path.split(" ")) {
                node = node == null ? null : node.getChild(segment);
            }
            if (node == null) {
                missing.append(path).append(' ');
            }
        }
        check("已装载的各组命令都挂在同一个 /bingzhengmod 根下（本次核对 " + paths.size() + " 条路径）",
                missing.length() == 0, "缺失：" + missing);
    }

    // ---------------- 2 内核营卫 ----------------

    private static void checkYingwei() {
        String bad = firstFailure(guest, "bingzhengmod yingwei", "bingzhengmod yingwei set 50",
                "bingzhengmod yingwei set 0", "bingzhengmod yingwei set 100");
        check("内核营卫：query 与 set（0-100 整数）解析正常", bad == null, bad);
    }

    // ---------------- 3-5 情志 ----------------

    private static void checkEmotionBusiness() {

        if (!skipUnless(emotionInstalled, "情志六条业务命令解析")) {
            return;
        }
        String bad = firstFailure(guest,
                "bingzhengmod emotion",
                "bingzhengmod emotion set anger 70",
                "bingzhengmod emotion add grief -10",
                "bingzhengmod emotion clear");
        check("情志：query / set / add / clear 四条业务命令解析正常", bad == null, bad);
    }

    /**
     * v1.08 指令重构新增：fatigue 组（无参查询 / set spirit|body / reset）。
     *
     * <p>神耗/体耗原先挂在 {@code emotion spirit|body} 下，重构后迁入 fatigue 组，
     * 因此这一项同时验证「新路径可用」与「fatigue 组确实从顶层调试命令升级为命令组」。
     */
    private static void checkFatigueBusiness() {

        if (!skipUnless(emotionInstalled, "疲劳组业务命令解析")) {
            return;
        }
        String bad = firstFailure(op,
                "bingzhengmod fatigue",
                "bingzhengmod fatigue set spirit 50",
                "bingzhengmod fatigue set body 50",
                "bingzhengmod fatigue reset");
        check("疲劳：query（无参）/ set spirit|body / reset 四条业务命令解析正常", bad == null, bad);
    }

    private static void checkEmotionKeysEnglish() {

        if (!skipUnless(emotionInstalled, "情志 7 个英文标识符解析")) {
            return;
        }
        String[] keys = { "anger", "joy", "worry", "pensiveness", "grief", "fear", "fright" };
        String[] inputs = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            inputs[i] = "bingzhengmod emotion set " + keys[i] + " 70";
        }
        String bad = firstFailure(guest, inputs);
        check("情志：7 个英文标识符全部可解析", bad == null, bad);
    }

    private static void checkEmotionKeyChineseRejected() {
        String bad = firstUnexpectedSuccess(guest,
                "bingzhengmod emotion set 怒 70",
                "bingzhengmod emotion set 喜 70",
                "bingzhengmod emotion add 悲 -10");
        check("情志：中文情志名（不加引号）必定报参数错误 —— 死分支删除后的真实行为",
                bad == null, bad);
    }

    // ---------------- 6-10 五脏 ----------------

    private static void checkWuzangBusiness() {

        if (!skipUnless(wuzangInstalled, "五脏四条业务命令解析")) {
            return;
        }
        String bad = firstFailure(guest,
                "bingzhengmod wuzang",
                "bingzhengmod wuzang set liver qi 30",
                "bingzhengmod wuzang add kidney blood -20",
                "bingzhengmod wuzang set heart blood 100",
                "bingzhengmod wuzang clear");
        check("五脏：query / set / add / clear 四条业务命令解析正常", bad == null, bad);
    }

    private static void checkWuzangZangEnglish() {

        if (!skipUnless(wuzangInstalled, "五脏 5 个英文脏名解析")) {
            return;
        }
        String bad = firstFailure(guest,
                "bingzhengmod wuzang set liver qi 30",
                "bingzhengmod wuzang set heart qi 30",
                "bingzhengmod wuzang set spleen qi 30",
                "bingzhengmod wuzang set lung qi 30",
                "bingzhengmod wuzang set kidney qi 30");
        check("五脏：5 个英文脏名全部可解析", bad == null, bad);
    }

    private static void checkWuzangZangPinyin() {

        if (!skipUnless(wuzangInstalled, "五脏 5 个拼音脏名解析")) {
            return;
        }
        String bad = firstFailure(guest,
                "bingzhengmod wuzang set gan qi 30",
                "bingzhengmod wuzang set xin qi 30",
                "bingzhengmod wuzang set pi qi 30",
                "bingzhengmod wuzang set fei qi 30",
                "bingzhengmod wuzang set shen qi 30");
        check("五脏：5 个拼音脏名（gan/xin/pi/fei/shen）全部可解析", bad == null, bad);
    }

    private static void checkWuzangZangChineseRejected() {
        String bad = firstUnexpectedSuccess(guest,
                "bingzhengmod wuzang set 肝 qi 30",
                "bingzhengmod wuzang set 心 qi 30",
                "bingzhengmod wuzang set 脾 qi 30");
        check("五脏：中文脏名（不加引号）必定报参数错误 —— 死分支删除后的真实行为",
                bad == null, bad);
    }

    /**
     * 第二个参数（属性）的行为：
     * <ul>
     *   <li>中文「气 / 血」：在<b>解析阶段</b>就被 Brigadier 拒掉（word() 不接受非 ASCII）；</li>
     *   <li>英文非法值 {@code xue / qixue}：能解析通过（它是自由 word 参数，
     *       数值语义校验写在命令体里），随后由命令体回一条「第二段只能是 qi 或 blood」并返回 0。</li>
     * </ul>
     *
     * <p>第二条**不在本次改动范围**：把 {@code part} 收紧成两个字面量节点可以改成解析期拒绝，
     * 但那会改动 12 条业务子命令的结构，与「完全不动」的要求冲突，因此保持现状并如实断言。
     */
    private static void checkWuzangPartStrict() {
        // 【v0.7.0 修复】本项断言的是「命令体如何拒绝非法属性」，因此必须先确认五脏在场：
        // 单开情志（不装五脏）时 wuzang 命令整条都不存在，解析期就失败，
        // 旧写法会把它误判成「失败方式不对」而报错。同区其余项用的是
        // firstUnexpectedSuccess（要求失败即可），因此在缺装时依然成立，无需加锁。
        // 注意 skipUnless 的语义：**已装载返回 true**，未装载返回 false（并记一次跳过）。
        if (!skipUnless(wuzangInstalled, "五脏：属性参数解析行为")) {
            return;
        }
        String badChinese = firstUnexpectedSuccess(guest,
                "bingzhengmod wuzang set liver 气 30",
                "bingzhengmod wuzang set liver 血 30");
        String badEnglish = firstFailure(guest,
                "bingzhengmod wuzang set liver xue 30",
                "bingzhengmod wuzang set liver qixue 30");
        check("五脏：属性参数的中文（气/血）解析期即被拒；英文非法值 xue/qixue 解析通过后由命令体拒绝",
                badChinese == null && badEnglish == null,
                (badChinese == null ? "" : badChinese) + (badEnglish == null ? "" : badEnglish));
    }

    // ---------------- 11-12 边界 ----------------

    private static void checkRangeRejected() {
        String bad = firstUnexpectedSuccess(guest,
                "bingzhengmod yingwei set 101",
                "bingzhengmod yingwei set -1",
                "bingzhengmod emotion set anger 150",
                "bingzhengmod emotion add anger 200",
                "bingzhengmod wuzang set liver qi 999",
                "bingzhengmod wuzang set liver qi -999");
        check("数值越界被参数类型当场拒绝（营卫 0-100 / 情志 0-100 / 增减 ±100 / 五脏 ±100）",
                bad == null, bad);
    }

    private static void checkUnknownRejected() {
        String bad = firstUnexpectedSuccess(guest,
                "bingzhengmod",
                "bingzhengmod nonsense",
                "bingzhengmod wuzang set liver",
                "bingzhengmod wuzang set",
                "bingzhengmod emotion set",
                "bingzhengmod emotion spirit 50",
                "bingzhengmod emotion body 50",
                "bingzhengmod emotion_debug",
                "bingzhengmod fatigue_debug",
                "bingzhengmod wuzang_debug");
        check("裸 /bingzhengmod、未知子命令、参数不全 一律报错", bad == null, bad);
    }

    // ---------------- 13-15 权限门槛 ----------------

    private static void checkDebugHiddenForGuest() {
        String bad = firstUnexpectedSuccess(guest,
                "bingzhengmod emotion debug",
                "bingzhengmod fatigue debug",
                "bingzhengmod wuzang debug");
        check("各组 debug 子命令对非 OP（权限 0）不可达 —— OP 2 级门槛生效", bad == null, bad);
    }

    /**
     * v0.9.9 新增：疲劳组的<b>写操作</b>（set / reset）对非 OP 不可达。
     *
     * <p>父节点 {@code fatigue} 本身就带 OP 门槛，Brigadier 解析时会连带拦住子节点；
     * 这一项额外把 set / reset 单拎出来断言，是为了守住「写操作必须是 OP」这条口径 ——
     * 日后若有人为了只读查询放开父节点，这条断言会立刻失败。
     */
    private static void checkFatigueWriteGated() {

        if (!skipUnless(emotionInstalled, "疲劳组写操作权限门槛")) {
            return;
        }
        String bad = firstUnexpectedSuccess(guest,
                "bingzhengmod fatigue",
                "bingzhengmod fatigue set spirit 50",
                "bingzhengmod fatigue reset");
        check("疲劳组（含 set / reset 写操作）对非 OP（权限 0）不可达 —— 多人服数值不可被绕过",
                bad == null, bad);
    }

    private static void checkDebugVisibleForOp() {
        List<String> debugCommands = new ArrayList<>();
        if (emotionInstalled) {
            debugCommands.add("bingzhengmod emotion debug");
            debugCommands.add("bingzhengmod fatigue debug");
        }
        if (wuzangInstalled) {
            debugCommands.add("bingzhengmod wuzang debug");
        }
        if (debugCommands.isEmpty()) {
            return;
        }
        String bad = firstFailure(op, debugCommands.toArray(new String[0]));
        check("*_debug 对 OP（权限 4）可达（开发环境下调试开关恒开；正式包的属性开关见 PROGRESS）",
                bad == null, bad);
    }

    private static void checkBusinessIgnoresPermission() {
        List<String> queries = new ArrayList<>();
        if (kernelInstalled) {
            queries.add("bingzhengmod yingwei");
        }
        if (emotionInstalled) {
            queries.add("bingzhengmod emotion");
        }
        if (wuzangInstalled) {
            queries.add("bingzhengmod wuzang");
        }
        String bad = firstFailure(guest, queries.toArray(new String[0]));
        check("已装载的各组业务子命令都不带权限门槛：非 OP 也能查询/设定", bad == null, bad);
    }

    // ---------------- 16 帮助文案 ----------------

    private static void checkUsageLangKeysPresent() {
        String[] files = {
                "/assets/bingzhengmod/lang/zh_cn.json",
                "/assets/bingzhengmod/lang/en_us.json",
        };
        List<String> installed = new ArrayList<>();
        if (emotionInstalled) {
            installed.add("command.bingzhengmod.emotion.usage");
            installed.add("command.bingzhengmod.fatigue.usage");
            // v0.9.9：emotion 查询的「判定用 / 现值 / 基准」三列附注必须两种语言都在
            installed.add("command.bingzhengmod.emotion.line");
        }
        if (wuzangInstalled) {
            installed.add("command.bingzhengmod.wuzang.usage");
        }
        if (installed.isEmpty()) {
            return;
        }
        String[] keys = installed.toArray(new String[0]);
        StringBuilder missing = new StringBuilder();
        for (String file : files) {
            try (InputStream in = CommandSelfCheck.class.getResourceAsStream(file)) {
                if (in == null) {
                    missing.append(file).append("(文件缺失) ");
                    continue;
                }
                JsonObject json = JsonParser
                        .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                for (String key : keys) {
                    if (!json.has(key) || json.get(key).getAsString().isBlank()) {
                        missing.append(file).append(':').append(key).append(' ');
                    }
                }
            } catch (Exception e) {
                missing.append(file).append("(读取失败)").append(' ');
            }
        }
        check("已装载模块的查询命令参数对照表在中文/英文语言文件里都有文案（不会显示裸 key）",
                missing.length() == 0, "缺失：" + missing);
    }
}
