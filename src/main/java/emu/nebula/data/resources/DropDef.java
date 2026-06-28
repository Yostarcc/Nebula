package emu.nebula.data.resources;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;

@Getter
@ResourceType(name = "Drop.json")
public class DropDef extends BaseDef {
    private int DropId;
    private int PkgId;

    private static final Int2ObjectMap<IntList> DROPS = new Int2ObjectOpenHashMap<>();

    @Override
    public int getId() {
        return DropId;
    }

    @Override
    public void onLoad() {
        var packageList = DROPS.computeIfAbsent(this.DropId, i -> new IntArrayList());
        packageList.add(this.PkgId);
    }

    public static IntList getPackageIds(int dropId) {
        return DROPS.get(dropId);
    }
}
