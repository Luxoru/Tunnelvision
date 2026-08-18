package dev.luxoru.tunnelvision.detection;

import java.util.EnumSet;
import java.util.Set;

/**
 * Decides whether a verdict is {@link Verdict#blocked() blocked}, so callers
 * that just want a yes or no (a proxy, a login form) don't need their own rules.
 *
 * @param categories - an address is blocked when any of these categories matched
 * @param riskThreshold - an address is blocked when its risk is at least this, above 1 to disable
 */
public record BlockPolicy(Set<Category> categories, double riskThreshold) {

    public static final BlockPolicy DEFAULT = new BlockPolicy(EnumSet.of(Category.VPN, Category.TOR, Category.PROXY), 1.0);

}
