package cn.cloudgift.command.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandArgumentsTest {

    @Test
    void removesOnlyTheFirstArgument() {
        CommandArguments arguments = CommandArguments.of("reload", "extra", "value");

        assertEquals(List.of("extra", "value"), arguments.withoutFirst().values());
        assertEquals("reload", arguments.get(0));
    }

    @Test
    void returnsSharedEmptyInstanceForEmptyArguments() {
        assertSame(CommandArguments.empty(), CommandArguments.of());
        assertTrue(CommandArguments.empty().isEmpty());
        assertEquals(0, CommandArguments.empty().size());
    }

    @Test
    void checksExactArgumentCount() {
        CommandArguments arguments = CommandArguments.of("one", "two");

        assertTrue(arguments.hasExactly(2));
        assertFalse(arguments.hasExactly(1));
    }
}
