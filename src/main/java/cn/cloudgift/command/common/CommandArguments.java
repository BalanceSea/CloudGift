package cn.cloudgift.command.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable command arguments with bounds and subcommand helpers. */
public final class CommandArguments {

    private static final CommandArguments EMPTY = new CommandArguments(List.of());

    private final List<String> values;

    private CommandArguments(List<String> values) {
        this.values = List.copyOf(values);
    }

    public static CommandArguments empty() {
        return EMPTY;
    }

    public static CommandArguments of(String... args) {
        if (args == null || args.length == 0) {
            return EMPTY;
        }
        List<String> copy = new ArrayList<>(args.length);
        Collections.addAll(copy, args);
        return new CommandArguments(copy);
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean hasExactly(int expected) {
        return values.size() == expected;
    }

    public String get(int index) {
        return values.get(index);
    }

    public List<String> values() {
        return values;
    }

    public CommandArguments withoutFirst() {
        return values.isEmpty() ? EMPTY : new CommandArguments(values.subList(1, values.size()));
    }
}
