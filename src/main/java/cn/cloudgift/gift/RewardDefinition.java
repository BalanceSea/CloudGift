package cn.cloudgift.gift;

public sealed interface RewardDefinition permits RewardDefinition.CommandReward, RewardDefinition.ItemReward {

    record CommandReward(String command) implements RewardDefinition {}

    record ItemReward(String itemId, int amount) implements RewardDefinition {}
}
