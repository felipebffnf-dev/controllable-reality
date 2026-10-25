package com.godmod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class Poderes {

    private static final String TAG_ALIADO = "godmod_aliado";
    private static final double RAIO_MOBS = 24; // blocos ao redor de você

    // ================= PODER SOBRE MOBS =================

    private static List<Entity> criaturasPerto(ServerWorld sw, PlayerEntity p) {
        return sw.getOtherEntities(p, p.getBoundingBox().expand(RAIO_MOBS),
            e -> e instanceof LivingEntity && !(e instanceof PlayerEntity) && !(e instanceof ArmorStandEntity));
    }

    // Aliados e pets domados são poupados
    private static boolean ehAliado(Entity e) {
        if (e.getCommandTags().contains(TAG_ALIADO)) return true;
        if (e instanceof TameableEntity t && t.isTamed()) return true;
        if (e instanceof IronGolemEntity g && g.isPlayerCreated()) return true;
        return false;
    }

    public static String aniquilar(ServerWorld sw, ServerPlayerEntity p) {
        int n = 0;
        for (Entity e : criaturasPerto(sw, p)) {
            if (ehAliado(e)) continue;
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getBodyY(0.5), e.getZ(),
                15, 0.3, 0.5, 0.3, 0.03);
            e.kill();
            n++;
        }
        return n + " criaturas aniquiladas.";
    }

    public static String curar(ServerWorld sw, ServerPlayerEntity p) {
        int n = 0;
        for (Entity e : criaturasPerto(sw, p)) {
            LivingEntity vivo = (LivingEntity) e;
            vivo.clearStatusEffects();
            vivo.setHealth(vivo.getMaxHealth());
            sw.spawnParticles(ParticleTypes.HEART, e.getX(), e.getBodyY(1.0), e.getZ(),
                6, 0.3, 0.3, 0.3, 0.02);
            n++;
        }
        return n + " criaturas curadas.";
    }

    private static WolfEntity novoLobo(ServerWorld sw, PlayerEntity dono, double x, double y, double z) {
        WolfEntity lobo = EntityType.WOLF.create(sw);
        if (lobo == null) return null;
        lobo.refreshPositionAndAngles(x, y, z, 0, 0);
        lobo.setTamed(true);
        lobo.setOwner(dono);
        lobo.setPersistent();
        lobo.addCommandTag(TAG_ALIADO);
        lobo.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 600, 1));
        sw.spawnEntity(lobo);
        return lobo;
    }

    /** Converte todos os monstros da área em lobos domados que lutam por você. */
    public static String converter(ServerWorld sw, ServerPlayerEntity p) {
        int n = 0;
        for (Entity e : criaturasPerto(sw, p)) {
            if (!(e instanceof Monster) || ehAliado(e)) continue;
            double x = e.getX(), y = e.getY(), z = e.getZ();
            e.discard();
            if (novoLobo(sw, p, x, y, z) != null) {
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 1, z, 10, 0.4, 0.5, 0.4, 0.02);
                n++;
            }
        }
        return n + " monstros convertidos em aliados.";
    }

    public static String invocarAliados(ServerWorld sw, ServerPlayerEntity p) {
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3;
            double x = p.getX() + Math.cos(ang) * 3;
            double z = p.getZ() + Math.sin(ang) * 3;

            if (i < 2) {
                IronGolemEntity golem = EntityType.IRON_GOLEM.create(sw);
                if (golem == null) continue;
                golem.refreshPositionAndAngles(x, p.getY(), z, 0, 0);
                golem.setPlayerCreated(true);
                golem.setPersistent();
                golem.addCommandTag(TAG_ALIADO);
                sw.spawnEntity(golem);
            } else {
                novoLobo(sw, p, x, p.getY(), z);
            }
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, p.getY() + 1, z, 8, 0.4, 0.5, 0.4, 0.02);
        }
        return "Aliados invocados: 2 golems de ferro e 4 lobos.";
    }

    // ================= CRIAÇÃO =================

    private static void colocar(ServerWorld sw, BlockPos pos, BlockState estado) {
        if (sw.getBlockState(pos).getHardness(sw, pos) < 0) return; // não mexe em bedrock
        sw.setBlockState(pos, estado);
    }

    private static void colocar(ServerWorld sw, BlockPos pos, Block bloco) {
        colocar(sw, pos, bloco.getDefaultState());
    }

    private static Block blocoDaMao(PlayerEntity p, Block padrao) {
        ItemStack off = p.getOffHandStack();
        if (off.getItem() instanceof BlockItem item) {
            return item.getBlock();
        }
        return padrao;
    }

    /** tipo: 0 Casa, 1 Torre, 2 Pirâmide, 3 Plataforma, 4 Cubo de blocos */
    public static String criarEstrutura(ServerWorld sw, ServerPlayerEntity p, int tipo) {
        BlockPos o;
        HitResult hit = p.raycast(60, 0, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hit;
            o = bhr.getBlockPos().offset(bhr.getSide());
        } else {
            Vec3d ponto = p.getCameraPosVec(1.0f).add(p.getRotationVec(1.0f).multiply(12));
            o = BlockPos.ofFloored(ponto);
        }

        switch (tipo) {
            case 0 -> casa(sw, o);
            case 1 -> torre(sw, o);
            case 2 -> piramide(sw, o);
            case 3 -> {
                Block b = blocoDaMao(p, Blocks.STONE_BRICKS);
                for (int dx = -5; dx <= 5; dx++)
                    for (int dz = -5; dz <= 5; dz++)
                        colocar(sw, o.add(dx, 0, dz), b);
            }
            case 4 -> {
                Block b = blocoDaMao(p, Blocks.STONE);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dy = 0; dy <= 4; dy++)
                        for (int dz = -2; dz <= 2; dz++)
                            colocar(sw, o.add(dx, dy, dz), b);
            }
        }

        sw.spawnParticles(ParticleTypes.END_ROD, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5,
            40, 1.5, 1.5, 1.5, 0.05);
        return "Criação concluída!";
    }

    private static void casa(ServerWorld sw, BlockPos o) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                colocar(sw, o.add(dx, -1, dz), Blocks.COBBLESTONE);
                boolean borda = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                for (int dy = 0; dy <= 3; dy++) {
                    colocar(sw, o.add(dx, dy, dz), borda ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
                colocar(sw, o.add(dx, 4, dz), Blocks.OAK_PLANKS);
            }
        }
        // porta (abertura)
        colocar(sw, o.add(0, 0, 3), Blocks.AIR);
        colocar(sw, o.add(0, 1, 3), Blocks.AIR);
        // janelas
        colocar(sw, o.add(3, 1, 0), Blocks.GLASS);
        colocar(sw, o.add(-3, 1, 0), Blocks.GLASS);
        colocar(sw, o.add(0, 1, -3), Blocks.GLASS);
        // tocha
        colocar(sw, o.add(-2, 0, -2), Blocks.TORCH);
    }

    private static void torre(ServerWorld sw, BlockPos o) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                colocar(sw, o.add(dx, -1, dz), Blocks.STONE_BRICKS);
                boolean borda = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                for (int dy = 0; dy <= 15; dy++) {
                    colocar(sw, o.add(dx, dy, dz), borda ? Blocks.STONE_BRICKS : Blocks.AIR);
                }
            }
        }
        // plataforma do topo com muralha
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                colocar(sw, o.add(dx, 16, dz), Blocks.STONE_BRICKS);
                boolean borda = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                colocar(sw, o.add(dx, 17, dz), borda ? Blocks.STONE_BRICKS : Blocks.AIR);
            }
        }
        // porta
        colocar(sw, o.add(0, 0, 2), Blocks.AIR);
        colocar(sw, o.add(0, 1, 2), Blocks.AIR);
        // escada até o topo
        BlockState escada = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.SOUTH);
        for (int dy = 0; dy <= 16; dy++) {
            colocar(sw, o.add(0, dy, -1), escada);
        }
    }

    private static void piramide(ServerWorld sw, BlockPos o) {
        for (int nivel = 0; nivel <= 8; nivel++) {
            int raio = 8 - nivel;
            for (int dx = -raio; dx <= raio; dx++) {
                for (int dz = -raio; dz <= raio; dz++) {
                    colocar(sw, o.add(dx, nivel, dz), nivel == 8 ? Blocks.GOLD_BLOCK : Blocks.SANDSTONE);
                }
            }
        }
    }

    // ================= JULGAMENTO DIVINO =================

    public static String raio(ServerWorld sw, ServerPlayerEntity p) {
        HitResult hit = p.raycast(200, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return "Mire em algum bloco para lançar o raio.";
        }
        LightningEntity raio = EntityType.LIGHTNING_BOLT.create(sw);
        if (raio == null) {
            return "Não foi possível criar o raio.";
        }
        raio.refreshPositionAfterTeleport(hit.getPos());
        raio.setChanneler(p);
        sw.spawnEntity(raio);
        return "Julgamento divino: raio!";
    }

    public static String meteoro(ServerWorld sw, ServerPlayerEntity p) {
        HitResult hit = p.raycast(150, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return "Mire em algum bloco para chamar o meteoro.";
        }
        Vec3d alvo = hit.getPos();
        FireballEntity bola = new FireballEntity(sw, p, 0, -1, 0, 6);
        double y = Math.min(alvo.y + 50, sw.getTopY() - 1);
        bola.setPosition(alvo.x, y, alvo.z);
        bola.setVelocity(0, -1.5, 0);
        sw.spawnEntity(bola);
        return "Julgamento divino: meteoro a caminho!";
    }
}
