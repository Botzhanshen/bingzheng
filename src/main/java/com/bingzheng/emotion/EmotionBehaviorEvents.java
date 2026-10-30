package com.bingzheng.emotion;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;

/**
 * 七情行为驱动的<b>事件接线</b>（v0.8.0）。
 *
 * <p><b>全部是后置 / 附加监听，绝不取消任何原版行为</b>：
 * {@code ALLOW_DAMAGE} 一律 {@code return true}（纯观察，只读伤害来源），
 * {@code UseBlockCallback} 一律返回 {@code PASS}（不拦截使用），其余都是「发生后通知」。
 *
 * <p><b>数据源全部是 MC 原版可观测事件</b>，不引用神耗 / 体耗 / 五脏数值 —— 防止自激环。
 * 本类只做「把原版事件翻译成点数」，写入统一交给 {@link EmotionBehaviorPoints#tick}。
 */
public final class EmotionBehaviorEvents {

    /** 视为「专注操作」的方块：工作台 / 附魔台 / 酿造台 / 铁砧 / 锻造台 / 砂轮 / 织布机 / 切石机 / 制图台 / 制箭台。 */
    private static final Set<Block> FOCUS_BLOCKS = Set.of(
            Blocks.CRAFTING_TABLE, Blocks.ENCHANTING_TABLE, Blocks.BREWING_STAND,
            Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL, Blocks.SMITHING_TABLE,
            Blocks.GRINDSTONE, Blocks.LOOM, Blocks.STONECUTTER,
            Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE);

    private EmotionBehaviorEvents() {
    }

