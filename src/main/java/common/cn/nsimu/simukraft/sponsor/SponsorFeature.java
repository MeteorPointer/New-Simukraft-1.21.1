package common.cn.nsimu.simukraft.sponsor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记未来的赞助功能入口。当前版本只提供框架，不给任何现有功能加限制。
 * 被标记的入口必须在方法体中调用 {@link SponsorGate#require(String)}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface SponsorFeature {
    String entitlement() default "sponsor";

    String id();
}
