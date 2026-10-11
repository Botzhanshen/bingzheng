package com.bingzheng.screen;

import com.bingzheng.recipe.XiuheMatch;
import com.bingzheng.recipe.XiuheRecipes;
import com.bingzheng.registry.BingzhengScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.Optional;

/**
 * 修合台界面逻辑：5×5 共 25 个输入格 + 1 个产物格 + 玩家背包。
 *
 * <p>采用与原版工作台同构的模型：格子只存在于界面处理器的内存中，
 * 关闭界面时药材原样退还玩家，因此<b>不需要方块实体、也不写入任何存档数据</b>。
 *
 * <p>//TODO 后续如果要做「药材长期存放的修合台」，再引入方块实体与持久化。
 */
public class XiuheTableScreenHandler extends ScreenHandler {

    /** 纵横各五。 */
    public static final int GRID = 5;
    public static final int INPUT_SIZE = GRID * GRID;      // 25
    public static final int RESULT_INDEX = INPUT_SIZE;     // 25
    public static final int INV_START = RESULT_INDEX + 1;  // 26
    public static final int SLOT_COUNT = INV_START + 36;   // 62

    // 界面布局（与 textures/gui/xiuhe_table.png 严格对应）
    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 222;
    private static final int GRID_X = 35;
    private static final int GRID_Y = 18;
    private static final int RESULT_X = 145;
    private static final int RESULT_Y = 54;
    private static final int INV_X = 8;
    private static final int INV_Y = 130;
    private static final int HOTBAR_Y = 188;

    private final SimpleInventory input = new SimpleInventory(INPUT_SIZE);
    private final SimpleInventory result = new SimpleInventory(1);
    private boolean refreshing;

    public XiuheTableScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(BingzhengScreenHandlers.XIUHE_TABLE, syncId);

        // 5×5 输入格：一格之内只放同种药，每格最多 64 —— 原版槽位天然满足这两条
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                this.addSlot(new Slot(this.input, col + row * GRID,
                        GRID_X + col * 18, GRID_Y + row * 18));
            }
        }

        // 产物格：只能取，不能放
        this.addSlot(new XiuheResultSlot(this.result, 0, RESULT_X, RESULT_Y));

        // 玩家背包 3×9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        INV_X + col * 18, INV_Y + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }

        this.input.addListener(inv -> this.refreshResult());
        this.refreshResult();
    }

    /** 依据当前输入格重算产物格内容。 */
    private void refreshResult() {
        if (this.refreshing) {
            return;
        }
        this.refreshing = true;
        try {
            Optional<XiuheMatch> match = XiuheRecipes.match(this.input);
            this.result.setStack(0, match.map(XiuheMatch::result).orElse(ItemStack.EMPTY));
        } finally {
            this.refreshing = false;
        }
    }

    /** 取走产物时消耗输入。 */
    private void consumeInputs() {
        Optional<XiuheMatch> match = XiuheRecipes.match(this.input);
        match.ifPresent(m -> m.consume(this.input));
        this.refreshResult();
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
        if (inventory == this.input) {
            this.refreshResult();
        }
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!player.getWorld().isClient()) {
            // 把没取走的药材原样退还玩家，避免凭空消失
            for (int i = 0; i < INPUT_SIZE; i++) {
                ItemStack stack = this.input.getStack(i);
                if (!stack.isEmpty()) {
                    player.getInventory().offerOrDrop(stack);
                    this.input.setStack(i, ItemStack.EMPTY);
                }
            }
            this.result.setStack(0, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        if (index == RESULT_INDEX) {
            if (!this.insertItem(stack, INV_START, SLOT_COUNT, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickTransfer(stack, original);
            slot.onTakeItem(player, original);
        } else if (index < INPUT_SIZE) {
            if (!this.insertItem(stack, INV_START, SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.insertItem(stack, 0, INPUT_SIZE, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return original;
    }

    /** 产物格：只出不进，取走时结算输入。 */
    private final class XiuheResultSlot extends Slot {

        XiuheResultSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            XiuheTableScreenHandler.this.consumeInputs();
            super.onTakeItem(player, stack);
        }
    }
}
