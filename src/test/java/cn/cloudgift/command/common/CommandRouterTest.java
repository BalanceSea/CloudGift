package cn.cloudgift.command.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class CommandRouterTest {

    @Test
    void findsCanonicalNamesAndAliasesCaseInsensitively() {
        TestModule reload = new TestModule("reload", List.of("r"), "");
        CommandRouter router = new CommandRouter(List.of(reload));

        assertSame(reload, router.find("RELOAD").orElseThrow());
        assertSame(reload, router.find("R").orElseThrow());
    }

    @Test
    void rejectsDuplicateCanonicalNamesAndAliases() {
        TestModule first = new TestModule("reload", List.of("r"), "");
        TestModule duplicate = new TestModule("help", List.of("reload"), "");

        assertThrows(IllegalArgumentException.class, () -> new CommandRouter(List.of(first, duplicate)));
    }

    @Test
    void filtersTopLevelCompletionByPermission() {
        TestModule list = new TestModule("list", List.of("ls"), "");
        TestModule reload = new TestModule("reload", List.of("r"), "cloudgift.command.reload");
        CommandRouter router = new CommandRouter(List.of(list, reload));

        assertEquals(List.of("list", "ls"), router.completeNames(sender(Set.of()), ""));
        assertEquals(List.of("reload"), router.completeNames(
                sender(Set.of("cloudgift.command.reload")), "re"));
        assertEquals(List.of(), router.completeNames(sender(Set.of()), "re"));
    }

    private static CommandSender sender(Set<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandRouterTest.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("hasPermission")) {
                        return permissions.contains(String.valueOf(arguments[0]));
                    }
                    if (method.getName().equals("toString")) {
                        return "TestSender";
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == arguments[0];
                    }
                    return null;
                });
    }

    private static final class TestModule implements CommandModule {

        private final String name;
        private final List<String> aliases;
        private final String permission;

        private TestModule(String name, List<String> aliases, String permission) {
            this.name = name;
            this.aliases = aliases;
            this.permission = permission;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }

        @Override
        public String permission() {
            return permission;
        }

        @Override
        public String helpKey() {
            return "test." + name;
        }

        @Override
        public boolean execute(CommandContext context) {
            return true;
        }
    }
}
