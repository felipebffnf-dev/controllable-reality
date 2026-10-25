package com.godmod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
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
        "Dia", "Noite", "Clima limpo", "Chuva", "Tempestade", "Teletransporte", "Transmutar blocos",
        "Escolher bioma", "Aplicar bioma", "Parar o tempo", "Voltar no tempo"
    };

    private static final String[] BIOMAS = {
        "plains", "desert", "snowy_plains", "jungle", "savanna", "badlands", "swamp",
        "mushroom_fields", "cherry_grove", "dark_forest", "ice_spikes", "deep_dark",
        "nether_wastes", "warped_forest", "the_end"
    };
    private static final String[] BIOMAS_NOMES = {
        "Planície", "Deserto", "Planície nevada", "Selva", "Savana", "Badlands", "Pântano",
        "Ilha de cogumelos", "Bosque de cerejeiras", "Floresta sombria", "Espinhos de gelo", "Cavernas profundas",
        "Ermos do Nether", "Floresta distorcida", "O End"
    };

    // Raio da mudança de bioma, em chunks (4 = área de 9x9 chunks ao seu redor)
    private static final int RAIO_CHUNKS = 4;
    // Altura de cada fatia (o comando fillbiome tem limite de volume)
    private static final int FATIA = 96;

    public CetroItem(Settings settings) {
        super(settings);
    }

    private int getModo(ItemStack stack) {
        return stack.getOrCreateNbt().getInt("modo");
    }

    private int getBioma(ItemStack stack) {
        return stack.getOrCreateNbt().getInt("bioma");
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
                String extra = (modo == 8) ? " (" + BIOMAS_NOMES[getBioma(stack)] + ")" : "";
                player.sendMessage(Text.literal("Poder: " + PODERES[modo] + extra), true);
            }
            return TypedActionResult.success(stack);
        }

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        ServerWorld sw = (ServerWorld) world;
        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        int modo = getModo(stack);
        String msg = "Poder usado: " + PODERES[modo];

        switch (modo) {
            case 0 -> setHora(sw, 1000);
            case 1 -> setHora(sw, 13000);
            case 2 -> sw.setWeather(12000, 0, false, false);
            case 3 -> sw.setWeather(0, 12000, true, false);
            case 4 -> sw.setWeather(0, 12000, true, true);
            case 5 -> teletransportar(player);
            case 6 -> transmutar(sw, player);
            case 7 -> {
                int b = (getBioma(stack) + 1) % BIOMAS.length;
                stack.getOrCreateNbt().putInt("bioma", b);
                msg = "Bioma escolhido: " + BIOMAS_NOMES[b];
            }
            case 8 -> {
                int b = getBioma(stack);
                aplicarBioma(sw, sp, b);
                msg = "Bioma alterado para: " + BIOMAS_NOMES[b];
            }
            case 9 -> {
                Tempo.pararTempo(sw, sp);
                msg = Tempo.estaParado() ? "O tempo parou por 10 segundos!" : "O tempo voltou a correr.";
            }
            case 10 -> {
                String erro = Tempo.voltarNoTempo(sw, sp);
                msg = (erro != null) ? erro : "Você voltou 5 segundos no tempo!";
            }
        }

        player.getItemCooldownManager().set(this, modo == 7 ? 5 : 20);
        player.sendMessage(Text.literal(msg), true);
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

    // Usa o comando /fillbiome do próprio Minecraft, em fatias, ao redor do jogador
    private void aplicarBioma(ServerWorld sw, ServerPlayerEntity jogador, int idx) {
        ServerCommandSource fonte = jogador.getCommandSource().withLevel(4).withSilent();
        CommandManager comandos = sw.getServer().getCommandManager();

        int cx = jogador.getChunkPos().x;
        int cz = jogador.getChunkPos().z;

        for (int chunkX = cx - RAIO_CHUNKS; chunkX <= cx + RAIO_CHUNKS; chunkX++) {
            for (int chunkZ = cz - RAIO_CHUNKS; chunkZ <= cz + RAIO_CHUNKS; chunkZ++) {
                int x1 = chunkX * 16;
                int x2 = x1 + 15;
                int z1 = chunkZ * 16;
                int z2 = z1 + 15;
                for (int y = sw.getBottomY(); y < sw.getTopY(); y += FATIA) {
                    int y2 = Math.min(y + FATIA - 1, sw.getTopY() - 1);
                    comandos.executeWithPrefix(fonte, "fillbiome " + x1 + " " + y + " " + z1 + " "
                        + x2 + " " + y2 + " " + z2 + " minecraft:" + BIOMAS[idx]);
                }
            }
        }
    }
}
