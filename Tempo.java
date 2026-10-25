package com.godmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Tempo {

    // ================= PARAR O TEMPO =================
    private static final int DURACAO_PARADA = 200; // 10 segundos (20 ticks = 1s)

    private static ServerWorld mundoParado = null;
    private static int ticksRestantes = 0;
    // UUID -> { tinhaIaDesligada, tinhaSemGravidade }
    private static final Map<UUID, boolean[]> congelados = new HashMap<>();

    private static final List<GameRules.Key<GameRules.BooleanRule>> REGRAS = List.of(
        GameRules.DO_DAYLIGHT_CYCLE,
        GameRules.DO_WEATHER_CYCLE,
        GameRules.DO_MOB_SPAWNING
    );
    private static final Map<GameRules.Key<GameRules.BooleanRule>, Boolean> regrasAntes = new HashMap<>();

    public static boolean estaParado() {
        return mundoParado != null;
    }

    /** Liga o tempo parado. Se já estiver parado, libera. */
    public static void pararTempo(ServerWorld sw, PlayerEntity jogador) {
        if (estaParado()) {
            liberar();
            return;
        }

        mundoParado = sw;
        ticksRestantes = DURACAO_PARADA;

        for (Entity e : sw.getOtherEntities(jogador, jogador.getBoundingBox().expand(64),
                alvo -> !(alvo instanceof PlayerEntity))) {
            boolean iaJaDesligada = false;
            if (e instanceof MobEntity mob1) {
                iaJaDesligada = mob1.isAiDisabled();
                mob1.setAiDisabled(true);
            }
            congelados.put(e.getUuid(), new boolean[]{iaJaDesligada, e.hasNoGravity()});
            e.setNoGravity(true);
        }

        MinecraftServer server = sw.getServer();
        GameRules regras = sw.getGameRules();
        regrasAntes.clear();
        for (GameRules.Key<GameRules.BooleanRule> chave : REGRAS) {
            regrasAntes.put(chave, regras.getBoolean(chave));
            regras.get(chave).set(false, server);
        }
    }

    public static void liberar() {
        if (mundoParado == null) {
            return;
        }
        ServerWorld sw = mundoParado;

        for (Map.Entry<UUID, boolean[]> par : congelados.entrySet()) {
            Entity e = sw.getEntity(par.getKey());
            if (e == null) continue;
            if (e instanceof MobEntity mob2) {
                mob2.setAiDisabled(par.getValue()[0]);
            }
            e.setNoGravity(par.getValue()[1]);
        }
        congelados.clear();

        MinecraftServer server = sw.getServer();
        for (GameRules.Key<GameRules.BooleanRule> chave : REGRAS) {
            Boolean antes = regrasAntes.get(chave);
            if (antes != null) {
                sw.getGameRules().get(chave).set(antes, server);
            }
        }
        regrasAntes.clear();

        mundoParado = null;
        ticksRestantes = 0;
    }

    // ================= VOLTAR NO TEMPO =================
    private static final int INTERVALO_FOTO = 10; // uma "foto" a cada 10 ticks (0,5 s)
    private static final int MAX_FOTOS = 20;      // guarda os últimos 10 s
    private static final int FOTOS_PARA_VOLTAR = 10; // volta 5 s

    private static class Foto {
        final ServerWorld mundo;
        final double x, y, z;
        final float yaw, pitch, vida;
        final int fome;
        // UUID -> { x, y, z, yaw, pitch, vida }
        final Map<UUID, double[]> entidades = new HashMap<>();

        Foto(ServerWorld mundo, ServerPlayerEntity p) {
            this.mundo = mundo;
            this.x = p.getX();
            this.y = p.getY();
            this.z = p.getZ();
            this.yaw = p.getYaw();
            this.pitch = p.getPitch();
            this.vida = p.getHealth();
            this.fome = p.getHungerManager().getFoodLevel();
        }
    }

    private static final Map<UUID, ArrayDeque<Foto>> historico = new HashMap<>();

    private static void gravar(ServerWorld world, ServerPlayerEntity p) {
        ArrayDeque<Foto> fila = historico.computeIfAbsent(p.getUuid(), k -> new ArrayDeque<>());

        // Só grava o passado de quem está com o cetro
        if (!p.isAlive() || !p.getInventory().containsAny(Set.of(GodMod.CETRO))) {
            fila.clear();
            return;
        }
        // Com o tempo parado não há passado novo
        if (estaParado()) {
            return;
        }

        Foto foto = new Foto(world, p);
        for (Entity e : world.getOtherEntities(p, p.getBoundingBox().expand(32),
                alvo -> alvo instanceof LivingEntity && !(alvo instanceof PlayerEntity))) {
            LivingEntity vivo = (LivingEntity) e;
            foto.entidades.put(vivo.getUuid(), new double[]{
                vivo.getX(), vivo.getY(), vivo.getZ(), vivo.getYaw(), vivo.getPitch(), vivo.getHealth()
            });
        }

        fila.addLast(foto);
        while (fila.size() > MAX_FOTOS) {
            fila.removeFirst();
        }
    }

    /** Retorna null se deu certo, ou uma mensagem de erro. */
    public static String voltarNoTempo(ServerWorld sw, ServerPlayerEntity p) {
        ArrayDeque<Foto> fila = historico.get(p.getUuid());
        if (fila == null || fila.isEmpty()) {
            return "Ainda não há passado gravado. Segure o cetro por alguns segundos.";
        }

        List<Foto> lista = new ArrayList<>(fila);
        Foto alvo = lista.get(Math.max(0, lista.size() - FOTOS_PARA_VOLTAR));

        if (alvo.mundo != sw) {
            fila.clear();
            return "Você mudou de dimensão, o passado se perdeu.";
        }

        // Jogador
        p.teleport(sw, alvo.x, alvo.y, alvo.z, alvo.yaw, alvo.pitch);
        p.setVelocity(Vec3d.ZERO);
        p.velocityModified = true;
        p.fallDistance = 0;
        p.setHealth(Math.min(alvo.vida, p.getMaxHealth()));
        p.getHungerManager().setFoodLevel(alvo.fome);

        // Criaturas ao redor (as que ainda estão vivas)
        for (Map.Entry<UUID, double[]> par : alvo.entidades.entrySet()) {
            Entity e = sw.getEntity(par.getKey());
            if (e instanceof LivingEntity vivo && vivo.isAlive()) {
                double[] d = par.getValue();
                vivo.refreshPositionAndAngles(d[0], d[1], d[2], (float) d[3], (float) d[4]);
                vivo.setVelocity(Vec3d.ZERO);
                vivo.fallDistance = 0;
                vivo.setHealth((float) Math.min(d[5], vivo.getMaxHealth()));
            }
        }

        sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getBodyY(0.5), p.getZ(),
            60, 0.5, 0.8, 0.5, 0.1);

        fila.clear();
        return null;
    }

    // ================= TICK =================
    public static void tick(ServerWorld world) {
        if (world == mundoParado) {
            ticksRestantes--;
            for (UUID id : congelados.keySet()) {
                Entity e = world.getEntity(id);
                if (e != null) {
                    e.setVelocity(Vec3d.ZERO);
                }
            }
            if (ticksRestantes <= 0) {
                liberar();
            }
        }

        if (world.getTime() % INTERVALO_FOTO == 0) {
            for (ServerPlayerEntity p : world.getPlayers()) {
                gravar(world, p);
            }
        }
    }

    public static void limpar() {
        liberar();
        historico.clear();
    }
}
