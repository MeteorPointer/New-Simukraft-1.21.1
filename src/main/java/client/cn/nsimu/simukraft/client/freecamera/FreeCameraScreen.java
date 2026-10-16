package client.cn.nsimu.simukraft.client.freecamera;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 标记接口：实现此接口的 Screen 在自由视角激活时，允许 WASD 飞行与鼠标转向继续作用于相机
 * （而不是被屏幕拦截）。键鼠 Mixin 以此判断是否放行，避免把白名单写死成具体屏幕类。
 */
@OnlyIn(Dist.CLIENT)
public interface FreeCameraScreen {
}
