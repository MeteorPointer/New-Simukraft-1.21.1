package common.cn.kafei.simukraft.event;

import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/**
 * 建筑建造事件，在建造开始和完成时由服务端触发。
 * <p>其他模组或内部系统可通过 {@code NeoForge.EVENT_BUS} 监听建造流程。
 */
public abstract class BuildingConstructionEvent extends Event {
    private final ServerLevel level;
    private final BuildingTaskData task;
    private final CitizenData citizen;

    protected BuildingConstructionEvent(ServerLevel level, BuildingTaskData task, CitizenData citizen) {
        this.level = level;
        this.task = task;
        this.citizen = citizen;
    }

    public ServerLevel level() {
        return level;
    }

    public BuildingTaskData task() {
        return task;
    }

    public CitizenData citizen() {
        return citizen;
    }

    /** 建造开始时触发，在 {@code BuilderConstructionService.startTask} 之后。 */
    public static final class Start extends BuildingConstructionEvent {
        public Start(ServerLevel level, BuildingTaskData task, CitizenData citizen) {
            super(level, task, citizen);
        }
    }

    /** 建造完成时触发，在建筑已注册到 {@code PlacedBuildingService} 之后。 */
    public static final class Complete extends BuildingConstructionEvent {
        private final PlacedBuildingRecord placedBuilding;

        public Complete(ServerLevel level, BuildingTaskData task, CitizenData citizen, PlacedBuildingRecord placedBuilding) {
            super(level, task, citizen);
            this.placedBuilding = placedBuilding;
        }

        public PlacedBuildingRecord placedBuilding() {
            return placedBuilding;
        }
    }
}
