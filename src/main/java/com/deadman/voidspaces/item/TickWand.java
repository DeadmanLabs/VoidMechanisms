package com.deadman.voidspaces.item;

import com.deadman.voidspaces.helpers.Dimensional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class TickWand extends Item {

    // Tick rate multipliers: 1x = normal speed, higher = faster (everything: crops, machines, mobs)
    private static final int[] PRESETS = {1, 5, 10, 20, 50};

    public TickWand() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            Dimensional wrapper = Dimensional.getWrapper(level.dimension());
            if (wrapper == null) {
                player.displayClientMessage(
                    Component.literal("Tick Wand only works inside a void dimension.").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }

            int current = wrapper.getSimulationSpeed();
            int next = cyclePreset(current, player.isShiftKeyDown());
            wrapper.setTickRate(next);
            String label = next == 1 ? "Normal speed" : next + "x speed";
            player.displayClientMessage(
                Component.literal("Tick Rate: " + label).withStyle(ChatFormatting.AQUA), true);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static int cyclePreset(int current, boolean reverse) {
        int idx = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < PRESETS.length; i++) {
            int dist = Math.abs(PRESETS[i] - current);
            if (dist < best) { best = dist; idx = i; }
        }
        if (reverse) {
            return PRESETS[(idx - 1 + PRESETS.length) % PRESETS.length];
        } else {
            return PRESETS[(idx + 1) % PRESETS.length];
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click: speed up dimension").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Shift+Right-click: slow down dimension").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Presets: 1x / 5x / 10x / 20x / 50x").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Speeds up everything: crops, machines, mobs").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Only works inside void dimensions").withStyle(ChatFormatting.DARK_GRAY));
    }
}
