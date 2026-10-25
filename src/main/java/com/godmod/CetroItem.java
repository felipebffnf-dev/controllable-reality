package com.godmod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CetroItem extends Item {
    private static final String[] PODERES = {
        "Dia", "Noite", "Clima limpo", "Chuva", "Tempestade", "Teletransporte", "Transmutar blocos"
    };

    public CetroItem(Settings settings) {
        super(settings);
    }

    private int getModo(ItemStack stack) {
        return stack.getOrCreateNbt().getInt("modo");
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        // Agachado: troca o poder
        if (player.isSneaking()) {
            int modo = (getModo(stack) + 1) % PODERES.length;
            stack.getOrCreateNbt().putInt("modo", modo);
            if (!world.isClient) {
                player.sendMessage(Text.literal("Poder: " + PODERES[modo]), true);
            }
            return TypedActionResult.success(stack);
        }

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        ServerWorld sw = (ServerWorld) world;
        int modo = getModo(stack);

        switch (modo) {
            case 0 -> setHora(sw, 1000);
            case 1 -> setHora(sw, 13000);
            case 2 -> sw.setWeather(12000, 0, false, false);
            case 3 -> sw.setWeather(0, 12000, true, false);
            case 4 -> sw.setWeather(0, 12000, true, true);
            case 5 -> teletransportar(player);
            case 6 -> transmutar(sw, player);
        }

        player.getItemCooldownManager().set(this, 20);
        player.sendMessage(Text.literal("Poder usado: " + PODERES[modo]), true);
        return TypedActionResult.success(stack);
    }

    private void setHora(ServerWorld sw, long hora) {
        long agora = sw.getTimeOfDay();
        long novo = (agora - agora % 24000) + hora;
        if (novo < agora) {
            novo += 24000;
        }
        sw.setTimeOfDay(novo);
    }

    private void teletransportar(PlayerEntity player) {
        HitResult hit = player.raycast(100, 0, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hit;
            BlockPos p = bhr.getBlockPos().offset(bhr.getSide());
            player.requestTeleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        }
    }

    private void transmutar(ServerWorld sw, PlayerEntity player) {
        HitResult hit = player.raycast(50, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos centro = ((BlockHitResult) hit).getBlockPos();

        Block novo = Blocks.GOLD_BLOCK;
        ItemStack offhand = player.getOffHandStack();
        if (offhand.getItem() instanceof BlockItem blockItem) {
            novo = blockItem.getBlock();
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dy * dy + dz * dz > 4) continue;
                    BlockPos pos = centro.add(dx, dy, dz);
                    BlockState estado = sw.getBlockState(pos);
                    if (estado.isAir() || estado.getHardness(sw, pos) < 0) continue;
                    sw.setBlockState(pos, novo.getDefaultState());
                }
            }
        }
    }
}
