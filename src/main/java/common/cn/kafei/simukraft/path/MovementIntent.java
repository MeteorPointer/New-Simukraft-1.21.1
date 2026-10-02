package common.cn.kafei.simukraft.path;

public enum MovementIntent {
    WALK,
    // WANDER：优先级最低的闲逛意图，路径失败时直接放弃，不触发传送。
    WANDER,
    RUN,
    // FOLLOW：玩家在 NPC 信息界面启用的手动跟随意图。
    FOLLOW,
    WORK,
    // SELF_FEEDING：自动买饭流程独占导航意图，防止普通工作移动抢占。
    SELF_FEEDING,
    // MEDICAL：前往医院床位接受治疗。
    MEDICAL,
    RETURN_HOME,
    // FLEE：被玩家攻击后的短距逃跑，可抢占其它导航，路径失败不传送。
    FLEE;

    /** allowsTeleportFallback：远距或寻路失败时是否允许传送兜底。 */
    public boolean allowsTeleportFallback() {
        return this != WANDER && this != FLEE;
    }
}
