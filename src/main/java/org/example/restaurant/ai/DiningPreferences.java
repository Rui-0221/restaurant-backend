package org.example.restaurant.ai;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
/** 从聊天识别的当前需求；不读取或约束手动购物车。 */
public record DiningPreferences(List<String> exclusions, Integer maxSpicyLevel,
                                Integer partySize, BigDecimal budget, List<String> cuisines) {
    public static DiningPreferences empty() {
        return new DiningPreferences(List.of(), null, null, null, List.of());
    }
    public DiningPreferences retainingExclusions(DiningPreferences previous) {
        var retained = new LinkedHashSet<>(previous.exclusions());
        retained.addAll(exclusions);
        Integer spicy = maxSpicyLevel == null ? previous.maxSpicyLevel() : maxSpicyLevel;
        return new DiningPreferences(List.copyOf(retained), spicy, partySize, budget, cuisines);
    }
}
