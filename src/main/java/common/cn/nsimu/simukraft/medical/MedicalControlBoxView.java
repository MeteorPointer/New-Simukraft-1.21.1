package common.cn.nsimu.simukraft.medical;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

/** 医疗控制箱的客户端只读视图。 */
public record MedicalControlBoxView(BlockPos boxPos,
                                    boolean hasBuilding,
                                    String buildingName,
                                    boolean definitionValid,
                                    String definitionName,
                                    int serviceRangeRings,
                                    int coveredChunkCount,
                                    String statusKey,
                                    boolean hasDoctor,
                                    UUID doctorId,
                                    String doctorName,
                                    int bedCount,
                                    int occupiedBedCount,
                                    List<PatientEntry> patients) {
    public MedicalControlBoxView {
        boxPos = boxPos != null ? boxPos.immutable() : BlockPos.ZERO;
        buildingName = buildingName != null ? buildingName : "";
        definitionName = definitionName != null ? definitionName : "";
        statusKey = statusKey != null ? statusKey : "gui.simukraft.medical.status.no_building";
        doctorName = doctorName != null ? doctorName : "";
        patients = patients != null ? List.copyOf(patients) : List.of();
    }

    /** PatientEntry：界面中展示的单个住院患者。 */
    public record PatientEntry(UUID citizenId, String name, String conditionKey, double health) {
        public PatientEntry {
            name = name != null ? name : "";
            conditionKey = conditionKey != null ? conditionKey : "";
        }
    }
}