    public static void register() {
        // ① 受击：纯观察（永远 return true，不取消、不改伤害）
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && amount > 0.0F) {
                onPlayerHurt(player, source);
            }
            return true;
        });

        // ② 死亡：玩家死亡（悲 +3 / 开死亡掉落窗口）、击杀 Boss（喜 +3）、驯服生物死亡（悲 +2）
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                EmotionBehaviorPoints.onPlayerDeath(player);
                return;
            }
            onLivingDeath(entity, source);
        });

        // ③ 实体出现：雷击（惊）、物品实体（登记死亡掉落）
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> onEntityLoad(entity, world));

        // ④ 实体消失：物品结局 → 悲
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> onEntityUnload(entity, world));

        // ⑤ 交互方块：专注操作（思）
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                BlockState state = world.getBlockState(hitResult.getBlockPos());
                if (FOCUS_BLOCKS.contains(state.getBlock())) {
                    EmotionBehaviorPoints.onFocusBlock(serverPlayer);
                    // v0.9.1：同一行为也累积神耗的「专注」来源（两套数值体系各自独立）
                    EmotionFatigue.onFocusBlock(serverPlayer);
                }
                {
                    // v0.9.7：加工 / 容器类方块计神耗（附魔台双倍，10 秒防刷）
                    EmotionFatigue.onUseBlock(serverPlayer, state);
                }
            }
            return ActionResult.PASS;
        });
    }

    // ---------------- 受击 ----------------

    /** 怒 = 实际受击（敌对生物 / 玩家 / 爆炸）；坠落属恐（保留项）。 */
    private static void onPlayerHurt(ServerPlayerEntity player, DamageSource source) {
        if (source.isIn(DamageTypeTags.IS_FALL)) {
            if (player.fallDistance >= EmotionConfig.FEAR_FALL_DISTANCE) {
                EmotionBehaviorPoints.addPoint(player, EmotionType.FEAR, EmotionConfig.FEAR_POINTS_FALL);
            }
            return;
        }
        if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
            EmotionBehaviorPoints.addPoint(player, EmotionType.ANGER, EmotionConfig.ANGER_POINTS_EXPLOSION);
            return;
        }
        Entity attacker = source.getAttacker();
        if (attacker instanceof ServerPlayerEntity) {
            EmotionBehaviorPoints.addPoint(player, EmotionType.ANGER, EmotionConfig.ANGER_POINTS_PLAYER_HIT);
        } else if (attacker instanceof HostileEntity) {
            EmotionBehaviorPoints.addPoint(player, EmotionType.ANGER, EmotionConfig.ANGER_POINTS_MOB_HIT);
        }
    }

    // ---------------- 死亡 ----------------

    private static void onLivingDeath(Entity entity, DamageSource source) {
        EntityType<?> type = entity.getType();
        boolean boss = type == EntityType.ENDER_DRAGON || type == EntityType.WITHER
                || type == EntityType.ELDER_GUARDIAN;
        if (boss && source.getAttacker() instanceof ServerPlayerEntity killer) {
            EmotionBehaviorPoints.addPoint(killer, EmotionType.JOY, EmotionConfig.JOY_POINTS_BOSS_KILL);
        }
        if (entity instanceof TameableEntity pet && pet.isTamed() && pet.getOwnerUuid() != null
                && pet.getServer() != null) {
            ServerPlayerEntity owner = pet.getServer().getPlayerManager().getPlayer(pet.getOwnerUuid());
            if (owner != null) {
                EmotionBehaviorPoints.addPoint(owner, EmotionType.GRIEF, EmotionConfig.GRIEF_POINTS_PET_DEATH);
            }
        }
    }

    // ---------------- 实体出现 ----------------

    private static void onEntityLoad(Entity entity, ServerWorld world) {
        // 惊：雷暴中雷击落在玩家 16 格内（一次性 +2，直接改稳定值 —— 惊不入点框架）
        if (entity instanceof LightningEntity && entity.getServer() != null) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.squaredDistanceTo(entity) <= EmotionConfig.FRIGHT_LIGHTNING_RADIUS
                        * EmotionConfig.FRIGHT_LIGHTNING_RADIUS) {
                    EmotionBehaviorPoints.addDirect(player, EmotionType.FRIGHT,
                            EmotionConfig.FRIGHT_POINTS_LIGHTNING);
                }
            }
            return;
        }
        // 悲：登记「死亡掉落物」，它们日后消失不再单独计悲点
        if (entity instanceof ItemEntity item) {
            EmotionBehaviorPoints.markDeathDropIfInWindow(item.getUuid(), world.getTime(),
                    item.getX(), item.getY(), item.getZ());
        }
    }

    // ---------------- 物品结局 → 悲 ----------------

    private static void onEntityUnload(Entity entity, ServerWorld world) {
        if (!(entity instanceof ItemEntity item)) {
            return;
        }
        // 【v0.8.1 修复】这里**绝不能做任何世界查询**。
        // 该回调位于原版「实体管理器迭代 / 区块卸载」的栈内，此时调用
        // world.getBlockState(...) 会经 World#getChunk(x,z,FULL,**true**) 触发**同步区块加载**，
        // 加载会向实体管理器写入 → 正在被迭代的 HashMap 被修改 → ConcurrentModificationException。
        // 因此只把实体自身的字段塞进队列，世界查询推迟到 tick 之外的 drainItemFates()。
        EmotionBehaviorPoints.queueItemFate(item, world);
    }

    /** 供自检与调试：当前认定为「专注方块」的集合大小。 */
    public static int focusBlockCount() {
        return FOCUS_BLOCKS.size();
    }

    /** 供自检：专注方块判定（不依赖世界）。 */
    public static boolean isFocusBlock(Block block) {
        return FOCUS_BLOCKS.contains(block);
    }

    /** 供自检：受击分类结果（0 = 不记点）。 */
    public static float angerPointsFor(boolean explosion, boolean playerAttacker, boolean hostileAttacker) {
        if (explosion) {
            return EmotionConfig.ANGER_POINTS_EXPLOSION;
        }
        if (playerAttacker) {
            return EmotionConfig.ANGER_POINTS_PLAYER_HIT;
        }
        return hostileAttacker ? EmotionConfig.ANGER_POINTS_MOB_HIT : 0.0F;
    }

    /** 便捷：玩家列表（避免调用方重复写 world.getPlayers()）。 */
    static List<ServerPlayerEntity> players(ServerWorld world) {
        return world.getPlayers();
    }
}
