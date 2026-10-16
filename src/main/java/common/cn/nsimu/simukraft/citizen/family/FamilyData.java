package common.cn.nsimu.simukraft.citizen.family;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class FamilyData {
    private final UUID familyId;
    private UUID cityId;
    private UUID husbandId;
    private UUID wifeId;
    private final List<UUID> childIds = new ArrayList<>();
    private UUID paternalFamilyId;
    private UUID maternalFamilyId;
    private int generation;
    private FamilyStatus status;

    public FamilyData(UUID familyId, UUID cityId) {
        this.familyId = familyId;
        this.cityId = cityId;
        this.status = FamilyStatus.FORMING;
        this.generation = 0;
    }

    public UUID familyId() {
        return familyId;
    }

    public UUID cityId() {
        return cityId;
    }

    public void setCityId(UUID cityId) {
        this.cityId = cityId;
    }

    public UUID husbandId() {
        return husbandId;
    }

    public void setHusbandId(UUID husbandId) {
        this.husbandId = husbandId;
    }

    public UUID wifeId() {
        return wifeId;
    }

    public void setWifeId(UUID wifeId) {
        this.wifeId = wifeId;
    }

    public List<UUID> childIds() {
        return Collections.unmodifiableList(childIds);
    }

    public void addChild(UUID childId) {
        if (childId != null) childIds.add(childId);
    }

    public void removeChild(UUID childId) {
        childIds.remove(childId);
    }

    public UUID paternalFamilyId() {
        return paternalFamilyId;
    }

    public void setPaternalFamilyId(UUID paternalFamilyId) {
        this.paternalFamilyId = paternalFamilyId;
    }

    public UUID maternalFamilyId() {
        return maternalFamilyId;
    }

    public void setMaternalFamilyId(UUID maternalFamilyId) {
        this.maternalFamilyId = maternalFamilyId;
    }

    public int generation() {
        return generation;
    }

    public void setGeneration(int generation) {
        this.generation = Math.max(0, generation);
    }

    public FamilyStatus status() {
        return status;
    }

    public void setStatus(FamilyStatus status) {
        this.status = status != null ? status : FamilyStatus.FORMING;
    }
}
