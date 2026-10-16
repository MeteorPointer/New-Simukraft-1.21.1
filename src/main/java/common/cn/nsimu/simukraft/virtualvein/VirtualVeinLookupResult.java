package common.cn.nsimu.simukraft.virtualvein;

public record VirtualVeinLookupResult(VirtualVeinLookupStatus status, VirtualVeinFieldProfile profile) {
    public boolean isReady() {
        return status == VirtualVeinLookupStatus.READY && profile != null;
    }
}
