package emu.nebula.game.mall;

import emu.nebula.proto.Public.ChangeInfo;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * Separate real reward delta from popup display data
 * Decouple settlement logic from client UI, prevent data mutation on reuse
 */
@Getter
public final class MallOrderCollectResult {
    private final ChangeInfo stateChange;
    private final ChangeInfo displayChange;
    @Getter(AccessLevel.NONE)
    private final ChangeInfo stateNotifyChange;

    private MallOrderCollectResult(ChangeInfo stateChange, ChangeInfo displayChange) {
        this.stateChange = copyOf(stateChange);
        this.displayChange = copyOf(displayChange);
        this.stateNotifyChange = buildStateNotifyChange(this.stateChange, this.displayChange);
    }

    public static MallOrderCollectResult empty() {
        return new MallOrderCollectResult(null, null);
    }

    public static MallOrderCollectResult of(ChangeInfo change) {
        return new MallOrderCollectResult(change, change);
    }

    public static MallOrderCollectResult split(ChangeInfo stateChange, ChangeInfo displayChange) {
        return new MallOrderCollectResult(stateChange, displayChange);
    }

    public boolean hasStateChange() {
        return !stateChange.isEmpty();
    }

    public boolean hasDisplayChange() {
        return !displayChange.isEmpty();
    }

    public boolean hasStateNotifyChange() {
        return !stateNotifyChange.isEmpty();
    }

    /**
     * Returns the minimal follow-up state delta that still needs a notify after
     * the client has already consumed displayChange from the main response.
     */
    public ChangeInfo getStateNotifyChange() {
        return ChangeInfo.newInstance().copyFrom(stateNotifyChange);
    }

    private static ChangeInfo copyOf(ChangeInfo change) {
        if (change == null || change.isEmpty()) {
            return ChangeInfo.newInstance();
        }

        return ChangeInfo.newInstance().copyFrom(change);
    }

    private static ChangeInfo buildStateNotifyChange(ChangeInfo stateChange, ChangeInfo displayChange) {
        if (stateChange == null || stateChange.isEmpty()) {
            return ChangeInfo.newInstance();
        }

        if (displayChange == null || displayChange.isEmpty()) {
            return ChangeInfo.newInstance().copyFrom(stateChange);
        }

        var notify = ChangeInfo.newInstance();
        for (var stateProp : stateChange.getProps()) {
            boolean duplicatedInDisplay = false;
            for (var displayProp : displayChange.getProps()) {
                if (stateProp.equals(displayProp)) {
                    duplicatedInDisplay = true;
                    break;
                }
            }

            if (!duplicatedInDisplay) {
                notify.addProps(stateProp.clone());
            }
        }

        return notify;
    }

}
