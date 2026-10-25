package com.godmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class GodMod implements ModInitializer {
    public static final String MOD_ID = "godmod";

    // Canal usado pelo menu radial para avisar o servidor qual poder foi escolhido
    public static final Identifier CANAL_MODO = new Identifier(MOD_ID, "modo");

    public static final Item CETRO = new CetroItem(new FabricItemSettings().maxCount(1));

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "cetro"), CETRO);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(CETRO));

        // Controle do tempo: roda a cada tick de cada mundo
        ServerTickEvents.START_WORLD_TICK.register(Tempo::tick);

        // Ao fechar o mundo, devolve tudo ao normal
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Tempo.limpar());

        // Menu radial: o cliente manda o número do poder escolhido
        ServerPlayNetworking.registerGlobalReceiver(CANAL_MODO, (server, player, handler, buf, responseSender) -> {
            int modo = buf.readInt();
            server.execute(() -> {
                ItemStack stack = player.getMainHandStack();
                if (!stack.isOf(CETRO)) {
                    stack = player.getOffHandStack();
                }
                if (!stack.isOf(CETRO)) return;
                if (modo < 0 || modo >= CetroItem.PODERES.length) return;

                stack.getOrCreateNbt().putInt("modo", modo);
                player.sendMessage(Text.literal("Poder: " + CetroItem.PODERES[modo]
                    + CetroItem.extra(stack, modo)), true);
            });
        });
    }
}
