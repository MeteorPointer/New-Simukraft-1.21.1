package common.cn.nsimu.simukraft.path;

/**
 * PathSectionStamp: 一次采样覆盖的区段，以及采样当时的版本号。
 */
record PathSectionStamp(long[] sections, long[] epochs) {
}
