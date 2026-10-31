package com.kve.common.code;

import java.util.ArrayList;
import java.util.List;

/**
 * Plantillas insertables del editor de codigo.
 * Escritas para KubeJS 6
 * (Minecraft 1.20.1). Cada una indica en que carpeta de scripts funciona,
 * porque es el error mas comun al copiar ejemplos: un evento de server en
 * startup_scripts (o al reves) simplemente no hace nada.
 */
public final class KubeJsSnippets {

    private KubeJsSnippets() {}

    /** {@code folder}: "server", "startup" o "client" - carpeta de scripts donde funciona. */
    public record Snippet(String name, String folder, List<String> lines) {}

    public static final List<Snippet> ALL = List.of(
            new Snippet("Receta con forma (shaped)", "server", List.of(
                    "ServerEvents.recipes(event => {",
                    "  event.shaped('minecraft:diamond', [",
                    "    'AAA',",
                    "    'ABA',",
                    "    'AAA'",
                    "  ], {",
                    "    A: 'minecraft:coal',",
                    "    B: 'minecraft:iron_ingot'",
                    "  }).id('kubejs:mi_receta')",
                    "})")),
            new Snippet("Receta sin forma (shapeless)", "server", List.of(
                    "ServerEvents.recipes(event => {",
                    "  event.shapeless('4x minecraft:stick', [",
                    "    '#minecraft:planks'",
                    "  ]).id('kubejs:palos')",
                    "})")),
            new Snippet("Horno / fundicion", "server", List.of(
                    "ServerEvents.recipes(event => {",
                    "  event.smelting('minecraft:iron_ingot', 'minecraft:raw_iron').xp(0.7)",
                    "  event.blasting('minecraft:iron_ingot', 'minecraft:raw_iron').xp(0.7)",
                    "})")),
            new Snippet("Quitar recetas", "server", List.of(
                    "ServerEvents.recipes(event => {",
                    "  event.remove({ output: 'minecraft:stick' })",
                    "  event.remove({ id: 'minecraft:torch' })",
                    "  event.remove({ mod: 'examplemod', type: 'minecraft:crafting_shaped' })",
                    "})")),
            new Snippet("Reemplazar ingrediente", "server", List.of(
                    "ServerEvents.recipes(event => {",
                    "  event.replaceInput({ output: 'minecraft:torch' }, 'minecraft:stick', 'minecraft:blaze_rod')",
                    "})")),
            new Snippet("Tags: agregar y quitar", "server", List.of(
                    "ServerEvents.tags('item', event => {",
                    "  event.add('forge:gems', 'minecraft:emerald')",
                    "  event.remove('minecraft:planks', 'minecraft:oak_planks')",
                    "})")),
            new Snippet("Modificar item (startup)", "startup", List.of(
                    "ItemEvents.modification(event => {",
                    "  event.modify('minecraft:diamond_sword', item => {",
                    "    item.maxDamage = 3000",
                    "  })",
                    "})")),
            new Snippet("Modificar bloque (startup)", "startup", List.of(
                    "BlockEvents.modification(event => {",
                    "  event.modify('minecraft:obsidian', block => {",
                    "    block.destroySpeed = 10",
                    "  })",
                    "})")),
            new Snippet("Crear item (startup)", "startup", List.of(
                    "StartupEvents.registry('item', event => {",
                    "  event.create('mi_item').displayName('Mi item')",
                    "})")),
            new Snippet("Tooltip de item (client)", "client", List.of(
                    "ItemEvents.tooltip(event => {",
                    "  event.add('minecraft:diamond', Text.gold('Brilla mucho'))",
                    "})")),
            new Snippet("Al entrar un jugador", "server", List.of(
                    "PlayerEvents.loggedIn(event => {",
                    "  const player = event.player",
                    "  if (!player.stages.has('shovel_js_welcome')) {",
                    "    player.stages.add('shovel_js_welcome')",
                    "    player.give('minecraft:bread')",
                    "    player.tell(Text.green('Bienvenido!'))",
                    "  }",
                    "})")),
            new Snippet("Drop extra al matar un mob", "server", List.of(
                    "EntityEvents.death('minecraft:zombie', event => {",
                    "  if (Math.random() < 0.25) {",
                    "    event.entity.block.popItem('minecraft:emerald')",
                    "  }",
                    "})")),
            new Snippet("Click derecho con un item", "server", List.of(
                    "ItemEvents.rightClicked('minecraft:stick', event => {",
                    "  event.player.tell('Click con un palo')",
                    "})")),
            new Snippet("Al romper un bloque", "server", List.of(
                    "BlockEvents.broken('minecraft:diamond_ore', event => {",
                    "  event.player.tell('Encontraste diamantes')",
                    "})")),
            new Snippet("Comando propio", "server", List.of(
                    "ServerEvents.commandRegistry(event => {",
                    "  const { commands: Commands } = event",
                    "  event.register(Commands.literal('hola')",
                    "    .executes(ctx => {",
                    "      ctx.source.player.tell('Hola!')",
                    "      return 1",
                    "    }))",
                    "})")),
            new Snippet("Ocultar item en JEI (client)", "client", List.of(
                    "JEIEvents.hideItems(event => {",
                    "  event.hide('minecraft:barrier')",
                    "})"))
    );

    /**
     * Lineas del snippet listas para insertar en la posicion del cursor:
     * la primera va tal cual (se pega despues de lo que ya hay en la
     * linea), las siguientes llevan la sangria {@code indent} de la linea
     * actual para que el bloque quede alineado.
     */
    public static List<String> indented(Snippet s, String indent) {
        List<String> out = new ArrayList<>(s.lines().size());
        for (int i = 0; i < s.lines().size(); i++) {
            out.add(i == 0 ? s.lines().get(i) : indent + s.lines().get(i));
        }
        return out;
    }
}
