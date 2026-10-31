package com.kve.common.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventEntryRunCommandBlacklistTest {

    private static EventEntry withRunCommand(String command) {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:blacklist_probe";
        e.enabled = true;
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "probe";

        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.RUN_COMMAND;
        a.command = command;
        e.actions.add(a);
        return e;
    }

    @Test
    void allowsAnOrdinaryCommand() {
        assertEquals(java.util.List.of(), withRunCommand("say hello").validate());
    }

    @Test
    void blocksNewlyAddedEntries() {
        for (String cmd : new String[] {
                "whitelist add Notch", "ban Notch griefing", "ban-ip 1.2.3.4",
                "pardon Notch", "pardon-ip 1.2.3.4", "save-off", "save-on", "save-all",
                "forceload add 0 0", "datapack disable vanilla", "reload",
                "kick Notch", "gamerule doFireTick false", "difficulty peaceful", "publish"
        }) {
            assertFalse(withRunCommand(cmd).validate().isEmpty(), "expected '" + cmd + "' to be blacklisted");
        }
    }

    @Test
    void blocksOriginalEntriesStill() {
        for (String cmd : new String[] {"op Notch", "deop Notch", "stop"}) {
            assertFalse(withRunCommand(cmd).validate().isEmpty(), "expected '" + cmd + "' to be blacklisted");
        }
    }

    @Test
    void stripsLeadingSlashBeforeMatching() {
        assertFalse(withRunCommand("/op Notch").validate().isEmpty(),
                "a command copy-pasted with a leading '/' must still be caught");
    }

    @Test
    void isCaseInsensitive() {
        assertFalse(withRunCommand("OP Notch").validate().isEmpty());
    }
}
