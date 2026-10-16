package common.cn.nsimu.simukraft.path;

/**
 * Outcome of one {@link ActiveNavigation#tick} call.
 */
enum ActiveTickResult {
    RUNNING,
    COMPLETE,
    REPATH
}
