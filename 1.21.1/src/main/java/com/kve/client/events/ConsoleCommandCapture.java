package com.kve.client.events;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Linea de comandos de la consola del editor de codigo. Los comandos como {@code /kubejs errors} o {@code /reload}
 * responden por chat, no en los logs que ya muestra la consola - asi que
 * durante unos segundos despues de mandar un comando desde la consola, los
 * mensajes de sistema que llegan se copian aca para mostrarlos ahi mismo.
 * El chat normal no se toca (el mensaje tambien sale en el chat).
 */
@OnlyIn(Dist.CLIENT)
public final class ConsoleCommandCapture {

    private ConsoleCommandCapture() {}

    private static final long CAPTURE_MS = 8000L;
    private static final int MAX_PENDING = 200;

    private static long captureUntil = 0L;
    private static final List<String> PENDING = new ArrayList<>();

    /** Llamado justo antes de mandar un comando desde la consola. */
    public static synchronized void startCapture() {
        captureUntil = System.currentTimeMillis() + CAPTURE_MS;
    }

    /** Devuelve (y vacia) las lineas capturadas desde la ultima llamada. */
    public static synchronized List<String> drain() {
        if (PENDING.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(PENDING);
        PENDING.clear();
        return out;
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (!event.isSystem()) return;
        synchronized (ConsoleCommandCapture.class) {
            if (System.currentTimeMillis() > captureUntil) return;
            if (PENDING.size() >= MAX_PENDING) return;
            for (String line : event.getMessage().getString().split("\n")) PENDING.add(line);
        }
    }
}
