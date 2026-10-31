package com.kve.common.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EventEntry.Condition#permissionLevel()} resuelve el nodo a un
 * nivel vanilla real (0-4 o "op"), y {@link EventEntry#validate()} debe
 * rechazar cualquier otra cosa.
 */
class EventEntryPermissionConditionTest {

    private static EventEntry.Condition permission(String node) {
        EventEntry.Condition c = new EventEntry.Condition();
        c.type = EventEntry.ConditionType.PERMISSION;
        c.permissionNode = node;
        return c;
    }

    private static EventEntry withCondition(EventEntry.Condition c) {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:perm_probe";
        e.enabled = true;
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "probe";
        e.conditions.add(c);

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "hi";
        e.actions.add(msg);
        return e;
    }

    @Test
    void blankMeansNoRestriction() {
        assertNull(permission("").permissionLevel());
        assertNull(permission("   ").permissionLevel());
        assertEquals(java.util.List.of(), withCondition(permission("")).validate());
    }

    @Test
    void opResolvesToLevel2() {
        assertEquals(2, permission("op").permissionLevel());
        assertEquals(2, permission("OP").permissionLevel());
        assertEquals(java.util.List.of(), withCondition(permission("op")).validate());
    }

    @Test
    void numericLevelsZeroToFourAreValid() {
        for (int lvl = 0; lvl <= 4; lvl++) {
            assertEquals(lvl, permission(String.valueOf(lvl)).permissionLevel());
            assertEquals(java.util.List.of(), withCondition(permission(String.valueOf(lvl))).validate());
        }
    }

    @Test
    void outOfRangeOrGarbageIsInvalid() {
        for (String bad : new String[] {"5", "-1", "admin", "kve.staff"}) {
            assertEquals(-1, permission(bad).permissionLevel(), "expected '" + bad + "' to be unparseable");
            assertFalse(withCondition(permission(bad)).validate().isEmpty(), "expected '" + bad + "' to fail validate()");
        }
    }
}
