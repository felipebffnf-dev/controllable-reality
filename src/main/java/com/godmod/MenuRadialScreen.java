package com.godmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Menu radial do Cetro da Realidade.
 * Nível 0: categorias. Nível 1: poderes da categoria escolhida.
 */
public class MenuRadialScreen extends Screen {

    private static final String[] CATEGORIAS = {
        "Tempo", "Clima", "Mundo", "Mobs", "Criação", "Julgamento"
    };

    private static final Item[] ICONES_CATEGORIAS = {
        Items.CLOCK, Items.WATER_BUCKET, Items.GRASS_BLOCK,
        Items.ZOMBIE_HEAD, Items.CRAFTING_TABLE, Items.LIGHTNING_ROD
    };

    // Índices dos poderes (os mesmos de CetroItem.PODERES) em cada categoria
    private static final int[][] PODERES_DA_CATEGORIA = {
        {0, 1, 9, 10},        // Tempo: Dia, Noite, Parar o tempo, Voltar no tempo
        {2, 3, 4},            // Clima: limpo, chuva, tempestade
        {5, 6, 7, 8},         // Mundo: teletransporte, transmutar, escolher/aplicar bioma
        {11, 12, 13, 14},     // Mobs: aniquilar, curar, converter, invocar
        {15, 16},             // Criação: escolher/criar estrutura
        {17, 18}              // Julgamento: raio, meteoro
    };

    // Na mesma ordem de CetroItem.PODERES
    private static final Item[] ICONES = {
        Items.SUNFLOWER, Items.INK_SAC, Items.LIGHT_BLUE_DYE, Items.WATER_BUCKET, Items.TRIDENT,
        Items.ENDER_PEARL, Items.GOLD_INGOT, Items.MAP, Items.GRASS_BLOCK,
        Items.CLOCK, Items.RECOVERY_COMPASS,
        Items.WITHER_SKELETON_SKULL, Items.GOLDEN_APPLE, Items.BONE, Items.TOTEM_OF_UNDYING,
        Items.BRICKS, Items.CRAFTING_TABLE,
        Items.LIGHTNING_ROD, Items.FIRE_CHARGE
    };

    private int nivel = 0;
    private int categoria = -1;

    private int cx, cy, r1, r2;

    public MenuRadialScreen() {
        super(Text.literal("Menu do Cetro"));
    }

    @Override
    protected void init() {
        cx = width / 2;
        cy = height / 2 - 10;
        r2 = Math.min(125, height / 2 - 45);
        r1 = (int) (r2 * 0.35);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------- dados do nível atual ----------

    private int totalItens() {
        return nivel == 0 ? CATEGORIAS.length : PODERES_DA_CATEGORIA[categoria].length;
    }

    private String nome(int i) {
        return nivel == 0 ? CATEGORIAS[i] : CetroItem.PODERES[PODERES_DA_CATEGORIA[categoria][i]];
    }

    private Item icone(int i) {
        return nivel == 0 ? ICONES_CATEGORIAS[i] : ICONES[PODERES_DA_CATEGORIA[categoria][i]];
    }

    private int lerModoAtual() {
        if (client == null || client.player == null) return -1;
        ItemStack s = client.player.getMainHandStack();
        if (!s.isOf(GodMod.CETRO)) s = client.player.getOffHandStack();
        if (!s.isOf(GodMod.CETRO)) return -1;
        return s.hasNbt() ? s.getNbt().getInt("modo") : 0;
    }

    private boolean ehAtual(int i, int modoAtual) {
        if (modoAtual < 0) return false;
        if (nivel == 1) {
            return PODERES_DA_CATEGORIA[categoria][i] == modoAtual;
        }
        for (int p : PODERES_DA_CATEGORIA[i]) {
            if (p == modoAtual) return true;
        }
        return false;
    }

    // ---------- mouse ----------

    private int indiceSob(double mx, double my, int n) {
        double dx = mx - cx;
        double dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < r1 * 0.6 || dist > r2 + 40) return -1;

        double theta = Math.atan2(dx, -dy); // 0 = topo, sentido horário
        if (theta < 0) theta += 2 * Math.PI;
        double passo = 2 * Math.PI / n;
        return (int) Math.floor((theta + passo / 2) / passo) % n;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1) {
            voltar();
            return true;
        }
        if (button == 0) {
            int hov = indiceSob(mx, my, totalItens());
            if (hov >= 0) {
                if (nivel == 0) {
                    categoria = hov;
                    nivel = 1;
                } else {
                    escolher(PODERES_DA_CATEGORIA[categoria][hov]);
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && nivel == 1) {
            voltar();
            return true;
        }
        if (GodModClient.TECLA_MENU != null && GodModClient.TECLA_MENU.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void voltar() {
        if (nivel == 1) {
            nivel = 0;
            categoria = -1;
        } else {
            close();
        }
    }

    private void escolher(int modo) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(modo);
        ClientPlayNetworking.send(GodMod.CANAL_MODO, buf);
        close();
    }

    // ---------- desenho ----------

    private void fatia(DrawContext ctx, int i, int n, int cor) {
        float passo = (float) (2 * Math.PI / n);
        float centro = i * passo;
        float folga = 0.03f;
        float a0 = centro - passo / 2 + folga;
        float a1 = centro + passo / 2 - folga;
        int passos = 28;

        float a = ((cor >>> 24) & 255) / 255f;
        float r = ((cor >> 16) & 255) / 255f;
        float g = ((cor >> 8) & 255) / 255f;
        float b = (cor & 255) / 255f;

        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int k = 0; k <= passos; k++) {
            float ang = a0 + (a1 - a0) * k / passos;
            float s = (float) Math.sin(ang);
            float c = (float) Math.cos(ang);
            buf.vertex(m, cx + s * r2, cy - c * r2, 0).color(r, g, b, a).next();
            buf.vertex(m, cx + s * r1, cy - c * r1, 0).color(r, g, b, a).next();
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x55000000);

        int n = totalItens();
        int hov = indiceSob(mx, my, n);
        int modoAtual = lerModoAtual();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        for (int i = 0; i < n; i++) {
            boolean sobre = (i == hov);
            boolean atual = ehAtual(i, modoAtual);
            int cor;
            if (sobre && atual) cor = 0xE890D8F0;
            else if (sobre) cor = 0xE04FA8E0;
            else if (atual) cor = 0xC0C89A28;
            else cor = 0xB0141418;
            fatia(ctx, i, n, cor);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // ícones e nomes
        float passo = (float) (2 * Math.PI / n);
        float rm = (r1 + r2) / 2f;
        for (int i = 0; i < n; i++) {
            float ang = i * passo;
            int px = (int) (cx + Math.sin(ang) * rm);
            int py = (int) (cy - Math.cos(ang) * rm);

            String[] linhas = nome(i).split(" ");
            int altura = 16 + linhas.length * 10;
            int topo = py - altura / 2;

            ctx.drawItem(new ItemStack(icone(i)), px - 8, topo);
            for (int l = 0; l < linhas.length; l++) {
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(linhas[l]),
                    px, topo + 18 + l * 10, 0xFFFFFF);
            }
        }

        // centro
        String titulo = nivel == 0 ? "Cetro" : CATEGORIAS[categoria];
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(titulo), cx, cy - 4, 0xFFE9A8);

        // legenda embaixo (como "Recall" no menu de referência)
        String legenda = hov >= 0 ? nome(hov) : "";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(legenda), cx, cy + r2 + 22, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Clique: selecionar    Botão direito / ESC: voltar    R: fechar"),
            cx, cy + r2 + 40, 0xAAAAAA);

        super.render(ctx, mx, my, delta);
    }
}
