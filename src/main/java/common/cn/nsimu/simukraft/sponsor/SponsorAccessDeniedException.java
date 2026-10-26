package common.cn.nsimu.simukraft.sponsor;

public final class SponsorAccessDeniedException extends IllegalStateException {
    private final String entitlement;

    public SponsorAccessDeniedException(String entitlement) {
        super("Missing sponsor entitlement: " + entitlement);
        this.entitlement = entitlement;
    }

    public String entitlement() {
        return entitlement;
    }
}
